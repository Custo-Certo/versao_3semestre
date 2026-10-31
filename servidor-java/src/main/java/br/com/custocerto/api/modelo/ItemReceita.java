package br.com.custocerto.api.modelo;

/**
 * Uma linha de uma execucao de receita: um ingrediente pesado (uma saida de
 * estoque vinculada a receita). {@code movId} e o id da movimentacao, usado
 * para estornar essa linha especifica.
 */
public record ItemReceita(
    int movId,
    int ingredienteId,
    String nome,
    String unidade,
    double quantidade,
    double precoUnitario,
    double custo
) {}
