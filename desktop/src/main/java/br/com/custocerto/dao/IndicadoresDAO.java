package br.com.custocerto.dao;

import br.com.custocerto.model.DesempenhoProduto;
import br.com.custocerto.model.IndicadorInsumo;
import br.com.custocerto.model.Periodo;
import br.com.custocerto.model.ResumoFinanceiro;
import br.com.custocerto.model.Unidade;
import br.com.custocerto.model.VarianciaInsumo;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * As consultas analíticas do sistema — as que respondem perguntas de negócio em
 * vez de devolver registros.
 *
 * <p>Fica separada dos demais DAOs porque a natureza do SQL é outra: aqui não há
 * inclusão nem alteração, só agregações que cruzam vendas, fichas técnicas e
 * movimentações de estoque. Concentrá-las em uma classe também deixa evidente,
 * para quem for ler o projeto, onde mora a inteligência do sistema.</p>
 *
 * <p>Todas as consultas recebem um {@link Periodo}. Não existe indicador sem
 * recorte de tempo: faturamento, consumo e variância só significam alguma coisa
 * dentro de um intervalo.</p>
 *
 * @author Equipe Custo Certo - FATEC Indaiatuba
 * @version 1.0
 */
public class IndicadoresDAO {

    /**
     * Apura os totais financeiros do período.
     *
     * <p>São três perguntas diferentes, e por isso três consultas:</p>
     *
     * <ol>
     *   <li>quanto a cafeteria faturou, em {@code vendas};</li>
     *   <li>quanto isso <em>deveria</em> ter custado, cruzando as vendas com as
     *       fichas técnicas e os preços atuais dos insumos;</li>
     *   <li>quanto de fato saiu do estoque e quanto foi comprado, em
     *       {@code movimentacoes_estoque}.</li>
     * </ol>
     *
     * @param periodo o intervalo analisado
     * @return o resumo com receita, custos, compras e volumes
     * @throws SQLException se houver falha de comunicação com o banco
     */
    public ResumoFinanceiro apurarResumo(Periodo periodo) throws SQLException {
        BigDecimal receita = BigDecimal.ZERO;
        int unidades = 0;
        int lancamentos = 0;

        String sqlVendas = """
                SELECT COALESCE(SUM(quantidade * preco_unitario), 0) AS receita,
                       COALESCE(SUM(quantidade), 0)                  AS unidades,
                       COUNT(*)                                      AS lancamentos
                  FROM vendas
                 WHERE data BETWEEN ? AND ?
                """;

        try (Connection conexao = ConexaoMySQL.obter();
             PreparedStatement comando = conexao.prepareStatement(sqlVendas)) {

            aplicarPeriodo(comando, periodo, 1);
            try (ResultSet r = comando.executeQuery()) {
                if (r.next()) {
                    receita = r.getBigDecimal("receita");
                    unidades = r.getInt("unidades");
                    lancamentos = r.getInt("lancamentos");
                }
            }
        }

        // Custo teórico: cada venda puxa a ficha técnica do seu produto, e cada
        // linha da ficha é avaliada pelo preço que o insumo tem hoje.
        String sqlTeorico = """
                SELECT COALESCE(SUM(v.quantidade * ri.quantidade * i.preco), 0) AS custo
                  FROM vendas v
                  JOIN receita_ingredientes ri ON ri.receita_id = v.receita_id
                  JOIN ingredientes i          ON i.id = ri.ingrediente_id
                 WHERE v.data BETWEEN ? AND ?
                """;

        BigDecimal custoTeorico = BigDecimal.ZERO;
        try (Connection conexao = ConexaoMySQL.obter();
             PreparedStatement comando = conexao.prepareStatement(sqlTeorico)) {

            aplicarPeriodo(comando, periodo, 1);
            try (ResultSet r = comando.executeQuery()) {
                if (r.next()) {
                    custoTeorico = r.getBigDecimal("custo");
                }
            }
        }

        // Custo real e compras saem da mesma tabela, separados pelo tipo.
        // Nas entradas o preço pago fica na própria movimentação; nas saídas não
        // há preço, então o custo é avaliado pelo preço atual do insumo.
        String sqlEstoque = """
                SELECT COALESCE(SUM(CASE WHEN m.tipo = 'saida'
                                         THEN m.quantidade * i.preco END), 0) AS custo_real,
                       COALESCE(SUM(CASE WHEN m.tipo = 'entrada'
                                         THEN m.quantidade * COALESCE(m.preco_unitario, i.preco)
                                    END), 0)                                  AS compras
                  FROM movimentacoes_estoque m
                  JOIN ingredientes i ON i.id = m.ingrediente_id
                 WHERE m.data BETWEEN ? AND ?
                """;

        BigDecimal custoReal = BigDecimal.ZERO;
        BigDecimal compras = BigDecimal.ZERO;
        try (Connection conexao = ConexaoMySQL.obter();
             PreparedStatement comando = conexao.prepareStatement(sqlEstoque)) {

            aplicarPeriodo(comando, periodo, 1);
            try (ResultSet r = comando.executeQuery()) {
                if (r.next()) {
                    custoReal = r.getBigDecimal("custo_real");
                    compras = r.getBigDecimal("compras");
                }
            }
        }

        return new ResumoFinanceiro(periodo, receita, custoTeorico, custoReal,
                compras, unidades, lancamentos);
    }

