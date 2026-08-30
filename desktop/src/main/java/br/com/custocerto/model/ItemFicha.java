package br.com.custocerto.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Uma linha da ficha técnica: quanto de um insumo entra em uma unidade do produto.
 *
 * <p>Espelha uma linha da tabela {@code receita_ingredientes}, carregando junto o
 * nome, a unidade e o preço atual do insumo — dados que vêm de {@code ingredientes}
 * por junção. Eles são copiados para cá porque o custo tem de ser recalculado a
 * partir do <em>preço de hoje</em>: quando o fornecedor reajusta o café, o custo do
 * cappuccino muda sozinho, sem ninguém reeditar receita nenhuma.</p>
 *
 * <p>A quantidade está sempre na unidade do próprio insumo. Café cadastrado em kg
 * com 18 gramas por dose vira {@code 0,018}.</p>
 *
 * @author Equipe Custo Certo - FATEC Indaiatuba
 * @version 1.0
 */
public class ItemFicha {

    private int id;
    private int receitaId;
    private int ingredienteId;
    private String nomeIngrediente;
    private Unidade unidade;
    private BigDecimal quantidade = BigDecimal.ZERO;
    private BigDecimal precoUnitario = BigDecimal.ZERO;

    /**
     * Cria uma linha vazia, preenchida pelos métodos de escrita.
     */
    public ItemFicha() {
    }

    /**
     * Cria uma linha de ficha para um insumo e uma quantidade.
     *
     * @param ingredienteId a chave do insumo
     * @param quantidade    quanto dele entra em uma unidade do produto
     */
    public ItemFicha(int ingredienteId, BigDecimal quantidade) {
        this.ingredienteId = ingredienteId;
        this.quantidade = quantidade;
    }

    /**
     * Calcula quanto este insumo custa dentro de uma unidade do produto.
     *
     * <p>Usa quatro casas decimais e não duas: meio grama de canela custa fração de
     * centavo, e arredondar linha a linha para centavos faria o custo do produto
     * somar errado. O arredondamento para centavos acontece só no total.</p>
     *
     * @return quantidade × preço unitário do insumo
     */
    public BigDecimal getCusto() {
        if (quantidade == null || precoUnitario == null) {
            return BigDecimal.ZERO;
        }
        return quantidade.multiply(precoUnitario).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * Retorna a quantidade seguida da unidade, para exibição.
     *
     * @return por exemplo {@code "0,018 kg"}
     */
    public String getQuantidadeFormatada() {
        String sigla = unidade == null ? "" : " " + unidade.getSigla();
        return quantidade.stripTrailingZeros().toPlainString().replace('.', ',') + sigla;
    }

    /**
     * Retorna a chave primária da linha.
     *
     * @return o identificador no banco
     */
    public int getId() {
        return id;
    }

    /**
     * Define a chave primária da linha.
     *
     * @param id o identificador no banco
     */
    public void setId(int id) {
        this.id = id;
    }

    /**
     * Retorna a receita a que esta linha pertence.
     *
     * @return a chave da receita
     */
    public int getReceitaId() {
        return receitaId;
    }

    /**
     * Define a receita a que esta linha pertence.
     *
     * @param receitaId a chave da receita
     */
    public void setReceitaId(int receitaId) {
        this.receitaId = receitaId;
    }

    /**
     * Retorna o insumo desta linha.
     *
     * @return a chave do insumo
     */
    public int getIngredienteId() {
        return ingredienteId;
    }

    /**
     * Define o insumo desta linha.
     *
     * @param ingredienteId a chave do insumo
     */
    public void setIngredienteId(int ingredienteId) {
        this.ingredienteId = ingredienteId;
    }

    /**
     * Retorna o nome do insumo, trazido por junção.
     *
     * @return o nome do insumo
     */
    public String getNomeIngrediente() {
        return nomeIngrediente;
    }

    /**
     * Define o nome do insumo.
     *
     * @param nomeIngrediente o nome do insumo
     */
    public void setNomeIngrediente(String nomeIngrediente) {
        this.nomeIngrediente = nomeIngrediente;
    }

    /**
     * Retorna a unidade de medida do insumo.
     *
     * @return a unidade
     */
    public Unidade getUnidade() {
        return unidade;
    }

    /**
     * Define a unidade de medida do insumo.
     *
     * @param unidade a unidade
     */
    public void setUnidade(Unidade unidade) {
        this.unidade = unidade;
    }

    /**
     * Retorna quanto do insumo entra em uma unidade do produto.
     *
     * @return a quantidade, na unidade do insumo
     */
    public BigDecimal getQuantidade() {
        return quantidade;
    }

    /**
     * Define quanto do insumo entra em uma unidade do produto.
     *
     * @param quantidade a quantidade, na unidade do insumo
     */
    public void setQuantidade(BigDecimal quantidade) {
        this.quantidade = quantidade;
    }

    /**
     * Retorna o preço atual do insumo, por unidade de medida.
     *
     * @return o preço unitário
     */
    public BigDecimal getPrecoUnitario() {
        return precoUnitario;
    }

    /**
     * Define o preço atual do insumo.
     *
     * @param precoUnitario o preço por unidade de medida
     */
    public void setPrecoUnitario(BigDecimal precoUnitario) {
        this.precoUnitario = precoUnitario;
    }

    /**
     * Retorna a linha descrita em texto.
     *
     * @return o nome do insumo e a quantidade
     */
    @Override
    public String toString() {
        return nomeIngrediente + " — " + getQuantidadeFormatada();
    }
}
