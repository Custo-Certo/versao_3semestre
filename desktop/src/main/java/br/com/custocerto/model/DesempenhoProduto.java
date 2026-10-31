package br.com.custocerto.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Como um produto do cardápio se comportou em um período: quanto vendeu e quanto
 * deixou por unidade.
 *
 * <p>Serve à <b>engenharia de cardápio</b>, técnica que cruza dois eixos — margem
 * de contribuição e popularidade — e coloca cada produto em um dos quatro
 * quadrantes descritos em {@link Quadrante}. A ideia é antiga e bem documentada na
 * gestão de restaurantes: reposicionar e reprecificar o que já existe costuma
 * render mais do que vender mais.</p>
 *
 * <p>Os cortes entre "alto" e "baixo" não são valores fixos: são as <b>médias do
 * próprio cardápio</b> no período. Um produto é popular se vendeu acima da média
 * de unidades, e tem margem alta se deixa acima da margem média. Isso faz a
 * classificação se ajustar sozinha a qualquer cardápio, em vez de depender de um
 * limite arbitrário que valeria para uma cafeteria e não para outra.</p>
 *
 * @author Equipe Custo Certo - FATEC Indaiatuba
 * @version 1.0
 */
public class DesempenhoProduto {

    /**
     * Os quatro quadrantes da engenharia de cardápio, cada um com a sua ação.
     */
    public enum Quadrante {

        /** Margem alta e vende muito. O melhor que o cardápio tem. */
        ESTRELA("Estrela", "margem alta · vende muito",
                "Proteger. Destaque no cardápio e cuidado ao mexer no preço."),

        /** Margem baixa e vende muito. Sustenta o movimento, mas deixa pouco. */
        CAVALO("Cavalo de carga", "margem baixa · vende muito",
               "Consertar. Renegociar insumo ou revisar a porção — pelo volume, "
               + "ganho pequeno aqui rende muito."),

        /** Margem alta e vende pouco. Vale tentar empurrar. */
        ENIGMA("Enigma", "margem alta · vende pouco",
               "Promover. Reposicionar no cardápio e sugerir no balcão."),

        /** Margem baixa e vende pouco. Ocupa espaço no cardápio e no estoque. */
        ABACAXI("Abacaxi", "margem baixa · vende pouco",
                "Remover ou repensar. Ocupa espaço no cardápio e no estoque.");

        private final String titulo;
        private final String eixos;
        private final String acao;

        /**
         * Cria o quadrante com o seu rótulo e a ação recomendada.
         *
         * @param titulo o nome do quadrante
         * @param eixos  a combinação de margem e popularidade que o define
         * @param acao   o que fazer com os produtos que caem nele
         */
        Quadrante(String titulo, String eixos, String acao) {
            this.titulo = titulo;
            this.eixos = eixos;
            this.acao = acao;
        }

        /**
         * Retorna o nome do quadrante.
         *
         * @return o título exibido na interface
         */
        public String getTitulo() {
            return titulo;
        }

        /**
         * Retorna a combinação de eixos que define o quadrante.
         *
         * @return por exemplo {@code "margem alta · vende muito"}
         */
        public String getEixos() {
            return eixos;
        }

        /**
         * Retorna a ação recomendada para o quadrante.
         *
         * @return o texto de recomendação
         */
        public String getAcao() {
            return acao;
        }
    }

    private int receitaId;
    private String nome;
    private BigDecimal precoMedio = BigDecimal.ZERO;
    private BigDecimal custoUnitario = BigDecimal.ZERO;
    private int unidadesVendidas;
    private boolean temFicha = true;
    private double participacaoVendas;
    private Quadrante quadrante = Quadrante.ABACAXI;

