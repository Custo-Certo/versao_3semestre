package br.com.custocerto.view;

import br.com.custocerto.model.Usuario;
import br.com.custocerto.service.EstoqueService;
import br.com.custocerto.service.AnaliseService;
import br.com.custocerto.service.FichaTecnicaService;
import br.com.custocerto.service.UsuarioService;
import br.com.custocerto.service.VendaService;
import br.com.custocerto.util.ConfiguracaoBanco;
import br.com.custocerto.util.Sessao;
import java.util.ArrayList;
import java.util.List;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Janela principal do sistema, com a mesma navegação do painel web.
 *
 * <p>Uma barra lateral fixa à esquerda agrupa as páginas em quatro seções —
 * operação, cardápio, análises e administração — e a área da direita troca de
 * conteúdo conforme a escolha, sem abrir novas janelas.</p>
 *
 * <p>O menu não é fixo: cada item só aparece se o perfil do usuário autenticado
 * permitir. Um operador não vê Dashboard nem Evolução de Preços, porque essas
 * páginas exigem a permissão de indicadores, e só o administrador vê Usuários. Como a decisão vem do próprio
 * {@link br.com.custocerto.model.Perfil}, incluir um perfil novo no futuro não
 * exige mexer nesta classe.</p>
 *
 * @author Equipe Custo Certo - FATEC Indaiatuba
 * @version 1.0
 */
public class TelaPrincipal {

    private final Stage palco;
    private final Usuario usuario;
    private final EstoqueService servico = new EstoqueService();
    private final UsuarioService servicoUsuarios = new UsuarioService();
    private final FichaTecnicaService servicoCardapio = new FichaTecnicaService();
    private final VendaService servicoVendas = new VendaService();
    private final AnaliseService servicoAnalise = new AnaliseService();

    /** Cor dos ícones e rótulos inativos do menu. */
    private static final javafx.scene.paint.Color COR_APAGADA =
            javafx.scene.paint.Color.web("#8892a4");

    /** Cor do item de menu aberto no momento. */
    private static final javafx.scene.paint.Color COR_VERDE =
            javafx.scene.paint.Color.web("#00a86b");

    private final StackPane area = new StackPane();
    private final VBox menu = new VBox(2);
    private final List<Button> botoesMenu = new ArrayList<>();
    private final java.util.Map<Button, String> iconesPorBotao = new java.util.HashMap<>();

    private PaginaDashboard dashboard;
    private PaginaBalanca balanca;
    private PaginaEstoque estoque;
    private PaginaEvolucao evolucao;
    private PaginaFichaTecnica fichaTecnica;
    private PaginaVendas vendas;
    private PaginaMargem margem;
    private PaginaUsuarios usuarios;

    /**
     * Cria a janela principal para o usuário autenticado.
     *
     * @param palco   a janela onde a tela será exibida
     * @param usuario o usuário que acabou de entrar no sistema
     */
    public TelaPrincipal(Stage palco, Usuario usuario) {
        this.palco = palco;
        this.usuario = usuario;
    }

    /**
     * Monta a cena completa: barra lateral, cabeçalho e área de conteúdo.
     *
     * @return a cena pronta para ser exibida na janela
     */
    public Scene criarCena() {
        BorderPane raiz = new BorderPane();
        raiz.getStyleClass().add("fundo-app");
        raiz.setLeft(montarBarraLateral());

        BorderPane direita = new BorderPane();
        direita.setTop(montarCabecalho());
        area.setPadding(new Insets(22, 24, 24, 24));
        direita.setCenter(area);
        raiz.setCenter(direita);

        montarPaginas();

        Scene cena = new Scene(raiz, 1180, 720);
        cena.getStylesheets().add(
                getClass().getResource("/css/estilo.css").toExternalForm());
        return cena;
    }

