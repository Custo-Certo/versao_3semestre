package br.com.custocerto.dao;

import br.com.custocerto.model.ExecucaoReceita;
import br.com.custocerto.model.ItemExecucao;
import br.com.custocerto.model.Unidade;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * Acesso às execuções de receita: a tabela {@code execucoes_receita} e as saídas
 * de estoque ligadas a ela por {@code movimentacoes_estoque.execucao_id}.
 *
 * <p>As mesmas tabelas são usadas pelo servidor Java do painel web, então uma
 * receita iniciada no painel aparece aqui, e vice-versa. As regras também são as
 * mesmas: uma receita em andamento por vez, e toda mudança de estoque acontece
 * dentro de uma transação.</p>
 *
 * @author Equipe Custo Certo - FATEC Indaiatuba
 * @version 1.0
 */
public class ExecucaoReceitaDAO {

    /**
     * Falha de regra de negócio detectada dentro de uma transação, com uma
     * mensagem pronta para o usuário. Distingue-se de uma falha de conexão.
     */
    public static class RegraException extends SQLException {

        private static final long serialVersionUID = 1L;

        /**
         * @param mensagem o texto exibido ao usuário
         */
        public RegraException(String mensagem) {
            super(mensagem);
        }
    }

    /** Colunas do cabeçalho, com o custo somado das saídas vinculadas. */
    private static final String SELECT_CABECALHO = """
            SELECT e.id, e.nome, e.status, e.porcoes, e.preco_venda, e.inicio, e.fim,
                   COALESCE((SELECT SUM(m.quantidade * m.preco_unitario)
                               FROM movimentacoes_estoque m
                              WHERE m.execucao_id = e.id AND m.tipo = 'saida'), 0) AS custo_total
              FROM execucoes_receita e
            """;

    /**
     * Lista as execuções, das mais recentes para as mais antigas, sem os itens.
     *
     * @param limite número máximo de linhas
     * @return as execuções com o custo total já somado
     * @throws SQLException se o banco não responder
     */
    public List<ExecucaoReceita> listar(int limite) throws SQLException {
        List<ExecucaoReceita> lista = new ArrayList<>();
        try (Connection conexao = ConexaoMySQL.obter();
             PreparedStatement comando = conexao.prepareStatement(
                     SELECT_CABECALHO + " ORDER BY e.id DESC LIMIT ?")) {
            comando.setInt(1, limite);
            try (ResultSet resultado = comando.executeQuery()) {
                while (resultado.next()) {
                    lista.add(montar(resultado));
                }
            }
        }
        return lista;
    }

    /**
     * Busca uma execução com todos os ingredientes pesados.
     *
     * @param id a chave da execução
     * @return a execução completa, ou {@code null} se não existir
     * @throws SQLException se o banco não responder
     */
    public ExecucaoReceita buscar(int id) throws SQLException {
        try (Connection conexao = ConexaoMySQL.obter()) {
            ExecucaoReceita execucao = buscarCabecalho(conexao, id);
            if (execucao != null) {
                execucao.setItens(buscarItens(conexao, id));
            }
            return execucao;
        }
    }

    /**
     * Busca a execução em andamento, se houver.
     *
     * @return a execução aberta com seus itens, ou {@code null}
     * @throws SQLException se o banco não responder
     */
    public ExecucaoReceita buscarAberta() throws SQLException {
        int id;
        try (Connection conexao = ConexaoMySQL.obter();
             PreparedStatement comando = conexao.prepareStatement(
                     "SELECT id FROM execucoes_receita WHERE status = 'aberta' "
                     + "ORDER BY id DESC LIMIT 1");
             ResultSet resultado = comando.executeQuery()) {
            if (!resultado.next()) {
                return null;
            }
            id = resultado.getInt("id");
        }
        return buscar(id);
    }

