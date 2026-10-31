package br.com.custocerto.view;

import br.com.custocerto.model.Periodo;
import br.com.custocerto.model.Receita;
import br.com.custocerto.model.Venda;
import br.com.custocerto.service.CardapioException;
import br.com.custocerto.service.FichaTecnicaService;
import br.com.custocerto.service.VendaService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/**
 * Página de vendas: o que saiu do balcão, quando e por quanto.
 *
 * <p>Não é um ponto de venda, e não pretende ser. O objetivo é registrar o
 * suficiente para o sistema ter o lado da receita — sem ele, nenhum indicador de
 * margem existe, e o CMV vira o palpite que era antes.</p>
 *
 * <p><b>Registrar uma venda aqui não abate o estoque.</b> A baixa dos insumos
 * acontece pela balança, num caminho separado. É isso que permite ao sistema
 * comparar depois quanto as vendas <em>pediam</em> com quanto de fato
 * <em>saiu</em> — se a venda abatesse o estoque sozinha, os dois números seriam
 * iguais por construção e a comparação não diria nada.</p>
 *
 * @author Equipe Custo Certo - FATEC Indaiatuba
 * @version 1.0
 */
public class PaginaVendas extends VBox {

    /** Cor dos ícones dos botões de ação. */
    private static final Color COR_ICONE = Color.web("#8892a4");

    private final VendaService servico;
    private final FichaTecnicaService cardapio;
    private final Runnable aoMudarVendas;

    private final ChoiceBox<OpcaoPeriodo> filtro = new ChoiceBox<>();
    private final HBox totais = new HBox(14);
    private final VBox tabela = new VBox(0);
    private final Label contagem = new Label();

    /**
     * Uma opção do seletor de período, com o rótulo já pronto para exibição.
     *
     * @param rotulo   o texto exibido no seletor
     * @param periodo  o intervalo correspondente
     */
    private record OpcaoPeriodo(String rotulo, Periodo periodo) {
        @Override
        public String toString() {
            return rotulo;
        }
    }

    /**
     * Monta a página de vendas.
     *
     * @param servico       o serviço de vendas
     * @param cardapio      o serviço do cardápio, que fornece os produtos
     * @param aoMudarVendas ação executada quando as vendas mudam, para as outras
     *                      páginas se atualizarem
     */
    public PaginaVendas(VendaService servico, FichaTecnicaService cardapio,
                        Runnable aoMudarVendas) {
        this.servico = servico;
        this.cardapio = cardapio;
        this.aoMudarVendas = aoMudarVendas;

        setSpacing(16);

        Label titulo = new Label("Vendas");
        titulo.getStyleClass().add("pagina-titulo");
        contagem.getStyleClass().add("pagina-sub");

        VBox textos = new VBox(2, titulo, contagem);

        filtro.getItems().addAll(
                new OpcaoPeriodo("Este mês", Periodo.mesAtual()),
                new OpcaoPeriodo("Últimos 7 dias", Periodo.ultimosDias(7)),
                new OpcaoPeriodo("Últimos 30 dias", Periodo.ultimosDias(30)),
                new OpcaoPeriodo("Últimos 90 dias", Periodo.ultimosDias(90)));
        filtro.setValue(filtro.getItems().get(2));
        filtro.setOnAction(e -> atualizar());

        Button registrar = new Button("Registrar Venda");
        registrar.getStyleClass().add("botao-principal");
        registrar.setGraphic(Icones.criar("mais", 15, Color.WHITE));
        registrar.setGraphicTextGap(8);
        registrar.setOnAction(e -> abrirFormulario());

        Region espaco = new Region();
        HBox.setHgrow(espaco, Priority.ALWAYS);

        HBox cabecalho = new HBox(12, textos, espaco, filtro, registrar);
        cabecalho.setAlignment(Pos.CENTER_LEFT);

        ScrollPane rolagem = new ScrollPane(tabela);
        rolagem.setFitToWidth(true);
        rolagem.getStyleClass().add("rolagem");
        VBox.setVgrow(rolagem, Priority.ALWAYS);

        getChildren().addAll(cabecalho, totais, rolagem);
        atualizar();
    }

    /**
     * Recarrega as vendas do período escolhido e redesenha a tela.
     */
    public final void atualizar() {
        totais.getChildren().clear();
        tabela.getChildren().clear();

        Periodo periodo = filtro.getValue().periodo();

        try {
            List<Venda> vendas = servico.listar(periodo);
            contagem.setText(periodo.toString());
            montarTotais(vendas);

            tabela.getChildren().add(montarCabecalho());
            for (Venda v : vendas) {
                tabela.getChildren().add(montarLinha(v));
            }

            if (vendas.isEmpty()) {
                Label vazio = new Label("Nenhuma venda registrada neste período.");
                vazio.getStyleClass().add("pagina-sub");
                vazio.setPadding(new Insets(14, 0, 0, 0));
                tabela.getChildren().add(vazio);
            }
        } catch (CardapioException e) {
            contagem.setText("");
            Label erro = new Label(e.getMessage());
            erro.getStyleClass().add("mensagem-erro");
            erro.setWrapText(true);
            tabela.getChildren().add(erro);
        }
    }

