package br.com.custocerto.dao;

import br.com.custocerto.util.ConfiguracaoBanco;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Abre conexões JDBC com o servidor MySQL na rede local.
 *
 * <p>JDBC (<em>Java Database Connectivity</em>) é a interface padrão do Java para
 * bancos relacionais. As classes {@link Connection}, {@link java.sql.PreparedStatement}
 * e {@link java.sql.ResultSet} são apenas contratos; quem os implementa para o MySQL
 * é o driver {@code com.mysql:mysql-connector-j}, declarado como dependência no
 * {@code pom.xml} e carregado automaticamente pelo {@link DriverManager}.</p>
 *
 * <p>A conexão é aberta contra o endereço configurado em {@code config.properties},
 * o que atende ao requisito de <strong>acesso ao banco pela rede local</strong>: o
 * MySQL roda em outra máquina da LAN e a comunicação ocorre por TCP na porta 3306.</p>
 *
 * @author Equipe Custo Certo - FATEC Indaiatuba
 * @version 1.0
 */
public final class ConexaoMySQL {

    /**
     * Classe utilitária: não deve ser instanciada.
     */
    private ConexaoMySQL() {
    }

    /**
     * Abre uma nova conexão com o banco de dados.
     *
     * <p>Quem chama é responsável por fechar a conexão. Nos DAOs isso é feito com
     * {@code try-with-resources}, que fecha automaticamente ao fim do bloco mesmo
     * quando ocorre uma exceção.</p>
     *
     * @return uma conexão aberta com o MySQL
     * @throws SQLException se o servidor estiver fora do ar, o endereço estiver
     *                      errado ou as credenciais forem recusadas
     */
    public static Connection obter() throws SQLException {
        return DriverManager.getConnection(
                ConfiguracaoBanco.getUrl(),
                ConfiguracaoBanco.getUsuario(),
                ConfiguracaoBanco.getSenha());
    }

    /**
     * Verifica se o banco está acessível.
     *
     * <p>Chamado quando a aplicação inicia, para que um servidor fora do ar seja
     * apontado logo na abertura, e não apenas quando alguém tenta entrar.</p>
     *
     * @return {@code true} se a conexão foi estabelecida com sucesso
     */
    public static boolean testar() {
        try (Connection conexao = obter()) {
            return conexao.isValid(3);
        } catch (SQLException e) {
            System.err.println("[banco] Falha ao conectar em " + ConfiguracaoBanco.getHost()
                    + ": " + e.getMessage());
            return false;
        }
    }
}
