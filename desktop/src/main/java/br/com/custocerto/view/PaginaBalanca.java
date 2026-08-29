package br.com.custocerto.view;

import br.com.custocerto.model.Ingrediente;
import br.com.custocerto.service.BalancaClient;
import br.com.custocerto.service.EstoqueException;
import br.com.custocerto.service.EstoqueService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Página da balança, equivalente à tela "Balança" do painel web.
 *
 * <p>O operador escolhe o insumo, informa o peso e confirma — a quantidade sai do
 * estoque e a movimentação fica registrada. Ao lado, a tabela de custo da porção
 * mostra quanto vale o que está sendo pesado, que é o dado que o barista usa para
 * entender o custo do que está produzindo.</p>
 *
 * <p><b>Sobre a leitura automática:</b> o peso vindo do ESP32 chega hoje ao
 * backend Node, não ao banco. Enquanto a decisão D4 não for revista, esta tela
 * trabalha com o peso digitado — que é a mesma "inserção manual" que o painel web
 * já oferece. O cálculo de custo e a baixa de estoque são idênticos nos dois casos.</p>
 *
 * @author Equipe Custo Certo - FATEC Indaiatuba
 * @version 1.0
 */
public class PaginaBalanca extends VBox {

    private final EstoqueService servico;
    private final Runnable aoMudarEstoque;

    private final ComboBox<Ingrediente> seletor = new ComboBox<>();
    private final TextField campoPeso = new TextField();
    private final Label unidadeLabel = new Label("kg");
    private final Label pesoGrande = new Label("0,000");
    private final VBox tabelaCusto = new VBox(0);
    private final Button confirmar = new Button("Confirmar retirada");

    /** Cliente do fluxo de peso em tempo real vindo do ESP32. */
    private final BalancaClient balanca = new BalancaClient();

    /** Indicador visual do estado da conexão com a balança. */
    private final Label statusConexao = new Label("Conectando à balança...");

    /** Ponto colorido ao lado do status. */
    private final javafx.scene.shape.Circle pontoStatus = new javafx.scene.shape.Circle(4);

    /** Último peso recebido do sensor, usado quando a leitura é automática. */
    private volatile BigDecimal pesoDoSensor;

    /** Liga a leitura automática; desliga quando o operador digita o peso. */
    private boolean usandoSensor = true;

    /**
     * Monta a página da balança.
     *
     * @param servico        o serviço que fala com o banco
     * @param aoMudarEstoque ação executada após uma baixa, para atualizar as
     *                       outras páginas
     */
    public PaginaBalanca(EstoqueService servico, Runnable aoMudarEstoque) {
        this.servico = servico;
        this.aoMudarEstoque = aoMudarEstoque;
        setSpacing(16);

        Label titulo = new Label("Balança");
        titulo.getStyleClass().add("pagina-titulo");
        Label sub = new Label("Pesagem do insumo e baixa automática no estoque");
        sub.getStyleClass().add("pagina-sub");

        HBox conteudo = new HBox(14, montarPainelBalanca(), montarPainelCusto());
        conteudo.setAlignment(Pos.TOP_LEFT);
        VBox.setVgrow(conteudo, Priority.ALWAYS);

        getChildren().addAll(new VBox(2, titulo, sub), conteudo);
        atualizar();
        ligarSensor();
    }

    /**
     * Abre o fluxo de peso em tempo real e passa a exibir cada leitura.
     *
     * <p>As leituras chegam em uma thread de rede, então a atualização da tela é
     * devolvida à thread do JavaFX por {@link javafx.application.Platform#runLater}
     * — mexer em componentes visuais fora dela quebraria a interface.</p>
     */
    private void ligarSensor() {
        balanca.conectar(
                leitura -> javafx.application.Platform.runLater(() -> {
                    pesoDoSensor = leitura.peso();
                    definirStatus(leitura.online()
                            ? "Leitura ao vivo" : "Balança sem enviar leitura",
                            leitura.online());
                    if (usandoSensor) {
                        recalcular();
                    }
                }),
                conectado -> javafx.application.Platform.runLater(() -> {
                    if (!conectado) {
                        pesoDoSensor = null;
                        definirStatus("Servidor da balança indisponível — use o peso manual", false);
                        if (usandoSensor) {
                            recalcular();
                        }
                    }
                }));
    }

    /**
     * Atualiza o texto e a cor do indicador de conexão.
     *
     * @param texto  a mensagem exibida
     * @param online {@code true} para o indicador verde
     */
    private void definirStatus(String texto, boolean online) {
        statusConexao.setText(texto);
        pontoStatus.setFill(javafx.scene.paint.Color.web(online ? "#00a86b" : "#8892a4"));
    }

    /**
     * Encerra a escuta do fluxo de peso.
     *
     * <p>Chamado quando o usuário sai do sistema, para que a conexão não fique
     * aberta sem necessidade.</p>
     */
    public void desligarSensor() {
        balanca.desconectar();
    }

