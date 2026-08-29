package br.com.custocerto.dao;

import br.com.custocerto.model.Movimentacao;
import br.com.custocerto.model.TipoMovimentacao;
import br.com.custocerto.model.Unidade;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * Acesso à tabela {@code movimentacoes_estoque} do MySQL.
 *
 * <p>O método {@link #registrar(Movimentacao)} é o mais importante da classe e o
 * único do projeto que usa <strong>transação</strong>: registrar uma compra
 * significa gravar a movimentação <em>e</em> alterar o saldo do insumo, e as duas
 * coisas precisam acontecer juntas. Se a segunda falhar depois de a primeira ter
 * dado certo, o estoque ficaria mentindo — por isso as duas operações são
 * confirmadas de uma vez com {@code commit}, ou desfeitas juntas com
 * {@code rollback}.</p>
 *
 * @author Equipe Custo Certo - FATEC Indaiatuba
 * @version 1.0
 */
public class MovimentacaoDAO {

    /**
     * Grava uma movimentação e atualiza o saldo do insumo, em uma única transação.
     *
     * @param movimentacao a movimentação a registrar
     * @return o saldo do insumo depois da operação
     * @throws SQLException se o saldo ficaria negativo, se o banco recusar a
     *                      operação ou se houver falha de comunicação
     */
    public BigDecimal registrar(Movimentacao movimentacao) throws SQLException {
        String sqlInsert = """
                INSERT INTO movimentacoes_estoque
                    (ingrediente_id, tipo, quantidade, preco_unitario, observacao, data)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        String sqlSaldo = "SELECT qtd FROM ingredientes WHERE id = ? FOR UPDATE";
        String sqlAtualiza = "UPDATE ingredientes SET qtd = ? WHERE id = ?";

        Connection conexao = null;
        try {
            conexao = ConexaoMySQL.obter();
            // Desliga a confirmação automática: daqui em diante nada é gravado
            // de verdade até o commit.
            conexao.setAutoCommit(false);

            BigDecimal saldoAtual;
            // FOR UPDATE trava a linha do insumo até o fim da transação, para
            // que duas máquinas da rede não leiam o mesmo saldo ao mesmo tempo.
            try (PreparedStatement comando = conexao.prepareStatement(sqlSaldo)) {
                comando.setInt(1, movimentacao.getIngredienteId());
                try (ResultSet resultado = comando.executeQuery()) {
                    if (!resultado.next()) {
                        throw new SQLException("Insumo não encontrado no banco.");
                    }
                    saldoAtual = resultado.getBigDecimal("qtd");
                }
            }

            BigDecimal delta = movimentacao.getQuantidade()
                    .multiply(BigDecimal.valueOf(movimentacao.getTipo().getSinal()));
            BigDecimal novoSaldo = saldoAtual.add(delta);

            if (novoSaldo.signum() < 0) {
                throw new SQLException("A retirada é maior do que o estoque disponível ("
                        + saldoAtual.stripTrailingZeros().toPlainString() + ").");
            }

            try (PreparedStatement comando = conexao.prepareStatement(sqlInsert)) {
                comando.setInt(1, movimentacao.getIngredienteId());
                comando.setString(2, movimentacao.getTipo().getValorBanco());
                comando.setBigDecimal(3, movimentacao.getQuantidade());
                if (movimentacao.getPrecoUnitario() == null) {
                    comando.setNull(4, Types.DECIMAL);
                } else {
                    comando.setBigDecimal(4, movimentacao.getPrecoUnitario());
                }
                comando.setString(5, movimentacao.getObservacao());
                comando.setDate(6, Date.valueOf(movimentacao.getData()));
                comando.executeUpdate();
            }

            try (PreparedStatement comando = conexao.prepareStatement(sqlAtualiza)) {
                comando.setBigDecimal(1, novoSaldo);
                comando.setInt(2, movimentacao.getIngredienteId());
                comando.executeUpdate();
            }

            // Numa entrada, o preço pago passa a ser o preço de referência do insumo.
            if (movimentacao.getTipo() == TipoMovimentacao.ENTRADA
                    && movimentacao.getPrecoUnitario() != null) {
                try (PreparedStatement comando = conexao.prepareStatement(
                        "UPDATE ingredientes SET preco = ? WHERE id = ?")) {
                    comando.setBigDecimal(1, movimentacao.getPrecoUnitario());
                    comando.setInt(2, movimentacao.getIngredienteId());
                    comando.executeUpdate();
                }
            }

            conexao.commit();
            return novoSaldo;

        } catch (SQLException e) {
            if (conexao != null) {
                try {
                    // Desfaz tudo: nem a movimentação nem o novo saldo ficam gravados.
                    conexao.rollback();
                } catch (SQLException ignorada) {
                    System.err.println("[banco] Falha ao desfazer a transação: "
                            + ignorada.getMessage());
                }
            }
            throw e;

        } finally {
            if (conexao != null) {
                try {
                    conexao.setAutoCommit(true);
                    conexao.close();
                } catch (SQLException ignorada) {
                    // conexão já encerrada
                }
            }
        }
    }

    /**
     * Lista as movimentações, da mais recente para a mais antiga.
     *
     * @param tipo   filtra por entrada ou saída; {@code null} traz as duas
     * @param limite número máximo de linhas devolvidas
     * @return a lista de movimentações, já com nome e unidade do insumo
     * @throws SQLException se houver falha de comunicação com o banco
     */
    public List<Movimentacao> listar(TipoMovimentacao tipo, int limite) throws SQLException {
        String sql = """
                SELECT m.id, m.ingrediente_id, m.tipo, m.quantidade, m.preco_unitario,
                       m.observacao, m.data, i.nome, i.unidade
                  FROM movimentacoes_estoque m
                  JOIN ingredientes i ON i.id = m.ingrediente_id
                 WHERE (? IS NULL OR m.tipo = ?)
                 ORDER BY m.data DESC, m.id DESC
                 LIMIT ?
                """;

        List<Movimentacao> lista = new ArrayList<>();
        try (Connection conexao = ConexaoMySQL.obter();
             PreparedStatement comando = conexao.prepareStatement(sql)) {

            String valorTipo = tipo == null ? null : tipo.getValorBanco();
            comando.setString(1, valorTipo);
            comando.setString(2, valorTipo);
            comando.setInt(3, limite);

            try (ResultSet resultado = comando.executeQuery()) {
                while (resultado.next()) {
                    lista.add(montar(resultado));
                }
            }
        }
        return lista;
    }

    /**
     * Converte a linha atual do resultado em um objeto de domínio.
     *
     * @param resultado o resultado posicionado sobre a linha
     * @return a movimentação correspondente
     * @throws SQLException se alguma coluna não puder ser lida
     */
    private Movimentacao montar(ResultSet resultado) throws SQLException {
        Movimentacao m = new Movimentacao();
        m.setId(resultado.getInt("id"));
        m.setIngredienteId(resultado.getInt("ingrediente_id"));
        m.setTipo(TipoMovimentacao.deValorDoBanco(resultado.getString("tipo")));
        m.setQuantidade(resultado.getBigDecimal("quantidade"));
        m.setPrecoUnitario(resultado.getBigDecimal("preco_unitario"));
        m.setObservacao(resultado.getString("observacao"));
        m.setData(resultado.getDate("data").toLocalDate());
        m.setNomeIngrediente(resultado.getString("nome"));
        m.setUnidade(Unidade.deSigla(resultado.getString("unidade")));
        return m;
    }
}
