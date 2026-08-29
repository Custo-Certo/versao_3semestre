package br.com.custocerto.view;

import br.com.custocerto.model.Usuario;
import br.com.custocerto.service.AutenticacaoException;
import br.com.custocerto.service.AutenticacaoService;
import br.com.custocerto.util.Sessao;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Tela de login do sistema.
 *
 * <p>Coleta e-mail e senha, entrega ao {@link AutenticacaoService} e, quando as
 * credenciais são aceitas, registra o usuário na {@link Sessao} e abre a
 * {@link TelaPrincipal}.</p>
 *
 * <p>Esta classe não contém nenhuma linha de SQL nem qualquer regra de
 * autenticação — essa separação é o que caracteriza a arquitetura em camadas
 * exigida pela matéria de Programação Orientada a Objetos. A tela apenas exibe
 * campos, chama o serviço e mostra o resultado.</p>
 *
 * <p>A verificação roda dentro de uma {@link Task}, em uma thread separada. O
 * cálculo do hash PBKDF2 somado à ida ao banco leva alguns décimos de segundo, e
 * executá-los na thread da interface deixaria a janela congelada nesse intervalo.</p>
 *
 * @author Equipe Custo Certo - FATEC Indaiatuba
 * @version 1.0
 */
public class TelaLogin {

    /** Janela principal da aplicação, compartilhada entre as telas. */
    private final Stage palco;

    /** Serviço que valida as credenciais contra o banco. */
    private final AutenticacaoService servico = new AutenticacaoService();

    private TextField campoEmail;
    private PasswordField campoSenha;
    private TextField campoSenhaVisivel;
    private Button botaoEntrar;
    private Label rotuloMensagem;

    /**
     * Cria a tela de login vinculada à janela informada.
     *
     * @param palco a janela onde a tela será exibida
     */
    public TelaLogin(Stage palco) {
        this.palco = palco;
    }

    /**
     * Monta a cena da tela de login.
     *
     * @return a cena pronta para ser exibida na janela
     */
    public Scene criarCena() {
        StackPane marca = new StackPane(
                Icones.criar("balanca", 26, javafx.scene.paint.Color.web("#00a86b")));
        marca.getStyleClass().add("marca-simbolo");
        marca.setMinSize(52, 52);
        marca.setPrefSize(52, 52);
        marca.setMaxSize(52, 52);

        Label titulo = new Label("Custo Certo");
        titulo.getStyleClass().add("titulo-marca");

        Label subtitulo = new Label("Controle de insumos e apuração de CMV");
        subtitulo.getStyleClass().add("subtitulo-marca");

        VBox cabecalho = new VBox(8, marca, new VBox(2, titulo, subtitulo));
        cabecalho.setAlignment(Pos.CENTER);
        ((VBox) cabecalho.getChildren().get(1)).setAlignment(Pos.CENTER);

        Label rotuloEmail = new Label("E-mail");
        rotuloEmail.getStyleClass().add("rotulo-campo");

        campoEmail = new TextField();
        campoEmail.setPromptText("seu.nome@custocerto.com.br");
        campoEmail.getStyleClass().add("campo");

        Label rotuloSenha = new Label("Senha");
        rotuloSenha.getStyleClass().add("rotulo-campo");

        campoSenha = new PasswordField();
        campoSenha.setPromptText("Digite a sua senha");
        campoSenha.getStyleClass().add("campo");

        // Campo espelho, exibido no lugar do anterior quando o usuário pede para
        // ver a senha. Os dois textos ficam sincronizados nos dois sentidos.
        campoSenhaVisivel = new TextField();
        campoSenhaVisivel.setPromptText("Digite a sua senha");
        campoSenhaVisivel.getStyleClass().add("campo");
        campoSenhaVisivel.setManaged(false);
        campoSenhaVisivel.setVisible(false);
        campoSenhaVisivel.textProperty().bindBidirectional(campoSenha.textProperty());

        StackPane areaSenha = new StackPane(campoSenha, campoSenhaVisivel);

        CheckBox mostrarSenha = new CheckBox("Mostrar senha");
        mostrarSenha.getStyleClass().add("mostrar-senha");
        mostrarSenha.selectedProperty().addListener((obs, antes, marcado) -> {
            campoSenhaVisivel.setVisible(marcado);
            campoSenhaVisivel.setManaged(marcado);
            campoSenha.setVisible(!marcado);
            campoSenha.setManaged(!marcado);
            if (marcado) {
                campoSenhaVisivel.requestFocus();
            } else {
                campoSenha.requestFocus();
            }
        });

        rotuloMensagem = new Label();
        rotuloMensagem.getStyleClass().add("mensagem-erro");
        rotuloMensagem.setWrapText(true);
        rotuloMensagem.setManaged(false);
        rotuloMensagem.setVisible(false);

        botaoEntrar = new Button("Entrar");
        botaoEntrar.getStyleClass().add("botao-principal");
        botaoEntrar.setMaxWidth(Double.MAX_VALUE);
        botaoEntrar.setDefaultButton(true);
        botaoEntrar.setOnAction(evento -> entrar());

        Hyperlink esqueciSenha = new Hyperlink("Esqueci a minha senha");
        esqueciSenha.getStyleClass().add("link-secundario");
        esqueciSenha.setOnAction(evento -> mostrarMensagem(
                "A redefinição de senha é feita pelo administrador do sistema.", false));

        HBox rodapeCampos = new HBox(mostrarSenha, espacador(), esqueciSenha);
        rodapeCampos.setAlignment(Pos.CENTER_LEFT);

        VBox cartao = new VBox(14,
                cabecalho,
                separador(),
                rotuloEmail, campoEmail,
                rotuloSenha, areaSenha,
                rodapeCampos,
                rotuloMensagem,
                botaoEntrar);
        cartao.getStyleClass().add("cartao");
        cartao.setPadding(new Insets(36, 34, 30, 34));
        cartao.setMaxWidth(380);
        cartao.setMinWidth(380);

        Label rodape = new Label("Projeto Integrador · FATEC Indaiatuba");
        rodape.getStyleClass().add("rodape");

        VBox raiz = new VBox(18, cartao, rodape);
        raiz.setAlignment(Pos.CENTER);
        raiz.getStyleClass().add("fundo");

        Scene cena = new Scene(raiz, 900, 620);
        cena.getStylesheets().add(
                getClass().getResource("/css/estilo.css").toExternalForm());

        campoEmail.requestFocus();
        return cena;
    }

