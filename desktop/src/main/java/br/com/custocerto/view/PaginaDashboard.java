package br.com.custocerto.view;

import br.com.custocerto.model.Ingrediente;
import br.com.custocerto.model.ResumoEstoque;
import br.com.custocerto.service.EstoqueException;
import br.com.custocerto.service.EstoqueService;
import java.util.List;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcType;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

/**
 * Página de indicadores, equivalente ao Dashboard do painel web.
 *
 * <p>Traz os quatro cartões de indicador (valor em estoque, itens em alerta, CMV
 * estimado e itens vencendo), a composição do estoque por valor, o status dos
 * ingredientes e o medidor de CMV. Os números vêm de {@link ResumoEstoque}, que
 * aplica exatamente as mesmas fórmulas do painel web.</p>
 *
 * @author Equipe Custo Certo - FATEC Indaiatuba
 * @version 1.0
 */
public class PaginaDashboard extends VBox {

    private final EstoqueService servico;
    private final FlowPane indicadores = new FlowPane(14, 14);
    private final VBox graficos = new VBox(14);

    /**
     * Monta a página de indicadores.
     *
     * @param servico o serviço que fala com o banco
     */
    public PaginaDashboard(EstoqueService servico) {
        this.servico = servico;
        setSpacing(16);

        Label titulo = new Label("Painel de Indicadores");
        titulo.getStyleClass().add("pagina-titulo");
        Label sub = new Label("Visão consolidada do estoque e do custo de mercadoria vendida");
        sub.getStyleClass().add("pagina-sub");

        ScrollPane rolagem = new ScrollPane(graficos);
        rolagem.setFitToWidth(true);
        rolagem.getStyleClass().add("rolagem");
        VBox.setVgrow(rolagem, Priority.ALWAYS);

        getChildren().addAll(new VBox(2, titulo, sub), indicadores, rolagem);
        atualizar();
    }

    /**
     * Recarrega os dados do banco e redesenha os indicadores e gráficos.
     */
    public final void atualizar() {
        indicadores.getChildren().clear();
        graficos.getChildren().clear();

        try {
            List<Ingrediente> lista = servico.listarIngredientes();
            ResumoEstoque resumo = new ResumoEstoque(lista);

            indicadores.getChildren().addAll(
                    criarIndicador("Valor Total em Estoque",
                            Formatos.dinheiro(resumo.getValorEmEstoque()),
                            resumo.getTotalItens() + " itens cadastrados",
                            "kpi-verde", "dinheiro", "#00a86b"),
                    criarIndicador("Itens em Alerta",
                            String.valueOf(resumo.getItensEmAlerta()),
                            resumo.getItensEmAlerta() > 0
                                    ? resumo.getItensEmAlerta() + " abaixo de 25% do estoque"
                                    : "Todos dentro do limite",
                            "kpi-vermelho", "alerta", "#e63946"),
                    criarIndicador("CMV Estimado",
                            Formatos.percentual(resumo.getPercentualCmv()),
                            "Meta: 28–31% da receita",
                            "kpi-azul", "evolucao", "#3a86ff"),
                    criarIndicador("Vencendo em 5 dias",
                            String.valueOf(resumo.getItensVencendo() + resumo.getItensVencidos()),
                            resumo.getItensVencidos() > 0
                                    ? resumo.getItensVencidos() + " já vencido(s)"
                                    : "itens próximos ao vencimento",
                            "kpi-laranja", "relogio", "#f4a435"));

            HBox linha1 = new HBox(14,
                    criarCartaoGrafico("Composição do Estoque",
                            Formatos.dinheiro(resumo.getValorEmEstoque()) + " distribuídos entre "
                                    + resumo.getTotalItens() + " insumos",
                            graficoComposicao(lista)),
                    criarCartaoGrafico("Status dos Ingredientes",
                            "Por nível de estoque e proximidade do vencimento",
                            graficoStatus(lista)));
            linha1.setAlignment(Pos.TOP_LEFT);

            HBox linha2 = new HBox(14,
                    criarCartaoGrafico("CMV — Custo da Mercadoria Vendida",
                            "Custo dos insumos sobre a receita projetada",
                            medidorCmv(resumo)),
                    criarCartaoGrafico("Rotatividade de Estoque",
                            "Quanto resta da capacidade de cada insumo",
                            barrasRotatividade(lista)));
            linha2.setAlignment(Pos.TOP_LEFT);

            graficos.getChildren().addAll(linha1, linha2);

        } catch (EstoqueException e) {
            Label erro = new Label(e.getMessage());
            erro.getStyleClass().add("mensagem-erro");
            erro.setWrapText(true);
            graficos.getChildren().add(erro);
        }
    }