    /**
     * Inicia uma nova execução, desde que não haja outra em andamento.
     *
     * @param nome o nome da receita
     * @return a chave gerada
     * @throws SQLException se já houver uma receita aberta ou o banco falhar
     */
    public int iniciar(String nome) throws SQLException {
        try (Connection conexao = ConexaoMySQL.obter()) {
            try (PreparedStatement comando = conexao.prepareStatement(
                    "SELECT id FROM execucoes_receita WHERE status = 'aberta' LIMIT 1");
                 ResultSet resultado = comando.executeQuery()) {
                if (resultado.next()) {
                    throw new RegraException("Já existe uma receita em andamento. "
                            + "Finalize ou cancele a atual antes de iniciar outra.");
                }
            }
            try (PreparedStatement comando = conexao.prepareStatement(
                    "INSERT INTO execucoes_receita (nome, status) VALUES (?, 'aberta')",
                    Statement.RETURN_GENERATED_KEYS)) {
                comando.setString(1, nome);
                comando.executeUpdate();
                try (ResultSet chaves = comando.getGeneratedKeys()) {
                    chaves.next();
                    return chaves.getInt(1);
                }
            }
        }
    }

    /**
     * Pesa um ingrediente para a receita: abate o estoque e grava a saída
     * vinculada, com o preço atual do insumo, numa única transação.
     *
     * @param execucaoId    a receita em andamento
     * @param ingredienteId o insumo pesado
     * @param quantidade    quanto saiu, na unidade do insumo
     * @throws SQLException se a receita não estiver aberta, faltar estoque ou o
     *                      banco falhar; nesse caso nada é gravado
     */
    public void adicionarItem(int execucaoId, int ingredienteId, BigDecimal quantidade)
            throws SQLException {
        try (Connection conexao = ConexaoMySQL.obter()) {
            conexao.setAutoCommit(false);
            try {
                exigirAberta(conexao, execucaoId);

                BigDecimal saldo;
                BigDecimal preco;
                String unidade;
                // FOR UPDATE trava o insumo: duas máquinas não leem o mesmo saldo.
                try (PreparedStatement comando = conexao.prepareStatement(
                        "SELECT qtd, preco, unidade FROM ingredientes WHERE id = ? FOR UPDATE")) {
                    comando.setInt(1, ingredienteId);
                    try (ResultSet resultado = comando.executeQuery()) {
                        if (!resultado.next()) {
                            throw new RegraException("Insumo não encontrado no banco.");
                        }
                        saldo = resultado.getBigDecimal("qtd");
                        preco = resultado.getBigDecimal("preco");
                        unidade = resultado.getString("unidade");
                    }
                }
                if (quantidade.compareTo(saldo) > 0) {
                    throw new RegraException("Estoque insuficiente. Disponível: "
                            + saldo.stripTrailingZeros().toPlainString() + " " + unidade + ".");
                }

                try (PreparedStatement comando = conexao.prepareStatement(
                        "UPDATE ingredientes SET qtd = qtd - ? WHERE id = ?")) {
                    comando.setBigDecimal(1, quantidade);
                    comando.setInt(2, ingredienteId);
                    comando.executeUpdate();
                }
                try (PreparedStatement comando = conexao.prepareStatement("""
                        INSERT INTO movimentacoes_estoque
                            (ingrediente_id, tipo, quantidade, preco_unitario, observacao,
                             data, execucao_id)
                        VALUES (?, 'saida', ?, ?, ?, CURDATE(), ?)
                        """)) {
                    comando.setInt(1, ingredienteId);
                    comando.setBigDecimal(2, quantidade);
                    comando.setBigDecimal(3, preco);
                    comando.setString(4, "Receita #" + execucaoId);
                    comando.setInt(5, execucaoId);
                    comando.executeUpdate();
                }
                conexao.commit();
            } catch (SQLException e) {
                conexao.rollback();
                throw e;
            }
        }
    }

