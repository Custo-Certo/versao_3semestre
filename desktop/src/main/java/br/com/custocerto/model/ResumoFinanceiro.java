package br.com.custocerto.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Os números de um período: o que entrou, o que saiu e o que sobrou.
 *
 * <p>Esta classe substitui o CMV que o sistema exibia antes, e vale explicar o que
 * havia de errado com ele. O painel estimava a receita multiplicando o valor em
 * estoque por 3,5, e em seguida dividia o valor em estoque por essa receita
 * estimada. Os dois lados da divisão eram a mesma grandeza, então o valor em
 * estoque se cancelava e o resultado era sempre 1 ÷ 3,5 — <b>28,6%, qualquer que
 * fosse a situação da cafeteria</b>. Como o limite de "ideal" estava em 31%, o
 * medidor também apontava para o verde para sempre.</p>
 *
 * <p>Agora os dois lados são medidos. A receita vem de {@code vendas}; o custo vem
 * de dois caminhos diferentes, que respondem perguntas diferentes:</p>
 *
 * <ul>
 *   <li><b>CMV teórico</b> — vendas × ficha técnica. O que a operação
 *       <em>deveria</em> ter custado. Cobre todos os insumos das fichas.</li>
 *   <li><b>CMV real</b> — o que de fato saiu do estoque. Só enxerga o que passa
 *       pela balança, então costuma ficar mais baixo; a diferença entre os dois é
 *       o que a tela de variância destrincha, insumo por insumo.</li>
 * </ul>
 *
 * @author Equipe Custo Certo - FATEC Indaiatuba
 * @version 1.0
 */
public class ResumoFinanceiro {

    /**
     * CMV até o qual o resultado é considerado saudável para uma cafeteria.
     *
     * <p>Referência de mercado para food service: a faixa usual vai de 25% a 35%, e
     * uma cafeteria com operação enxuta fica na ponta baixa. O valor antigo do
     * sistema — 31% como ideal e 35% como limite — não tinha origem citada.</p>
     */
    public static final double CMV_IDEAL = 25.0;

    /** CMV a partir do qual o resultado sai da faixa aceitável do setor. */
    public static final double CMV_LIMITE = 35.0;

    private final Periodo periodo;
    private final BigDecimal receita;
    private final BigDecimal custoTeorico;
    private final BigDecimal custoReal;
    private final BigDecimal compras;
    private final int unidadesVendidas;
    private final int numeroDeLancamentos;

    /**
     * Monta o resumo a partir dos totais já apurados no banco.
     *
     * @param periodo             o intervalo analisado
     * @param receita             faturamento do período
     * @param custoTeorico        custo pelas fichas técnicas das vendas
     * @param custoReal           custo do que saiu do estoque
     * @param compras             valor das entradas no período
     * @param unidadesVendidas    total de unidades vendidas
     * @param numeroDeLancamentos quantas linhas de venda foram registradas
     */
    public ResumoFinanceiro(Periodo periodo, BigDecimal receita, BigDecimal custoTeorico,
                            BigDecimal custoReal, BigDecimal compras,
                            int unidadesVendidas, int numeroDeLancamentos) {
        this.periodo = periodo;
        this.receita = zeroSeNulo(receita);
        this.custoTeorico = zeroSeNulo(custoTeorico);
        this.custoReal = zeroSeNulo(custoReal);
        this.compras = zeroSeNulo(compras);
        this.unidadesVendidas = unidadesVendidas;
        this.numeroDeLancamentos = numeroDeLancamentos;
    }

    /**
     * Devolve zero no lugar de nulo, para as somas do banco que voltam vazias.
     *
     * @param valor o valor lido
     * @return o próprio valor, ou zero
     */
    private static BigDecimal zeroSeNulo(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }

    /**
     * Calcula o CMV teórico do período.
     *
     * @return custo pelas fichas ÷ receita, em percentual; zero se não houve venda
     */
    public double getCmvTeorico() {
        return percentual(custoTeorico);
    }

    /**
     * Calcula o CMV real do período.
     *
     * @return custo do que saiu do estoque ÷ receita, em percentual
     */
    public double getCmvReal() {
        return percentual(custoReal);
    }

