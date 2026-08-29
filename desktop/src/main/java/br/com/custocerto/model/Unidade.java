package br.com.custocerto.model;

/**
 * Unidades de medida aceitas para um insumo.
 *
 * <p>Os nomes correspondem aos valores da coluna {@code ingredientes.unidade},
 * declarada como {@code ENUM('kg','g','L','ml','un')} no MySQL. Como o rótulo
 * exibido difere do nome da constante (Java não aceita {@code kg} minúsculo como
 * padrão de constante), cada uma carrega a sua sigla.</p>
 *
 * @author Equipe Custo Certo - FATEC Indaiatuba
 * @version 1.0
 */
public enum Unidade {

    /** Quilograma. */
    KG("kg", "quilograma"),
    /** Grama. */
    G("g", "grama"),
    /** Litro. */
    L("L", "litro"),
    /** Mililitro. */
    ML("ml", "mililitro"),
    /** Unidade avulsa. */
    UN("un", "unidade");

    private final String sigla;
    private final String nome;

    /**
     * Cria a unidade com a sua sigla e o nome por extenso.
     *
     * @param sigla o texto gravado no banco e exibido na interface
     * @param nome  o nome por extenso, usado em textos explicativos
     */
    Unidade(String sigla, String nome) {
        this.sigla = sigla;
        this.nome = nome;
    }

    /**
     * Retorna a sigla da unidade, como gravada no banco.
     *
     * @return a sigla (ex.: {@code "kg"})
     */
    public String getSigla() {
        return sigla;
    }

    /**
     * Retorna o nome por extenso da unidade.
     *
     * @return o nome (ex.: {@code "quilograma"})
     */
    public String getNome() {
        return nome;
    }

    /**
     * Converte a sigla lida do banco na constante correspondente.
     *
     * @param sigla o conteúdo da coluna {@code unidade}
     * @return a unidade correspondente
     * @throws IllegalArgumentException se a sigla não corresponder a nenhuma unidade
     */
    public static Unidade deSigla(String sigla) {
        if (sigla != null) {
            for (Unidade u : values()) {
                if (u.sigla.equalsIgnoreCase(sigla.trim())) {
                    return u;
                }
            }
        }
        throw new IllegalArgumentException("Unidade desconhecida: " + sigla);
    }

    /**
     * Retorna a sigla, para exibição direta em listas e tabelas.
     *
     * @return a sigla da unidade
     */
    @Override
    public String toString() {
        return sigla;
    }
}