    /**
     * Desfaz a pesagem de um ingrediente: devolve ao estoque e apaga a saída.
     *
     * @param execucaoId     a receita em andamento
     * @param movimentacaoId a saída a desfazer
     * @throws SQLException se a linha não pertencer à receita aberta ou o banco falhar
     */
    public void estornarItem(int execucaoId, int movimentacaoId) throws SQLException {
        try (Connection conexao = ConexaoMySQL.obter()) {
            conexao.setAutoCommit(false);
            try {
                exigirAberta(conexao, execucaoId);

                int ingredienteId;
                BigDecimal quantidade;
                try (PreparedStatement comando = conexao.prepareStatement(
                        "SELECT ingrediente_id, quantidade FROM movimentacoes_estoque "
                        + "WHERE id = ? AND execucao_id = ? AND tipo = 'saida'")) {
                    comando.setInt(1, movimentacaoId);
                    comando.setInt(2, execucaoId);
                    try (ResultSet resultado = comando.executeQuery()) {
                        if (!resultado.next()) {
                            throw new RegraException("Este ingrediente não está mais na receita.");
                        }
                        ingredienteId = resultado.getInt("ingrediente_id");
                        quantidade = resultado.getBigDecimal("quantidade");
                    }
                }
                devolverAoEstoque(conexao, ingredienteId, quantidade);
                try (PreparedStatement comando = conexao.prepareStatement(
                        "DELETE FROM movimentacoes_estoque WHERE id = ?")) {
                    comando.setInt(1, movimentacaoId);
                    comando.executeUpdate();
                }
                conexao.commit();
            } catch (SQLException e) {
                conexao.rollback();
                throw e;
            }
        }
    }

    /**
     * Encerra a receita, gravando porções e preço de venda.
     *
     * @param execucaoId a receita em andamento
     * @param porcoes    quantas porções a leva rendeu, ou {@code null}
     * @param precoVenda o preço de cada porção, ou {@code null}
     * @throws SQLException se a receita não estiver aberta ou o banco falhar
     */
    public void finalizar(int execucaoId, Integer porcoes, BigDecimal precoVenda)
            throws SQLException {
        try (Connection conexao = ConexaoMySQL.obter()) {
            exigirAberta(conexao, execucaoId);
            try (PreparedStatement comando = conexao.prepareStatement(
                    "UPDATE execucoes_receita SET status = 'finalizada', fim = NOW(), "
                    + "porcoes = ?, preco_venda = ? WHERE id = ?")) {
                if (porcoes == null) {
                    comando.setNull(1, Types.INTEGER);
                } else {
                    comando.setInt(1, porcoes);
                }
                if (precoVenda == null) {
                    comando.setNull(2, Types.DECIMAL);
                } else {
                    comando.setBigDecimal(2, precoVenda);
                }
                comando.setInt(3, execucaoId);
                comando.executeUpdate();
            }
        }
    }

    /**
     * Cancela a receita: devolve tudo o que foi pesado ao estoque, apaga as
     * saídas e marca a execução como cancelada, numa única transação.
     *
     * @param execucaoId a receita em andamento
     * @throws SQLException se a receita não estiver aberta ou o banco falhar
     */
    public void cancelar(int execucaoId) throws SQLException {
        try (Connection conexao = ConexaoMySQL.obter()) {
            conexao.setAutoCommit(false);
            try {
                exigirAberta(conexao, execucaoId);

                for (ItemExecucao item : buscarItens(conexao, execucaoId)) {
                    devolverAoEstoque(conexao, item.ingredienteId(), item.quantidade());
                }
                try (PreparedStatement comando = conexao.prepareStatement(
                        "DELETE FROM movimentacoes_estoque WHERE execucao_id = ? AND tipo = 'saida'")) {
                    comando.setInt(1, execucaoId);
                    comando.executeUpdate();
                }
                try (PreparedStatement comando = conexao.prepareStatement(
                        "UPDATE execucoes_receita SET status = 'cancelada', fim = NOW() WHERE id = ?")) {
                    comando.setInt(1, execucaoId);
                    comando.executeUpdate();
                }
                conexao.commit();
            } catch (SQLException e) {
                conexao.rollback();
                throw e;
            }
        }
    }

    /**
     * Confere, dentro da conexão dada, que a execução existe e está aberta.
     *
     * @param conexao    a conexão em uso
     * @param execucaoId a execução a conferir
     * @throws SQLException se não existir ou não estiver mais aberta
     */
    private void exigirAberta(Connection conexao, int execucaoId) throws SQLException {
        ExecucaoReceita execucao = buscarCabecalho(conexao, execucaoId);
        if (execucao == null) {
            throw new RegraException("Receita não encontrada.");
        }
        if (!execucao.isAberta()) {
            throw new RegraException("Esta receita não está mais em andamento. "
                    + "Atualize a página.");
        }
    }

