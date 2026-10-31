package br.com.custocerto.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * O que aconteceu com um insumo dentro de um período: quanto girou, quanto custou
 * e quanto tempo o saldo ainda cobre.
 *
 * <p>Substitui o alerta de estoque que o sistema tinha antes, que disparava sempre
 * que um insumo caía abaixo de 25% da capacidade — o mesmo limite para todo mundo,
 * sem olhar a velocidade com que cada um sai. Pelo limite antigo, um leite que
 * acaba em dois dias e uma canela que dura sete meses recebiam o mesmo aviso.</p>
 *
 * <p>A pergunta útil não é "quanto resta da capacidade", é <b>"quantos dias isso
 * ainda cobre"</b>. É o que {@link #getDiasCobertura()} responde, dividindo o saldo
 * pelo consumo médio diário observado.</p>
 *
 * @author Equipe Custo Certo - FATEC Indaiatuba
 * @version 1.0
 */
public class IndicadorInsumo {

    /**
     * Classificação de curva ABC — onde o dinheiro do estoque está concentrado.
     *
     * <p>Ordenando os insumos do que mais consome dinheiro para o que menos
     * consome e acumulando o percentual, os primeiros até 80% são A, até 95% são B,
     * e o resto é C. É o que diz onde vale a pena negociar com fornecedor: um
     * desconto de 5% num insumo classe A vale mais do que zerar o custo de vários
     * classe C.</p>
     */
    public enum ClasseAbc {

        /** Primeiros 80% do custo acumulado. Poucos itens, quase todo o dinheiro. */
        A("A", "Concentra o custo — negocie aqui primeiro"),

        /** Faixa entre 80% e 95% do custo acumulado. */
        B("B", "Peso intermediário no custo"),

        /** Últimos 5% do custo acumulado. Muitos itens, pouco dinheiro. */
        C("C", "Pouco peso no custo — não vale esforço de negociação");

        private final String letra;
        private final String explicacao;

        /**
         * Cria a classe com a sua letra e a explicação exibida na interface.
         *
         * @param letra      a letra da curva
         * @param explicacao o que a classificação significa na prática
         */
        ClasseAbc(String letra, String explicacao) {
            this.letra = letra;
            this.explicacao = explicacao;
        }

        /**
         * Retorna a letra da classe.
         *
         * @return {@code "A"}, {@code "B"} ou {@code "C"}
         */
        public String getLetra() {
            return letra;
        }

        /**
         * Retorna a explicação da classe.
         *
         * @return o texto exibido ao usuário
         */
        public String getExplicacao() {
            return explicacao;
        }
    }

    private int ingredienteId;
    private String nome;
    private Unidade unidade;
    private BigDecimal saldo = BigDecimal.ZERO;
    private BigDecimal precoUnitario = BigDecimal.ZERO;
    private BigDecimal consumoPeriodo = BigDecimal.ZERO;
    private BigDecimal compradoPeriodo = BigDecimal.ZERO;
    private long diasDoPeriodo = 1;
    private double participacaoCusto;
    private ClasseAbc classe = ClasseAbc.C;

    /**
     * Calcula quanto do insumo sai, em média, por dia do período.
     *
     * @return consumo do período dividido pelos dias do período
     */
    public BigDecimal getConsumoMedioDiario() {
        if (diasDoPeriodo <= 0) {
            return BigDecimal.ZERO;
        }
        return consumoPeriodo.divide(BigDecimal.valueOf(diasDoPeriodo), 4, RoundingMode.HALF_UP);
    }

