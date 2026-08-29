package br.com.custocerto.model;

/**
 * Natureza de uma movimentação de estoque.
 *
 * <p>Corresponde à coluna {@code movimentacoes_estoque.tipo}, declarada como
 * {@code ENUM('entrada','saida')} no MySQL.</p>
 *
 * @author Equipe Custo Certo - FATEC Indaiatuba
 * @version 1.0
 */
public enum TipoMovimentacao {

    /** Compra ou reposição: soma ao estoque e registra o preço pago. */
    ENTRADA("entrada", "Entrada", 1),

    /** Consumo, perda ou retirada: subtrai do estoque. */
    SAIDA("saida", "Saída", -1);

    private final String valorBanco;
    private final String descricao;
    private final int sinal;

    /**
     * Cria o tipo com o valor do banco, o rótulo e o sinal aplicado ao estoque.
     *
     * @param valorBanco o texto gravado na coluna {@code tipo}
     * @param descricao  o rótulo exibido na interface
     * @param sinal      {@code 1} para somar ao estoque, {@code -1} para subtrair
     */
    TipoMovimentacao(String valorBanco, String descricao, int sinal) {
        this.valorBanco = valorBanco;
        this.descricao = descricao;
        this.sinal = sinal;
    }

    /**
     * Retorna o valor gravado no banco.
     *
     * @return {@code "entrada"} ou {@code "saida"}
     */
    public String getValorBanco() {
        return valorBanco;
    }

    /**
     * Retorna o rótulo exibido na interface.
     *
     * @return a descrição legível
     */
    public String getDescricao() {
        return descricao;
    }

    /**
     * Retorna o sinal que esta movimentação aplica ao estoque.
     *
     * @return {@code 1} para entrada, {@code -1} para saída
     */
    public int getSinal() {
        return sinal;
    }

    /**
     * Converte o valor lido do banco na constante correspondente.
     *
     * @param valor o conteúdo da coluna {@code tipo}
     * @return o tipo correspondente
     * @throws IllegalArgumentException se o valor não corresponder a nenhum tipo
     */
    public static TipoMovimentacao deValorDoBanco(String valor) {
        if (valor != null) {
            for (TipoMovimentacao t : values()) {
                if (t.valorBanco.equalsIgnoreCase(valor.trim())) {
                    return t;
                }
            }
        }
        throw new IllegalArgumentException("Tipo de movimentação desconhecido: " + valor);
    }

    /**
     * Retorna o rótulo do tipo.
     *
     * @return a descrição legível
     */
    @Override
    public String toString() {
        return descricao;
    }
}
