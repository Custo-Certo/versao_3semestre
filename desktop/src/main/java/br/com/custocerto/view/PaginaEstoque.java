package br.com.custocerto.view;

import br.com.custocerto.model.Ingrediente;
import br.com.custocerto.model.Unidade;
import br.com.custocerto.service.EstoqueException;
import br.com.custocerto.service.EstoqueService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Dialog;

/**
 * Página de estoque: os insumos cadastrados, em cartões.
 *
 * <p>Reproduz a tela "Estoque" do painel web: a faixa de alerta de vencimento no
 * topo, o botão de novo insumo, e um cartão por insumo com a barra de nível, a
 * etiqueta de validade e as ações de compra, retirada, edição e exclusão.</p>
 *
 * <p>As cores da barra seguem as mesmas faixas do painel web: verde acima de
 * 50%, laranja entre 25% e 50%, vermelho abaixo de 25%.</p>
 *
 * @author Equipe Custo Certo - FATEC Indaiatuba
 * @version 1.0
 */
public class PaginaEstoque extends VBox {

    private final EstoqueService servico;
    private final FlowPane grade = new FlowPane(14, 14);
    private final Label contagem = new Label();
    private final VBox faixaAlerta = new VBox(4);
    private final Runnable aoMudarEstoque;

    /**
     * Monta a página de estoque.
     *
     * @param servico        o serviço que fala com o banco
     * @param aoMudarEstoque ação executada quando o estoque muda, para que as
     *                       outras páginas se atualizem
     */
    public PaginaEstoque(EstoqueService servico, Runnable aoMudarEstoque) {
        this.servico = servico;
        this.aoMudarEstoque = aoMudarEstoque;

        setSpacing(16);

        Label titulo = new Label("Ingredientes Cadastrados");
        titulo.getStyleClass().add("pagina-titulo");
        contagem.getStyleClass().add("pagina-sub");

        VBox textos = new VBox(2, titulo, contagem);

        Button novo = new Button("Novo Ingrediente");
        novo.getStyleClass().add("botao-principal");
        novo.setGraphic(Icones.criar("mais", 15, javafx.scene.paint.Color.WHITE));
        novo.setGraphicTextGap(8);
        novo.setOnAction(e -> abrirFormulario(null));

        Region espaco = new Region();
        HBox.setHgrow(espaco, Priority.ALWAYS);

        HBox cabecalho = new HBox(12, textos, espaco, novo);
        cabecalho.setAlignment(Pos.CENTER_LEFT);

        faixaAlerta.getStyleClass().add("faixa-alerta");
        faixaAlerta.setManaged(false);
        faixaAlerta.setVisible(false);

        ScrollPane rolagem = new ScrollPane(grade);
        rolagem.setFitToWidth(true);
        rolagem.getStyleClass().add("rolagem");
        VBox.setVgrow(rolagem, Priority.ALWAYS);

        getChildren().addAll(cabecalho, faixaAlerta, rolagem);
        atualizar();
    }

    /**
     * Recarrega os insumos do banco e redesenha os cartões.
     */
    public final void atualizar() {
        grade.getChildren().clear();
        try {
            List<Ingrediente> lista = servico.listarIngredientes();
            contagem.setText(lista.size() + " itens cadastrados");
            montarFaixaAlerta(lista);
            for (Ingrediente i : lista) {
                grade.getChildren().add(criarCartao(i));
            }
            if (lista.isEmpty()) {
                Label vazio = new Label("Nenhum insumo cadastrado ainda.");
                vazio.getStyleClass().add("pagina-sub");
                grade.getChildren().add(vazio);
            }
        } catch (EstoqueException e) {
            contagem.setText("");
            Label erro = new Label(e.getMessage());
            erro.getStyleClass().add("mensagem-erro");
            erro.setWrapText(true);
            grade.getChildren().add(erro);
        }
    }

