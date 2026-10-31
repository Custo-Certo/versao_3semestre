package br.com.custocerto.api.modelo;

/**
 * Um lote = uma entrada (compra) registrada em movimentacoes_estoque.
 * Os nomes dos campos batem com o que o front consome: quantidade,
 * precoUnitario, dataEntrada e validade.
 */
public record Lote(
    int id,
    double quantidade,
    double precoUnitario,
    String dataEntrada,
    String validade
) {}
