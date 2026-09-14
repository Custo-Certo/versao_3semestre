package br.com.custocerto.api.balanca;

import io.javalin.http.sse.SseClient;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Peso e tara em memoria + broadcast SSE para o painel.
 * Zera quando o servidor reinicia -- e estado volatil de tempo real, nao dado.
 */
public final class EstadoBalanca {

    private static volatile double  pesoAtual    = 0;
    private static volatile long    ultima       = 0;
    private static volatile boolean precisaTarar = false;
    private static final Set<SseClient> CLIENTES = ConcurrentHashMap.newKeySet();

    private EstadoBalanca() {}

    /** ESP32 envia nova leitura -> guarda e faz push para o painel. */
    public static void registrarLeitura(double peso) {
        pesoAtual = Math.max(0, peso);
        ultima = System.currentTimeMillis();
        String json = snapshot();
        for (SseClient c : CLIENTES) {
            try {
                c.sendEvent(json);
            } catch (Exception e) {
                CLIENTES.remove(c);
            }
        }
    }

    public static double peso() {
        return pesoAtual;
    }

    public static boolean online() {
        return ultima != 0 && System.currentTimeMillis() - ultima < 5000;
    }

    public static void solicitarTara() {
        precisaTarar = true;
    }

    /** ESP32 le e zera a flag (read-and-clear). */
    public static boolean consumirTara() {
        boolean p = precisaTarar;
        precisaTarar = false;
        return p;
    }

    public static void adicionarCliente(SseClient c) {
        CLIENTES.add(c);
        c.sendEvent(snapshot());
    }

    public static void removerCliente(SseClient c) {
        CLIENTES.remove(c);
    }

    /** JSON que o EventSource do painel recebe (onmessage). */
    public static String snapshot() {
        return "{\"peso\":" + pesoAtual
             + ",\"online\":" + online()
             + ",\"ultimaAtualizacao\":" + ultima + "}";
    }
}