    /**
     * Monta a faixa de alerta com os insumos vencidos ou perto de vencer.
     *
     * @param lista os insumos cadastrados
     */
    private void montarFaixaAlerta(List<Ingrediente> lista) {
        List<Ingrediente> criticos = new ArrayList<>();
        for (Ingrediente i : lista) {
            if (i.isVencido() || i.isVencimentoProximo()) {
                criticos.add(i);
            }
        }

        faixaAlerta.getChildren().clear();
        boolean mostrar = !criticos.isEmpty();
        faixaAlerta.setManaged(mostrar);
        faixaAlerta.setVisible(mostrar);
        if (!mostrar) {
            return;
        }

        Label titulo = new Label("Produtos com vencimento próximo ou vencidos");
        titulo.getStyleClass().add("faixa-alerta-titulo");
        titulo.setGraphic(Icones.criar("alerta", 15, javafx.scene.paint.Color.web("#b97a10")));
        titulo.setGraphicTextGap(8);
        faixaAlerta.getChildren().add(titulo);

        for (Ingrediente i : criticos) {
            Label linha = new Label("• " + i.getNome() + " — " + i.getRotuloValidade()
                    + " (" + Formatos.data(i.getValidade()) + ")");
            linha.getStyleClass().add("faixa-alerta-item");
            faixaAlerta.getChildren().add(linha);
        }
    }

    /**
     * Constrói o cartão de um insumo.
     *
     * @param i o insumo a exibir
     * @return o cartão montado
     */
    private VBox criarCartao(Ingrediente i) {
        Label nome = new Label(i.getNome());
        nome.getStyleClass().add("insumo-nome");
        nome.setWrapText(true);

        Label validade = new Label(i.getRotuloValidade());
        validade.getStyleClass().add("etiqueta");
        if (i.isVencido()) {
            validade.getStyleClass().add("etiqueta-vencido");
        } else if (i.isVencimentoProximo()) {
            validade.getStyleClass().add("etiqueta-aviso");
        } else {
            validade.getStyleClass().add("etiqueta-ok");
        }

        Region esp = new Region();
        HBox.setHgrow(esp, Priority.ALWAYS);
        HBox linhaTopo = new HBox(8, nome, esp, validade);
        linhaTopo.setAlignment(Pos.TOP_LEFT);

        Label preco = new Label(Formatos.dinheiro(i.getPreco()) + " / " + i.getUnidade().getSigla());
        preco.getStyleClass().add("insumo-preco");

        Label saldo = new Label(Formatos.quantidadeComUnidade(i.getQtd(), i.getUnidade().getSigla())
                + "  de  " + Formatos.quantidadeComUnidade(i.getQtdMax(), i.getUnidade().getSigla()));
        saldo.getStyleClass().add("insumo-saldo");

        double pct = i.getPercentualEstoque();
        Label percentual = new Label(Math.round(pct) + "% do estoque");
        percentual.getStyleClass().add("insumo-pct");

        Region trilho = new Region();
        trilho.getStyleClass().add("barra-fundo");
        Region preenchido = new Region();
        preenchido.getStyleClass().addAll("barra-nivel", classeDaBarra(pct));
        preenchido.setMaxWidth(Region.USE_PREF_SIZE);
        preenchido.prefWidthProperty().bind(trilho.widthProperty().multiply(pct / 100.0));

        StackPane barra = new StackPane(trilho, preenchido);
        StackPane.setAlignment(preenchido, Pos.CENTER_LEFT);

        Label valor = new Label("Valor em estoque: " + Formatos.dinheiro(i.getValorEmEstoque()));
        valor.getStyleClass().add("insumo-valor");

        Button comprar = criarBotaoAcao("Compra", "entrada", false);
        comprar.setOnAction(e -> abrirMovimentacao(i, true));

        Button retirar = criarBotaoAcao("Retirada", "saida", false);
        retirar.setOnAction(e -> abrirMovimentacao(i, false));

        Button editar = criarBotaoAcao("Editar", "editar", false);
        editar.setOnAction(e -> abrirFormulario(i));

        Button excluir = criarBotaoAcao("Excluir", "excluir", true);
        excluir.setOnAction(e -> confirmarExclusao(i));

        HBox acoes = new HBox(6, comprar, retirar, editar, excluir);
        acoes.setAlignment(Pos.CENTER_LEFT);

        VBox cartao = new VBox(8, linhaTopo, preco, saldo, barra, percentual, valor, acoes);
        cartao.getStyleClass().add("cartao-insumo");
        if (i.isVencido()) {
            cartao.getStyleClass().add("cartao-insumo-vencido");
        } else if (i.isVencimentoProximo()) {
            cartao.getStyleClass().add("cartao-insumo-aviso");
        }
        cartao.setPadding(new Insets(16));
        cartao.setPrefWidth(300);
        return cartao;
    }

