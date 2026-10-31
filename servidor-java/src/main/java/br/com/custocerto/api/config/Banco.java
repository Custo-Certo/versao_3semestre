package br.com.custocerto.api.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Pool de conexoes com o banco custo_certo -- o MESMO que o app desktop usa.
 *
 * Fonte unica de credenciais: le o arquivo desktop/config.properties (o mesmo
 * do JavaFX), para nao existirem dois lugares com usuario/senha. A ordem de
 * precedencia de cada valor e:
 *
 *   1. Variavel de ambiente (DB_HOST, DB_PORTA, DB_NOME, DB_USUARIO, DB_SENHA,
 *      DB_PARAMETROS) -- util na demonstracao em rede, sem editar arquivo.
 *   2. desktop/config.properties (db.host, db.porta, db.nome, db.usuario,
 *      db.senha, db.parametros).
 *   3. Padrao interno (localhost / root / custo_certo).
 *
 * Se config.properties nao existir, o servidor ainda sobe usando env + padroes,
 * e avisa no console qual caminho tentou.
 */
public final class Banco {

    private static final HikariDataSource DS;

    static {
        Properties cfg = carregarConfigDesktop();

        String host   = valor("DB_HOST",       cfg, "db.host",       "localhost");
        String porta  = valor("DB_PORTA",      cfg, "db.porta",      "3306");
        String nome   = valor("DB_NOME",       cfg, "db.nome",       "custo_certo");
        String user   = valor("DB_USUARIO",    cfg, "db.usuario",    "root");
        String senha  = valor("DB_SENHA",      cfg, "db.senha",      "");
        String params = valor("DB_PARAMETROS", cfg, "db.parametros",
            "useSSL=false&allowPublicKeyRetrieval=true"
            + "&serverTimezone=America/Sao_Paulo&characterEncoding=UTF-8");

        String jdbcUrl = "jdbc:mysql://" + host + ":" + porta + "/" + nome + "?" + params;

        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(jdbcUrl);
        hc.setUsername(user);
        hc.setPassword(senha);
        hc.setMaximumPoolSize(5);
        hc.setPoolName("custo-certo-pool");

        System.out.println("[Banco] Conectando em " + jdbcUrl + " como usuario '" + user + "'");
        DS = new HikariDataSource(hc);
    }

    private Banco() {}

    public static Connection conexao() throws SQLException {
        return DS.getConnection();
    }

    /**
     * Procura o desktop/config.properties em caminhos plausiveis, dependendo de
     * onde o servidor foi iniciado (raiz do repo, servidor-java/, ou via env
     * CONFIG_DESKTOP apontando direto para o arquivo).
     */
    private static Properties carregarConfigDesktop() {
        Properties p = new Properties();

        String override = System.getenv("CONFIG_DESKTOP");
        Path[] candidatos = {
            override != null ? Path.of(override) : null,
            Path.of("../desktop/config.properties"),   // rodando de servidor-java/
            Path.of("desktop/config.properties"),       // rodando da raiz do repo
            Path.of("../../desktop/config.properties")   // fallback
        };

        for (Path c : candidatos) {
            if (c != null && Files.isRegularFile(c)) {
                try (InputStream in = new FileInputStream(c.toFile())) {
                    p.load(in);
                    System.out.println("[Banco] Credenciais lidas de " + c.toAbsolutePath());
                    return p;
                } catch (Exception e) {
                    System.out.println("[Banco] Falha ao ler " + c + ": " + e.getMessage());
                }
            }
        }

        System.out.println("[Banco] desktop/config.properties nao encontrado. "
            + "Usando variaveis de ambiente e/ou padroes. "
            + "Crie o arquivo (copie de config.properties.example) para credenciais fixas.");
        return p;
    }

    /** Env var -> config.properties -> padrao. */
    private static String valor(String envKey, Properties cfg, String propKey, String padrao) {
        String env = System.getenv(envKey);
        if (env != null && !env.isBlank()) return env;
        String prop = cfg.getProperty(propKey);
        if (prop != null && !prop.isBlank()) return prop;
        return padrao;
    }
}
