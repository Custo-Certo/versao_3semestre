package br.com.custocerto.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Um insumo do estoque, espelhando uma linha da tabela {@code ingredientes}.
 *
 * <p>Os valores monetários e as quantidades usam {@link BigDecimal}, e não
 * {@code double}, porque ponto flutuante acumula erro de arredondamento — e o
 * sistema existe justamente para calcular custo. No banco as colunas
 * correspondentes são {@code DECIMAL(10,2)} para dinheiro e
 * {@code DECIMAL(10,3)} para quantidade, o que dá precisão de grama.</p>
 *
 * <p>Os métodos de cálculo desta classe repetem exatamente as regras já usadas
 * pelo painel web, para que as duas interfaces mostrem sempre o mesmo número.</p>
 *
 * @author Equipe Custo Certo - FATEC Indaiatuba
 * @version 1.0
 */
public class Ingrediente {

    /** Percentual abaixo do qual o estoque entra em alerta, como no painel web. */
    public static final double LIMITE_ALERTA_PERCENTUAL = 25.0;

    /** Dias de antecedência para avisar sobre vencimento, como no painel web. */
    public static final int DIAS_AVISO_VALIDADE = 5;

    private int id;
    private String nome;
    private Unidade unidade;
    private BigDecimal preco = BigDecimal.ZERO;
    private BigDecimal qtd = BigDecimal.ZERO;
    private BigDecimal qtdMax = BigDecimal.ZERO;
    private LocalDate validade;

    /**
     * Cria um insumo vazio, a ser preenchido pelos métodos de escrita.
     */
    public Ingrediente() {
    }

    /**
     * Cria um insumo com os campos preenchidos pelo formulário de cadastro.
     *
     * @param nome     o nome do insumo
     * @param unidade  a unidade de medida
     * @param preco    o preço por unidade
     * @param qtd      a quantidade atual em estoque
     * @param qtdMax   a capacidade de referência, usada como 100% da barra
     * @param validade a data de validade, ou {@code null} se não se aplica
     */
    public Ingrediente(String nome, Unidade unidade, BigDecimal preco,
                       BigDecimal qtd, BigDecimal qtdMax, LocalDate validade) {
        this.nome = nome;
        this.unidade = unidade;
        this.preco = preco;
        this.qtd = qtd;
        this.qtdMax = qtdMax;
        this.validade = validade;
    }

    /**
     * Calcula quanto o estoque deste insumo vale hoje.
     *
     * @return o preço unitário multiplicado pela quantidade em estoque
     */
    public BigDecimal getValorEmEstoque() {
        if (preco == null || qtd == null) {
            return BigDecimal.ZERO;
        }
        return preco.multiply(qtd).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calcula quanto do estoque de referência ainda resta, em porcentagem.
     *
     * @return um valor entre 0 e 100; devolve 0 quando não há capacidade definida
     */
    public double getPercentualEstoque() {
        if (qtdMax == null || qtdMax.signum() <= 0 || qtd == null) {
            return 0;
        }
        double pct = qtd.doubleValue() / qtdMax.doubleValue() * 100.0;
        return Math.max(0, Math.min(100, pct));
    }

    /**
     * Informa se o insumo está abaixo do limite de estoque que dispara alerta.
     *
     * @return {@code true} quando resta menos de 25% da capacidade
     */
    public boolean isEstoqueBaixo() {
        return qtdMax != null && qtdMax.signum() > 0
                && getPercentualEstoque() < LIMITE_ALERTA_PERCENTUAL;
    }

    /**
     * Calcula quantos dias faltam para o vencimento.
     *
     * @return os dias restantes; negativo se já venceu, ou {@link Integer#MAX_VALUE}
     *         quando o insumo não tem validade
     */
    public int getDiasParaVencer() {
        if (validade == null) {
            return Integer.MAX_VALUE;
        }
        return (int) ChronoUnit.DAYS.between(LocalDate.now(), validade);
    }

    /**
     * Informa se a validade já passou.
     *
     * @return {@code true} se o insumo está vencido
     */
    public boolean isVencido() {
        return validade != null && getDiasParaVencer() < 0;
    }

    /**
     * Informa se o vencimento está próximo o bastante para avisar.
     *
     * @return {@code true} quando faltam 5 dias ou menos, e ainda não venceu
     */
    public boolean isVencimentoProximo() {
        if (validade == null || isVencido()) {
            return false;
        }
        return getDiasParaVencer() <= DIAS_AVISO_VALIDADE;
    }

    /**
     * Monta o texto de validade exibido no cartão do insumo.
     *
     * @return {@code "VENCIDO"}, {@code "vence em 3d"}, {@code "120d p/ vencer"}
     *         ou {@code "sem validade"}
     */
    public String getRotuloValidade() {
        if (validade == null) {
            return "sem validade";
        }
        int dias = getDiasParaVencer();
        if (dias < 0) {
            return "VENCIDO";
        }
        if (dias <= DIAS_AVISO_VALIDADE) {
            return "vence em " + dias + "d";
        }
        return dias + "d p/ vencer";
    }

    /**
     * Retorna a chave primária do insumo.
     *
     * @return o identificador no banco
     */
    public int getId() {
        return id;
    }

    /**
     * Define a chave primária do insumo.
     *
     * @param id o identificador no banco
     */
    public void setId(int id) {
        this.id = id;
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
     * @param nome o nome, único na tabela
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
     * Retorna o preço por unidade.
     *
     * @return o preço unitário
     */
    public BigDecimal getPreco() {
        return preco;
    }

    /**
     * Define o preço por unidade.
     *
     * @param preco o preço unitário
     */
    public void setPreco(BigDecimal preco) {
        this.preco = preco;
    }

    /**
     * Retorna a quantidade atual em estoque.
     *
     * @return a quantidade
     */
    public BigDecimal getQtd() {
        return qtd;
    }

    /**
     * Define a quantidade atual em estoque.
     *
     * @param qtd a quantidade
     */
    public void setQtd(BigDecimal qtd) {
        this.qtd = qtd;
    }

    /**
     * Retorna a capacidade de referência do estoque.
     *
     * @return a quantidade máxima
     */
    public BigDecimal getQtdMax() {
        return qtdMax;
    }

    /**
     * Define a capacidade de referência do estoque.
     *
     * @param qtdMax a quantidade máxima
     */
    public void setQtdMax(BigDecimal qtdMax) {
        this.qtdMax = qtdMax;
    }

    /**
     * Retorna a data de validade.
     *
     * @return a validade, ou {@code null} se não se aplica
     */
    public LocalDate getValidade() {
        return validade;
    }

    /**
     * Define a data de validade.
     *
     * @param validade a validade, ou {@code null}
     */
    public void setValidade(LocalDate validade) {
        this.validade = validade;
    }

    /**
     * Retorna o nome do insumo, para exibição direta em listas.
     *
     * @return o nome
     */
    @Override
    public String toString() {
        return nome;
    }
}