    /**
     * Levanta, por insumo, o saldo atual e o que girou no período.
     *
     * <p>As duas junções à esquerda são o ponto da consulta: um insumo que não teve
     * nenhuma saída precisa aparecer mesmo assim, com consumo zero. Uma junção
     * comum o deixaria de fora, e ele sumiria justamente da tela que existe para
     * mostrar o que está parado.</p>
     *
     * @param periodo o intervalo analisado
     * @return um indicador por insumo cadastrado, em ordem alfabética
     * @throws SQLException se houver falha de comunicação com o banco
     */
    public List<IndicadorInsumo> levantarIndicadores(Periodo periodo) throws SQLException {
        String sql = """
                SELECT i.id, i.nome, i.unidade, i.qtd, i.preco,
                       COALESCE(saidas.consumo, 0)    AS consumo,
                       COALESCE(entradas.comprado, 0) AS comprado
                  FROM ingredientes i
                  LEFT JOIN (SELECT ingrediente_id, SUM(quantidade) AS consumo
                               FROM movimentacoes_estoque
                              WHERE tipo = 'saida' AND data BETWEEN ? AND ?
                              GROUP BY ingrediente_id) saidas
                         ON saidas.ingrediente_id = i.id
                  LEFT JOIN (SELECT ingrediente_id,
                                    SUM(quantidade * COALESCE(preco_unitario, 0)) AS comprado
                               FROM movimentacoes_estoque
                              WHERE tipo = 'entrada' AND data BETWEEN ? AND ?
                              GROUP BY ingrediente_id) entradas
                         ON entradas.ingrediente_id = i.id
                 ORDER BY i.nome
                """;

        List<IndicadorInsumo> lista = new ArrayList<>();

        try (Connection conexao = ConexaoMySQL.obter();
             PreparedStatement comando = conexao.prepareStatement(sql)) {

            aplicarPeriodo(comando, periodo, 1);
            aplicarPeriodo(comando, periodo, 3);

            try (ResultSet r = comando.executeQuery()) {
                while (r.next()) {
                    IndicadorInsumo ind = new IndicadorInsumo();
                    ind.setIngredienteId(r.getInt("id"));
                    ind.setNome(r.getString("nome"));
                    ind.setUnidade(Unidade.deSigla(r.getString("unidade")));
                    ind.setSaldo(r.getBigDecimal("qtd"));
                    ind.setPrecoUnitario(r.getBigDecimal("preco"));
                    ind.setConsumoPeriodo(r.getBigDecimal("consumo"));
                    ind.setCompradoPeriodo(r.getBigDecimal("comprado"));
                    ind.setDiasDoPeriodo(periodo.getDias());
                    lista.add(ind);
                }
            }
        }
        return lista;
    }

