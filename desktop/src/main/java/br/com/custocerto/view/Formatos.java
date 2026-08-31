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
     * Escreve um número em um campo de formulário, no padrão brasileiro.
     *
     * <p>Existe por causa de um defeito real: os formulários eram preenchidos com
     * {@code BigDecimal.toPlainString()}, que escreve {@code 0.040} e {@code 8.00}
     * com <b>ponto decimal</b>. Como {@link #lerNumero(String)} trata ponto como
     * separador de milhar, reabrir um cadastro e salvar gravava {@code 40} no
     * lugar de {@code 0,040} e {@code 800} no lugar de {@code 8,00}. Não era um
     * erro de exibição: os dados iam corrompidos para o banco.</p>
     *
     * <p>Não usa separador de milhar, de propósito. Um campo de digitação com
     * {@code 1.234,56} convida a pessoa a apagar o ponto no meio da edição e
     * deixar o número ambíguo.</p>
     *
     * @param valor o valor a escrever
     * @return o número com vírgula decimal, ou vazio se o valor for nulo
     */
    public static String paraCampo(BigDecimal valor) {
        if (valor == null) {
            return "";
        }
        return valor.toPlainString().replace('.', ',');
    }

    /**
     * Converte o que foi digitado em um campo para número.
     *
     * <p>Aceita as duas formas que aparecem na prática, e a regra é a vírgula:</p>
     *
     * <ul>
     *   <li><b>Com vírgula</b> — é o padrão brasileiro. A vírgula é o separador
     *       decimal e os pontos são separadores de milhar, então
     *       {@code 1.234,56} vira {@code 1234.56}.</li>
     *   <li><b>Sem vírgula</b> — o ponto, se houver, é o separador decimal, então
     *       {@code 0.018} vira {@code 0.018}. É o que o teclado numérico produz e
     *       o que sai de {@code toPlainString()}.</li>
     * </ul>
     *
     * <p>Resta um caso ambíguo: {@code 1.234} sem vírgula pode ser mil duzentos e
     * trinta e quatro ou um inteiro e duzentos e trinta e quatro milésimos. Ele é
     * lido como decimal, porque é assim que quantidades de ficha técnica são
     * digitadas ({@code 0.018}) e é o que o preenchimento automático dos campos
     * produz. Quem quiser o valor de milhar escreve {@code 1234}.</p>
     *
     * @param texto o conteúdo do campo
     * @return o número correspondente
     * @throws NumberFormatException se o texto não for um número
     */
    public static BigDecimal lerNumero(String texto) {
        if (texto == null) {
            throw new NumberFormatException("campo vazio");
        }

        String limpo = texto.trim().replace(" ", "").replace("R$", "");

        if (limpo.contains(",")) {
            limpo = limpo.replace(".", "").replace(",", ".");
        }

        return new BigDecimal(limpo);
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
