package br.com.custocerto.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Os indicadores de estoque, calculados a partir da lista de insumos.
 *
 * <ul>
 *   <li><b>Valor em estoque</b> — soma de preço × quantidade de cada insumo.</li>
 *   <li><b>Itens em alerta</b> — insumos com menos de 25% da capacidade.</li>
 *   <li><b>Vencendo</b> — insumos que vencem nos próximos 5 dias.</li>
 *   <li><b>Vencidos</b> — insumos que já passaram da validade.</li>
 * </ul>
 *
 * <p><b>O CMV saiu daqui.</b> Esta classe calculava um "CMV estimado" que na
 * verdade era uma constante: a receita era estimada multiplicando o valor em
 * estoque por 3,5, e o CMV era o valor em estoque dividido por essa receita. Os
 * dois lados da divisão eram a mesma grandeza, então ela se cancelava e o
 * resultado era sempre 1 ÷ 3,5 — 28,6%, qualquer que fosse a situação da
 * cafeteria. Como o limite de "ideal" era 31%, o indicador também apontava para o
 * verde para sempre.</p>
 *
 * <p>O CMV de verdade precisa de receita medida e de custo do que foi
 * <em>vendido</em>, não do que está parado na prateleira. Ele agora vive em
 * {@link ResumoFinanceiro}, que apura os dois lados a partir de {@code vendas},
 * das fichas técnicas e das movimentações de estoque.</p>
 *
 * @author Equipe Custo Certo - FATEC Indaiatuba
 * @version 2.0
 */
public class ResumoEstoque {

    private final BigDecimal valorEmEstoque;
    private final int totalItens;
    private final int itensEmAlerta;
    private final int itensVencendo;
    private final int itensVencidos;
    private final BigDecimal valorVencido;

    /**
     * Calcula todos os indicadores a partir da lista de insumos.
     *
     * @param ingredientes os insumos cadastrados
     */
    public ResumoEstoque(List<Ingrediente> ingredientes) {
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal perdido = BigDecimal.ZERO;
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
                perdido = perdido.add(i.getValorEmEstoque());
            } else if (i.isVencimentoProximo()) {
                vencendo++;
            }
        }

        this.valorEmEstoque = total.setScale(2, RoundingMode.HALF_UP);
        this.valorVencido = perdido.setScale(2, RoundingMode.HALF_UP);
        this.totalItens = ingredientes.size();
        this.itensEmAlerta = alerta;
        this.itensVencendo = vencendo;
        this.itensVencidos = vencidos;
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
     * Retorna quanto dinheiro está parado em insumos já vencidos.
     *
     * <p>Contar itens vencidos não ajuda a decidir nada: dois potes de canela e
     * dois quilos de café apareciam como o mesmo "2". O que interessa é o valor.</p>
     *
     * @return a soma do valor dos insumos vencidos que ainda têm saldo
     */
    public BigDecimal getValorVencido() {
        return valorVencido;
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
}