    /**
     * Cria um cartão de indicador, com rótulo, número e legenda.
     *
     * @param rotulo   o nome do indicador
     * @param valor    o número em destaque
     * @param legenda  a linha explicativa abaixo
     * @param cor      a classe de estilo que define a cor da faixa lateral
     * @param icone    a chave do ícone em {@link Icones}
     * @param corIcone a cor do ícone, em hexadecimal, também usada no fundo da marca
     * @return o cartão montado
     */
    private VBox criarIndicador(String rotulo, String valor, String legenda,
                                String cor, String icone, String corIcone) {
        Label r = new Label(rotulo.toUpperCase());
        r.getStyleClass().add("kpi-rotulo");

        Region esp = new Region();
        HBox.setHgrow(esp, Priority.ALWAYS);

        StackPane marca = new StackPane(
                Icones.criar(icone, 15, javafx.scene.paint.Color.web(corIcone)));
        marca.getStyleClass().add("kpi-icone");
        marca.setStyle("-fx-background-color: " + corIcone + "1F;");
        marca.setMinSize(30, 30);
        marca.setPrefSize(30, 30);
        marca.setMaxSize(30, 30);

        HBox topo = new HBox(8, r, esp, marca);
        topo.setAlignment(Pos.CENTER_LEFT);

        Label v = new Label(valor);
        v.getStyleClass().add("kpi-valor");
        Label l = new Label(legenda);
        l.getStyleClass().add("kpi-legenda");
        l.setWrapText(true);

        VBox cartao = new VBox(3, topo, v, l);
        cartao.getStyleClass().addAll("kpi-cartao", cor);
        cartao.setPadding(new Insets(16, 18, 16, 18));
        cartao.setPrefWidth(258);
        cartao.setMinHeight(118);
        return cartao;
    }

    /**
     * Envolve um gráfico em um cartão com título e legenda.
     *
     * @param titulo  o título do cartão
     * @param legenda a linha de contexto abaixo do título
     * @param corpo   o gráfico
     * @return o cartão montado
     */
    private VBox criarCartaoGrafico(String titulo, String legenda, javafx.scene.Node corpo) {
        Label t = new Label(titulo);
        t.getStyleClass().add("cartao-titulo");

        Label l = new Label(legenda);
        l.getStyleClass().add("cartao-legenda");
        l.setWrapText(true);

        VBox cabecalho = new VBox(2, t, l);

        VBox cartao = new VBox(12, cabecalho, corpo);
        cartao.getStyleClass().add("cartao-painel");
        cartao.setPadding(new Insets(18));
        cartao.setPrefWidth(430);
        HBox.setHgrow(cartao, Priority.ALWAYS);
        return cartao;
    }

    /**
     * Monta a pizza de composição do estoque pelo valor de cada insumo.
     *
     * @param lista os insumos cadastrados
     * @return o gráfico pronto
     */
    private PieChart graficoComposicao(List<Ingrediente> lista) {
        PieChart pizza = new PieChart();
        for (Ingrediente i : lista) {
            if (i.getValorEmEstoque().signum() > 0) {
                pizza.getData().add(new PieChart.Data(
                        i.getNome(), i.getValorEmEstoque().doubleValue()));
            }
        }
        pizza.setLegendVisible(true);
        pizza.setLegendSide(javafx.geometry.Side.BOTTOM);
        pizza.setLabelsVisible(false);
        pizza.setStartAngle(90);
        pizza.setAnimated(false);
        pizza.setPrefHeight(250);
        return pizza;
    }

    /**
     * Monta a pizza de status: quantos insumos estão bem, em atenção ou críticos.
     *
     * @param lista os insumos cadastrados
     * @return o gráfico pronto
     */
    private PieChart graficoStatus(List<Ingrediente> lista) {
        int ok = 0;
        int atencao = 0;
        int critico = 0;
        for (Ingrediente i : lista) {
            double pct = i.getPercentualEstoque();
            if (i.isVencido() || pct < 25) {
                critico++;
            } else if (i.isVencimentoProximo() || pct <= 50) {
                atencao++;
            } else {
                ok++;
            }
        }

        PieChart pizza = new PieChart();
        if (ok > 0) {
            pizza.getData().add(new PieChart.Data("Adequado (" + ok + ")", ok));
        }
        if (atencao > 0) {
            pizza.getData().add(new PieChart.Data("Atenção (" + atencao + ")", atencao));
        }
        if (critico > 0) {
            pizza.getData().add(new PieChart.Data("Crítico (" + critico + ")", critico));
        }
        pizza.setLabelsVisible(false);
        pizza.setLegendSide(javafx.geometry.Side.BOTTOM);
        pizza.setStartAngle(90);
        pizza.setAnimated(false);
        pizza.setPrefHeight(250);
        return pizza;
    }

