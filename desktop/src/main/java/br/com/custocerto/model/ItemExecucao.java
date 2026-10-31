package br.com.custocerto.model;

import java.math.BigDecimal;

/**
 * Um ingrediente pesado durante uma {@link ExecucaoReceita}.
 *
 * <p>Cada item é uma saída de estoque ligada à receita pela coluna
 * {@code execucao_id} de {@code movimentacoes_estoque}. O preço unitário é o do
 * insumo no momento da pesagem, então o custo de uma receita antiga não muda
 * quando o preço do insumo sobe depois.</p>
 *
 * @param movimentacaoId  a chave da saída de estoque, usada para estornar a linha
 * @param ingredienteId   a chave do insumo
 * @param nomeIngrediente o nome do insumo
 * @param unidade         a unidade em que o insumo é controlado
 * @param quantidade      quanto saiu do estoque, na unidade do insumo
 * @param precoUnitario   o preço do insumo no momento da pesagem
 * @author Equipe Custo Certo - FATEC Indaiatuba
 * @version 1.0
 */
public record ItemExecucao(int movimentacaoId, int ingredienteId, String nomeIngrediente,
                           Unidade unidade, BigDecimal quantidade, BigDecimal precoUnitario) {

    /**
     * Calcula o custo desta linha.
     *
     * @return quantidade vezes preço unitário
     */
    public BigDecimal getCusto() {
        if (quantidade == null || precoUnitario == null) {
            return BigDecimal.ZERO;
        }
        return quantidade.multiply(precoUnitario);
    }
}
