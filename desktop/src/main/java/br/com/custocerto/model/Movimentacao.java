package br.com.custocerto.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Uma entrada ou saída de estoque, espelhando uma linha de
 * {@code movimentacoes_estoque}.
 *
 * <p>Além das colunas da tabela, esta classe carrega o nome e a unidade do
 * insumo. Eles vêm de um {@code JOIN} com {@code ingredientes} feito no DAO —
 * assim a tela do histórico exibe "Café em Grãos" em vez de "ingrediente 1"
 * sem precisar de uma segunda consulta por linha.</p>
 *
 * @author Equipe Custo Certo - FATEC Indaiatuba
 * @version 1.0
 */
public class Movimentacao {

    private int id;
    private int ingredienteId;
    private String nomeIngrediente;
    private Unidade unidade;
    private TipoMovimentacao tipo;
    private BigDecimal quantidade;
    private BigDecimal precoUnitario;
    private String observacao;
    private LocalDate data;

    /**
     * Cria uma movimentação vazia, a ser preenchida pelos métodos de escrita.
     */
    public Movimentacao() {
    }

    /**
     * Cria uma movimentação pronta para ser gravada.
     *
     * @param ingredienteId  a chave do insumo movimentado
     * @param tipo           entrada ou saída
     * @param quantidade     quanto entrou ou saiu, sempre positivo
     * @param precoUnitario  o preço pago, apenas nas entradas
     * @param observacao     texto livre explicando a movimentação
     */
    public Movimentacao(int ingredienteId, TipoMovimentacao tipo, BigDecimal quantidade,
                        BigDecimal precoUnitario, String observacao) {
        this.ingredienteId = ingredienteId;
        this.tipo = tipo;
        this.quantidade = quantidade;
        this.precoUnitario = precoUnitario;
        this.observacao = observacao;
        this.data = LocalDate.now();
    }

    /**
     * Calcula o valor total da movimentação.
     *
     * @return quantidade multiplicada pelo preço unitário, ou zero nas saídas,
     *         que não têm preço registrado
     */
    public BigDecimal getValorTotal() {
        if (precoUnitario == null || quantidade == null) {
            return BigDecimal.ZERO;
        }
        return precoUnitario.multiply(quantidade).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Retorna a chave primária da movimentação.
     *
     * @return o identificador no banco
     */
    public int getId() {
        return id;
    }

    /**
     * Define a chave primária da movimentação.
     *
     * @param id o identificador no banco
     */
    public void setId(int id) {
        this.id = id;
    }

    /**
     * Retorna a chave do insumo movimentado.
     *
     * @return o identificador do insumo
     */
    public int getIngredienteId() {
        return ingredienteId;
    }

    /**
     * Define a chave do insumo movimentado.
     *
     * @param ingredienteId o identificador do insumo
     */
    public void setIngredienteId(int ingredienteId) {
        this.ingredienteId = ingredienteId;
    }

    /**
     * Retorna o nome do insumo, vindo do JOIN.
     *
     * @return o nome do insumo
     */
    public String getNomeIngrediente() {
        return nomeIngrediente;
    }

    /**
     * Define o nome do insumo.
     *
     * @param nomeIngrediente o nome vindo do JOIN
     */
    public void setNomeIngrediente(String nomeIngrediente) {
        this.nomeIngrediente = nomeIngrediente;
    }

    /**
     * Retorna a unidade do insumo, vinda do JOIN.
     *
     * @return a unidade de medida
     */
    public Unidade getUnidade() {
        return unidade;
    }

    /**
     * Define a unidade do insumo.
     *
     * @param unidade a unidade vinda do JOIN
     */
    public void setUnidade(Unidade unidade) {
        this.unidade = unidade;
    }

    /**
     * Retorna a natureza da movimentação.
     *
     * @return entrada ou saída
     */
    public TipoMovimentacao getTipo() {
        return tipo;
    }

    /**
     * Define a natureza da movimentação.
     *
     * @param tipo entrada ou saída
     */
    public void setTipo(TipoMovimentacao tipo) {
        this.tipo = tipo;
    }

    /**
     * Retorna a quantidade movimentada.
     *
     * @return a quantidade, sempre positiva
     */
    public BigDecimal getQuantidade() {
        return quantidade;
    }

    /**
     * Define a quantidade movimentada.
     *
     * @param quantidade a quantidade, sempre positiva
     */
    public void setQuantidade(BigDecimal quantidade) {
        this.quantidade = quantidade;
    }

    /**
     * Retorna o preço unitário pago nesta movimentação.
     *
     * @return o preço, ou {@code null} nas saídas
     */
    public BigDecimal getPrecoUnitario() {
        return precoUnitario;
    }

    /**
     * Define o preço unitário pago nesta movimentação.
     *
     * @param precoUnitario o preço, ou {@code null} nas saídas
     */
    public void setPrecoUnitario(BigDecimal precoUnitario) {
        this.precoUnitario = precoUnitario;
    }

    /**
     * Retorna a observação registrada.
     *
     * @return o texto livre, ou {@code null}
     */
    public String getObservacao() {
        return observacao;
    }

    /**
     * Define a observação da movimentação.
     *
     * @param observacao o texto livre
     */
    public void setObservacao(String observacao) {
        this.observacao = observacao;
    }

    /**
     * Retorna a data da movimentação.
     *
     * @return a data
     */
    public LocalDate getData() {
        return data;
    }

    /**
     * Define a data da movimentação.
     *
     * @param data a data
     */
    public void setData(LocalDate data) {
        this.data = data;
    }
}