    /**
     * Recarrega a lista de insumos disponíveis para pesagem.
     */
    public final void atualizar() {
        Ingrediente selecionado = seletor.getValue();
        seletor.getItems().clear();
        try {
            seletor.getItems().addAll(servico.listarIngredientes());
            if (selecionado != null) {
                for (Ingrediente i : seletor.getItems()) {
                    if (i.getId() == selecionado.getId()) {
                        seletor.setValue(i);
                        break;
                    }
                }
            }
        } catch (EstoqueException e) {
            Dialogos.erro(e.getMessage());
        }
        recalcular();
    }

    /**
     * Monta a coluna da esquerda: seleção do insumo, visor e ações.
     *
     * @return o painel da balança
     */
    private VBox montarPainelBalanca() {
        Label rotuloSel = new Label("Ingrediente");
        rotuloSel.getStyleClass().add("cartao-titulo");

        seletor.setPromptText("— Selecione —");
        seletor.setMaxWidth(Double.MAX_VALUE);
        seletor.valueProperty().addListener((o, a, b) -> recalcular());

        VBox cartaoSel = new VBox(8, rotuloSel, seletor);
        cartaoSel.getStyleClass().add("cartao-painel");
        cartaoSel.setPadding(new Insets(16));

        Label lbl = new Label("Peso na balança");
        lbl.getStyleClass().add("visor-rotulo");
        pesoGrande.getStyleClass().add("visor-peso");
        unidadeLabel.getStyleClass().add("visor-unidade");

        statusConexao.getStyleClass().add("visor-status");
        pontoStatus.setFill(javafx.scene.paint.Color.web("#8892a4"));
        HBox status = new HBox(7, pontoStatus, statusConexao);
        status.setAlignment(Pos.CENTER);

        VBox visor = new VBox(2, lbl, pesoGrande, unidadeLabel, status);
        visor.setAlignment(Pos.CENTER);
        visor.getStyleClass().add("visor");
        visor.setPadding(new Insets(22));

        campoPeso.setPromptText("deixe vazio para usar a balança");
        campoPeso.getStyleClass().add("campo");
        campoPeso.textProperty().addListener((o, a, b) -> {
            // Digitar assume o controle; apagar devolve a leitura ao sensor.
            usandoSensor = b == null || b.isBlank();
            recalcular();
        });

        Button zerar = new Button("Tarar");
        zerar.getStyleClass().add("botao-secundario");
        zerar.setGraphic(Icones.criar("atualizar", 14, javafx.scene.paint.Color.web("#8892a4")));
        zerar.setGraphicTextGap(7);
        zerar.setMaxWidth(Double.MAX_VALUE);
        zerar.setOnAction(e -> tarar());

        confirmar.getStyleClass().add("botao-principal");
        confirmar.setMaxWidth(Double.MAX_VALUE);
        confirmar.setDisable(true);
        confirmar.setOnAction(e -> confirmarRetirada());

        HBox.setHgrow(zerar, Priority.ALWAYS);
        HBox.setHgrow(confirmar, Priority.ALWAYS);
        HBox acoes = new HBox(8, zerar, confirmar);

        VBox coluna = new VBox(12, cartaoSel, visor,
                rotulado("Peso lido", campoPeso), acoes);
        coluna.setPrefWidth(340);
        coluna.setMinWidth(320);
        return coluna;
    }

    /**
     * Monta a coluna da direita: a tabela de custo da porção.
     *
     * @return o painel de custo
     */
    private VBox montarPainelCusto() {
        Label titulo = new Label("Tabela de Custo da Porção");
        titulo.getStyleClass().add("cartao-titulo");

        VBox cartao = new VBox(10, titulo, tabelaCusto);
        cartao.getStyleClass().add("cartao-painel");
        cartao.setPadding(new Insets(16));
        HBox.setHgrow(cartao, Priority.ALWAYS);
        return cartao;
    }

    /**
     * Recalcula o visor e a tabela de custo a partir do insumo e do peso atuais.
     */
    private void recalcular() {
        Ingrediente i = seletor.getValue();
        tabelaCusto.getChildren().clear();

        if (i == null) {
            unidadeLabel.setText("kg");
            pesoGrande.setText("0,000");
            confirmar.setDisable(true);
            tabelaCusto.getChildren().add(vazio("Selecione um ingrediente e informe o peso"));
            return;
        }

        unidadeLabel.setText(i.getUnidade().getSigla());

        BigDecimal peso = lerPeso();
        if (peso == null || peso.signum() <= 0) {
            pesoGrande.setText("0,000");
            confirmar.setDisable(true);
            tabelaCusto.getChildren().add(vazio("Informe o peso para calcular o custo"));
            return;
        }

        pesoGrande.setText(Formatos.quantidade(peso));
        confirmar.setDisable(false);

        BigDecimal custo = i.getPreco().multiply(peso).setScale(2, RoundingMode.HALF_UP);
        BigDecimal restante = i.getQtd().subtract(peso);

        tabelaCusto.getChildren().addAll(
                cabecalhoTabela(),
                linhaTabela("Preço de referência",
                        Formatos.dinheiro(i.getPreco()) + " / " + i.getUnidade().getSigla(), ""),
                linhaTabela("Peso da porção",
                        Formatos.quantidadeComUnidade(peso, i.getUnidade().getSigla()),
                        Formatos.dinheiro(custo)),
                linhaTabela("Estoque antes",
                        Formatos.quantidadeComUnidade(i.getQtd(), i.getUnidade().getSigla()),
                        Formatos.dinheiro(i.getValorEmEstoque())),
                linhaTabela("Estoque depois",
                        Formatos.quantidadeComUnidade(restante, i.getUnidade().getSigla()),
                        Formatos.dinheiro(i.getPreco().multiply(restante)
                                .setScale(2, RoundingMode.HALF_UP))));

        if (restante.signum() < 0) {
            Label aviso = new Label("O peso informado é maior do que o estoque disponível.");
            aviso.getStyleClass().add("mensagem-erro");
            aviso.setWrapText(true);
            tabelaCusto.getChildren().add(aviso);
            confirmar.setDisable(true);
        }
    }

