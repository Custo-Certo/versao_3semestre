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
 * Recebe o peso da balança em tempo real, direto do fluxo do backend.
 *
 * <p>O peso lido pelo ESP32 não passa pelo banco de dados: ele é enviado ao
 * backend Node, que o mantém em memória e o retransmite por <b>SSE</b>
 * (<em>Server-Sent Events</em>) — uma conexão HTTP que fica aberta e por onde o
 * servidor empurra cada nova leitura assim que ela chega. É o mesmo fluxo que o
 * painel web já consome, agora também consumido pelo desktop.</p>
 *
 * <p><b>Por que SSE e não consulta repetida:</b> perguntar o peso a cada segundo
 * acrescentaria até um segundo de atraso, além de gerar tráfego constante mesmo
 * com a balança parada. No SSE o dado chega no instante em que o servidor o
 * recebe, então esta camada não soma praticamente nada à latência total.</p>
 *
 * <p>Esta é a única parte do sistema que fala HTTP. Todo o acesso a dados
 * continua sendo feito pelo próprio Java via JDBC, conforme a decisão D1-A — o
 * que vem por aqui é apenas a leitura instantânea de um sensor, que não existe
 * no banco.</p>
 *
 * @author Equipe Custo Certo - FATEC Indaiatuba
 * @version 1.0
 */
public class BalancaClient {

    /** Extrai o peso do corpo JSON sem precisar de biblioteca externa. */
    private static final Pattern PESO =
            Pattern.compile("\"peso\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?)");

    /** Extrai o indicador de balança online do corpo JSON. */
    private static final Pattern ONLINE =
            Pattern.compile("\"online\"\\s*:\\s*(true|false)");

    /** Espera antes de tentar reconectar quando o fluxo cai. */
    private static final Duration ESPERA_RECONEXAO = Duration.ofSeconds(3);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    private volatile boolean ativo;
    private Thread thread;

    /**
     * Abre a conexão com o fluxo de peso e passa a entregar cada leitura.
     *
     * <p>A escuta roda em uma thread própria, marcada como <em>daemon</em> para
     * não impedir o encerramento da aplicação. Se a conexão cair — servidor
     * reiniciado, rede oscilando — ela é refeita sozinha.</p>
     *
     * @param aoReceber      chamado a cada leitura, com o peso e se a balança
     *                       está respondendo
     * @param aoMudarEstado  chamado quando a conexão com o servidor cai ou volta
     */
    public void conectar(Consumer<Leitura> aoReceber, Consumer<Boolean> aoMudarEstado) {
        if (ativo) {
            return;
        }
        ativo = true;

        thread = new Thread(() -> {
            while (ativo) {
                try {
                    escutar(aoReceber, aoMudarEstado);
                } catch (Exception e) {
                    aoMudarEstado.accept(false);
                }
                if (ativo) {
                    try {
                        Thread.sleep(ESPERA_RECONEXAO.toMillis());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }, "balanca-sse");

        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Mantém a conexão aberta e processa cada linha recebida do servidor.
     *
     * @param aoReceber     destino das leituras
     * @param aoMudarEstado destino das mudanças de conexão
     * @throws IOException          se a conexão falhar
     * @throws InterruptedException se a thread for interrompida
     */
    private void escutar(Consumer<Leitura> aoReceber, Consumer<Boolean> aoMudarEstado)
            throws IOException, InterruptedException {

        HttpRequest requisicao = HttpRequest.newBuilder()
                .uri(URI.create(ConfiguracaoBanco.getUrlApi() + "/balanca/stream"))
                .header("Accept", "text/event-stream")
                .timeout(Duration.ofHours(12))
                .GET()
                .build();

        HttpResponse<java.util.stream.Stream<String>> resposta =
                http.send(requisicao, HttpResponse.BodyHandlers.ofLines());

        if (resposta.statusCode() != 200) {
            aoMudarEstado.accept(false);
            return;
        }

        aoMudarEstado.accept(true);

        // O protocolo SSE manda uma linha por evento, no formato "data: {...}".
        // Linhas iniciadas por ':' são apenas o heartbeat que mantém a conexão viva.
        resposta.body().forEach(linha -> {
            if (!ativo || !linha.startsWith("data:")) {
                return;
            }
            String corpo = linha.substring(5).trim();
            Matcher peso = PESO.matcher(corpo);
            if (peso.find()) {
                Matcher online = ONLINE.matcher(corpo);
                aoReceber.accept(new Leitura(
                        new java.math.BigDecimal(peso.group(1)),
                        !online.find() || Boolean.parseBoolean(online.group(1))));
            }
        });

        aoMudarEstado.accept(false);
    }

    /**
     * Pede ao ESP32 que zere a balança.
     *
     * <p>O pedido fica registrado no servidor e o firmware o consome na próxima
     * troca de mensagens, executando a tara no próprio sensor.</p>
     *
     * @return {@code true} se o servidor aceitou o pedido
     */
    public boolean solicitarTara() {
        try {
            HttpRequest requisicao = HttpRequest.newBuilder()
                    .uri(URI.create(ConfiguracaoBanco.getUrlApi() + "/balanca/tara"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(3))
                    .POST(HttpRequest.BodyPublishers.ofString("{}"))
                    .build();

            return http.send(requisicao, HttpResponse.BodyHandlers.discarding())
                    .statusCode() < 400;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Encerra a escuta do fluxo de peso.
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
     * @param online se o ESP32 enviou leitura recentemente
     */
    public record Leitura(java.math.BigDecimal peso, boolean online) {
    }
}