    /**
     * Desenha o medidor de CMV e a conta que leva até ele.
     *
     * @param resumo os indicadores calculados
     * @return a caixa com o medidor e os valores
     */
    private HBox medidorCmv(ResumoEstoque resumo) {
        Canvas canvas = new Canvas(170, 150);
        GraphicsContext g = canvas.getGraphicsContext2D();

        double pct = Math.min(100, resumo.getPercentualCmv());
        Color cor = switch (resumo.getClassificacaoCmv()) {
            case "ok" -> Color.web("#00a86b");
            case "warn" -> Color.web("#f4a435");
            default -> Color.web("#e63946");
        };

        g.setLineWidth(16);
        g.setStroke(Color.web("#e8eaf0"));
        g.strokeArc(20, 20, 130, 130, 180, -180, ArcType.OPEN);

        g.setStroke(cor);
        g.strokeArc(20, 20, 130, 130, 180, -(180 * pct / 100.0), ArcType.OPEN);

        g.setFill(cor);
        g.setFont(Font.font("Segoe UI", FontWeight.BOLD, 26));
        g.setTextAlign(TextAlignment.CENTER);
        g.fillText(Formatos.percentual(resumo.getPercentualCmv()), 85, 100);

        g.setFill(Color.web("#8892a4"));
        g.setFont(Font.font("Segoe UI", 11));
        g.fillText(resumo.getRotuloCmv(), 85, 122);

        VBox valores = new VBox(8,
                linhaValor("Custo dos insumos", Formatos.dinheiro(resumo.getValorEmEstoque())),
                linhaValor("Receita estimada", Formatos.dinheiro(resumo.getReceitaEstimada())),
                linhaValor("CMV", Formatos.percentual(resumo.getPercentualCmv())),
                linhaValor("Faixa saudável", "28% a 31%"));
        valores.setAlignment(Pos.CENTER_LEFT);

        HBox caixa = new HBox(18, canvas, valores);
        caixa.setAlignment(Pos.CENTER_LEFT);
        caixa.setPrefHeight(160);
        return caixa;
    }

    /**
     * Monta uma linha de rótulo e valor para o quadro do CMV.
     *
     * @param rotulo o nome do valor
     * @param valor  o valor formatado
     * @return a linha montada
     */
    private VBox linhaValor(String rotulo, String valor) {
        Label r = new Label(rotulo);
        r.getStyleClass().add("mini-rotulo");
        Label v = new Label(valor);
        v.getStyleClass().add("mini-valor");
        return new VBox(1, r, v);
    }

    /**
     * Monta a lista de barras de nível, uma por insumo.
     *
     * @param lista os insumos cadastrados
     * @return a caixa com as barras
     */
    private VBox barrasRotatividade(List<Ingrediente> lista) {
        VBox caixa = new VBox(10);
        for (Ingrediente i : lista) {
            double pct = i.getPercentualEstoque();

            Label nome = new Label(i.getNome());
            nome.getStyleClass().add("mini-rotulo");
            Label valor = new Label(Math.round(pct) + "%");
            valor.getStyleClass().add("mini-valor");

            Region esp = new Region();
            HBox.setHgrow(esp, Priority.ALWAYS);
            HBox topo = new HBox(6, nome, esp, valor);

            Region trilho = new Region();
            trilho.getStyleClass().add("barra-fundo");
            Region cheio = new Region();
            cheio.getStyleClass().addAll("barra-nivel",
                    pct > 50 ? "barra-ok" : pct > 25 ? "barra-media" : "barra-baixa");
            cheio.prefWidthProperty().bind(trilho.widthProperty().multiply(pct / 100.0));

            StackPane barra = new StackPane(trilho, cheio);
            StackPane.setAlignment(cheio, Pos.CENTER_LEFT);

            caixa.getChildren().add(new VBox(3, topo, barra));
        }
        return caixa;
    }
}
