package br.com.custocerto.api.modelo;

import java.util.List;

/**
 * Ingrediente do estoque, no formato que o painel web (public/script.js) espera.
 *
 * O campo {@code lotes} carrega as ENTRADAS (compras) desse ingrediente e e o
 * que alimenta os cards de lote, o selo "Usar primeiro" e os alertas de
 * validade por lote. Em respostas de item unico (apos criar/compra/retirada) o
 * front recarrega a lista inteira, entao ali lotes pode vir nulo.
 */
public record Ingrediente(
    int id,
    String nome,
    String unidade,
    double preco,
    double qtd,
    double qtdMax,
    String validade,
    List<Lote> lotes
) {
    /** Copia este ingrediente adicionando a lista de lotes (records sao imutaveis). */
    public Ingrediente comLotes(List<Lote> novosLotes) {
        return new Ingrediente(id, nome, unidade, preco, qtd, qtdMax, validade, novosLotes);
    }
}