    /**
     * Soma uma quantidade de volta ao saldo do insumo.
     *
     * @param conexao       a conexão da transação em curso
     * @param ingredienteId o insumo
     * @param quantidade    quanto devolver
     * @throws SQLException se o banco falhar
     */
    private void devolverAoEstoque(Connection conexao, int ingredienteId, BigDecimal quantidade)
            throws SQLException {
        try (PreparedStatement comando = conexao.prepareStatement(
                "UPDATE ingredientes SET qtd = qtd + ? WHERE id = ?")) {
            comando.setBigDecimal(1, quantidade);
            comando.setInt(2, ingredienteId);
            comando.executeUpdate();
        }
    }

    /**
     * Lê o cabeçalho de uma execução, sem os itens.
     *
     * @param conexao a conexão em uso
     * @param id      a chave da execução
     * @return a execução, ou {@code null} se não existir
     * @throws SQLException se o banco falhar
     */
    private ExecucaoReceita buscarCabecalho(Connection conexao, int id) throws SQLException {
        try (PreparedStatement comando = conexao.prepareStatement(
                SELECT_CABECALHO + " WHERE e.id = ?")) {
            comando.setInt(1, id);
            try (ResultSet resultado = comando.executeQuery()) {
                return resultado.next() ? montar(resultado) : null;
            }
        }
    }

    /**
     * Lê os ingredientes pesados de uma execução, na ordem em que entraram.
     *
     * @param conexao    a conexão em uso
     * @param execucaoId a chave da execução
     * @return os itens
     * @throws SQLException se o banco falhar
     */
    private List<ItemExecucao> buscarItens(Connection conexao, int execucaoId) throws SQLException {
        List<ItemExecucao> itens = new ArrayList<>();
        try (PreparedStatement comando = conexao.prepareStatement("""
                SELECT m.id, m.ingrediente_id, i.nome, i.unidade, m.quantidade, m.preco_unitario
                  FROM movimentacoes_estoque m
                  JOIN ingredientes i ON i.id = m.ingrediente_id
                 WHERE m.execucao_id = ? AND m.tipo = 'saida'
                 ORDER BY m.id
                """)) {
            comando.setInt(1, execucaoId);
            try (ResultSet resultado = comando.executeQuery()) {
                while (resultado.next()) {
                    itens.add(new ItemExecucao(
                            resultado.getInt("id"),
                            resultado.getInt("ingrediente_id"),
                            resultado.getString("nome"),
                            Unidade.deSigla(resultado.getString("unidade")),
                            resultado.getBigDecimal("quantidade"),
                            resultado.getBigDecimal("preco_unitario")));
                }
            }
        }
        return itens;
    }

    /**
     * Converte a linha atual do resultado em objeto.
     *
     * @param resultado o cursor posicionado na linha
     * @return a execução, sem itens
     * @throws SQLException se uma coluna não puder ser lida
     */
    private ExecucaoReceita montar(ResultSet resultado) throws SQLException {
        ExecucaoReceita e = new ExecucaoReceita();
        e.setId(resultado.getInt("id"));
        e.setNome(resultado.getString("nome"));
        e.setStatus(ExecucaoReceita.Status.doBanco(resultado.getString("status")));
        int porcoes = resultado.getInt("porcoes");
        e.setPorcoes(resultado.wasNull() ? null : porcoes);
        e.setPrecoVenda(resultado.getBigDecimal("preco_venda"));
        Timestamp inicio = resultado.getTimestamp("inicio");
        e.setInicio(inicio == null ? null : inicio.toLocalDateTime());
        Timestamp fim = resultado.getTimestamp("fim");
        e.setFim(fim == null ? null : fim.toLocalDateTime());
        e.setCustoTotal(resultado.getBigDecimal("custo_total"));
        return e;
    }
}
