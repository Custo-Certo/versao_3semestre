package br.com.custocerto.dao;

import br.com.custocerto.model.Periodo;
import br.com.custocerto.model.Venda;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Acesso à tabela {@code vendas}.
 *
 * <p>Cada linha é um produto vendido em um dia. O objetivo nunca foi virar um
 * ponto de venda: é registrar o suficiente para o sistema calcular receita,
 * margem e CMV — os números que faltavam para responder se a cafeteria está
 * ganhando dinheiro.</p>
 *
 * <p>As consultas trazem, por junção, o custo unitário calculado a partir da ficha
 * técnica e dos preços atuais dos insumos. Assim a tela mostra margem sem precisar
 * carregar as receitas inteiras.</p>
 *
 * @author Equipe Custo Certo - FATEC Indaiatuba
 * @version 1.0
 */
public class VendaDAO {

    /**
     * Lista as vendas de um período, da mais recente para a mais antiga.
     *
     * @param periodo o intervalo consultado
     * @param limite  número máximo de linhas devolvidas
     * @return as vendas encontradas
     * @throws SQLException se houver falha de comunicação com o banco
     */
    public List<Venda> listar(Periodo periodo, int limite) throws SQLException {
        String sql = """
                SELECT v.id, v.receita_id, v.quantidade, v.preco_unitario,
                       v.observacao, v.data,
                       r.nome AS nome_receita,
                       (SELECT COALESCE(SUM(ri.quantidade * i.preco), 0)
                          FROM receita_ingredientes ri
                          JOIN ingredientes i ON i.id = ri.ingrediente_id
                         WHERE ri.receita_id = v.receita_id) AS custo_unitario
                  FROM vendas v
                  JOIN receitas r ON r.id = v.receita_id
                 WHERE v.data BETWEEN ? AND ?
                 ORDER BY v.data DESC, v.id DESC
                 LIMIT ?
                """;

        List<Venda> vendas = new ArrayList<>();

        try (Connection conexao = ConexaoMySQL.obter();
             PreparedStatement comando = conexao.prepareStatement(sql)) {

            comando.setDate(1, Date.valueOf(periodo.getInicio()));
            comando.setDate(2, Date.valueOf(periodo.getFim()));
            comando.setInt(3, limite);

            try (ResultSet resultado = comando.executeQuery()) {
                while (resultado.next()) {
                    vendas.add(montar(resultado));
                }
            }
        }
        return vendas;
    }

    /**
     * Registra uma venda.
     *
     * @param venda a venda a gravar
     * @return a mesma venda, agora com o {@code id} gerado pelo banco
     * @throws SQLException se o produto não existir ou houver falha de comunicação
     */
    public Venda inserir(Venda venda) throws SQLException {
        String sql = """
                INSERT INTO vendas (receita_id, quantidade, preco_unitario, observacao, data)
                VALUES (?, ?, ?, ?, ?)
                """;

        try (Connection conexao = ConexaoMySQL.obter();
             PreparedStatement comando =
                     conexao.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            comando.setInt(1, venda.getReceitaId());
            comando.setInt(2, venda.getQuantidade());
            comando.setBigDecimal(3, venda.getPrecoUnitario());
            comando.setString(4, venda.getObservacao());
            comando.setDate(5, Date.valueOf(venda.getData()));
            comando.executeUpdate();

            try (ResultSet chaves = comando.getGeneratedKeys()) {
                if (chaves.next()) {
                    venda.setId(chaves.getInt(1));
                }
            }
        }
        return venda;
    }

    /**
     * Remove um lançamento de venda.
     *
     * <p>Existe para corrigir digitação. Não mexe no estoque: a baixa dos insumos
     * acontece pela balança, em {@code movimentacoes_estoque}, e é justamente a
     * independência entre os dois registros que permite comparar o consumo teórico
     * com o real.</p>
     *
     * @param id a chave do lançamento
     * @return {@code true} se alguma linha foi removida
     * @throws SQLException se houver falha de comunicação com o banco
     */
    public boolean excluir(int id) throws SQLException {
        try (Connection conexao = ConexaoMySQL.obter();
             PreparedStatement comando =
                     conexao.prepareStatement("DELETE FROM vendas WHERE id = ?")) {

            comando.setInt(1, id);
            return comando.executeUpdate() > 0;
        }
    }

    /**
     * Converte a linha atual do resultado em uma venda.
     *
     * @param resultado o resultado posicionado sobre a linha
     * @return a venda correspondente
     * @throws SQLException se alguma coluna não puder ser lida
     */
    private Venda montar(ResultSet resultado) throws SQLException {
        Venda v = new Venda();
        v.setId(resultado.getInt("id"));
        v.setReceitaId(resultado.getInt("receita_id"));
        v.setNomeReceita(resultado.getString("nome_receita"));
        v.setQuantidade(resultado.getInt("quantidade"));
        v.setPrecoUnitario(resultado.getBigDecimal("preco_unitario"));
        v.setCustoUnitario(resultado.getBigDecimal("custo_unitario"));
        v.setObservacao(resultado.getString("observacao"));

        Date data = resultado.getDate("data");
        if (data != null) {
            v.setData(data.toLocalDate());
        }
        return v;
    }
}