    /**
     * Cria um dos botões de ação do cartão de insumo, com ícone.
     *
     * @param texto   o rótulo do botão
     * @param icone   a chave do ícone em {@link Icones}
     * @param perigo  {@code true} para a ação destrutiva, que fica vermelha ao passar o mouse
     * @return o botão montado
     */
    private Button criarBotaoAcao(String texto, String icone, boolean perigo) {
        Button botao = new Button(texto);
        botao.getStyleClass().add("botao-mini");
        if (perigo) {
            botao.getStyleClass().add("botao-mini-perigo");
        }
        botao.setGraphic(Icones.criar(icone, 13, javafx.scene.paint.Color.web("#8892a4")));
        botao.setGraphicTextGap(5);
        return botao;
    }

    /**
     * Escolhe a cor da barra conforme o percentual restante.
     *
     * @param pct o percentual de estoque
     * @return a classe de estilo correspondente
     */
    private String classeDaBarra(double pct) {
        if (pct > 50) {
            return "barra-ok";
        }
        return pct > 25 ? "barra-media" : "barra-baixa";
    }

    /**
     * Abre o formulário de cadastro ou edição de um insumo.
     *
     * @param existente o insumo a editar, ou {@code null} para cadastrar um novo
     */
    private void abrirFormulario(Ingrediente existente) {
        boolean edicao = existente != null;

        TextField campoNome = new TextField(edicao ? existente.getNome() : "");
        campoNome.setPromptText("Café em Grãos Arábica");

        ChoiceBox<Unidade> campoUnidade = new ChoiceBox<>();
        campoUnidade.getItems().addAll(Unidade.values());
        campoUnidade.setValue(edicao ? existente.getUnidade() : Unidade.KG);

        TextField campoPreco = new TextField(edicao ? Formatos.paraCampo(existente.getPreco()) : "0");
        TextField campoQtd = new TextField(edicao ? Formatos.paraCampo(existente.getQtd()) : "0");
        TextField campoQtdMax = new TextField(edicao ? Formatos.paraCampo(existente.getQtdMax()) : "0");

        DatePicker campoValidade = new DatePicker(edicao ? existente.getValidade() : null);
        campoValidade.setPromptText("opcional");

        VBox corpo = new VBox(10,
                rotulado("Nome do insumo", campoNome),
                rotulado("Unidade de medida", campoUnidade),
                rotulado("Preço por unidade (R$)", campoPreco),
                rotulado("Quantidade em estoque", campoQtd),
                rotulado("Capacidade de referência", campoQtdMax),
                rotulado("Validade", campoValidade));
        corpo.setPadding(new Insets(4, 2, 4, 2));

        Dialog<ButtonType> dialogo = Dialogos.criar(
                edicao ? "Editar insumo" : "Novo insumo",
                edicao ? "Alterar os dados de " + existente.getNome() : "Cadastrar um insumo no estoque",
                corpo);

        if (dialogo.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        try {
            Ingrediente alvo = edicao ? existente : new Ingrediente();
            alvo.setNome(campoNome.getText());
            alvo.setUnidade(campoUnidade.getValue());
            alvo.setPreco(lerDecimal(campoPreco.getText(), "preço"));
            alvo.setQtd(lerDecimal(campoQtd.getText(), "quantidade"));
            alvo.setQtdMax(lerDecimal(campoQtdMax.getText(), "capacidade"));
            alvo.setValidade(campoValidade.getValue());

            servico.salvar(alvo);
            atualizar();
            aoMudarEstoque.run();
        } catch (EstoqueException e) {
            Dialogos.erro(e.getMessage());
        }
    }

    /**
     * Abre o formulário de compra ou de retirada de um insumo.
     *
     * @param i       o insumo movimentado
     * @param entrada {@code true} para compra, {@code false} para retirada
     */
    private void abrirMovimentacao(Ingrediente i, boolean entrada) {
        TextField campoQtd = new TextField();
        campoQtd.setPromptText("em " + i.getUnidade().getSigla());

        TextField campoPreco = new TextField(Formatos.paraCampo(i.getPreco()));
        TextField campoObs = new TextField();
        campoObs.setPromptText(entrada ? "Compra mensal" : "Consumo pesado na balança");

        VBox corpo = new VBox(10);
        corpo.getChildren().add(rotulado("Quantidade (" + i.getUnidade().getSigla() + ")", campoQtd));
        if (entrada) {
            corpo.getChildren().add(rotulado("Preço pago por unidade (R$)", campoPreco));
        }
        corpo.getChildren().add(rotulado("Observação", campoObs));

        Label saldoAtual = new Label("Estoque atual: "
                + Formatos.quantidadeComUnidade(i.getQtd(), i.getUnidade().getSigla()));
        saldoAtual.getStyleClass().add("pagina-sub");
        corpo.getChildren().add(saldoAtual);
        corpo.setPadding(new Insets(4, 2, 4, 2));

        Dialog<ButtonType> dialogo = Dialogos.criar(
                entrada ? "Registrar compra" : "Registrar retirada",
                i.getNome(), corpo);

        if (dialogo.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        try {
            BigDecimal qtd = lerDecimal(campoQtd.getText(), "quantidade");
            if (entrada) {
                servico.registrarEntrada(i.getId(), qtd,
                        lerDecimal(campoPreco.getText(), "preço"), campoObs.getText());
            } else {
                servico.registrarSaida(i.getId(), qtd, campoObs.getText());
            }
            atualizar();
            aoMudarEstoque.run();
        } catch (EstoqueException e) {
            Dialogos.erro(e.getMessage());
        }
    }

    /**
     * Pede confirmação antes de excluir um insumo.
     *
     * @param i o insumo a excluir
     */
    private void confirmarExclusao(Ingrediente i) {
        Alert confirma = new Alert(Alert.AlertType.CONFIRMATION);
        confirma.setTitle("Excluir insumo");
        confirma.setHeaderText("Excluir \"" + i.getNome() + "\"?");
        confirma.setContentText("O histórico de movimentações deste insumo também será apagado. "
                + "Esta ação não pode ser desfeita.");

        if (confirma.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        try {
            servico.excluir(i.getId());
            atualizar();
            aoMudarEstoque.run();
        } catch (EstoqueException e) {
            Dialogos.erro(e.getMessage());
        }
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

    /**
     * Converte o texto digitado em número, aceitando vírgula como separador.
     *
     * @param texto o conteúdo do campo
     * @param campo o nome do campo, usado na mensagem de erro
     * @return o valor convertido
     * @throws EstoqueException se o texto não for um número válido
     */
    private BigDecimal lerDecimal(String texto, String campo) throws EstoqueException {
        if (texto == null || texto.isBlank()) {
            throw new EstoqueException("Preencha o campo " + campo + ".");
        }
        try {
            return Formatos.lerNumero(texto);
        } catch (NumberFormatException e) {
            throw new EstoqueException("O valor de " + campo + " não é um número válido.");
        }
    }
}
