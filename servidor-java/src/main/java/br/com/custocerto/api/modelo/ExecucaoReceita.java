package br.com.custocerto.api.modelo;

import java.util.List;

/**
 * Uma execucao de receita: acumula os ingredientes pesados e, ao finalizar,
 * carrega o CMV calculado. Campos de custo sao derivados das saidas vinculadas;
 * {@code custoPorcao}, {@code cmvPercent} e {@code margem} podem vir nulos
 * quando nao ha preco de venda informado. {@code itens} vem nulo nas listagens
 * (historico) e preenchido no detalhe.
 */
public record ExecucaoReceita(
    int id,
    String nome,
    String status,
    Integer porcoes,
    Double precoVenda,
    String inicio,
    String fim,
    double custoTotal,
    Double custoPorcao,
    Double cmvPercent,
    Double margem,
    List<ItemReceita> itens
) {}