    /**
     * Grava a retirada correspondente ao peso informado.
     */
    private void confirmarRetirada() {
        Ingrediente i = seletor.getValue();
        BigDecimal peso = lerPeso();
        if (i == null || peso == null) {
            return;
        }

        try {
            BigDecimal novoSaldo = servico.registrarSaida(i.getId(), peso,
                    "Pesagem confirmada na balança");
            campoPeso.clear();
            atualizar();
            aoMudarEstoque.run();
            Dialogos.sucesso("Retirada registrada. O estoque de " + i.getNome()
                    + " passou a ser " + Formatos.quantidadeComUnidade(
                            novoSaldo, i.getUnidade().getSigla()) + ".");
        } catch (EstoqueException e) {
            Dialogos.erro(e.getMessage());
        }
    }

    /**
     * Pede a tara ao ESP32 e limpa o peso digitado.
     */
    private void tarar() {
        campoPeso.clear();
        usandoSensor = true;
        if (balanca.solicitarTara()) {
            definirStatus("Tara solicitada — aguardando o sensor zerar", true);
        } else {
            definirStatus("Não foi possível pedir a tara ao servidor", false);
        }
    }

    /**
     * Devolve o peso a usar: o digitado, quando houver, ou o do sensor.
     *
     * @return o peso corrente, ou {@code null} se não houver nenhum dos dois
     */
    private BigDecimal lerPeso() {
        String texto = campoPeso.getText();
        if (texto != null && !texto.isBlank()) {
            try {
                return new BigDecimal(texto.trim().replace(",", "."));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return pesoDoSensor;
    }

    /**
     * Monta a linha de cabeçalho da tabela de custo.
     *
     * @return a linha de cabeçalho
     */
    private HBox cabecalhoTabela() {
        return linhaBase("Grandeza", "Quantidade", "Valor (R$)", "tabela-cabecalho");
    }

    /**
     * Monta uma linha de dados da tabela de custo.
     *
     * @param grandeza  a descrição
     * @param quantidade o valor do meio
     * @param valor     o valor monetário
     * @return a linha montada
     */
    private HBox linhaTabela(String grandeza, String quantidade, String valor) {
        return linhaBase(grandeza, quantidade, valor, "tabela-linha");
    }

    /**
     * Monta uma linha da tabela com três colunas.
     *
     * @param a      primeira coluna
     * @param b      segunda coluna
     * @param c      terceira coluna
     * @param estilo a classe de estilo da linha
     * @return a linha montada
     */
    private HBox linhaBase(String a, String b, String c, String estilo) {
        Label l1 = new Label(a);
        Label l2 = new Label(b);
        Label l3 = new Label(c);
        l1.setPrefWidth(180);
        l2.setPrefWidth(150);
        HBox.setHgrow(l3, Priority.ALWAYS);
        l3.setMaxWidth(Double.MAX_VALUE);
        l3.setAlignment(Pos.CENTER_RIGHT);

        HBox linha = new HBox(8, l1, l2, l3);
        linha.getStyleClass().add(estilo);
        linha.setPadding(new Insets(9, 4, 9, 4));
        return linha;
    }

    /**
     * Monta a mensagem exibida quando ainda não há o que calcular.
     *
     * @param texto a mensagem
     * @return o rótulo montado
     */
    private Label vazio(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("pagina-sub");
        l.setPadding(new Insets(24, 0, 24, 0));
        l.setMaxWidth(Double.MAX_VALUE);
        l.setAlignment(Pos.CENTER);
        return l;
    }

    /**
     * Envolve um campo com o seu rótulo.
     *
     * @param texto o rótulo
     * @param campo o controle
     * @return a caixa com rótulo e campo
     */
    private VBox rotulado(String texto, Region campo) {
        Label rotulo = new Label(texto);
        rotulo.getStyleClass().add("rotulo-campo");
        campo.setMaxWidth(Double.MAX_VALUE);
        return new VBox(4, rotulo, campo);
    }
}
