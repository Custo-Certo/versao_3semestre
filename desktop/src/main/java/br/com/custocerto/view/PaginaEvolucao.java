package br.com.custocerto.view;

import br.com.custocerto.model.Movimentacao;
import br.com.custocerto.model.TipoMovimentacao;
import br.com.custocerto.service.EstoqueException;
import br.com.custocerto.service.EstoqueService;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.beans.property.SimpleStringProperty;

/**
 * Página de evolução de preços, equivalente à tela homônima do painel web.
 *
 * <p>Cada ponto do gráfico é uma entrada registrada no sistema, e uma linha por
 * insumo mostra como o custo de aquisição variou ao longo do tempo. É o dado que
 * sustenta a decisão de compra: dá para ver que o café subiu 6% entre julho e
 * agosto sem precisar abrir nota fiscal nenhuma.</p>
 *
 * <p>Abaixo, o histórico de entradas repete a mesma informação em tabela, com o
 * total pago em cada compra.</p>
 *
 * @author Equipe Custo Certo - FATEC Indaiatuba
 * @version 1.0
 */
public class PaginaEvolucao extends VBox {

    /** Formato curto de data usado no eixo horizontal do gráfico. */
    private static final DateTimeFormatter EIXO = DateTimeFormatter.ofPattern("dd/MM");

    private final EstoqueService servico;
    private final VBox areaGrafico = new VBox();
    private final TableView<LinhaHistorico> tabela = new TableView<>();

    /**
     * Monta a página de evolução de preços.
     *
     * @param servico o serviço que fala com o banco
     */
    public PaginaEvolucao(EstoqueService servico) {
        this.servico = servico;
        setSpacing(16);

        Label titulo = new Label("Evolução do Preço por Produto");
        titulo.getStyleClass().add("pagina-titulo");
        Label sub = new Label("Cada ponto é uma entrada no sistema. "
                + "Acompanhe a variação de custo ao longo do tempo.");
        sub.getStyleClass().add("pagina-sub");

        VBox cartaoGrafico = new VBox(10, areaGrafico);
        cartaoGrafico.getStyleClass().add("cartao-painel");
        cartaoGrafico.setPadding(new Insets(16));

        Label tituloTabela = new Label("Histórico de Entradas");
        tituloTabela.getStyleClass().add("cartao-titulo");

        montarTabela();
        VBox cartaoTabela = new VBox(10, tituloTabela, tabela);
        cartaoTabela.getStyleClass().add("cartao-painel");
        cartaoTabela.setPadding(new Insets(16));

        VBox conteudo = new VBox(14, cartaoGrafico, cartaoTabela);
        ScrollPane rolagem = new ScrollPane(conteudo);
        rolagem.setFitToWidth(true);
        rolagem.getStyleClass().add("rolagem");
        VBox.setVgrow(rolagem, Priority.ALWAYS);

        getChildren().addAll(new VBox(2, titulo, sub), rolagem);
        atualizar();
    }

    /**
     * Recarrega o histórico de entradas e redesenha o gráfico e a tabela.
     */
    public final void atualizar() {
        areaGrafico.getChildren().clear();
        tabela.getItems().clear();

        try {
            List<Movimentacao> entradas = servico.listarHistorico(TipoMovimentacao.ENTRADA, 200);

            if (entradas.isEmpty()) {
                Label vazio = new Label("Nenhuma compra registrada ainda.");
                vazio.getStyleClass().add("pagina-sub");
                vazio.setPadding(new Insets(30));
                vazio.setMaxWidth(Double.MAX_VALUE);
                vazio.setAlignment(Pos.CENTER);
                areaGrafico.getChildren().add(vazio);
                return;
            }

            areaGrafico.getChildren().add(montarGrafico(entradas));

            for (Movimentacao m : entradas) {
                tabela.getItems().add(new LinhaHistorico(m));
            }

        } catch (EstoqueException e) {
            Label erro = new Label(e.getMessage());
            erro.getStyleClass().add("mensagem-erro");
            erro.setWrapText(true);
            areaGrafico.getChildren().add(erro);
        }
    }

