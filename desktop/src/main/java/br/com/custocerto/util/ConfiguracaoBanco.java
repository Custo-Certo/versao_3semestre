package br.com.custocerto.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Carrega os dados de conexão com o MySQL.
 *
 * <p>O endereço do servidor fica em um arquivo de configuração e não no código,
 * porque o banco roda em outra máquina da rede local — requisito da matéria de
 * Programação Orientada a Objetos. Cada integrante aponta para o host que estiver
 * usando (a própria máquina durante o desenvolvimento, o servidor do grupo na
 * apresentação) sem alterar uma linha de Java.</p>
 *
 * <p>A busca acontece nesta ordem:</p>
 * <ol>
 *   <li>arquivo {@code config.properties} na pasta de execução — não versionado,
 *       é onde cada um coloca a sua senha;</li>
 *   <li>arquivo {@code config.properties} embutido no projeto
 *       ({@code src/main/resources}), com os valores padrão de desenvolvimento.</li>
 * </ol>
 *
 * @author Equipe Custo Certo - FATEC Indaiatuba
 * @version 1.0
 */
public final class ConfiguracaoBanco {

    /** Nome do arquivo de configuração procurado nas duas localizações. */
    private static final String ARQUIVO = "config.properties";

    /** Propriedades carregadas uma única vez, na primeira utilização. */
    private static final Properties PROPRIEDADES = carregar();

    /**
     * Classe utilitária: não deve ser instanciada.
     */
    private ConfiguracaoBanco() {
    }

    /**
     * Lê o arquivo de configuração, preferindo a cópia local à embutida.
     *
     * @return as propriedades de conexão
     * @throws IllegalStateException se nenhum arquivo for encontrado
     */
    private static Properties carregar() {
        Properties props = new Properties();

        Path local = Path.of(ARQUIVO);
        if (Files.exists(local)) {
            try (InputStream entrada = Files.newInputStream(local)) {
                props.load(entrada);
                System.out.println("[config] Configuração lida de " + local.toAbsolutePath());
                return props;
            } catch (IOException e) {
                throw new IllegalStateException("Falha ao ler " + local.toAbsolutePath(), e);
            }
        }

        try (InputStream entrada = ConfiguracaoBanco.class.getClassLoader().getResourceAsStream(ARQUIVO)) {
            if (entrada == null) {
                throw new IllegalStateException(
                        "Arquivo " + ARQUIVO + " não encontrado. "
                        + "Copie config.properties.example para config.properties e preencha os dados do MySQL.");
            }
            props.load(entrada);
            System.out.println("[config] Configuração padrão do projeto carregada.");
            return props;
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao ler o arquivo " + ARQUIVO + " do projeto.", e);
        }
    }

    /**
     * Monta a URL JDBC completa a partir do host, da porta e do nome do banco.
     *
     * <p>Formato: {@code jdbc:mysql://<host>:<porta>/<banco>?<parâmetros>}. O host
     * é o endereço da máquina que roda o MySQL na rede local.</p>
     *
     * @return a URL de conexão JDBC
     */
    public static String getUrl() {
        String host = valor("db.host", "localhost");
        String porta = valor("db.porta", "3306");
        String banco = valor("db.nome", "custo_certo");
        String parametros = valor("db.parametros",
                "useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=America/Sao_Paulo"
                        + "&characterEncoding=UTF-8&connectTimeout=3000&socketTimeout=15000");
        return "jdbc:mysql://" + host + ":" + porta + "/" + banco + "?" + parametros;
    }

    /**
     * Retorna o usuário do MySQL usado pela aplicação.
     *
     * @return o nome do usuário do banco
     */
    public static String getUsuario() {
        return valor("db.usuario", "root");
    }

    /**
     * Retorna a senha do usuário do MySQL.
     *
     * @return a senha do banco
     */
    public static String getSenha() {
        return valor("db.senha", "");
    }

    /**
     * Retorna o endereço do servidor de banco, para exibição em mensagens de erro.
     *
     * @return o host configurado
     */
    public static String getHost() {
        return valor("db.host", "localhost");
    }

    /**
     * Retorna o endereço do backend Node que recebe o peso da balança.
     *
     * <p>É o único endereço HTTP usado pela aplicação. Ele existe porque o peso
     * lido pelo ESP32 não é gravado no banco: fica em memória no backend e é
     * retransmitido em tempo real. Todo o resto do sistema continua indo direto
     * ao MySQL por JDBC.</p>
     *
     * @return a URL base da API, sem barra no final
     */
    public static String getUrlApi() {
        String url = valor("api.url", "http://localhost:3000");
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * Retorna o endereco da balanca na rede.
     *
     * <p>A balanca e um dispositivo de rede com API propria: responde
     * {@code GET /peso} e {@code POST /tara}. Este aplicativo a consulta
     * diretamente, sem passar por servidor nenhum.</p>
     *
     * <p>O padrao usa o nome anunciado por mDNS, que dispensa decorar o IP.
     * Nem toda rede repassa mDNS -- se o nome nao resolver, coloque o IP do
     * aparelho em {@code balanca.url} no config.properties. O IP aparece no
     * monitor serial do ESP32 e na pagina de status da propria balanca.</p>
     *
     * @return a URL base da balanca, sem barra no final
     */
    public static String getUrlBalanca() {
        String url = valor("balanca.url", "http://custocerto-balanca.local");
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * Lê uma propriedade, devolvendo um valor padrão quando ela está ausente.
     *
     * @param chave  o nome da propriedade
     * @param padrao o valor usado quando a propriedade não existe
     * @return o valor configurado ou o padrão
     */
    private static String valor(String chave, String padrao) {
        String v = PROPRIEDADES.getProperty(chave);
        return (v == null || v.isBlank()) ? padrao : v.trim();
    }
}
