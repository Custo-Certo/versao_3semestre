package br.com.custocerto.model;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Um intervalo de datas fechado, usado por todos os indicadores financeiros.
 *
 * <p>Existe porque quase toda pergunta que o dono da cafeteria faz tem um recorte
 * de tempo embutido: "quanto vendi", "quanto consumi", "qual foi meu CMV" — todas
 * precisam de um <em>quando</em>. Passar duas datas soltas por toda a aplicação
 * convidaria a inverter início e fim em algum lugar; aqui isso é verificado uma
 * vez, no construtor.</p>
 *
 * <p>O intervalo inclui as duas pontas. Um período de 1 a 31 de agosto tem 31
 * dias, e uma venda registrada no dia 31 pertence a ele.</p>
 *
 * @author Equipe Custo Certo - FATEC Indaiatuba
 * @version 1.0
 */
public class Periodo {

    /** Formato usado nos rótulos exibidos na interface. */
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final LocalDate inicio;
    private final LocalDate fim;

    /**
     * Cria um período entre duas datas, ambas incluídas.
     *
     * @param inicio o primeiro dia do intervalo
     * @param fim    o último dia do intervalo
     * @throws IllegalArgumentException se alguma data for nula ou o fim vier antes
     *                                  do início
     */
    public Periodo(LocalDate inicio, LocalDate fim) {
        if (inicio == null || fim == null) {
            throw new IllegalArgumentException("O período precisa de data de início e de fim.");
        }
        if (fim.isBefore(inicio)) {
            throw new IllegalArgumentException("A data final do período é anterior à inicial.");
        }
        this.inicio = inicio;
        this.fim = fim;
    }

    /**
     * Cria o período correspondente a um mês inteiro.
     *
     * @param mes o mês desejado
     * @return o período do primeiro ao último dia daquele mês
     */
    public static Periodo doMes(YearMonth mes) {
        return new Periodo(mes.atDay(1), mes.atEndOfMonth());
    }

    /**
     * Cria o período do mês corrente, do dia 1 até hoje.
     *
     * <p>Vai só até hoje, e não até o fim do mês, porque dividir o custo por dias
     * que ainda não aconteceram distorceria a média diária de consumo.</p>
     *
     * @return o período do mês em andamento
     */
    public static Periodo mesAtual() {
        LocalDate hoje = LocalDate.now();
        return new Periodo(hoje.withDayOfMonth(1), hoje);
    }

    /**
     * Cria o período dos últimos {@code dias} dias, terminando hoje.
     *
     * @param dias quantos dias o intervalo cobre, incluindo hoje
     * @return o período correspondente
     */
    public static Periodo ultimosDias(int dias) {
        LocalDate hoje = LocalDate.now();
        return new Periodo(hoje.minusDays(Math.max(1, dias) - 1L), hoje);
    }

    /**
     * Retorna o primeiro dia do intervalo.
     *
     * @return a data inicial
     */
    public LocalDate getInicio() {
        return inicio;
    }

    /**
     * Retorna o último dia do intervalo.
     *
     * @return a data final
     */
    public LocalDate getFim() {
        return fim;
    }

    /**
     * Retorna quantos dias o período cobre, contando as duas pontas.
     *
     * <p>É o divisor do consumo médio diário, e por isso nunca pode ser zero.</p>
     *
     * @return o número de dias, sempre pelo menos 1
     */
    public long getDias() {
        return ChronoUnit.DAYS.between(inicio, fim) + 1;
    }

    /**
     * Retorna o período descrito para exibição.
     *
     * @return o intervalo no formato {@code dd/MM/yyyy a dd/MM/yyyy}
     */
    @Override
    public String toString() {
        return DATA.format(inicio) + " a " + DATA.format(fim);
    }
}
