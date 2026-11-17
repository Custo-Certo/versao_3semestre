package br.com.custocerto.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * A versão da aplicação, lida do {@code versao.properties} que o Maven gera a
 * partir do {@code pom.xml}.
 *
 * <p>Assim o número exibido na tela vem da mesma fonte que o executável e o
 * artefato, e não precisa ser lembrado a cada nova versão.</p>
 *
 * @author Equipe Custo Certo - FATEC Indaiatuba
 * @version 1.0
 */
public final class Versao {

    /** Usada quando o recurso não foi gerado, como ao rodar sem o Maven. */
    private static final String DESCONHECIDA = "dev";

    private static final String ATUAL = carregar();

    private Versao() {
    }

    /**
     * Devolve a versão atual da aplicação.
     *
     * @return a versão no formato MAIOR.MENOR.CORREÇÃO, ou {@code "dev"} se não
     *         puder ser determinada
     */
    public static String atual() {
        return ATUAL;
    }

    /**
     * Lê a versão do recurso gerado pelo Maven.
     *
     * @return a versão lida, ou {@code "dev"} se o recurso faltar ou não tiver
     *         sido filtrado
     */
    private static String carregar() {
        try (InputStream entrada = Versao.class.getResourceAsStream("/versao.properties")) {
            if (entrada == null) {
                return DESCONHECIDA;
            }
            Properties props = new Properties();
            props.load(entrada);
            String valor = props.getProperty("versao", "").trim();
            return valor.isEmpty() || valor.startsWith("${") ? DESCONHECIDA : valor;
        } catch (IOException e) {
            return DESCONHECIDA;
        }
    }
}