    /**
     * Executa a tentativa de login em uma thread separada da interface.
     *
     * <p>Enquanto a verificação ocorre, os campos ficam desabilitados e o botão
     * muda de texto, para que o usuário não envie o formulário duas vezes.</p>
     */
    private void entrar() {
        esconderMensagem();
        definirCarregando(true);

        String email = campoEmail.getText();
        String senha = campoSenha.getText();

        Task<Usuario> tarefa = new Task<>() {
            @Override
            protected Usuario call() throws AutenticacaoException {
                return servico.autenticar(email, senha);
            }
        };

        tarefa.setOnSucceeded(evento -> {
            definirCarregando(false);
            Usuario autenticado = tarefa.getValue();
            Sessao.iniciar(autenticado);
            abrirTelaPrincipal(autenticado);
        });

        tarefa.setOnFailed(evento -> {
            definirCarregando(false);
            Throwable causa = tarefa.getException();
            String mensagem = causa instanceof AutenticacaoException
                    ? causa.getMessage()
                    : "Erro inesperado ao entrar: " + causa.getMessage();
            mostrarMensagem(mensagem, true);
            campoSenha.clear();
            campoSenha.requestFocus();
        });

        Thread thread = new Thread(tarefa, "autenticacao");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Substitui a cena da janela pela tela principal do usuário autenticado.
     *
     * @param usuario o usuário que acabou de entrar
     */
    private void abrirTelaPrincipal(Usuario usuario) {
        TelaPrincipal principal = new TelaPrincipal(palco, usuario);
        palco.setScene(principal.criarCena());
        palco.setTitle("Custo Certo — " + usuario.getPerfil().getDescricao());
        palco.centerOnScreen();
    }

    /**
     * Habilita ou desabilita os campos durante a verificação das credenciais.
     *
     * @param carregando {@code true} enquanto a autenticação está em andamento
     */
    private void definirCarregando(boolean carregando) {
        botaoEntrar.setText(carregando ? "Entrando..." : "Entrar");
        botaoEntrar.setDisable(carregando);
        campoEmail.setDisable(carregando);
        campoSenha.setDisable(carregando);
        campoSenhaVisivel.setDisable(carregando);
    }

    /**
     * Exibe uma mensagem abaixo dos campos.
     *
     * @param texto a mensagem a exibir
     * @param erro  {@code true} para estilo de erro, {@code false} para informativo
     */
    private void mostrarMensagem(String texto, boolean erro) {
        rotuloMensagem.setText(texto);
        rotuloMensagem.getStyleClass().removeAll("mensagem-erro", "mensagem-info");
        rotuloMensagem.getStyleClass().add(erro ? "mensagem-erro" : "mensagem-info");
        rotuloMensagem.setManaged(true);
        rotuloMensagem.setVisible(true);
    }

    /**
     * Esconde a mensagem exibida anteriormente.
     */
    private void esconderMensagem() {
        rotuloMensagem.setManaged(false);
        rotuloMensagem.setVisible(false);
    }

    /**
     * Cria uma região que se expande para empurrar o próximo elemento à direita.
     *
     * @return o espaçador flexível
     */
    private Region espacador() {
        Region regiao = new Region();
        HBox.setHgrow(regiao, Priority.ALWAYS);
        return regiao;
    }

    /**
     * Cria a linha divisória usada abaixo do cabeçalho do cartão.
     *
     * @return a região estilizada como linha
     */
    private Region separador() {
        Region linha = new Region();
        linha.getStyleClass().add("separador");
        linha.setMinHeight(1);
        linha.setMaxHeight(1);
        return linha;
    }
}
