package br.com.custocerto.api.receita;

import br.com.custocerto.api.config.Banco;
import br.com.custocerto.api.modelo.ErroApp;
import br.com.custocerto.api.modelo.ExecucaoReceita;
import br.com.custocerto.api.modelo.ItemReceita;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Acesso a dados das execucoes de receita.
 *
 * Uma execucao e iniciada com um nome; cada pesagem confirmada enquanto ela
 * esta aberta vira uma saida de estoque vinculada (execucao_id). Ao finalizar,
 * o CMV e calculado a partir das saidas (quantidade x preco_unitario), que ja
 * guardam o preco do ingrediente no momento da pesagem.
 *
 * O abatimento e o estorno acontecem SEMPRE em transacao, no mesmo molde do
 * IngredienteDAO.
 */
public final class ReceitaDAO {

    private ReceitaDAO() {}

    // =====================================================================
    // LEITURA
    // =====================================================================

    /** Cabecalho da execucao (sem itens nem calculos). null se nao existir. */
    private static ExecucaoReceita buscarCabecalho(Connection con, int id) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT id, nome, status, porcoes, preco_venda, inicio, fim "
              + "FROM execucoes_receita WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return cabecalhoDoRs(rs);
            }
        }
    }

    private static ExecucaoReceita cabecalhoDoRs(ResultSet rs) throws SQLException {
        Integer porcoes = (Integer) rs.getObject("porcoes");
        BigDecimal pv = rs.getBigDecimal("preco_venda");
        Double precoVenda = (pv != null) ? pv.doubleValue() : null;
        return new ExecucaoReceita(
            rs.getInt("id"), rs.getString("nome"), rs.getString("status"),
            porcoes, precoVenda,
            textoDataHora(rs.getTimestamp("inicio")), textoDataHora(rs.getTimestamp("fim")),
            0, null, null, null, null);
    }

    /** Detalhe completo: cabecalho + itens + CMV calculado. null se nao existir. */
    public static ExecucaoReceita buscar(int id) throws SQLException {
        try (Connection con = Banco.conexao()) {
            ExecucaoReceita cab = buscarCabecalho(con, id);
            if (cab == null) return null;

            List<ItemReceita> itens = new ArrayList<>();
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT m.id AS mov_id, m.ingrediente_id, i.nome, i.unidade, "
                  + "       m.quantidade, m.preco_unitario "
                  + "FROM movimentacoes_estoque m "
                  + "JOIN ingredientes i ON i.id = m.ingrediente_id "
                  + "WHERE m.execucao_id = ? AND m.tipo = 'saida' "
                  + "ORDER BY m.id ASC")) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        double q = rs.getDouble("quantidade");
                        double pu = rs.getDouble("preco_unitario");
                        itens.add(new ItemReceita(
                            rs.getInt("mov_id"), rs.getInt("ingrediente_id"),
                            rs.getString("nome"), rs.getString("unidade"),
                            q, pu, q * pu));
                    }
                }
            }
            double custoTotal = 0;
            for (ItemReceita it : itens) custoTotal += it.custo();
            return comCalculos(cab, itens, custoTotal);
        }
    }

    /** A execucao em andamento (status aberta), ou null. So uma por vez. */
    public static ExecucaoReceita ativa() throws SQLException {
        try (Connection con = Banco.conexao();
             PreparedStatement ps = con.prepareStatement(
                 "SELECT id FROM execucoes_receita WHERE status = 'aberta' ORDER BY id DESC LIMIT 1");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? buscar(rs.getInt("id")) : null;
        }
    }

    /** Historico de execucoes, mais recentes primeiro, com custo total (sem itens). */
    public static List<ExecucaoReceita> listar() throws SQLException {
        String sql =
            "SELECT e.id, e.nome, e.status, e.porcoes, e.preco_venda, e.inicio, e.fim, "
          + "       COALESCE((SELECT SUM(m.quantidade * m.preco_unitario) "
          + "                 FROM movimentacoes_estoque m "
          + "                 WHERE m.execucao_id = e.id AND m.tipo = 'saida'), 0) AS custo_total "
          + "FROM execucoes_receita e ORDER BY e.id DESC";
        try (Connection con = Banco.conexao();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<ExecucaoReceita> lista = new ArrayList<>();
            while (rs.next()) {
                ExecucaoReceita cab = cabecalhoDoRs(rs);
                double custoTotal = rs.getDouble("custo_total");
                lista.add(comCalculos(cab, null, custoTotal));
            }
            return lista;
        }
    }

    /** Nomes ja usados, para autocomplete. */
    public static List<String> nomesUsados() throws SQLException {
        try (Connection con = Banco.conexao();
             PreparedStatement ps = con.prepareStatement(
                 "SELECT DISTINCT nome FROM execucoes_receita ORDER BY nome");
             ResultSet rs = ps.executeQuery()) {
            List<String> nomes = new ArrayList<>();
            while (rs.next()) nomes.add(rs.getString("nome"));
            return nomes;
        }
    }

    // =====================================================================
    // ESCRITA
    // =====================================================================

    /** Inicia uma receita. 409 se ja houver uma em andamento (uma por vez). */
    public static ExecucaoReceita iniciar(String nome) throws SQLException {
        if (nome == null || nome.isBlank())
            throw new ErroApp("Informe o nome da receita.", 400);
        try (Connection con = Banco.conexao()) {
            try (PreparedStatement chk = con.prepareStatement(
                    "SELECT id FROM execucoes_receita WHERE status = 'aberta' LIMIT 1");
                 ResultSet rs = chk.executeQuery()) {
                if (rs.next())
                    throw new ErroApp("Ja existe uma receita em andamento. Finalize ou cancele antes.", 409);
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO execucoes_receita (nome, status) VALUES (?, 'aberta')",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, nome.trim());
                ps.executeUpdate();
                try (ResultSet gk = ps.getGeneratedKeys()) {
                    gk.next();
                    return buscar(gk.getInt(1));
                }
            }
        }
    }

    /**
     * Abate o ingrediente do estoque e vincula a saida a execucao (numa
     * transacao). Devolve a execucao atualizada (com itens e custo). 404/409
     * nos mesmos casos do abatimento normal.
     */
    public static ExecucaoReceita adicionarItem(int execucaoId, int ingredienteId,
                                                double qtd, String obs) throws SQLException {
        try (Connection con = Banco.conexao()) {
            con.setAutoCommit(false);
            try {
                ExecucaoReceita exec = buscarCabecalho(con, execucaoId);
                if (exec == null) throw new ErroApp("Receita nao encontrada.", 404);
                if (!"aberta".equals(exec.status()))
                    throw new ErroApp("Esta receita nao esta em andamento.", 409);

                double qtdAtual, preco;
                String unidade;
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT qtd, preco, unidade FROM ingredientes WHERE id = ?")) {
                    ps.setInt(1, ingredienteId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new ErroApp("Ingrediente nao encontrado.", 404);
                        qtdAtual = rs.getDouble("qtd");
                        preco = rs.getDouble("preco");
                        unidade = rs.getString("unidade");
                    }
                }
                if (qtd > qtdAtual)
                    throw new ErroApp("Estoque insuficiente. Disponivel: " + qtdAtual + " " + unidade, 409);

                double nova = Math.max(0, qtdAtual - qtd);
                try (PreparedStatement up = con.prepareStatement(
                        "UPDATE ingredientes SET qtd = ?, atualizado_em = NOW() WHERE id = ?")) {
                    up.setDouble(1, nova);
                    up.setInt(2, ingredienteId);
                    up.executeUpdate();
                }
                try (PreparedStatement mov = con.prepareStatement(
                        "INSERT INTO movimentacoes_estoque "
                      + "(ingrediente_id, tipo, quantidade, preco_unitario, observacao, data, execucao_id) "
                      + "VALUES (?, 'saida', ?, ?, ?, CURDATE(), ?)")) {
                    mov.setInt(1, ingredienteId);
                    mov.setDouble(2, qtd);
                    mov.setDouble(3, preco);
                    mov.setString(4, obs);
                    mov.setInt(5, execucaoId);
                    mov.executeUpdate();
                }
                con.commit();
            } catch (Exception e) {
                con.rollback();
                throw e;
            }
        }
        return buscar(execucaoId);
    }

    /** Estorna uma linha: devolve ao estoque e remove a saida. Numa transacao. */
    public static ExecucaoReceita estornarItem(int execucaoId, int movId) throws SQLException {
        try (Connection con = Banco.conexao()) {
            con.setAutoCommit(false);
            try {
                int ingId;
                double qtd;
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT ingrediente_id, quantidade FROM movimentacoes_estoque "
                      + "WHERE id = ? AND execucao_id = ? AND tipo = 'saida'")) {
                    ps.setInt(1, movId);
                    ps.setInt(2, execucaoId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new ErroApp("Item nao encontrado nesta receita.", 404);
                        ingId = rs.getInt("ingrediente_id");
                        qtd = rs.getDouble("quantidade");
                    }
                }
                try (PreparedStatement up = con.prepareStatement(
                        "UPDATE ingredientes SET qtd = qtd + ?, atualizado_em = NOW() WHERE id = ?")) {
                    up.setDouble(1, qtd);
                    up.setInt(2, ingId);
                    up.executeUpdate();
                }
                try (PreparedStatement del = con.prepareStatement(
                        "DELETE FROM movimentacoes_estoque WHERE id = ?")) {
                    del.setInt(1, movId);
                    del.executeUpdate();
                }
                con.commit();
            } catch (Exception e) {
                con.rollback();
                throw e;
            }
        }
        return buscar(execucaoId);
    }

    /** Finaliza: grava porcoes e preco de venda, fecha e calcula o CMV. */
    public static ExecucaoReceita finalizar(int id, Integer porcoes, Double precoVenda) throws SQLException {
        try (Connection con = Banco.conexao()) {
            ExecucaoReceita cab = buscarCabecalho(con, id);
            if (cab == null) throw new ErroApp("Receita nao encontrada.", 404);
            if (!"aberta".equals(cab.status()))
                throw new ErroApp("Esta receita nao esta em andamento.", 409);
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE execucoes_receita SET status = 'finalizada', fim = NOW(), "
                  + "porcoes = ?, preco_venda = ? WHERE id = ?")) {
                if (porcoes != null) ps.setInt(1, porcoes); else ps.setNull(1, Types.INTEGER);
                if (precoVenda != null) ps.setDouble(2, precoVenda); else ps.setNull(2, Types.DECIMAL);
                ps.setInt(3, id);
                ps.executeUpdate();
            }
        }
        return buscar(id);
    }

    /** Cancela: devolve todas as saidas ao estoque e marca como cancelada. */
    public static ExecucaoReceita cancelar(int id) throws SQLException {
        try (Connection con = Banco.conexao()) {
            con.setAutoCommit(false);
            try {
                ExecucaoReceita cab = buscarCabecalho(con, id);
                if (cab == null) throw new ErroApp("Receita nao encontrada.", 404);
                if (!"aberta".equals(cab.status()))
                    throw new ErroApp("Esta receita nao esta em andamento.", 409);

                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT ingrediente_id, quantidade FROM movimentacoes_estoque "
                      + "WHERE execucao_id = ? AND tipo = 'saida'")) {
                    ps.setInt(1, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            int ingId = rs.getInt("ingrediente_id");
                            double qtd = rs.getDouble("quantidade");
                            try (PreparedStatement up = con.prepareStatement(
                                    "UPDATE ingredientes SET qtd = qtd + ?, atualizado_em = NOW() WHERE id = ?")) {
                                up.setDouble(1, qtd);
                                up.setInt(2, ingId);
                                up.executeUpdate();
                            }
                        }
                    }
                }
                try (PreparedStatement del = con.prepareStatement(
                        "DELETE FROM movimentacoes_estoque WHERE execucao_id = ? AND tipo = 'saida'")) {
                    del.setInt(1, id);
                    del.executeUpdate();
                }
                try (PreparedStatement up = con.prepareStatement(
                        "UPDATE execucoes_receita SET status = 'cancelada', fim = NOW() WHERE id = ?")) {
                    up.setInt(1, id);
                    up.executeUpdate();
                }
                con.commit();
            } catch (Exception e) {
                con.rollback();
                throw e;
            }
        }
        return buscar(id);
    }

    // =====================================================================
    // AUXILIARES
    // =====================================================================

    /** Preenche os campos derivados (CMV, margem, custo/porcao) e os itens. */
    private static ExecucaoReceita comCalculos(ExecucaoReceita c, List<ItemReceita> itens, double custoTotal) {
        int nPor = (c.porcoes() != null && c.porcoes() > 0) ? c.porcoes() : 1;
        Double custoPorcao = custoTotal / nPor;
        Double cmvPercent = null;
        Double margem = null;
        if (c.precoVenda() != null && c.precoVenda() > 0) {
            double receita = c.precoVenda() * nPor;
            cmvPercent = (receita > 0) ? (custoTotal / receita) * 100.0 : null;
            margem = receita - custoTotal;
        }
        return new ExecucaoReceita(
            c.id(), c.nome(), c.status(), c.porcoes(), c.precoVenda(),
            c.inicio(), c.fim(), custoTotal, custoPorcao, cmvPercent, margem, itens);
    }

    private static String textoDataHora(Timestamp t) {
        return (t != null) ? t.toString() : null;
    }
}
