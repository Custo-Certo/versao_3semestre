package br.com.custocerto.view;

import br.com.custocerto.model.Perfil;
import br.com.custocerto.model.Usuario;
import br.com.custocerto.service.UsuarioException;
import br.com.custocerto.service.UsuarioService;
import br.com.custocerto.util.Sessao;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/**
 * Página de administração das contas de acesso.
 *
 * <p>Só aparece no menu para quem tem {@link Perfil#podeGerenciarUsuarios()} —
 * hoje, apenas o administrador. É a contrapartida na interface da permissão que o
 * enum {@link Perfil} já declarava: sem esta tela, o administrador tinha o direito
 * de gerenciar contas e nenhum lugar onde exercê-lo.</p>
 *
 * <p>Segue a mesma linguagem visual da página de estoque: um cartão por conta, com
 * as ações à mão. Contas não são excluídas, apenas inativadas — apagar um usuário
 * levaria junto o vínculo dele com as tentativas de login registradas em
 * {@code log_acesso}, e a trilha de auditoria perderia o sentido.</p>
 *
 * @author Equipe Custo Certo - FATEC Indaiatuba
 * @version 1.0
 */
public class PaginaUsuarios extends VBox {

    /** Formato das datas de último acesso e de criação da conta. */
    private static final DateTimeFormatter DATA_HORA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

    /** Cor dos ícones dos botões de ação, igual à da página de estoque. */
    private static final Color COR_ICONE = Color.web("#8892a4");

    private final UsuarioService servico;
    private final FlowPane grade = new FlowPane(14, 14);
    private final Label contagem = new Label();

    /**
     * Monta a página de contas de acesso.
     *
     * @param servico o serviço que aplica as regras e fala com o banco
     */
    public PaginaUsuarios(UsuarioService servico) {
        this.servico = servico;

        setSpacing(16);

        Label titulo = new Label("Contas de Acesso");
        titulo.getStyleClass().add("pagina-titulo");
        contagem.getStyleClass().add("pagina-sub");

        VBox textos = new VBox(2, titulo, contagem);

        Button novo = new Button("Nova Conta");
        novo.getStyleClass().add("botao-principal");
        novo.setGraphic(Icones.criar("mais", 15, Color.WHITE));
        novo.setGraphicTextGap(8);
        novo.setOnAction(e -> abrirFormulario(null));

        Region espaco = new Region();
        HBox.setHgrow(espaco, Priority.ALWAYS);

        HBox cabecalho = new HBox(12, textos, espaco, novo);
        cabecalho.setAlignment(Pos.CENTER_LEFT);

        ScrollPane rolagem = new ScrollPane(grade);
        rolagem.setFitToWidth(true);
        rolagem.getStyleClass().add("rolagem");
        VBox.setVgrow(rolagem, Priority.ALWAYS);

        getChildren().addAll(cabecalho, rolagem);
        atualizar();
    }

    /**
     * Recarrega as contas do banco e redesenha os cartões.
     */
    public final void atualizar() {
        grade.getChildren().clear();
        try {
            List<Usuario> lista = servico.listar();
            contagem.setText(resumo(lista));
            for (Usuario u : lista) {
                grade.getChildren().add(criarCartao(u));
            }
        } catch (UsuarioException e) {
            contagem.setText("");
            Label erro = new Label(e.getMessage());
            erro.getStyleClass().add("mensagem-erro");
            erro.setWrapText(true);
            grade.getChildren().add(erro);
        }
    }

    /**
     * Monta a linha de contagem exibida abaixo do título.
     *
     * @param lista as contas carregadas
     * @return o texto com o total de contas e quantas estão ativas
     */
    private String resumo(List<Usuario> lista) {
        long ativas = lista.stream().filter(Usuario::isAtivo).count();
        return lista.size() + " contas cadastradas · " + ativas + " ativas";
    }

