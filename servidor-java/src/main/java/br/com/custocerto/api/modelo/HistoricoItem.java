package br.com.custocerto.api.modelo;

/**
 * Linha do historico de compras (entradas), no formato que o grafico de
 * evolucao de precos e a tabela de historico do painel consomem. O campo
 * {@code produtoId} tem esse nome de proposito, para casar com o front.
 */
public record HistoricoItem(
    int id,
    int produtoId,
    String nome,
    String unidade,
    double qtd,
    double preco,
    String data
) {}
