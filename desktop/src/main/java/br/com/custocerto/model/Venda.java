package br.com.custocerto.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Uma venda registrada: quantas unidades de um produto saíram, quando e por quanto.
 *
 * <p>Espelha uma linha de {@code vendas}. É o dado que faltava para o sistema
 * calcular qualquer coisa sobre margem: sem saber o que foi vendido e por qual
 * preço, não existe receita, e sem receita não existe CMV.</p>
 *
 * <p>O {@code precoUnitario} é o preço <b>praticado nesta venda</b>, e não uma
 * cópia do preço de tabela do produto. Eles divergem em promoção, cortesia ou
 * reajuste — e guardar o praticado é o que impede que mexer no cardápio hoje
 * reescreva o faturamento do mês passado.</p>
 *
 * @author Equipe Custo Certo - FATEC Indaiatuba
 * @version 1.0
 */
public class Venda {

    private int id;
    private int receitaId;
    private String nomeReceita;
    private int quantidade;
    private BigDecimal precoUnitario = BigDecimal.ZERO;
    private BigDecimal custoUnitario = BigDecimal.ZERO;
    private String observacao;
    private LocalDate data = LocalDate.now();

    /**
     * Cria uma venda vazia, preenchida pelos métodos de escrita.
     */
    public Venda() {
    }

    /**
     * Cria uma venda com os dados essenciais.
     *
     * @param receitaId     o produto vendido
     * @param quantidade    quantas unidades
     * @param precoUnitario o preço praticado por unidade
     * @param data          o dia da venda
     */
    public Venda(int receitaId, int quantidade, BigDecimal precoUnitario, LocalDate data) {
        this.receitaId = receitaId;
        this.quantidade = quantidade;
        this.precoUnitario = precoUnitario;
        this.data = data;
    }

    /**
     * Calcula o faturamento desta linha.
     *
     * @return quantidade × preço praticado
     */
    public BigDecimal getTotal() {
        return precoUnitario.multiply(BigDecimal.valueOf(quantidade))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calcula o custo desta linha, pela ficha técnica do produto.
     *
     * <p>O custo unitário é o de <em>hoje</em>, calculado a partir dos preços atuais
     * dos insumos, e não o que valia no dia da venda. É uma simplificação
     * consciente: reconstruir o custo histórico exigiria guardar o preço de cada
     * insumo em cada data, o que o modelo ainda não faz.</p>
     *
     * @return quantidade × custo unitário
     */
    public BigDecimal getCustoTotal() {
        return custoUnitario.multiply(BigDecimal.valueOf(quantidade))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calcula quanto esta venda deixou depois de pagar os insumos.
     *
     * @return faturamento menos custo
     */
    public BigDecimal getMargemTotal() {
        return getTotal().subtract(getCustoTotal());
    }

    /**
     * Retorna a chave primária da venda.
     *
     * @return o identificador no banco
     */
    public int getId() {
        return id;
    }

    /**
     * Define a chave primária da venda.
     *
     * @param id o identificador no banco
     */
    public void setId(int id) {
        this.id = id;
    }

    /**
     * Retorna o produto vendido.
     *
     * @return a chave da receita
     */
    public int getReceitaId() {
        return receitaId;
    }

    /**
     * Define o produto vendido.
     *
     * @param receitaId a chave da receita
     */
    public void setReceitaId(int receitaId) {
        this.receitaId = receitaId;
    }

    /**
     * Retorna o nome do produto, trazido por junção.
     *
     * @return o nome do produto
     */
    public String getNomeReceita() {
        return nomeReceita;
    }

    /**
     * Define o nome do produto.
     *
     * @param nomeReceita o nome do produto
     */
    public void setNomeReceita(String nomeReceita) {
        this.nomeReceita = nomeReceita;
    }

    /**
     * Retorna quantas unidades foram vendidas.
     *
     * @return a quantidade
     */
    public int getQuantidade() {
        return quantidade;
    }

    /**
     * Define quantas unidades foram vendidas.
     *
     * @param quantidade a quantidade
     */
    public void setQuantidade(int quantidade) {
        this.quantidade = quantidade;
    }

    /**
     * Retorna o preço praticado por unidade.
     *
     * @return o preço unitário
     */
    public BigDecimal getPrecoUnitario() {
        return precoUnitario;
    }

    /**
     * Define o preço praticado por unidade.
     *
     * @param precoUnitario o preço unitário
     */
    public void setPrecoUnitario(BigDecimal precoUnitario) {
        this.precoUnitario = precoUnitario;
    }

    /**
     * Retorna o custo unitário calculado pela ficha técnica.
     *
     * @return o custo por unidade
     */
    public BigDecimal getCustoUnitario() {
        return custoUnitario;
    }

    /**
     * Define o custo unitário calculado pela ficha técnica.
     *
     * @param custoUnitario o custo por unidade
     */
    public void setCustoUnitario(BigDecimal custoUnitario) {
        this.custoUnitario = custoUnitario;
    }

    /**
     * Retorna a observação da venda.
     *
     * @return o texto livre, ou {@code null}
     */
    public String getObservacao() {
        return observacao;
    }

    /**
     * Define a observação da venda.
     *
     * @param observacao o texto livre
     */
    public void setObservacao(String observacao) {
        this.observacao = observacao;
    }

    /**
     * Retorna o dia da venda.
     *
     * @return a data
     */
    public LocalDate getData() {
        return data;
    }

    /**
     * Define o dia da venda.
     *
     * @param data a data
     */
    public void setData(LocalDate data) {
        this.data = data;
    }

    /**
     * Retorna a venda descrita em texto.
     *
     * @return a quantidade e o nome do produto
     */
    @Override
    public String toString() {
        return quantidade + "x " + nomeReceita;
    }
}