    /**
     * Divide um custo pela receita do período.
     *
     * @param custo o custo a comparar
     * @return o percentual, ou zero quando não houve faturamento
     */
    private double percentual(BigDecimal custo) {
        if (receita.signum() <= 0) {
            return 0;
        }
        return custo.doubleValue() / receita.doubleValue() * 100.0;
    }

    /**
     * Calcula quanto sobrou do faturamento depois de pagar os insumos.
     *
     * <p>Usa o custo teórico, que cobre todos os insumos das fichas. O custo real só
     * enxerga o que passa pela balança e subestimaria o gasto.</p>
     *
     * @return receita menos custo teórico
     */
    public BigDecimal getMargemBruta() {
        return receita.subtract(custoTeorico).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calcula o valor médio de cada unidade vendida.
     *
     * @return receita ÷ unidades vendidas, ou zero se nada foi vendido
     */
    public BigDecimal getTicketMedio() {
        if (unidadesVendidas <= 0) {
            return BigDecimal.ZERO;
        }
        return receita.divide(BigDecimal.valueOf(unidadesVendidas), 2, RoundingMode.HALF_UP);
    }

    /**
     * Calcula quantas vezes o valor comprado supera o efetivamente consumido.
     *
     * <p>É o indicador que denuncia caixa parado na prateleira. Comprar 4 vezes o
     * que se gasta no mês não é reposição, é estoque inflando.</p>
     *
     * @return compras ÷ custo real, ou zero quando não houve consumo medido
     */
    public double getRazaoCompraConsumo() {
        if (custoReal.signum() <= 0) {
            return 0;
        }
        return compras.doubleValue() / custoReal.doubleValue();
    }

    /**
     * Classifica o CMV teórico segundo as faixas de mercado.
     *
     * @return {@code "sem-venda"}, {@code "ok"}, {@code "atencao"} ou {@code "ruim"}
     */
    public String getClassificacaoCmv() {
        if (receita.signum() <= 0) {
            return "sem-venda";
        }
        double cmv = getCmvTeorico();
        if (cmv <= CMV_IDEAL) {
            return "ok";
        }
        return cmv <= CMV_LIMITE ? "atencao" : "ruim";
    }

    /**
     * Monta a frase que acompanha o indicador de CMV.
     *
     * @return o texto exibido no painel
     */
    public String getRotuloCmv() {
        return switch (getClassificacaoCmv()) {
            case "sem-venda" -> "Sem vendas no período";
            case "ok" -> "Dentro do ideal para cafeteria";
            case "atencao" -> "Acima do ideal, dentro da faixa comum";
            default -> "Acima da faixa do setor";
        };
    }

    /**
     * Informa se houve movimento suficiente para os indicadores fazerem sentido.
     *
     * @return {@code true} se existe faturamento no período
     */
    public boolean temVendas() {
        return receita.signum() > 0;
    }

    /**
     * Retorna o período analisado.
     *
     * @return o intervalo de datas
     */
    public Periodo getPeriodo() {
        return periodo;
    }

    /**
     * Retorna o faturamento do período.
     *
     * @return a receita
     */
    public BigDecimal getReceita() {
        return receita;
    }

    /**
     * Retorna o custo calculado pelas fichas técnicas das vendas.
     *
     * @return o custo teórico
     */
    public BigDecimal getCustoTeorico() {
        return custoTeorico;
    }

    /**
     * Retorna o custo do que efetivamente saiu do estoque.
     *
     * @return o custo real
     */
    public BigDecimal getCustoReal() {
        return custoReal;
    }

    /**
     * Retorna o valor das compras do período.
     *
     * @return o total das entradas
     */
    public BigDecimal getCompras() {
        return compras;
    }

    /**
     * Retorna quantas unidades foram vendidas.
     *
     * @return o total de unidades
     */
    public int getUnidadesVendidas() {
        return unidadesVendidas;
    }

    /**
     * Retorna quantas linhas de venda foram registradas.
     *
     * @return o número de lançamentos
     */
    public int getNumeroDeLancamentos() {
        return numeroDeLancamentos;
    }
}