    /**
     * Compara, por insumo, o consumo que as vendas pediam com o que a balança pesou.
     *
     * <p>O lado teórico vem das vendas multiplicadas pela ficha técnica; o lado real
     * vem das saídas de estoque. A consulta descarta os insumos em que os dois lados
     * são zero — não há o que dizer sobre um insumo que ninguém vendeu nem
     * consumiu.</p>
     *
     * @param periodo o intervalo analisado
     * @return uma variância por insumo com movimento no período
     * @throws SQLException se houver falha de comunicação com o banco
     */
    public List<VarianciaInsumo> compararConsumo(Periodo periodo) throws SQLException {
        String sql = """
                SELECT i.id, i.nome, i.unidade, i.preco,
                       COALESCE(previsto.teorico, 0)   AS teorico,
                       COALESCE(medido.quantidade, 0)  AS medido
                  FROM ingredientes i
                  LEFT JOIN (SELECT ri.ingrediente_id,
                                    SUM(v.quantidade * ri.quantidade) AS teorico
                               FROM vendas v
                               JOIN receita_ingredientes ri ON ri.receita_id = v.receita_id
                              WHERE v.data BETWEEN ? AND ?
                              GROUP BY ri.ingrediente_id) previsto
                         ON previsto.ingrediente_id = i.id
                  LEFT JOIN (SELECT ingrediente_id, SUM(quantidade) AS quantidade
                               FROM movimentacoes_estoque
                              WHERE tipo = 'saida' AND data BETWEEN ? AND ?
                              GROUP BY ingrediente_id) medido
                         ON medido.ingrediente_id = i.id
                 WHERE COALESCE(previsto.teorico, 0) > 0
                    OR COALESCE(medido.quantidade, 0) > 0
                 ORDER BY i.nome
                """;

        List<VarianciaInsumo> lista = new ArrayList<>();

        try (Connection conexao = ConexaoMySQL.obter();
             PreparedStatement comando = conexao.prepareStatement(sql)) {

            aplicarPeriodo(comando, periodo, 1);
            aplicarPeriodo(comando, periodo, 3);

            try (ResultSet r = comando.executeQuery()) {
                while (r.next()) {
                    VarianciaInsumo v = new VarianciaInsumo();
                    v.setIngredienteId(r.getInt("id"));
                    v.setNome(r.getString("nome"));
                    v.setUnidade(Unidade.deSigla(r.getString("unidade")));
                    v.setPrecoUnitario(r.getBigDecimal("preco"));
                    v.setTeorico(r.getBigDecimal("teorico"));
                    v.setReal(r.getBigDecimal("medido"));
                    lista.add(v);
                }
            }
        }
        return lista;
    }

    /**
     * Levanta o desempenho de cada produto do cardápio no período.
     *
     * <p>O preço usado é o <b>médio praticado</b>, ponderado pelas quantidades, e
     * não o preço de tabela: promoção e cortesia mudam o que de fato entrou no
     * caixa. Quando não houve venda, cai no preço de tabela, para o produto ainda
     * aparecer na tela com a sua margem potencial.</p>
     *
     * @param periodo o intervalo analisado
     * @return um desempenho por produto cadastrado
     * @throws SQLException se houver falha de comunicação com o banco
     */
    public List<DesempenhoProduto> levantarDesempenho(Periodo periodo) throws SQLException {
        String sql = """
                SELECT r.id, r.nome, r.preco_venda,
                       COALESCE(SUM(v.quantidade), 0) AS unidades,
                       COALESCE(SUM(v.quantidade * v.preco_unitario)
                                / NULLIF(SUM(v.quantidade), 0), r.preco_venda) AS preco_medio,
                       (SELECT COALESCE(SUM(ri.quantidade * i.preco), 0)
                          FROM receita_ingredientes ri
                          JOIN ingredientes i ON i.id = ri.ingrediente_id
                         WHERE ri.receita_id = r.id) AS custo_unitario,
                       (SELECT COUNT(*) FROM receita_ingredientes ri2
                         WHERE ri2.receita_id = r.id) AS itens_ficha
                  FROM receitas r
                  LEFT JOIN vendas v
                         ON v.receita_id = r.id
                        AND v.data BETWEEN ? AND ?
                 GROUP BY r.id, r.nome, r.preco_venda
                 ORDER BY r.nome
                """;

        List<DesempenhoProduto> lista = new ArrayList<>();

        try (Connection conexao = ConexaoMySQL.obter();
             PreparedStatement comando = conexao.prepareStatement(sql)) {

            aplicarPeriodo(comando, periodo, 1);

            try (ResultSet r = comando.executeQuery()) {
                while (r.next()) {
                    DesempenhoProduto d = new DesempenhoProduto();
                    d.setReceitaId(r.getInt("id"));
                    d.setNome(r.getString("nome"));
                    d.setUnidadesVendidas(r.getInt("unidades"));
                    d.setPrecoMedio(r.getBigDecimal("preco_medio"));
                    d.setCustoUnitario(r.getBigDecimal("custo_unitario"));
                    d.setTemFicha(r.getInt("itens_ficha") > 0);
                    lista.add(d);
                }
            }
        }
        return lista;
    }

    /**
     * Preenche um par de parâmetros de data a partir do período.
     *
     * <p>Existe porque as consultas repetem o mesmo intervalo em mais de um ponto —
     * uma vez para as saídas, outra para as entradas — e numerar os parâmetros à
     * mão em cada uma convidaria a trocar a ordem.</p>
     *
     * @param comando o comando preparado
     * @param periodo o intervalo a aplicar
     * @param posicao o índice do primeiro dos dois parâmetros
     * @throws SQLException se os índices não corresponderem ao comando
     */
    private void aplicarPeriodo(PreparedStatement comando, Periodo periodo, int posicao)
            throws SQLException {
        comando.setDate(posicao, Date.valueOf(periodo.getInicio()));
        comando.setDate(posicao + 1, Date.valueOf(periodo.getFim()));
    }
}
