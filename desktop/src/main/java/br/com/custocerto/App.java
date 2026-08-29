package br.com.custocerto;

import br.com.custocerto.dao.ConexaoMySQL;
import br.com.custocerto.util.ConfiguracaoBanco;
import br.com.custocerto.view.TelaLogin;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

/**
 * Ponto de entrada da aplicação desktop do Custo Certo.
 *
 * <p>Em JavaFX, a aplicação é uma subclasse de {@link Application}. O método
 * {@link #start(Stage)} recebe o <em>palco</em> ({@link Stage}), que é a janela do
 * sistema operacional. Dentro dele são trocadas as <em>cenas</em>
 * ({@link javafx.scene.Scene}): primeiro a tela de login, depois a tela principal.</p>
 *
 * <p>Assim que a janela aparece, a aplicação testa a conexão com o MySQL. Se o
 * servidor estiver inacessível, o aviso surge com o endereço que foi tentado — em
 * vez de deixar o usuário descobrir o problema só ao tentar entrar.</p>
 *
 * <p>Esse teste roda em uma thread separada: um servidor fora do ar demora até
 * estourar o tempo limite da conexão, e fazer isso na thread da interface deixaria
 * a janela travada nesse intervalo.</p>
 *
 * <p>Executar com {@code mvnw javafx:run}.</p>
 *
 * @author Equipe Custo Certo - FATEC Indaiatuba
 * @version 1.0
 */
public class App extends Application {

    /**
     * Monta e exibe a janela inicial da aplicação.
     *
     * @param palco a janela fornecida pelo JavaFX
     */
    @Override
    public void start(Stage palco) {
        TelaLogin login = new TelaLogin(palco);
        palco.setTitle("Custo Certo — Acesso ao sistema");
        palco.setScene(login.criarCena());
        palco.setMinWidth(720);
        palco.setMinHeight(560);
        palco.show();

        verificarBancoEmSegundoPlano();
    }

    /**
     * Testa a conexão com o banco fora da thread da interface e, se ela falhar,
     * pede ao JavaFX que exiba o aviso na thread correta.
     */
    private void verificarBancoEmSegundoPlano() {
        Thread verificacao = new Thread(() -> {
            if (!ConexaoMySQL.testar()) {
                Platform.runLater(this::avisarBancoIndisponivel);
            }
        }, "verificacao-banco");
        verificacao.setDaemon(true);
        verificacao.start();
    }

    /**
     * Exibe o aviso de banco de dados inacessível, com o endereço configurado.
     */
    private void avisarBancoIndisponivel() {
        Alert alerta = new Alert(Alert.AlertType.WARNING);
        alerta.setTitle("Banco de dados indisponível");
        alerta.setHeaderText("Não foi possível conectar ao MySQL");
        alerta.setContentText(
                "Endereço configurado: " + ConfiguracaoBanco.getHost() + "\n\n"
                + "Verifique se o servidor MySQL está ligado, se esta máquina está na mesma "
                + "rede local e se os dados de acesso em config.properties estão corretos.\n\n"
                + "Enquanto isso não for resolvido, o login não vai funcionar.");
        alerta.show();
    }

    /**
     * Inicia a aplicação JavaFX.
     *
     * @param args argumentos de linha de comando, repassados ao JavaFX
     */
    public static void main(String[] args) {
        launch(args);
    }
}