    /**
     * Monta o gráfico de linhas, uma série por insumo.
     *
     * @param entradas as compras registradas
     * @return o gráfico pronto
     */
    private LineChart<String, Number> montarGrafico(List<Movimentacao> entradas) {
        CategoryAxis eixoX = new CategoryAxis();
        eixoX.setLabel("Data da compra");

        NumberAxis eixoY = new NumberAxis();
        eixoY.setLabel("Preço por unidade (R$)");
        eixoY.setForceZeroInRange(false);

        LineChart<String, Number> grafico = new LineChart<>(eixoX, eixoY);
        grafico.setPrefHeight(320);
        grafico.setCreateSymbols(true);
        grafico.setAnimated(false);

        // Uma série por insumo, com os pontos do mais antigo para o mais recente.
        Map<String, XYChart.Series<String, Number>> series = new LinkedHashMap<>();
        for (int i = entradas.size() - 1; i >= 0; i--) {
            Movimentacao m = entradas.get(i);
            if (m.getPrecoUnitario() == null) {
                continue;
            }
            XYChart.Series<String, Number> serie = series.computeIfAbsent(
                    m.getNomeIngrediente(), nome -> {
                        XYChart.Series<String, Number> s = new XYChart.Series<>();
                        s.setName(nome);
                        return s;
                    });
            serie.getData().add(new XYChart.Data<>(
                    m.getData().format(EIXO), m.getPrecoUnitario()));
        }
        grafico.getData().addAll(series.values());
        return grafico;
    }

    /**
     * Declara as colunas da tabela de histórico.
     */
    private void montarTabela() {
        TableColumn<LinhaHistorico, String> data = new TableColumn<>("Data");
        data.setCellValueFactory(new PropertyValueFactory<>("data"));
        data.setPrefWidth(100);

        TableColumn<LinhaHistorico, String> produto = new TableColumn<>("Produto");
        produto.setCellValueFactory(new PropertyValueFactory<>("produto"));
        produto.setPrefWidth(220);

        TableColumn<LinhaHistorico, String> unidade = new TableColumn<>("Unidade");
        unidade.setCellValueFactory(new PropertyValueFactory<>("unidade"));
        unidade.setPrefWidth(80);

        TableColumn<LinhaHistorico, String> qtd = new TableColumn<>("Qtd comprada");
        qtd.setCellValueFactory(new PropertyValueFactory<>("quantidade"));
        qtd.setPrefWidth(120);

        TableColumn<LinhaHistorico, String> custo = new TableColumn<>("Custo/un (R$)");
        custo.setCellValueFactory(new PropertyValueFactory<>("custoUnitario"));
        custo.setPrefWidth(120);

        TableColumn<LinhaHistorico, String> total = new TableColumn<>("Total (R$)");
        total.setCellValueFactory(new PropertyValueFactory<>("total"));
        total.setPrefWidth(120);

        tabela.getColumns().addAll(List.of(data, produto, unidade, qtd, custo, total));
        tabela.setPrefHeight(280);
        tabela.setPlaceholder(new Label("Nenhuma compra registrada."));
    }

    /**
     * Uma linha da tabela de histórico, já com os valores formatados.
     *
     * <p>O JavaFX preenche as colunas chamando os métodos {@code getX()} desta
     * classe, e por isso ela precisa ser pública.</p>
     */
    public static class LinhaHistorico {

        private final SimpleStringProperty data;
        private final SimpleStringProperty produto;
        private final SimpleStringProperty unidade;
        private final SimpleStringProperty quantidade;
        private final SimpleStringProperty custoUnitario;
        private final SimpleStringProperty total;

        /**
         * Converte uma movimentação em linha de tabela.
         *
         * @param m a movimentação de entrada
         */
        public LinhaHistorico(Movimentacao m) {
            this.data = new SimpleStringProperty(Formatos.data(m.getData()));
            this.produto = new SimpleStringProperty(m.getNomeIngrediente());
            this.unidade = new SimpleStringProperty(m.getUnidade().getSigla());
            this.quantidade = new SimpleStringProperty(Formatos.quantidade(m.getQuantidade()));
            this.custoUnitario = new SimpleStringProperty(Formatos.numero(m.getPrecoUnitario()));
            this.total = new SimpleStringProperty(Formatos.numero(m.getValorTotal()));
        }

        /**
         * Retorna a data formatada.
         *
         * @return a data da compra
         */
        public String getData() {
            return data.get();
        }

        /**
         * Retorna o nome do insumo.
         *
         * @return o produto comprado
         */
        public String getProduto() {
            return produto.get();
        }

        /**
         * Retorna a sigla da unidade.
         *
         * @return a unidade de medida
         */
        public String getUnidade() {
            return unidade.get();
        }

        /**
         * Retorna a quantidade comprada.
         *
         * @return a quantidade formatada
         */
        public String getQuantidade() {
            return quantidade.get();
        }

        /**
         * Retorna o custo por unidade.
         *
         * @return o preço unitário formatado
         */
        public String getCustoUnitario() {
            return custoUnitario.get();
        }

        /**
         * Retorna o valor total da compra.
         *
         * @return o total formatado
         */
        public String getTotal() {
            return total.get();
        }
    }
}