    /**
     * Monta a faixa de totais do período.
     *
     * @param vendas as vendas carregadas
     */
    private void montarTotais(List<Venda> vendas) {
        BigDecimal faturamento = BigDecimal.ZERO;
        BigDecimal custo = BigDecimal.ZERO;
        int unidades = 0;

        for (Venda v : vendas) {
            faturamento = faturamento.add(v.getTotal());
            custo = custo.add(v.getCustoTotal());
            unidades += v.getQuantidade();
        }

        BigDecimal ticket = unidades == 0
                ? BigDecimal.ZERO
                : faturamento.divide(BigDecimal.valueOf(unidades), 2, RoundingMode.HALF_UP);

        totais.getChildren().addAll(
                cartaoTotal("Faturamento", Formatos.dinheiro(faturamento),
                        unidades + " unidades vendidas"),
                cartaoTotal("Custo dos insumos", Formatos.dinheiro(custo),
                        "pela ficha técnica"),
                cartaoTotal("Margem bruta", Formatos.dinheiro(faturamento.subtract(custo)),
                        "antes de aluguel e folha"),
                cartaoTotal("Ticket médio", Formatos.dinheiro(ticket),
                        "por unidade vendida"));
    }

    /**
     * Cria um cartão da faixa de totais.
     *
     * @param rotulo  o nome do total
     * @param valor   o número em destaque
     * @param legenda a linha explicativa
     * @return o cartão montado
     */
    private VBox cartaoTotal(String rotulo, String valor, String legenda) {
        Label r = new Label(rotulo.toUpperCase());
        r.getStyleClass().add("kpi-rotulo");
        Label v = new Label(valor);
        v.getStyleClass().add("kpi-valor");
        Label l = new Label(legenda);
        l.getStyleClass().add("kpi-legenda");
        l.setWrapText(true);

        VBox cartao = new VBox(3, r, v, l);
        cartao.getStyleClass().addAll("kpi-cartao", "kpi-verde");
        cartao.setPadding(new Insets(14, 16, 14, 16));
        cartao.setPrefWidth(230);
        HBox.setHgrow(cartao, Priority.ALWAYS);
        return cartao;
    }

    /**
     * Monta a linha de cabeçalho da tabela.
     *
     * @return o cabeçalho montado
     */
    private HBox montarCabecalho() {
        HBox linha = new HBox(
                celula("Data", 100),
                celula("Produto", 220),
                celula("Qtd", 60),
                celula("Preço unit.", 110),
                celula("Total", 110),
                celula("Custo", 110),
                celula("Margem", 110),
                celula("", 44));
        linha.getStyleClass().add("tabela-cabecalho");
        linha.setPadding(new Insets(10, 12, 10, 12));
        return linha;
    }

    /**
     * Monta uma linha da tabela de vendas.
     *
     * @param v a venda a exibir
     * @return a linha montada
     */
    private HBox montarLinha(Venda v) {
        Button excluir = new Button();
        excluir.getStyleClass().addAll("botao-mini", "botao-mini-perigo");
        excluir.setGraphic(Icones.criar("excluir", 12, COR_ICONE));
        excluir.setOnAction(e -> confirmarExclusao(v));

        HBox acao = new HBox(excluir);
        acao.setPrefWidth(44);
        acao.setAlignment(Pos.CENTER_LEFT);

        HBox linha = new HBox(
                celula(Formatos.data(v.getData()), 100),
                celula(v.getNomeReceita(), 220),
                celula(String.valueOf(v.getQuantidade()), 60),
                celula(Formatos.dinheiro(v.getPrecoUnitario()), 110),
                celula(Formatos.dinheiro(v.getTotal()), 110),
                celula(Formatos.dinheiro(v.getCustoTotal()), 110),
                celula(Formatos.dinheiro(v.getMargemTotal()), 110),
                acao);
        linha.getStyleClass().add("tabela-linha");
        linha.setAlignment(Pos.CENTER_LEFT);
        linha.setPadding(new Insets(9, 12, 9, 12));
        return linha;
    }

    /**
     * Cria uma célula de largura fixa para a tabela.
     *
     * @param texto  o conteúdo
     * @param largura a largura em pixels
     * @return o rótulo dimensionado
     */
    private Label celula(String texto, double largura) {
        Label l = new Label(texto == null ? "" : texto);
        l.setPrefWidth(largura);
        l.setMinWidth(largura);
        return l;
    }

