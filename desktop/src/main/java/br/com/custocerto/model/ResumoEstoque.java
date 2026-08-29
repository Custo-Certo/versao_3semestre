package br.com.custocerto.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Os indicadores do painel, calculados a partir da lista de insumos.
 *
 * <p>As fórmulas repetem exatamente as do painel web, para que as duas
 * interfaces nunca mostrem números diferentes para o mesmo estoque:</p>
 *
 * <ul>
 *   <li><b>Valor em estoque</b> — soma de preço × quantidade de cada insumo.</li>
 *   <li><b>Itens em alerta</b> — insumos com menos de 25% da capacidade.</li>
 *   <li><b>Vencendo</b> — insumos que vencem nos próximos 5 dias.</li>
 *   <li><b>CMV</b> — custo dividido pela receita estimada, que o painel projeta
 *       como 3,5 vezes o custo enquanto o módulo de vendas não existe.</li>
 * </ul>
 *
 * @author Equipe Custo Certo - FATEC Indaiatuba
 * @version 1.0
 */
public class ResumoEstoque {

    /** Multiplicador usado para estimar a receita a partir do custo. */
    private static final BigDecimal FATOR_RECEITA = BigDecimal.valueOf(3.5);

    /** Percentual de CMV até o qual o resultado é considerado saudável. */
    private static final double CMV_IDEAL = 31.0;

    /** Percentual de CMV a partir do qual o resultado é considerado ruim. */
    private static final double CMV_LIMITE = 35.0;

    private final BigDecimal valorEmEstoque;
    private final int totalItens;
    private final int itensEmAlerta;
    private final int itensVencendo;
    private final int itensVencidos;
    private final BigDecimal receitaEstimada;
    private final double percentualCmv;

    /**
     * Calcula todos os indicadores a partir da lista de insumos.
     *
     * @param ingredientes os insumos cadastrados
     */
    public ResumoEstoque(List<Ingrediente> ingredientes) {
        BigDecimal total = BigDecimal.ZERO;
        int alerta = 0;
        int vencendo = 0;
        int vencidos = 0;

        for (Ingrediente i : ingredientes) {
            total = total.add(i.getValorEmEstoque());
            if (i.isEstoqueBaixo()) {
                alerta++;
            }
            if (i.isVencido()) {
                vencidos++;
            } else if (i.isVencimentoProximo()) {
                vencendo++;
            }
        }

        this.valorEmEstoque = total.setScale(2, RoundingMode.HALF_UP);
        this.totalItens = ingredientes.size();
        this.itensEmAlerta = alerta;
        this.itensVencendo = vencendo;
        this.itensVencidos = vencidos;
        this.receitaEstimada = total.multiply(FATOR_RECEITA).setScale(2, RoundingMode.HALF_UP);
        this.percentualCmv = receitaEstimada.signum() > 0
                ? valorEmEstoque.doubleValue() / receitaEstimada.doubleValue() * 100.0
                : 0;
    }

    /**
     * Retorna o valor total imobilizado em estoque.
     *
     * @return a soma de preço × quantidade de todos os insumos
     */
    public BigDecimal getValorEmEstoque() {
        return valorEmEstoque;
    }

    /**
     * Retorna quantos insumos estão cadastrados.
     *
     * @return o total de itens
     */
    public int getTotalItens() {
        return totalItens;
    }

    /**
     * Retorna quantos insumos estão abaixo de 25% da capacidade.
     *
     * @return o número de itens em alerta de estoque
     */
    public int getItensEmAlerta() {
        return itensEmAlerta;
    }

    /**
     * Retorna quantos insumos vencem nos próximos 5 dias.
     *
     * @return o número de itens com vencimento próximo
     */
    public int getItensVencendo() {
        return itensVencendo;
    }

    /**
     * Retorna quantos insumos já passaram da validade.
     *
     * @return o número de itens vencidos
     */
    public int getItensVencidos() {
        return itensVencidos;
    }

    /**
     * Retorna a receita projetada a partir do custo em estoque.
     *
     * @return a receita estimada
     */
    public BigDecimal getReceitaEstimada() {
        return receitaEstimada;
    }

    /**
     * Retorna o Custo de Mercadoria Vendida em porcentagem.
     *
     * @return o percentual de CMV
     */
    public double getPercentualCmv() {
        return percentualCmv;
    }

    /**
     * Classifica o CMV segundo as faixas usadas no painel web.
     *
     * @return {@code "ok"} até 31%, {@code "warn"} até 35%, {@code "bad"} acima
     */
    public String getClassificacaoCmv() {
        if (percentualCmv <= CMV_IDEAL) {
            return "ok";
        }
        return percentualCmv <= CMV_LIMITE ? "warn" : "bad";
    }

    /**
     * Monta a frase que acompanha o indicador de CMV.
     *
     * @return o texto de classificação exibido no painel
     */
    public String getRotuloCmv() {
        return switch (getClassificacaoCmv()) {
            case "ok" -> "Dentro do ideal";
            case "warn" -> "Levemente alto";
            default -> "Acima do limite";
        };
    }
}