    /**
     * Cria as páginas permitidas ao perfil e abre a primeira delas.
     */
    private void montarPaginas() {
        boolean indicadores = usuario.getPerfil().podeAcessarIndicadores();
        boolean operacao = usuario.getPerfil().podeOperarBalanca();

        menu.getChildren().add(rotuloSecao("Principal"));

        if (indicadores) {
            dashboard = new PaginaDashboard(servico, servicoAnalise);
            adicionarItem("Dashboard", "painel", dashboard);
        }
        if (operacao) {
            balanca = new PaginaBalanca(servico, this::atualizarTudo);
            adicionarItem("Balança", "balanca", balanca);

            estoque = new PaginaEstoque(servico, this::atualizarTudo);
            adicionarItem("Estoque", "estoque", estoque);
        }

        if (operacao) {
            menu.getChildren().add(rotuloSecao("Cardápio"));

            vendas = new PaginaVendas(servicoVendas, servicoCardapio, this::atualizarTudo);
            adicionarItem("Vendas", "vendas", vendas);

            if (indicadores) {
                fichaTecnica = new PaginaFichaTecnica(
                        servicoCardapio, servico, this::atualizarTudo);
                adicionarItem("Ficha Técnica", "ficha", fichaTecnica);
            }
        }

        if (indicadores) {
            menu.getChildren().add(rotuloSecao("Análises"));

            margem = new PaginaMargem(servicoAnalise);
            adicionarItem("Margem e Desperdício", "margem", margem);

            evolucao = new PaginaEvolucao(servico);
            adicionarItem("Evolução de Preços", "evolucao", evolucao);
        }

        if (usuario.getPerfil().podeGerenciarUsuarios()) {
            menu.getChildren().add(rotuloSecao("Administração"));
            usuarios = new PaginaUsuarios(servicoUsuarios);
            adicionarItem("Usuários", "usuarios", usuarios);
        }

        if (!botoesMenu.isEmpty()) {
            botoesMenu.get(0).fire();
        }
    }

    /**
     * Acrescenta um item ao menu lateral, ligado à sua página.
     *
     * <p>O ícone é redesenhado ao trocar de item, porque ele muda de cor junto
     * com o texto: apagado quando inativo, verde quando é a página aberta.</p>
     *
     * @param titulo o texto do botão
     * @param icone  a chave do ícone em {@link Icones}
     * @param pagina o conteúdo exibido quando ele for acionado
     */
    private void adicionarItem(String titulo, String icone, Node pagina) {
        Button botao = new Button(titulo);
        botao.getStyleClass().add("item-menu");
        botao.setMaxWidth(Double.MAX_VALUE);
        botao.setAlignment(Pos.CENTER_LEFT);
        botao.setGraphic(Icones.criar(icone, 17, COR_APAGADA));
        botao.setGraphicTextGap(11);

        botao.setOnAction(e -> {
            for (Button b : botoesMenu) {
                b.getStyleClass().remove("item-menu-ativo");
                b.setGraphic(Icones.criar(iconesPorBotao.get(b), 17, COR_APAGADA));
            }
            botao.getStyleClass().add("item-menu-ativo");
            botao.setGraphic(Icones.criar(icone, 17, COR_VERDE));
            area.getChildren().setAll(pagina);
        });

        iconesPorBotao.put(botao, icone);
        botoesMenu.add(botao);
        menu.getChildren().add(botao);
    }

    /**
     * Cria o rótulo que separa as seções do menu.
     *
     * @param texto o nome da seção
     * @return o rótulo montado
     */
    private Label rotuloSecao(String texto) {
        Label l = new Label(texto.toUpperCase());
        l.getStyleClass().add("menu-secao");
        l.setPadding(new Insets(14, 0, 4, 0));
        return l;
    }

