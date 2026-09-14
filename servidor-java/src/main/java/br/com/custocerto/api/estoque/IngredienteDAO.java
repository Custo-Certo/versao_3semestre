package br.com.custocerto.api.estoque;

import br.com.custocerto.api.config.Banco;
import br.com.custocerto.api.modelo.ErroApp;
import br.com.custocerto.api.modelo.HistoricoItem;
import br.com.custocerto.api.modelo.Ingrediente;
import br.com.custocerto.api.modelo.Lote;

import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Acesso a dados de ingredientes e movimentacoes, no MySQL custo_certo.
 *
 * Reproduz o contrato do backend Node/SQLite anterior para que o painel
 * (public/) funcione sem mudar uma linha:
 *   - listarTodos devolve cada ingrediente COM seus lotes (entradas);
 *   - criar registra a entrada inicial no historico;
 *   - registrarCompra aumenta estoque e grava a compra (evolucao de preco);
 *   - abaterEstoque (pesagem/retirada) baixa o estoque e grava a saida, tudo
 *     numa transacao.
 */
public final class IngredienteDAO {

    private IngredienteDAO() {}

    // =====================================================================
    // LEITURA
    // =====================================================================

    /**
     * Lista todos os ingredientes (mais recentes primeiro), cada um com a lista
     * de lotes (entradas) que o painel usa para o FIFO visual e os alertas de
     * validade por lote.
     */
    public static List<Ingrediente> listarTodos() throws SQLException {
        try (Connection con = Banco.conexao()) {
            List<Ingrediente> ingredientes = new ArrayList<>();
            try (PreparedStatement ps = con.prepareStatement(
                     "SELECT * FROM ingredientes ORDER BY id DESC");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ingredientes.add(mapear(rs));
            }

            // Lotes = entradas, agrupadas por ingrediente, mais antiga primeiro.
            Map<Integer, List<Lote>> lotesPorId = new LinkedHashMap<>();
            try (PreparedStatement ps = con.prepareStatement(
                     "SELECT id, ingrediente_id, quantidade, preco_unitario, data, validade "
                   + "FROM movimentacoes_estoque WHERE tipo = 'entrada' "
                   + "ORDER BY data ASC, id ASC");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int ingId = rs.getInt("ingrediente_id");
                    lotesPorId.computeIfAbsent(ingId, k -> new ArrayList<>()).add(new Lote(
                        rs.getInt("id"),
                        rs.getDouble("quantidade"),
                        rs.getDouble("preco_unitario"),
                        textoData(rs.getDate("data")),
                        textoData(rs.getDate("validade"))
                    ));
                }
            }

            List<Ingrediente> resultado = new ArrayList<>(ingredientes.size());
            for (Ingrediente ing : ingredientes) {
                resultado.add(ing.comLotes(lotesPorId.getOrDefault(ing.id(), List.of())));
            }
            return resultado;
        }
    }

    /** Historico de compras (entradas) com nome do ingrediente. Para a evolucao de precos. */
    public static List<HistoricoItem> listarHistorico() throws SQLException {
        String sql =
            "SELECT m.id, m.ingrediente_id AS produto_id, i.nome, i.unidade, "
          + "       m.quantidade AS qtd, m.preco_unitario AS preco, m.data "
          + "FROM movimentacoes_estoque m "
          + "JOIN ingredientes i ON i.id = m.ingrediente_id "
          + "WHERE m.tipo = 'entrada' "
          + "ORDER BY m.data DESC, m.id DESC";
        try (Connection con = Banco.conexao();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<HistoricoItem> lista = new ArrayList<>();
            while (rs.next()) {
                lista.add(new HistoricoItem(
                    rs.getInt("id"),
                    rs.getInt("produto_id"),
                    rs.getString("nome"),
                    rs.getString("unidade"),
                    rs.getDouble("qtd"),
                    rs.getDouble("preco"),
                    textoData(rs.getDate("data"))
                ));
            }
            return lista;
        }
    }

    /** Busca por id em conexao propria. Devolve null se nao existir (lotes vem nulo). */
    public static Ingrediente buscarPorId(int id) throws SQLException {
        try (Connection con = Banco.conexao()) {
            return buscar(con, id);
        }
    }

    /** Busca por id usando a conexao recebida (para uso dentro de transacao). */
    private static Ingrediente buscar(Connection con, int id) throws SQLException {
        try (PreparedStatement ps =
                 con.prepareStatement("SELECT * FROM ingredientes WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    // =====================================================================
    // ESCRITA
    // =====================================================================

    /**
     * Cria um ingrediente e, se qtd > 0, registra a entrada inicial no
     * historico (para aparecer como lote e alimentar a evolucao de preco).
     * Tudo numa transacao. 409 se o nome ja existir.
     */
    public static Ingrediente criar(String nome, String unidade, double preco,
                                    double qtd, Double qtdMax, String validade) throws SQLException {
        double max = (qtdMax != null && qtdMax > 0) ? qtdMax : qtd;
        try (Connection con = Banco.conexao()) {
            con.setAutoCommit(false);
            try {
                int novoId;
                try (PreparedStatement ins = con.prepareStatement(
                        "INSERT INTO ingredientes (nome, unidade, preco, qtd, qtd_max, validade) "
                      + "VALUES (?, ?, ?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ins.setString(1, nome);
                    ins.setString(2, unidade);
                    ins.setDouble(3, preco);
                    ins.setDouble(4, qtd);
                    ins.setDouble(5, max);
                    setDataOuNull(ins, 6, validade);
                    ins.executeUpdate();
                    try (ResultSet gk = ins.getGeneratedKeys()) {
                        gk.next();
                        novoId = gk.getInt(1);
                    }
                }

                if (qtd > 0) {
                    inserirMovimentacao(con, novoId, "entrada", qtd, preco,
                        "Cadastro inicial", validade);
                }

                con.commit();
                return buscar(con, novoId);
            } catch (SQLIntegrityConstraintViolationException dup) {
                con.rollback();
                throw new ErroApp("Ja existe um ingrediente com esse nome.", 409);
            } catch (Exception e) {
                con.rollback();
                throw e;
            }
        }
    }

    /**
     * Atualizacao parcial (PUT /ingredientes/{id}). NUNCA mexe em qtd -- estoque
     * so muda por movimentacao. Campos nulos sao ignorados. 404 se nao existir.
     */
    public static Ingrediente atualizar(int id, String nome, String unidade,
                                        Double preco, Double qtdMax, String validade,
                                        boolean validadeInformada) throws SQLException {
        try (Connection con = Banco.conexao()) {
            if (buscar(con, id) == null) throw new ErroApp("Ingrediente nao encontrado", 404);

            // Cada campo enviado vira uma coluna no SET e um binder que sabe se
            // aplicar no PreparedStatement, na mesma ordem.
            List<String> colunas = new ArrayList<>();
            List<Binder> binders = new ArrayList<>();
            if (nome != null)      { colunas.add("nome = ?");     binders.add((ps, i) -> ps.setString(i, nome)); }
            if (unidade != null)   { colunas.add("unidade = ?");  binders.add((ps, i) -> ps.setString(i, unidade)); }
            if (preco != null)     { colunas.add("preco = ?");    binders.add((ps, i) -> ps.setDouble(i, preco)); }
            if (qtdMax != null)    { colunas.add("qtd_max = ?");  binders.add((ps, i) -> ps.setDouble(i, qtdMax)); }
            if (validadeInformada) { colunas.add("validade = ?"); binders.add((ps, i) -> setDataOuNull(ps, i, validade)); }

            if (colunas.isEmpty()) return buscar(con, id);

            colunas.add("atualizado_em = NOW()"); // sem parametro
            String sql = "UPDATE ingredientes SET " + String.join(", ", colunas) + " WHERE id = ?";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                int i = 1;
                for (Binder b : binders) b.bind(ps, i++);
                ps.setInt(i, id);
                ps.executeUpdate();
            }
            return buscar(con, id);
        }
    }

    /** Aplica um valor a um parametro do PreparedStatement (para o UPDATE dinamico). */
    @FunctionalInterface
    private interface Binder {
        void bind(PreparedStatement ps, int idx) throws SQLException;
    }

    /**
     * Nova compra (POST /ingredientes/{id}/compras): aumenta qtd, sobe qtd_max
     * para o novo total, atualiza o preco e (se informada) a validade, e grava
     * a entrada no historico. Tudo numa transacao. 404 se nao existir.
     */
    public static Ingrediente registrarCompra(int id, double quantidade, double precoUnitario,
                                              String validade, String observacao) throws SQLException {
        try (Connection con = Banco.conexao()) {
            con.setAutoCommit(false);
            try {
                Ingrediente atual = buscar(con, id);
                if (atual == null) throw new ErroApp("Ingrediente nao encontrado", 404);

                double novaQtd = atual.qtd() + quantidade;
                // Mantem a validade do cabecalho se a compra nao trouxe uma nova
                // (o lote registra a sua propria validade de qualquer forma).
                String validadeFinal = (validade != null) ? validade : atual.validade();

                try (PreparedStatement up = con.prepareStatement(
                        "UPDATE ingredientes SET qtd = ?, qtd_max = ?, preco = ?, "
                      + "validade = ?, atualizado_em = NOW() WHERE id = ?")) {
                    up.setDouble(1, novaQtd);
                    up.setDouble(2, novaQtd);
                    up.setDouble(3, precoUnitario);
                    setDataOuNull(up, 4, validadeFinal);
                    up.setInt(5, id);
                    up.executeUpdate();
                }

                inserirMovimentacao(con, id, "entrada", quantidade, precoUnitario,
                    observacao != null ? observacao : "Nova compra", validade);

                con.commit();
                return buscar(con, id);
            } catch (Exception e) {
                con.rollback();
                throw e;
            }
        }
    }

    /**
     * Abate consumo do estoque e registra a saida -- TUDO numa transacao.
     * E o que POST /balanca/confirmar e POST /ingredientes/{id}/retirada
     * disparam. 404 se nao existir, 409 se faltar estoque.
     */
    public static Ingrediente abaterEstoque(int id, double qtd, String obs) throws SQLException {
        try (Connection con = Banco.conexao()) {
            con.setAutoCommit(false);
            try {
                Ingrediente atual = buscar(con, id);
                if (atual == null) throw new ErroApp("Ingrediente nao encontrado", 404);
                if (qtd > atual.qtd())
                    throw new ErroApp("Estoque insuficiente. Disponivel: "
                        + atual.qtd() + " " + atual.unidade(), 409);

                double nova = Math.max(0, atual.qtd() - qtd);

                try (PreparedStatement up = con.prepareStatement(
                        "UPDATE ingredientes SET qtd = ?, atualizado_em = NOW() WHERE id = ?")) {
                    up.setDouble(1, nova);
                    up.setInt(2, id);
                    up.executeUpdate();
                }
                // Saida guarda o preco atual do ingrediente (custo do consumo).
                inserirMovimentacao(con, id, "saida", qtd, atual.preco(), obs, null);

                con.commit();
                return buscar(con, id);
            } catch (Exception e) {
                con.rollback();
                throw e;
            }
        }
    }

    /** Remove o ingrediente. ON DELETE CASCADE limpa as movimentacoes. */
    public static boolean remover(int id) throws SQLException {
        try (Connection con = Banco.conexao();
             PreparedStatement ps = con.prepareStatement("DELETE FROM ingredientes WHERE id = ?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    // =====================================================================
    // AUXILIARES
    // =====================================================================

    /** INSERT padrao em movimentacoes_estoque (entrada ou saida), data = hoje. */
    private static void inserirMovimentacao(Connection con, int ingredienteId, String tipo,
                                            double quantidade, double precoUnitario,
                                            String observacao, String validade) throws SQLException {
        try (PreparedStatement mov = con.prepareStatement(
                "INSERT INTO movimentacoes_estoque "
              + "(ingrediente_id, tipo, quantidade, preco_unitario, observacao, validade, data) "
              + "VALUES (?, ?, ?, ?, ?, ?, CURDATE())")) {
            mov.setInt(1, ingredienteId);
            mov.setString(2, tipo);
            mov.setDouble(3, quantidade);
            mov.setDouble(4, precoUnitario);
            mov.setString(5, observacao);
            setDataOuNull(mov, 6, validade);
            mov.executeUpdate();
        }
    }

    /** Converte a linha do ResultSet no record Ingrediente (sem lotes ainda). */
    private static Ingrediente mapear(ResultSet rs) throws SQLException {
        return new Ingrediente(
            rs.getInt("id"),
            rs.getString("nome"),
            rs.getString("unidade"),
            rs.getDouble("preco"),
            rs.getDouble("qtd"),
            rs.getDouble("qtd_max"),
            textoData(rs.getDate("validade")),
            null
        );
    }

    /** Data do banco -> "yyyy-MM-dd" (ou null). */
    private static String textoData(Date d) {
        return d != null ? d.toString() : null;
    }

    /** Seta um parametro DATE a partir de "yyyy-MM-dd", ou NULL se vazio. */
    private static void setDataOuNull(PreparedStatement ps, int idx, String iso) throws SQLException {
        if (iso == null || iso.isBlank()) ps.setNull(idx, Types.DATE);
        else ps.setDate(idx, Date.valueOf(iso));
    }
}
