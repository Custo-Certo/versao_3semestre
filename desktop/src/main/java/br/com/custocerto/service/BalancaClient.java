package br.com.custocerto.service;

import br.com.custocerto.util.ConfiguracaoBanco;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lê o peso direto da balança.
 *
 * <p>A balança é um <b>dispositivo de rede</b>: o ESP32 sobe um servidor HTTP e
 * responde {@code GET /peso} e {@code POST /tara}. Esta classe é o cliente
 * dessa conversa.</p>
 *
 * <p><b>Antes era diferente, e valia a pena mudar.</b> O ESP32 empurrava a
 * leitura para o servidor Node do painel web, e este aplicativo consumia o fluxo
 * de lá. Isso amarrava o desktop a um projeto que hoje vive em outro
 * repositório: sem aquele servidor no ar, a balança não existia para o Java.
 * Agora os dois sistemas são clientes do mesmo aparelho, e nenhum depende do
 * outro.</p>
 *
 * <p>Há um ganho que não é óbvio: as máquinas do grupo não têm privilégio de
 * administrador, e abrir porta de <em>entrada</em> no Firewall do Windows foi
 * justamente o que travou o acesso ao MySQL pela rede. Perguntar o peso é uma
 * conexão de <em>saída</em>, que não precisa de regra nenhuma.</p>
 *
 * <p><b>Por que consulta repetida e não um fluxo aberto:</b> o aparelho é um
 * microcontrolador, e manter conexões longas nele custa memória que faz falta.
 * Perguntar a cada {@value #INTERVALO_MS} ms em rede local acrescenta um atraso
 * muito menor do que a própria suavização do firmware, e o custo é irrelevante
 * — a resposta tem poucas dezenas de bytes.</p>
 *
 * <p>Esta é a única parte do sistema que fala HTTP. Todo o acesso a dados
 * continua sendo feito pelo próprio Java via JDBC, conforme a decisão D1-A: o
 * que vem por aqui é a leitura instantânea de um sensor, que não existe no
 * banco.</p>
 *
 * @author Equipe Custo Certo - FATEC Indaiatuba
 * @version 2.0
 */
public class BalancaClient {

    /** Intervalo entre consultas ao aparelho, em milissegundos. */
    private static final int INTERVALO_MS = 400;

    /** Extrai o peso do corpo JSON sem precisar de biblioteca externa. */
    private static final Pattern PESO =
            Pattern.compile("\"peso\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?)");

    /**
     * Espera antes de voltar a consultar quando o aparelho não responde.
     *
     * <p>Mais longa que o intervalo normal de propósito: se a balança está
     * desligada, insistir dez vezes por segundo só gasta rede e enche o log.</p>
     */
    private static final int ESPERA_APOS_FALHA_MS = 2000;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    private volatile boolean ativo;
    private Thread thread;

    /**
     * Começa a consultar a balança e a entregar cada leitura.
     *
     * <p>A consulta roda em uma thread própria, marcada como <em>daemon</em>
     * para não impedir o encerramento da aplicação. Se o aparelho parar de
     * responder, a thread continua tentando — a balança pode ser religada no
     * meio da operação sem precisar reabrir a tela.</p>
     *
     * @param aoReceber     chamado a cada leitura bem-sucedida
     * @param aoMudarEstado chamado quando a balança some ou volta a responder
     */
    public void conectar(Consumer<Leitura> aoReceber, Consumer<Boolean> aoMudarEstado) {
        if (ativo) {
            return;
        }
        ativo = true;

        thread = new Thread(() -> {
            boolean respondiaAntes = false;

            while (ativo) {
                java.math.BigDecimal peso = consultarPeso();
                boolean respondeAgora = peso != null;

                // Só avisa a tela quando o estado muda de verdade. Repetir o
                // aviso a cada leitura faria a interface se redesenhar à toa.
                if (respondeAgora != respondiaAntes) {
                    aoMudarEstado.accept(respondeAgora);
                    respondiaAntes = respondeAgora;
                }

                if (respondeAgora) {
                    aoReceber.accept(new Leitura(peso, true));
                }

                try {
                    Thread.sleep(respondeAgora ? INTERVALO_MS : ESPERA_APOS_FALHA_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "balanca-consulta");

        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Pergunta o peso ao aparelho.
     *
     * @return o peso lido, ou {@code null} se a balança não respondeu
     */
    private java.math.BigDecimal consultarPeso() {
        try {
            HttpRequest requisicao = HttpRequest.newBuilder()
                    .uri(URI.create(ConfiguracaoBanco.getUrlBalanca() + "/peso"))
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();

            HttpResponse<String> resposta =
                    http.send(requisicao, HttpResponse.BodyHandlers.ofString());

            if (resposta.statusCode() != 200) {
                return null;
            }

            Matcher peso = PESO.matcher(resposta.body());
            return peso.find() ? new java.math.BigDecimal(peso.group(1)) : null;

        } catch (IOException | RuntimeException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /**
     * Manda a balança zerar.
     *
     * <p>O pedido vai direto ao aparelho. Antes ele ficava guardado como uma
     * flag no servidor Node, que o firmware consumia na troca de mensagens
     * seguinte — e por isso firmware e backend precisavam subir juntos, sob pena
     * de a tara nunca chegar. Esse acoplamento deixou de existir.</p>
     *
     * @return {@code true} se o aparelho confirmou
     */
    public boolean solicitarTara() {
        try {
            HttpRequest requisicao = HttpRequest.newBuilder()
                    .uri(URI.create(ConfiguracaoBanco.getUrlBalanca() + "/tara"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(3))
                    .POST(HttpRequest.BodyPublishers.ofString("{}"))
                    .build();

            return http.send(requisicao, HttpResponse.BodyHandlers.discarding())
                    .statusCode() < 400;
        } catch (IOException | RuntimeException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Encerra a consulta à balança.
     */
    public void desconectar() {
        ativo = false;
        if (thread != null) {
            thread.interrupt();
        }
    }

    /**
     * Uma leitura instantânea da balança.
     *
     * @param peso   o peso lido, na unidade calibrada no firmware
     * @param online se o aparelho respondeu a esta consulta
     */
    public record Leitura(java.math.BigDecimal peso, boolean online) {
    }
}