    /**
     * Constrói o cartão de uma conta.
     *
     * @param u a conta a exibir
     * @return o cartão montado
     */
    private VBox criarCartao(Usuario u) {
        boolean euMesmo = ehOProprioUsuario(u);

        Label nome = new Label(u.getNome());
        nome.getStyleClass().add("insumo-nome");
        nome.setWrapText(true);

        Label situacao = new Label(u.isAtivo() ? "Ativa" : "Inativa");
        situacao.getStyleClass().addAll("etiqueta", u.isAtivo() ? "etiqueta-ok" : "etiqueta-vencido");

        Region esp = new Region();
        HBox.setHgrow(esp, Priority.ALWAYS);

        HBox linhaTopo = new HBox(8, criarAvatar(u), nome, esp, situacao);
        linhaTopo.setAlignment(Pos.CENTER_LEFT);

        Label email = new Label(u.getEmail());
        email.getStyleClass().add("insumo-saldo");
        email.setWrapText(true);

        Label perfil = new Label(u.getPerfil().getDescricao() + (euMesmo ? "  ·  você" : ""));
        perfil.getStyleClass().add("insumo-preco");

        Label permissoes = new Label(descreverPermissoes(u.getPerfil()));
        permissoes.getStyleClass().add("insumo-pct");
        permissoes.setWrapText(true);

        Label acesso = new Label("Último acesso: " + formatar(u.getUltimoAcesso(), "nunca entrou"));
        acesso.getStyleClass().add("insumo-valor");

        Label criacao = new Label("Cadastrada em: " + formatar(u.getCriadoEm(), "—"));
        criacao.getStyleClass().add("insumo-valor");

        Button editar = criarBotaoAcao("Editar", "editar", false);
        editar.setOnAction(e -> abrirFormulario(u));

        Button senha = criarBotaoAcao("Senha", "cadeado", false);
        senha.setOnAction(e -> abrirRedefinicaoDeSenha(u));

        Button situacaoBotao = criarBotaoAcao(
                u.isAtivo() ? "Inativar" : "Ativar",
                u.isAtivo() ? "excluir" : "entrada",
                u.isAtivo());
        situacaoBotao.setOnAction(e -> confirmarMudancaDeSituacao(u));

        HBox acoes = new HBox(6, editar, senha, situacaoBotao);
        acoes.setAlignment(Pos.CENTER_LEFT);

        VBox cartao = new VBox(8, linhaTopo, email, perfil, permissoes, acesso, criacao, acoes);
        cartao.getStyleClass().add("cartao-insumo");
        if (!u.isAtivo()) {
            cartao.getStyleClass().add("cartao-insumo-vencido");
        }
        cartao.setPadding(new Insets(16));
        cartao.setPrefWidth(320);
        return cartao;
    }

    /**
     * Cria o círculo com as iniciais da pessoa, igual ao do cabeçalho.
     *
     * @param u a conta exibida no cartão
     * @return o avatar montado
     */
    private StackPane criarAvatar(Usuario u) {
        String nome = u.getNome() == null ? "" : u.getNome().trim();
        StringBuilder iniciais = new StringBuilder();
        for (String parte : nome.split("\\s+")) {
            if (!parte.isBlank() && iniciais.length() < 2) {
                iniciais.append(Character.toUpperCase(parte.charAt(0)));
            }
        }

        Label texto = new Label(iniciais.toString());
        texto.getStyleClass().add("avatar-texto");

        StackPane avatar = new StackPane(texto);
        avatar.getStyleClass().add("avatar");
        avatar.setMinSize(32, 32);
        avatar.setPrefSize(32, 32);
        avatar.setMaxSize(32, 32);
        return avatar;
    }

    /**
     * Descreve, em uma linha, o que o perfil libera.
     *
     * <p>O texto é montado a partir das próprias permissões do {@link Perfil}, e não
     * de uma lista fixa — incluir um perfil novo no enum não exige mexer aqui.</p>
     *
     * @param perfil o perfil da conta
     * @return a lista de permissões separada por vírgula
     */
    private String descreverPermissoes(Perfil perfil) {
        StringBuilder texto = new StringBuilder();
        if (perfil.podeGerenciarUsuarios()) {
            texto.append("contas de acesso");
        }
        if (perfil.podeAcessarIndicadores()) {
            if (texto.length() > 0) {
                texto.append(", ");
            }
            texto.append("indicadores e CMV");
        }
        if (perfil.podeOperarBalanca()) {
            if (texto.length() > 0) {
                texto.append(", ");
            }
            texto.append("balança e estoque");
        }
        return "Acessa: " + texto;
    }