    /**
     * Abre o formulário de registro de venda.
     */
    private void abrirFormulario() {
        List<Receita> produtos = new ArrayList<>();
        try {
            for (Receita r : cardapio.listar()) {
                if (r.isAtivo()) {
                    produtos.add(r);
                }
            }
        } catch (CardapioException e) {
            Dialogos.erro(e.getMessage());
            return;
        }

        if (produtos.isEmpty()) {
            Dialogos.erro("Nenhum produto ativo no cardápio. Cadastre um produto na "
                    + "Ficha Técnica antes de registrar vendas.");
            return;
        }

        ChoiceBox<Receita> campoProduto = new ChoiceBox<>();
        campoProduto.getItems().addAll(produtos);
        campoProduto.setValue(produtos.get(0));
        campoProduto.setPrefWidth(320);

        TextField campoQtd = new TextField("1");
        TextField campoPreco = new TextField(Formatos.paraCampo(produtos.get(0).getPrecoVenda()));
        DatePicker campoData = new DatePicker(LocalDate.now());
        TextField campoObs = new TextField();
        campoObs.setPromptText("Movimento da tarde");

        Label previa = new Label();
        previa.getStyleClass().add("insumo-valor");
        previa.setWrapText(true);

        Runnable recalcular = () -> {
            Receita r = campoProduto.getValue();
            if (r == null) {
                previa.setText("");
                return;
            }
            previa.setText(r.temFicha()
                    ? "Custo pela ficha: " + Formatos.dinheiro(r.getCusto())
                      + "  ·  margem por unidade: " + Formatos.dinheiro(r.getMargem())
                    : "Este produto não tem ficha técnica, então a venda entra sem custo "
                      + "e a margem sai inflada.");
        };
        recalcular.run();

        campoProduto.valueProperty().addListener((o, a, b) -> {
            if (b != null) {
                campoPreco.setText(Formatos.paraCampo(b.getPrecoVenda()));
            }
            recalcular.run();
        });

        VBox corpo = new VBox(10,
                rotulado("Produto", campoProduto),
                rotulado("Quantidade vendida", campoQtd),
                rotulado("Preço praticado por unidade (R$)", campoPreco),
                rotulado("Data", campoData),
                rotulado("Observação", campoObs),
                previa);
        corpo.setPadding(new Insets(4, 2, 4, 2));

        Dialog<ButtonType> dialogo = Dialogos.criar("Registrar venda",
                "Lançar o que foi vendido", corpo);

        if (dialogo.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        try {
            Venda venda = new Venda();
            venda.setReceitaId(campoProduto.getValue().getId());
            venda.setQuantidade(lerInteiro(campoQtd.getText()));
            venda.setPrecoUnitario(lerDecimal(campoPreco.getText(), "preço praticado"));
            venda.setData(campoData.getValue());
            venda.setObservacao(campoObs.getText());

            servico.registrar(venda);
            atualizar();
            aoMudarVendas.run();
        } catch (CardapioException e) {
            Dialogos.erro(e.getMessage());
        }
    }

    /**
     * Pede confirmação antes de remover um lançamento.
     *
     * @param v a venda a remover
     */
    private void confirmarExclusao(Venda v) {
        Alert confirma = new Alert(Alert.AlertType.CONFIRMATION);
        confirma.setTitle("Remover lançamento");
        confirma.setHeaderText("Remover a venda de " + v.getQuantidade() + "x "
                + v.getNomeReceita() + "?");
        confirma.setContentText("O estoque não é afetado — a baixa dos insumos é "
                + "registrada pela balança, num caminho separado.");

        if (confirma.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        try {
            servico.excluir(v);
            atualizar();
            aoMudarVendas.run();
        } catch (CardapioException e) {
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
     * Converte o texto digitado em número inteiro.
     *
     * @param texto o conteúdo do campo
     * @return a quantidade
     * @throws CardapioException se o texto não for um inteiro válido
     */
    private int lerInteiro(String texto) throws CardapioException {
        try {
            return Integer.parseInt(texto.trim());
        } catch (RuntimeException e) {
            throw new CardapioException("A quantidade precisa ser um número inteiro.");
        }
    }

    /**
     * Converte o texto digitado em número, aceitando vírgula como separador.
     *
     * @param texto o conteúdo do campo
     * @param campo o nome do campo, usado na mensagem de erro
     * @return o valor convertido
     * @throws CardapioException se o texto não for um número válido
     */
    private BigDecimal lerDecimal(String texto, String campo) throws CardapioException {
        if (texto == null || texto.isBlank()) {
            throw new CardapioException("Preencha o campo " + campo + ".");
        }
        try {
            return Formatos.lerNumero(texto);
        } catch (NumberFormatException e) {
            throw new CardapioException("O valor de " + campo + " não é um número válido.");
        }
    }
}