    /**
     * Calcula quanto uma unidade deixa depois de pagar os insumos.
     *
     * @return preço médio praticado menos custo da ficha
     */
    public BigDecimal getMargemUnitaria() {
        return precoMedio.subtract(custoUnitario).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calcula quanto o produto deixou no período inteiro.
     *
     * <p>É o número que mais importa na prática: um produto de margem pequena que
     * vende muito pode contribuir mais do que um de margem alta que quase não sai.</p>
     *
     * @return margem unitária × unidades vendidas
     */
    public BigDecimal getMargemTotal() {
        return getMargemUnitaria().multiply(BigDecimal.valueOf(unidadesVendidas))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calcula o faturamento do produto no período.
     *
     * @return preço médio × unidades vendidas
     */
    public BigDecimal getFaturamento() {
        return precoMedio.multiply(BigDecimal.valueOf(unidadesVendidas))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calcula quanto o custo representa do preço deste produto.
     *
     * @return o percentual do preço consumido pelo custo
     */
    public double getPercentualCusto() {
        if (precoMedio.signum() <= 0) {
            return 0;
        }
        return custoUnitario.doubleValue() / precoMedio.doubleValue() * 100.0;
    }

    /**
     * Retorna a chave do produto.
     *
     * @return o identificador no banco
     */
    public int getReceitaId() {
        return receitaId;
    }

    /**
     * Define a chave do produto.
     *
     * @param receitaId o identificador no banco
     */
    public void setReceitaId(int receitaId) {
        this.receitaId = receitaId;
    }

    /**
     * Retorna o nome do produto.
     *
     * @return o nome
     */
    public String getNome() {
        return nome;
    }

    /**
     * Define o nome do produto.
     *
     * @param nome o nome
     */
    public void setNome(String nome) {
        this.nome = nome;
    }

    /**
     * Retorna o preço médio efetivamente praticado no período.
     *
     * @return o preço médio
     */
    public BigDecimal getPrecoMedio() {
        return precoMedio;
    }

    /**
     * Define o preço médio efetivamente praticado no período.
     *
     * @param precoMedio o preço médio ponderado pelas quantidades
     */
    public void setPrecoMedio(BigDecimal precoMedio) {
        this.precoMedio = precoMedio;
    }

    /**
     * Retorna o custo de uma unidade, pela ficha técnica.
     *
     * @return o custo unitário
     */
    public BigDecimal getCustoUnitario() {
        return custoUnitario;
    }

    /**
     * Define o custo de uma unidade.
     *
     * @param custoUnitario o custo pela ficha técnica
     */
    public void setCustoUnitario(BigDecimal custoUnitario) {
        this.custoUnitario = custoUnitario;
    }

    /**
     * Retorna quantas unidades foram vendidas no período.
     *
     * @return o total de unidades
     */
    public int getUnidadesVendidas() {
        return unidadesVendidas;
    }

    /**
     * Define quantas unidades foram vendidas no período.
     *
     * @param unidadesVendidas o total de unidades
     */
    public void setUnidadesVendidas(int unidadesVendidas) {
        this.unidadesVendidas = unidadesVendidas;
    }

    /**
     * Informa se o produto tem ficha técnica cadastrada.
     *
     * <p>Sem ficha, o custo é zero e a margem apareceria igual ao preço inteiro —
     * o produto viraria uma estrela falsa. A tela avisa em vez de classificar.</p>
     *
     * @return {@code true} se há ficha técnica
     */
    public boolean isTemFicha() {
        return temFicha;
    }

    /**
     * Define se o produto tem ficha técnica cadastrada.
     *
     * @param temFicha {@code true} se há ficha
     */
    public void setTemFicha(boolean temFicha) {
        this.temFicha = temFicha;
    }

    /**
     * Retorna quanto o produto representa das unidades vendidas.
     *
     * @return o percentual de participação
     */
    public double getParticipacaoVendas() {
        return participacaoVendas;
    }

    /**
     * Define quanto o produto representa das unidades vendidas.
     *
     * @param participacaoVendas o percentual de participação
     */
    public void setParticipacaoVendas(double participacaoVendas) {
        this.participacaoVendas = participacaoVendas;
    }

    /**
     * Retorna o quadrante da engenharia de cardápio.
     *
     * @return a classificação
     */
    public Quadrante getQuadrante() {
        return quadrante;
    }

    /**
     * Define o quadrante da engenharia de cardápio.
     *
     * @param quadrante a classificação, calculada contra as médias do cardápio
     */
    public void setQuadrante(Quadrante quadrante) {
        this.quadrante = quadrante;
    }

    /**
     * Retorna o desempenho descrito em texto.
     *
     * @return o nome do produto e o quadrante
     */
    @Override
    public String toString() {
        return nome + " — " + quadrante.getTitulo();
    }
}
