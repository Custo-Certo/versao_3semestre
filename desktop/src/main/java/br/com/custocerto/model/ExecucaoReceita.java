package br.com.custocerto.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Uma receita produzida e pesada na balança, do início ao fim.
 *
 * <p>É o complemento da {@link Receita} da ficha técnica. A ficha diz quanto um
 * produto <em>deveria</em> consumir; a execução registra quanto foi de fato
 * pesado numa produção. Ao finalizar, o custo do que foi pesado vira o CMV real
 * daquela leva.</p>
 *
 * <p>Uma execução passa por três estados: aberta, enquanto os ingredientes são
 * pesados; finalizada, quando porções e preço são informados; e cancelada,
 * quando a produção é abandonada e tudo volta ao estoque.</p>
 *
 * @author Equipe Custo Certo - FATEC Indaiatuba
 * @version 1.0
 */
public class ExecucaoReceita {

    /**
     * Os estados possíveis de uma execução, com o valor gravado no banco.
     */
    public enum Status {
        /** Em andamento: ainda recebe ingredientes. */
        ABERTA("aberta", "Em andamento"),
        /** Encerrada com porções e preço informados. */
        FINALIZADA("finalizada", "Finalizada"),
        /** Abandonada: os ingredientes voltaram ao estoque. */
        CANCELADA("cancelada", "Cancelada");

        private final String valorBanco;
        private final String descricao;

        Status(String valorBanco, String descricao) {
            this.valorBanco = valorBanco;
            this.descricao = descricao;
        }

        /**
         * Retorna o valor usado na coluna {@code status}.
         *
         * @return o valor do ENUM no MySQL
         */
        public String getValorBanco() {
            return valorBanco;
        }

        /**
         * Retorna o nome exibido na tela.
         *
         * @return a descrição do estado
         */
        public String getDescricao() {
            return descricao;
        }

        /**
         * Converte o valor lido do banco no estado correspondente.
         *
         * @param valor o conteúdo da coluna {@code status}
         * @return o estado
         * @throws IllegalArgumentException se o valor for desconhecido
         */
        public static Status doBanco(String valor) {
            for (Status s : values()) {
                if (s.valorBanco.equalsIgnoreCase(valor)) {
                    return s;
                }
            }
            throw new IllegalArgumentException("Status de receita desconhecido: " + valor);
        }
    }

    private int id;
    private String nome;
    private Status status;
    private Integer porcoes;
    private BigDecimal precoVenda;
    private LocalDateTime inicio;
    private LocalDateTime fim;
    private BigDecimal custoTotal = BigDecimal.ZERO;
    private List<ItemExecucao> itens = new ArrayList<>();

    /**
     * Número de porções usado nos cálculos: o informado, ou 1 se não houver.
     *
     * @return as porções, nunca menor que 1
     */
    private int porcoesParaCalculo() {
        return porcoes != null && porcoes > 0 ? porcoes : 1;
    }

    /**
     * Calcula o custo de cada porção.
     *
     * @return o custo total dividido pelas porções
     */
    public BigDecimal getCustoPorcao() {
        return custoTotal.divide(BigDecimal.valueOf(porcoesParaCalculo()), 2, RoundingMode.HALF_UP);
    }

    /**
     * Calcula o faturamento esperado da leva: preço de venda vezes porções.
     *
     * @return o faturamento, ou {@code null} se o preço não foi informado
     */
    public BigDecimal getFaturamento() {
        if (precoVenda == null || precoVenda.signum() <= 0) {
            return null;
        }
        return precoVenda.multiply(BigDecimal.valueOf(porcoesParaCalculo()));
    }

    /**
     * Calcula o CMV da leva: custo do que foi pesado sobre o faturamento.
     *
     * @return o percentual, ou {@code null} se o preço não foi informado
     */
    public Double getCmvPercentual() {
        BigDecimal faturamento = getFaturamento();
        if (faturamento == null) {
            return null;
        }
        return custoTotal.doubleValue() / faturamento.doubleValue() * 100.0;
    }

    /**
     * Calcula a margem bruta da leva: faturamento menos o custo.
     *
     * @return a margem, ou {@code null} se o preço não foi informado
     */
    public BigDecimal getMargem() {
        BigDecimal faturamento = getFaturamento();
        return faturamento == null ? null : faturamento.subtract(custoTotal);
    }

    /**
     * Indica se a execução ainda recebe ingredientes.
     *
     * @return {@code true} se estiver aberta
     */
    public boolean isAberta() {
        return status == Status.ABERTA;
    }

    /** @return a chave primária */
    public int getId() {
        return id;
    }

    /** @param id a chave primária */
    public void setId(int id) {
        this.id = id;
    }

    /** @return o nome da receita */
    public String getNome() {
        return nome;
    }

    /** @param nome o nome da receita */
    public void setNome(String nome) {
        this.nome = nome;
    }

    /** @return o estado atual */
    public Status getStatus() {
        return status;
    }

    /** @param status o estado atual */
    public void setStatus(Status status) {
        this.status = status;
    }

    /** @return as porções produzidas, ou {@code null} se não informadas */
    public Integer getPorcoes() {
        return porcoes;
    }

    /** @param porcoes as porções produzidas */
    public void setPorcoes(Integer porcoes) {
        this.porcoes = porcoes;
    }

    /** @return o preço de venda por porção, ou {@code null} */
    public BigDecimal getPrecoVenda() {
        return precoVenda;
    }

    /** @param precoVenda o preço de venda por porção */
    public void setPrecoVenda(BigDecimal precoVenda) {
        this.precoVenda = precoVenda;
    }

    /** @return quando a receita foi iniciada */
    public LocalDateTime getInicio() {
        return inicio;
    }

    /** @param inicio quando a receita foi iniciada */
    public void setInicio(LocalDateTime inicio) {
        this.inicio = inicio;
    }

    /** @return quando foi finalizada ou cancelada, ou {@code null} */
    public LocalDateTime getFim() {
        return fim;
    }

    /** @param fim quando foi finalizada ou cancelada */
    public void setFim(LocalDateTime fim) {
        this.fim = fim;
    }

    /** @return a soma do custo dos ingredientes pesados */
    public BigDecimal getCustoTotal() {
        return custoTotal;
    }

    /** @param custoTotal a soma do custo dos ingredientes pesados */
    public void setCustoTotal(BigDecimal custoTotal) {
        this.custoTotal = custoTotal == null ? BigDecimal.ZERO : custoTotal;
    }

    /** @return os ingredientes pesados, vazio nas listagens resumidas */
    public List<ItemExecucao> getItens() {
        return itens;
    }

    /** @param itens os ingredientes pesados */
    public void setItens(List<ItemExecucao> itens) {
        this.itens = itens == null ? new ArrayList<>() : itens;
    }
}
