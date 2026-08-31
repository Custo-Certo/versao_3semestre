package br.com.custocerto.model;

import java.math.BigDecimal;
import java.util.List;

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
     * Retorna as unidades em que uma quantidade desta pode ser digitada.
     *
     * <p>Uma ficha técnica quase sempre é escrita em gramas, mesmo quando o insumo
     * é comprado e estocado em quilos: ninguém pensa "zero vírgula zero dezoito de
     * café", pensa "dezoito gramas". Sem esta lista, a pessoa era obrigada a fazer
     * a divisão de cabeça e digitar {@code 0,018} — e um zero a menos passava
     * despercebido.</p>
     *
     * <p>A primeira da lista é sempre a própria unidade do insumo, que continua
     * sendo a forma como o valor é guardado no banco. As outras são apenas formas
     * de <em>entrada</em>, convertidas na hora de salvar.</p>
     *
     * @return as unidades aceitas na digitação, começando pela própria
     */
    public List<Unidade> getEquivalentes() {
        return switch (this) {
            case KG -> List.of(KG, G);
            case G  -> List.of(G, KG);
            case L  -> List.of(L, ML);
            case ML -> List.of(ML, L);
            case UN -> List.of(UN);
        };
    }

    /**
     * Retorna por quanto multiplicar um valor digitado em {@code entrada} para
     * obter o valor nesta unidade.
     *
     * <p>Exemplo: o café é estocado em {@link #KG}, e alguém digita a ficha em
     * {@link #G}. {@code KG.fatorDe(G)} devolve {@code 0.001}, então 18 gramas
     * viram {@code 0,018} kg — que é como o banco guarda.</p>
     *
     * @param entrada a unidade em que o número foi digitado
     * @return o fator de conversão
     * @throws IllegalArgumentException se as duas unidades não forem conversíveis
     *                                  entre si (peso não vira volume)
     */
    public BigDecimal fatorDe(Unidade entrada) {
        if (entrada == this) {
            return BigDecimal.ONE;
        }
        if ((this == KG && entrada == G) || (this == L && entrada == ML)) {
            return new BigDecimal("0.001");
        }
        if ((this == G && entrada == KG) || (this == ML && entrada == L)) {
            return new BigDecimal("1000");
        }
        throw new IllegalArgumentException(
                "Não dá para converter " + entrada.sigla + " em " + sigla + ".");
    }

    /**
     * Escolhe a unidade mais legível para exibir uma quantidade desta unidade.
     *
     * <p>Serve para reabrir uma ficha técnica sem obrigar a pessoa a reler
     * {@code 0,018}. Valores pequenos em quilo ou litro passam a aparecer em
     * grama ou mililitro; o resto fica como está.</p>
     *
     * @param quantidade o valor guardado, nesta unidade
     * @return a unidade em que o valor fica mais fácil de ler
     */
    public Unidade melhorParaExibir(BigDecimal quantidade) {
        if (quantidade == null || quantidade.signum() == 0) {
            return this;
        }
        boolean pequeno = quantidade.abs().compareTo(BigDecimal.ONE) < 0;
        if (pequeno && this == KG) {
            return G;
        }
        if (pequeno && this == L) {
            return ML;
        }
        return this;
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
