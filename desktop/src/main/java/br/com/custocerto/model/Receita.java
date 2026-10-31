package br.com.custocerto.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Um produto do cardápio, com a sua ficha técnica.
 *
 * <p>Espelha uma linha de {@code receitas} mais as linhas de
 * {@code receita_ingredientes} que pertencem a ela. É a peça que faltava para o
 * sistema saber quanto custa o que a cafeteria vende: até então o custo de um
 * cappuccino era palpite, porque nada ligava o produto aos insumos que entram
 * nele.</p>
 *
 * <p>O custo <b>não é armazenado</b>. Ele é somado a cada consulta, a partir do
 * preço que os insumos têm no momento. É de propósito: guardar o custo em coluna
 * significaria recalcular o cardápio inteiro toda vez que um fornecedor
 * reajustasse um preço, e conviver com números velhos até alguém lembrar de
 * fazê-lo.</p>
 *
 * @author Equipe Custo Certo - FATEC Indaiatuba
 * @version 1.0
 */
public class Receita {

    private int id;
    private String nome;
    private String descricao;
    private BigDecimal precoVenda = BigDecimal.ZERO;
    private boolean ativo = true;
    private final List<ItemFicha> itens = new ArrayList<>();

    /**
     * Cria um produto vazio, preenchido pelos métodos de escrita.
     */
    public Receita() {
    }

    /**
     * Cria um produto com nome e preço de venda.
     *
     * @param nome       o nome exibido no cardápio
     * @param precoVenda o preço de tabela
     */
    public Receita(String nome, BigDecimal precoVenda) {
        this.nome = nome;
        this.precoVenda = precoVenda;
    }

    /**
     * Calcula o custo de produzir uma unidade, somando as linhas da ficha.
     *
     * @return a soma dos custos dos insumos, com duas casas decimais
     */
    public BigDecimal getCusto() {
        BigDecimal total = BigDecimal.ZERO;
        for (ItemFicha item : itens) {
            total = total.add(item.getCusto());
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calcula a margem de contribuição de uma unidade.
     *
     * <p>É quanto sobra do preço depois de pagar os insumos — o dinheiro que o
     * produto entrega para cobrir aluguel, folha e lucro. É a medida que a
     * engenharia de cardápio usa para decidir o que promover e o que tirar.</p>
     *
     * @return preço de venda menos custo
     */
    public BigDecimal getMargem() {
        return precoVenda.subtract(getCusto()).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calcula quanto o custo representa do preço de venda.
     *
     * <p>É o CMV deste produto isolado. Um valor alto significa que o insumo come
     * boa parte do preço; a referência usual de cafeteria fica em torno de 25%.</p>
     *
     * @return o percentual do preço consumido pelo custo, ou zero se não há preço
     */
    public double getPercentualCusto() {
        if (precoVenda == null || precoVenda.signum() <= 0) {
            return 0;
        }
        return getCusto().doubleValue() / precoVenda.doubleValue() * 100.0;
    }

    /**
     * Informa se a ficha técnica está preenchida.
     *
     * <p>Um produto sem itens tem custo zero, o que faria a margem parecer igual ao
     * preço inteiro. As telas usam este teste para avisar em vez de exibir um
     * número bonito e errado.</p>
     *
     * @return {@code true} se existe ao menos um insumo na ficha
     */
    public boolean temFicha() {
        return !itens.isEmpty();
    }

    /**
     * Retorna a chave primária do produto.
     *
     * @return o identificador no banco
     */
    public int getId() {
        return id;
    }

    /**
     * Define a chave primária do produto.
     *
     * @param id o identificador no banco
     */
    public void setId(int id) {
        this.id = id;
    }

    /**
     * Retorna o nome do produto.
     *
     * @return o nome exibido no cardápio
     */
    public String getNome() {
        return nome;
    }

    /**
     * Define o nome do produto.
     *
     * @param nome o nome exibido no cardápio
     */
    public void setNome(String nome) {
        this.nome = nome;
    }

    /**
     * Retorna a descrição do produto.
     *
     * @return a descrição, ou {@code null}
     */
    public String getDescricao() {
        return descricao;
    }

    /**
     * Define a descrição do produto.
     *
     * @param descricao o texto livre de apoio
     */
    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    /**
     * Retorna o preço de tabela.
     *
     * @return o preço de venda
     */
    public BigDecimal getPrecoVenda() {
        return precoVenda;
    }

    /**
     * Define o preço de tabela.
     *
     * @param precoVenda o preço de venda
     */
    public void setPrecoVenda(BigDecimal precoVenda) {
        this.precoVenda = precoVenda;
    }

    /**
     * Informa se o produto está ativo no cardápio.
     *
     * @return {@code true} se está sendo vendido
     */
    public boolean isAtivo() {
        return ativo;
    }

    /**
     * Define se o produto está ativo no cardápio.
     *
     * @param ativo {@code true} para produto em venda
     */
    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }

    /**
     * Retorna as linhas da ficha técnica.
     *
     * @return a lista de insumos e quantidades
     */
    public List<ItemFicha> getItens() {
        return itens;
    }

    /**
     * Substitui as linhas da ficha técnica.
     *
     * @param novos as linhas novas
     */
    public void setItens(List<ItemFicha> novos) {
        itens.clear();
        if (novos != null) {
            itens.addAll(novos);
        }
    }

    /**
     * Retorna o produto descrito em texto.
     *
     * @return o nome do produto
     */
    @Override
    public String toString() {
        return nome;
    }
}