    /**
     * Formata uma data e hora, ou devolve o texto alternativo se ela for nula.
     *
     * @param momento    a data e hora a exibir
     * @param seForNulo o texto usado quando não há data
     * @return o texto pronto para o rótulo
     */
    private String formatar(LocalDateTime momento, String seForNulo) {
        return momento == null ? seForNulo : DATA_HORA.format(momento);
    }

    /**
     * Cria um dos botões de ação do cartão, com ícone.
     *
     * @param texto  o rótulo do botão
     * @param icone  a chave do ícone em {@link Icones}
     * @param perigo {@code true} para a ação que bloqueia o acesso
     * @return o botão montado
     */
    private Button criarBotaoAcao(String texto, String icone, boolean perigo) {
        Button botao = new Button(texto);
        botao.getStyleClass().add("botao-mini");
        if (perigo) {
            botao.getStyleClass().add("botao-mini-perigo");
        }
        botao.setGraphic(Icones.criar(icone, 13, COR_ICONE));
        botao.setGraphicTextGap(5);
        return botao;
    }

    /**
     * Abre o formulário de cadastro ou de edição de uma conta.
     *
     * <p>No cadastro o formulário pede a senha inicial; na edição, não — trocar a
     * senha é a ação separada de {@link #abrirRedefinicaoDeSenha(Usuario)}, para que
     * corrigir um nome não corra o risco de mexer na credencial de alguém.</p>
     *
     * @param existente a conta a editar, ou {@code null} para cadastrar uma nova
     */
    private void abrirFormulario(Usuario existente) {
        boolean edicao = existente != null;

        TextField campoNome = new TextField(edicao ? existente.getNome() : "");
        campoNome.setPromptText("Marina Duarte");

        TextField campoEmail = new TextField(edicao ? existente.getEmail() : "");
        campoEmail.setPromptText("marina@letscoffee.com.br");

        ChoiceBox<Perfil> campoPerfil = new ChoiceBox<>();
        campoPerfil.getItems().addAll(Perfil.values());
        campoPerfil.setValue(edicao ? existente.getPerfil() : Perfil.OPERADOR);

        CheckBox campoAtivo = new CheckBox("Conta liberada para entrar no sistema");
        campoAtivo.setSelected(!edicao || existente.isAtivo());

        PasswordField campoSenha = new PasswordField();
        campoSenha.setPromptText("ao menos 6 caracteres");
        PasswordField campoConfirmacao = new PasswordField();
        campoConfirmacao.setPromptText("repita a senha");

        VBox corpo = new VBox(10,
                rotulado("Nome completo", campoNome),
                rotulado("E-mail de acesso", campoEmail),
                rotulado("Perfil", campoPerfil));

        if (!edicao) {
            corpo.getChildren().addAll(
                    rotulado("Senha inicial", campoSenha),
                    rotulado("Confirmação da senha", campoConfirmacao));
        }
        corpo.getChildren().add(campoAtivo);
        corpo.setPadding(new Insets(4, 2, 4, 2));

        Dialog<ButtonType> dialogo = Dialogos.criar(
                edicao ? "Editar conta" : "Nova conta",
                edicao ? "Alterar os dados de " + existente.getNome()
                       : "Cadastrar uma pessoa no sistema",
                corpo);

        if (dialogo.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        try {
            if (edicao) {
                Usuario alterado = copiar(existente);
                alterado.setNome(campoNome.getText());
                alterado.setEmail(campoEmail.getText());
                alterado.setPerfil(campoPerfil.getValue());
                alterado.setAtivo(campoAtivo.isSelected());

                servico.atualizar(alterado);
            } else {
                if (!campoSenha.getText().equals(campoConfirmacao.getText())) {
                    throw new UsuarioException("A senha e a confirmação não são iguais.");
                }

                Usuario novo = new Usuario();
                novo.setNome(campoNome.getText());
                novo.setEmail(campoEmail.getText());
                novo.setPerfil(campoPerfil.getValue());
                novo.setAtivo(campoAtivo.isSelected());

                servico.cadastrar(novo, campoSenha.getText());
            }
            atualizar();
        } catch (UsuarioException e) {
            Dialogos.erro(e.getMessage());
        }
    }

    /**
     * Abre o formulário de redefinição de senha de uma conta.
     *
     * @param u a conta que receberá a senha nova
     */
    private void abrirRedefinicaoDeSenha(Usuario u) {
        PasswordField campoSenha = new PasswordField();
        campoSenha.setPromptText("ao menos 6 caracteres");
        PasswordField campoConfirmacao = new PasswordField();
        campoConfirmacao.setPromptText("repita a senha");

        Label aviso = new Label(
                "A senha anterior não é necessária: quem redefine é o administrador, "
                + "justamente para o caso de a pessoa ter esquecido a dela.");
        aviso.getStyleClass().add("pagina-sub");
        aviso.setWrapText(true);
        aviso.setMaxWidth(320);

        VBox corpo = new VBox(10,
                rotulado("Senha nova", campoSenha),
                rotulado("Confirmação da senha", campoConfirmacao),
                aviso);
        corpo.setPadding(new Insets(4, 2, 4, 2));

        Dialog<ButtonType> dialogo = Dialogos.criar("Redefinir senha", u.getNome(), corpo);

        if (dialogo.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        try {
            if (!campoSenha.getText().equals(campoConfirmacao.getText())) {
                throw new UsuarioException("A senha e a confirmação não são iguais.");
            }
            servico.redefinirSenha(u.getId(), campoSenha.getText());
            Dialogos.sucesso("A senha de " + u.getNome() + " foi redefinida.");
        } catch (UsuarioException e) {
            Dialogos.erro(e.getMessage());
        }
    }

    /**
     * Pede confirmação antes de liberar ou bloquear o acesso de uma conta.
     *
     * @param u a conta a alterar
     */
    private void confirmarMudancaDeSituacao(Usuario u) {
        boolean ativando = !u.isAtivo();

        Alert confirma = new Alert(Alert.AlertType.CONFIRMATION);
        confirma.setTitle(ativando ? "Ativar conta" : "Inativar conta");
        confirma.setHeaderText((ativando ? "Liberar o acesso de " : "Bloquear o acesso de ")
                + u.getNome() + "?");
        confirma.setContentText(ativando
                ? "A pessoa volta a conseguir entrar no sistema com a senha que já tinha."
                : "A conta continua cadastrada e o histórico de acessos é preservado — "
                  + "ela apenas deixa de conseguir entrar.");

        if (confirma.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        try {
            servico.alterarSituacao(u, ativando);
            atualizar();
        } catch (UsuarioException e) {
            Dialogos.erro(e.getMessage());
        }
    }

    /**
     * Cria uma cópia da conta para receber o que foi digitado no formulário.
     *
     * <p>A edição trabalha sobre a cópia para que, se o serviço recusar a alteração,
     * o cartão exibido continue mostrando os dados que estão de fato no banco.</p>
     *
     * @param original a conta carregada do banco
     * @return uma cópia independente
     */
    private Usuario copiar(Usuario original) {
        Usuario copia = new Usuario();
        copia.setId(original.getId());
        copia.setNome(original.getNome());
        copia.setEmail(original.getEmail());
        copia.setPerfil(original.getPerfil());
        copia.setAtivo(original.isAtivo());
        return copia;
    }

    /**
     * Verifica se a conta é a do usuário autenticado nesta execução.
     *
     * @param u a conta exibida
     * @return {@code true} se for a própria conta
     */
    private boolean ehOProprioUsuario(Usuario u) {
        Usuario logado = Sessao.getUsuarioLogado();
        return logado != null && logado.getId() == u.getId();
    }

    /**
     * Envolve um campo com o seu rótulo.
     *
     * @param texto o rótulo
     * @param campo o controle
     * @return a caixa com rótulo e campo
     */
    private VBox rotulado(String texto, javafx.scene.Node campo) {
        Label rotulo = new Label(texto);
        rotulo.getStyleClass().add("rotulo-campo");
        if (campo instanceof Region r) {
            r.setPrefWidth(320);
        }
        return new VBox(4, rotulo, campo);
    }
}