    /**
     * Monta a barra lateral com a marca, o menu e o rodapé.
     *
     * @return a barra lateral
     */
    private VBox montarBarraLateral() {
        Label marca = new Label("Custo Certo");
        marca.getStyleClass().add("marca-lateral");
        Label sub = new Label("Controle de insumos e CMV");
        sub.getStyleClass().add("marca-lateral-sub");

        VBox topo = new VBox(1, marca, sub);
        topo.setPadding(new Insets(20, 18, 16, 18));
        topo.getStyleClass().add("marca-caixa");

        menu.setPadding(new Insets(6, 12, 12, 12));

        Region esp = new Region();
        VBox.setVgrow(esp, Priority.ALWAYS);

        Label rodape = new Label("Custo Certo v2.0 · FATEC Indaiatuba");
        rodape.getStyleClass().add("rodape-lateral");
        rodape.setWrapText(true);
        rodape.setPadding(new Insets(12, 18, 16, 18));

        VBox lateral = new VBox(topo, menu, esp, rodape);
        lateral.getStyleClass().add("barra-lateral");
        lateral.setPrefWidth(230);
        lateral.setMinWidth(230);
        return lateral;
    }

    /**
     * Monta o cabeçalho com a identificação do usuário e o botão de sair.
     *
     * @return o cabeçalho da área de conteúdo
     */
    private HBox montarCabecalho() {
        Label saudacao = new Label("Bem-vindo, " + usuario.getPrimeiroNome() + ".");
        saudacao.getStyleClass().add("usuario-nome");

        Label perfil = new Label(usuario.getPerfil().getDescricao()
                + " · MySQL em " + ConfiguracaoBanco.getHost());
        perfil.getStyleClass().add("etiqueta-perfil");

        HBox dados = new HBox(11, criarAvatar(), new VBox(1, saudacao, perfil));
        dados.setAlignment(Pos.CENTER_LEFT);

        Region esp = new Region();
        HBox.setHgrow(esp, Priority.ALWAYS);

        Button atualizar = new Button("Atualizar");
        atualizar.getStyleClass().add("botao-secundario");
        atualizar.setGraphic(Icones.criar("atualizar", 14, COR_APAGADA));
        atualizar.setGraphicTextGap(7);
        atualizar.setOnAction(e -> atualizarTudo());

        Button sair = new Button("Sair");
        sair.getStyleClass().add("botao-secundario");
        sair.setGraphic(Icones.criar("sair", 14, COR_APAGADA));
        sair.setGraphicTextGap(7);
        sair.setOnAction(e -> sair());

        HBox barra = new HBox(10, dados, esp, atualizar, sair);
        barra.setAlignment(Pos.CENTER_LEFT);
        barra.setPadding(new Insets(15, 24, 15, 24));
        barra.getStyleClass().add("barra-superior");
        return barra;
    }

    /**
     * Cria o círculo com as iniciais do usuário autenticado.
     *
     * @return o avatar montado
     */
    private StackPane criarAvatar() {
        String nome = usuario.getNome() == null ? "" : usuario.getNome().trim();
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
        avatar.setMinSize(38, 38);
        avatar.setPrefSize(38, 38);
        avatar.setMaxSize(38, 38);
        return avatar;
    }

    /**
     * Recarrega todas as páginas abertas.
     *
     * <p>Chamado depois de qualquer alteração no estoque, para que o indicador do
     * Dashboard e o gráfico de evolução reflitam a mudança imediatamente.</p>
     */
    private void atualizarTudo() {
        if (dashboard != null) {
            dashboard.atualizar();
        }
        if (estoque != null) {
            estoque.atualizar();
        }
        if (balanca != null) {
            balanca.atualizar();
        }
        if (evolucao != null) {
            evolucao.atualizar();
        }
        if (usuarios != null) {
            usuarios.atualizar();
        }
        if (fichaTecnica != null) {
            fichaTecnica.atualizar();
        }
        if (vendas != null) {
            vendas.atualizar();
        }
        if (margem != null) {
            margem.atualizar();
        }
    }

    /**
     * Encerra a sessão e devolve a janela à tela de login.
     */
    private void sair() {
        Sessao.encerrar();
        TelaLogin login = new TelaLogin(palco);
        palco.setScene(login.criarCena());
        palco.setTitle("Custo Certo — Acesso ao sistema");
        palco.centerOnScreen();
    }
}