    /**
     * Calcula quanto custou o que saiu do estoque no período.
     *
     * @return consumo do período × preço atual do insumo
     */
    public BigDecimal getCustoConsumido() {
        return consumoPeriodo.multiply(precoUnitario).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calcula por quantos dias o saldo atual ainda cobre o consumo observado.
     *
     * @return o número de dias, ou {@code -1} quando não houve consumo no período e
     *         portanto não há ritmo do qual projetar
     */
    public double getDiasCobertura() {
        BigDecimal porDia = getConsumoMedioDiario();
        if (porDia.signum() <= 0) {
            return -1;
        }
        return saldo.divide(porDia, 2, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * Informa se houve saída deste insumo no período.
     *
     * @return {@code true} se algo foi consumido
     */
    public boolean temConsumo() {
        return consumoPeriodo.signum() > 0;
    }

    /**
     * Classifica a urgência a partir dos dias de cobertura.
     *
     * <p>Os cortes — 3 e 7 dias — valem para uma cafeteria que compra ao menos uma
     * vez por semana. Abaixo de três dias não dá tempo de um fornecedor entregar;
     * abaixo de sete, o item entra na próxima lista de compras.</p>
     *
     * @return {@code "critico"}, {@code "atencao"}, {@code "ok"}, ou
     *         {@code "sem-consumo"} quando não há ritmo para projetar
     */
    public String getUrgencia() {
        double dias = getDiasCobertura();
        if (dias < 0) {
            return "sem-consumo";
        }
        if (dias < 3) {
            return "critico";
        }
        return dias < 7 ? "atencao" : "ok";
    }

    /**
     * Monta a frase de cobertura exibida na interface.
     *
     * @return por exemplo {@code "cobre 2 dias"}, ou o aviso de que não houve saída
     */
    public String getRotuloCobertura() {
        double dias = getDiasCobertura();
        if (dias < 0) {
            return "sem saída no período";
        }
        if (dias < 1) {
            return "acaba hoje";
        }
        long inteiro = Math.round(dias);
        return "cobre " + inteiro + (inteiro == 1 ? " dia" : " dias");
    }

    /**
     * Retorna a chave do insumo.
     *
     * @return o identificador no banco
     */
    public int getIngredienteId() {
        return ingredienteId;
    }

    /**
     * Define a chave do insumo.
     *
     * @param ingredienteId o identificador no banco
     */
    public void setIngredienteId(int ingredienteId) {
        this.ingredienteId = ingredienteId;
    }

    /**
     * Retorna o nome do insumo.
     *
     * @return o nome
     */
    public String getNome() {
        return nome;
    }

    /**
     * Define o nome do insumo.
     *
     * @param nome o nome
     */
    public void setNome(String nome) {
        this.nome = nome;
    }

    /**
     * Retorna a unidade de medida.
     *
     * @return a unidade
     */
    public Unidade getUnidade() {
        return unidade;
    }

    /**
     * Define a unidade de medida.
     *
     * @param unidade a unidade
     */
    public void setUnidade(Unidade unidade) {
        this.unidade = unidade;
    }

    /**
     * Retorna o saldo atual em estoque.
     *
     * @return a quantidade disponível
     */
    public BigDecimal getSaldo() {
        return saldo;
    }

    /**
     * Define o saldo atual em estoque.
     *
     * @param saldo a quantidade disponível
     */
    public void setSaldo(BigDecimal saldo) {
        this.saldo = saldo;
    }

    /**
     * Retorna o preço atual por unidade de medida.
     *
     * @return o preço unitário
     */
    public BigDecimal getPrecoUnitario() {
        return precoUnitario;
    }

    /**
     * Define o preço atual por unidade de medida.
     *
     * @param precoUnitario o preço unitário
     */
    public void setPrecoUnitario(BigDecimal precoUnitario) {
        this.precoUnitario = precoUnitario;
    }

    /**
     * Retorna quanto saiu do estoque no período.
     *
     * @return a quantidade consumida
     */
    public BigDecimal getConsumoPeriodo() {
        return consumoPeriodo;
    }

    /**
     * Define quanto saiu do estoque no período.
     *
     * @param consumoPeriodo a quantidade consumida
     */
    public void setConsumoPeriodo(BigDecimal consumoPeriodo) {
        this.consumoPeriodo = consumoPeriodo;
    }

    /**
     * Retorna quanto foi comprado no período.
     *
     * @return o valor das entradas
     */
    public BigDecimal getCompradoPeriodo() {
        return compradoPeriodo;
    }

    /**
     * Define quanto foi comprado no período.
     *
     * @param compradoPeriodo o valor das entradas
     */
    public void setCompradoPeriodo(BigDecimal compradoPeriodo) {
        this.compradoPeriodo = compradoPeriodo;
    }

    /**
     * Retorna quantos dias o período cobre.
     *
     * @return o número de dias
     */
    public long getDiasDoPeriodo() {
        return diasDoPeriodo;
    }

    /**
     * Define quantos dias o período cobre.
     *
     * @param diasDoPeriodo o número de dias, usado como divisor da média diária
     */
    public void setDiasDoPeriodo(long diasDoPeriodo) {
        this.diasDoPeriodo = Math.max(1, diasDoPeriodo);
    }

    /**
     * Retorna quanto este insumo representa do custo consumido total.
     *
     * @return o percentual de participação
     */
    public double getParticipacaoCusto() {
        return participacaoCusto;
    }

    /**
     * Define quanto este insumo representa do custo consumido total.
     *
     * @param participacaoCusto o percentual de participação
     */
    public void setParticipacaoCusto(double participacaoCusto) {
        this.participacaoCusto = participacaoCusto;
    }

    /**
     * Retorna a classe da curva ABC.
     *
     * @return a classificação
     */
    public ClasseAbc getClasse() {
        return classe;
    }

    /**
     * Define a classe da curva ABC.
     *
     * @param classe a classificação, calculada sobre a lista ordenada
     */
    public void setClasse(ClasseAbc classe) {
        this.classe = classe;
    }

    /**
     * Retorna o indicador descrito em texto.
     *
     * @return o nome do insumo e a cobertura
     */
    @Override
    public String toString() {
        return nome + " — " + getRotuloCobertura();
    }
}
