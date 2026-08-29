package br.com.custocerto.view;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Formatação de números e datas no padrão brasileiro.
 *
 * <p>Centraliza aqui o que apareceria repetido em cada tela: dinheiro com
 * "R$" e vírgula decimal, quantidades sem zeros à direita inúteis
 * (<code>3,2 kg</code> em vez de <code>3,200 kg</code>) e datas em dd/MM/aaaa.</p>
 *
 * @author Equipe Custo Certo - FATEC Indaiatuba
 * @version 1.0
 */
public final class Formatos {

    /** Localidade brasileira, para vírgula decimal e ponto de milhar. */
    private static final Locale BR = Locale.forLanguageTag("pt-BR");

    /** Formato de data usado em toda a interface. */
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Classe utilitária: não deve ser instanciada.
     */
    private Formatos() {
    }

    /**
     * Formata um valor monetário com o símbolo da moeda.
     *
     * @param valor o valor a formatar
     * @return o texto no formato {@code R$ 1.234,56}
     */
    public static String dinheiro(BigDecimal valor) {
        if (valor == null) {
            return "R$ 0,00";
        }
        return String.format(BR, "R$ %,.2f", valor);
    }

    /**
     * Formata um valor monetário sem o símbolo da moeda, para colunas de tabela.
     *
     * @param valor o valor a formatar
     * @return o texto no formato {@code 1.234,56}
     */
    public static String numero(BigDecimal valor) {
        if (valor == null) {
            return "0,00";
        }
        return String.format(BR, "%,.2f", valor);
    }

    /**
     * Formata uma quantidade, removendo zeros decimais que não informam nada.
     *
     * @param valor a quantidade a formatar
     * @return o texto com no máximo três casas, sem zeros à direita
     */
    public static String quantidade(BigDecimal valor) {
        if (valor == null) {
            return "0";
        }
        BigDecimal limpo = valor.stripTrailingZeros();
        if (limpo.scale() < 0) {
            limpo = limpo.setScale(0);
        }
        return String.format(BR, "%,." + limpo.scale() + "f", limpo);
    }

    /**
     * Formata uma quantidade acompanhada da sua unidade.
     *
     * @param valor  a quantidade
     * @param unidade a sigla da unidade
     * @return o texto no formato {@code 3,2 kg}
     */
    public static String quantidadeComUnidade(BigDecimal valor, String unidade) {
        return quantidade(valor) + " " + unidade;
    }

    /**
     * Formata uma data no padrão brasileiro.
     *
     * @param data a data a formatar
     * @return o texto no formato {@code dd/MM/aaaa}, ou um travessão se nula
     */
    public static String data(LocalDate data) {
        return data == null ? "—" : data.format(DATA);
    }

    /**
     * Formata um percentual com uma casa decimal.
     *
     * @param valor o percentual
     * @return o texto no formato {@code 28,6%}
     */
    public static String percentual(double valor) {
        return String.format(BR, "%.1f%%", valor);
    }
}
