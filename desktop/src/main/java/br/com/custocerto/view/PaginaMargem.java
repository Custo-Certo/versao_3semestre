package br.com.custocerto.view;

import br.com.custocerto.model.DesempenhoProduto;
import br.com.custocerto.model.Periodo;
import br.com.custocerto.model.VarianciaInsumo;
import br.com.custocerto.service.AnaliseService;
import br.com.custocerto.service.CardapioException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Página de margem e desperdício — onde o dinheiro está sendo feito e perdido.
 *
 * <p>Reúne as duas análises que o sistema passou a conseguir fazer depois que
 * ganhou ficha técnica e vendas:</p>
 *
 * <ul>
 *   <li><b>Engenharia de cardápio</b> — cada produto cai num de quatro quadrantes,
 *       cruzando margem de contribuição com popularidade, e cada quadrante tem uma
 *       ação clara. É uma técnica antiga da gestão de restaurantes, e o ganho de
 *       quem a aplica costuma vir de reorganizar o que já existe, sem vender uma
 *       unidade a mais.</li>
 *   <li><b>Variância teórico × real</b> — quanto os insumos <em>deveriam</em> ter
 *       saído, segundo as vendas e as fichas, contra quanto de fato saiu pela
 *       balança. A diferença é dose exagerada, desperdício ou perda.</li>
 * </ul>
 *
 * <p>A variância é o indicador que justifica a balança existir. A maioria dos
 * sistemas de gestão só consegue estimar o consumo, porque não mede nada — este
 * mede, e por isso consegue apontar o problema insumo por insumo, em reais.</p>
 *
 * @author Equipe Custo Certo - FATEC Indaiatuba
 * @version 1.0
 */
public class PaginaMargem extends VBox {

    private final AnaliseService servico;
    private final ChoiceBox<OpcaoPeriodo> filtro = new ChoiceBox<>();
    private final VBox conteudo = new VBox(16);
    private final Label subtitulo = new Label();

    /**
     * Uma opção do seletor de período.
     *
     * @param rotulo  o texto exibido no seletor
     * @param periodo o intervalo correspondente
     */
    private record OpcaoPeriodo(String rotulo, Periodo periodo) {
        @Override
        public String toString() {
            return rotulo;
        }
    }

    /**
     * Monta a página de margem e desperdício.
     *
     * @param servico o serviço de análise
     */
    public PaginaMargem(AnaliseService servico) {
        this.servico = servico;
        setSpacing(16);

        Label titulo = new Label("Margem e Desperdício");
        titulo.getStyleClass().add("pagina-titulo");
        subtitulo.getStyleClass().add("pagina-sub");

        VBox textos = new VBox(2, titulo, subtitulo);

        filtro.getItems().addAll(
                new OpcaoPeriodo("Este mês", Periodo.mesAtual()),
                new OpcaoPeriodo("Últimos 30 dias", Periodo.ultimosDias(30)),
                new OpcaoPeriodo("Últimos 90 dias", Periodo.ultimosDias(90)));
        filtro.setValue(filtro.getItems().get(2));
        filtro.setOnAction(e -> atualizar());

        Region espaco = new Region();
        HBox.setHgrow(espaco, Priority.ALWAYS);

        HBox cabecalho = new HBox(12, textos, espaco, filtro);
        cabecalho.setAlignment(Pos.CENTER_LEFT);

        ScrollPane rolagem = new ScrollPane(conteudo);
        rolagem.setFitToWidth(true);
        rolagem.getStyleClass().add("rolagem");
        VBox.setVgrow(rolagem, Priority.ALWAYS);

        getChildren().addAll(cabecalho, rolagem);
        atualizar();
    }

    /**
     * Recarrega as análises do período escolhido e redesenha a tela.
     */
    public final void atualizar() {
        conteudo.getChildren().clear();
        Periodo periodo = filtro.getValue().periodo();
        subtitulo.setText(periodo.toString());

        try {
            List<DesempenhoProduto> produtos = servico.desempenhoDoCardapio(periodo);
            List<VarianciaInsumo> variancias = servico.variancias(periodo);

            conteudo.getChildren().addAll(
                    montarQuadrantes(produtos),
                    montarTabelaProdutos(produtos),
                    montarVariancia(variancias));

        } catch (CardapioException e) {
            Label erro = new Label(e.getMessage());
            erro.getStyleClass().add("mensagem-erro");
            erro.setWrapText(true);
            conteudo.getChildren().add(erro);
        }
    }

    // ---------------------------------------------------------------- cardápio

    /**
     * Monta o quadro com os quatro quadrantes da engenharia de cardápio.
     *
     * @param produtos os produtos analisados
     * @return o cartão montado
     */
    private VBox montarQuadrantes(List<DesempenhoProduto> produtos) {
        Map<DesempenhoProduto.Quadrante, List<DesempenhoProduto>> porQuadrante =
                new EnumMap<>(DesempenhoProduto.Quadrante.class);
        List<DesempenhoProduto> foraDaAnalise = new ArrayList<>();

        for (DesempenhoProduto p : produtos) {
            if (p.getUnidadesVendidas() == 0 || !p.isTemFicha()) {
                foraDaAnalise.add(p);
            } else {
                porQuadrante.computeIfAbsent(p.getQuadrante(), q -> new ArrayList<>()).add(p);
            }
        }

        HBox linha1 = new HBox(14,
                caixaQuadrante(DesempenhoProduto.Quadrante.ESTRELA, porQuadrante),
                caixaQuadrante(DesempenhoProduto.Quadrante.CAVALO, porQuadrante));
        HBox linha2 = new HBox(14,
                caixaQuadrante(DesempenhoProduto.Quadrante.ENIGMA, porQuadrante),
                caixaQuadrante(DesempenhoProduto.Quadrante.ABACAXI, porQuadrante));

        VBox corpo = new VBox(14, linha1, linha2);

        if (!foraDaAnalise.isEmpty()) {
            StringBuilder nomes = new StringBuilder();
            for (DesempenhoProduto p : foraDaAnalise) {
                if (nomes.length() > 0) {
                    nomes.append(", ");
                }
                nomes.append(p.getNome());
                nomes.append(p.isTemFicha() ? " (sem venda)" : " (sem ficha)");
            }

            Label aviso = new Label("Fora da classificação: " + nomes
                    + ". Produto sem venda não tem popularidade a medir, e produto sem "
                    + "ficha técnica teria custo zero — entraria como margem máxima e "
                    + "distorceria a média de todos os outros.");
            aviso.getStyleClass().add("faixa-alerta-item");
            aviso.setWrapText(true);
            corpo.getChildren().add(aviso);
        }

        return cartao("Engenharia de Cardápio",
                "Cada produto comparado com a média do próprio cardápio no período",
                corpo);
    }

    /**
     * Monta a caixa de um quadrante, com os produtos que caíram nele.
     *
     * @param quadrante   o quadrante a exibir
     * @param porQuadrante o agrupamento calculado
     * @return a caixa montada
     */
    private VBox caixaQuadrante(DesempenhoProduto.Quadrante quadrante,
                                Map<DesempenhoProduto.Quadrante, List<DesempenhoProduto>> porQuadrante) {

        Label titulo = new Label(quadrante.getTitulo());
        titulo.getStyleClass().add("cartao-titulo");

        Label eixos = new Label(quadrante.getEixos());
        eixos.getStyleClass().add("mini-rotulo");

        VBox caixa = new VBox(6, titulo, eixos);

        List<DesempenhoProduto> lista = porQuadrante.get(quadrante);
        if (lista == null || lista.isEmpty()) {
            Label vazio = new Label("nenhum produto aqui");
            vazio.getStyleClass().add("insumo-pct");
            caixa.getChildren().add(vazio);
        } else {
            for (DesempenhoProduto p : lista) {
                Label nome = new Label(p.getNome());
                nome.getStyleClass().add("insumo-nome");

                Region esp = new Region();
                HBox.setHgrow(esp, Priority.ALWAYS);

                Label numeros = new Label(p.getUnidadesVendidas() + " un  ·  "
                        + Formatos.dinheiro(p.getMargemTotal()));
                numeros.getStyleClass().add("mini-valor");

                HBox linha = new HBox(8, nome, esp, numeros);
                linha.setAlignment(Pos.CENTER_LEFT);
                caixa.getChildren().add(linha);
            }
        }

        Label acao = new Label(quadrante.getAcao());
        acao.getStyleClass().add("insumo-pct");
        acao.setWrapText(true);
        caixa.getChildren().add(acao);

        caixa.getStyleClass().add("cartao-insumo");
        caixa.setPadding(new Insets(14));
        HBox.setHgrow(caixa, Priority.ALWAYS);
        caixa.setPrefWidth(400);
        return caixa;
    }

    /**
     * Monta a tabela detalhada de desempenho por produto.
     *
     * @param produtos os produtos analisados
     * @return o cartão montado
     */
    private VBox montarTabelaProdutos(List<DesempenhoProduto> produtos) {
        VBox tabela = new VBox(0);
        tabela.getChildren().add(linhaTabela(true,
                "Produto", "Vendidas", "Preço médio", "Custo", "Margem un.",
                "Margem total", "Custo %"));

        for (DesempenhoProduto p : produtos) {
            tabela.getChildren().add(linhaTabela(false,
                    p.getNome(),
                    String.valueOf(p.getUnidadesVendidas()),
                    Formatos.dinheiro(p.getPrecoMedio()),
                    p.isTemFicha() ? Formatos.dinheiro(p.getCustoUnitario()) : "sem ficha",
                    p.isTemFicha() ? Formatos.dinheiro(p.getMargemUnitaria()) : "—",
                    p.isTemFicha() ? Formatos.dinheiro(p.getMargemTotal()) : "—",
                    p.isTemFicha() ? Formatos.percentual(p.getPercentualCusto()) : "—"));
        }

        return cartao("Desempenho por Produto",
                "Ordenado pelo que mais contribuiu no período", tabela);
    }

    // ---------------------------------------------------------------- variância

    /**
     * Monta o quadro de variância entre consumo teórico e real.
     *
     * @param variancias as variâncias calculadas
     * @return o cartão montado
     */
    private VBox montarVariancia(List<VarianciaInsumo> variancias) {
        VBox corpo = new VBox(12);

        List<VarianciaInsumo> comparaveis = new ArrayList<>();
        List<VarianciaInsumo> semBaixa = new ArrayList<>();

        for (VarianciaInsumo v : variancias) {
            if (v.isSemBaixaRegistrada()) {
                semBaixa.add(v);
            } else {
                comparaveis.add(v);
            }
        }

        if (comparaveis.isEmpty()) {
            Label vazio = new Label(
                    "Ainda não há período com vendas e baixas de estoque ao mesmo tempo. "
                    + "A comparação precisa dos dois lados.");
            vazio.getStyleClass().add("pagina-sub");
            vazio.setWrapText(true);
            corpo.getChildren().add(vazio);
        } else {
            BigDecimal perdaTotal = BigDecimal.ZERO;
            for (VarianciaInsumo v : comparaveis) {
                if (v.getCustoDiferenca().signum() > 0) {
                    perdaTotal = perdaTotal.add(v.getCustoDiferenca());
                }
            }

            Label resumo = new Label("Saiu do estoque além do que as vendas pediam: "
                    + Formatos.dinheiro(perdaTotal) + " no período.");
            resumo.getStyleClass().add("insumo-valor");
            resumo.setWrapText(true);
            corpo.getChildren().add(resumo);

            VBox tabela = new VBox(0);
            tabela.getChildren().add(linhaTabela(true,
                    "Insumo", "Teórico", "Real", "Diferença", "Variação", "Custo", "Leitura"));

            for (VarianciaInsumo v : comparaveis) {
                String sigla = v.getUnidade() == null ? "" : " " + v.getUnidade().getSigla();
                tabela.getChildren().add(linhaVariancia(v, sigla));
            }
            corpo.getChildren().add(tabela);

            Label referencia = new Label(
                    "Referência do setor: variação até 2% indica operação bem controlada; "
                    + "de 2% a 5% é o comum; acima disso vale investigar dose, desperdício "
                    + "ou perda.");
            referencia.getStyleClass().add("insumo-pct");
            referencia.setWrapText(true);
            corpo.getChildren().add(referencia);
        }

        if (!semBaixa.isEmpty()) {
            StringBuilder nomes = new StringBuilder();
            for (VarianciaInsumo v : semBaixa) {
                if (nomes.length() > 0) {
                    nomes.append(", ");
                }
                nomes.append(v.getNome());
            }

            Label aviso = new Label("Sem consumo real para comparar: " + nomes
                    + ". Estes insumos entram nas fichas técnicas mas não passam pela "
                    + "balança, então o sistema não sabe quanto realmente saiu — e não "
                    + "seria honesto exibir 100% de variação para eles.");
            aviso.getStyleClass().add("faixa-alerta-item");
            aviso.setWrapText(true);
            corpo.getChildren().add(aviso);
        }

        return cartao("Consumo Teórico × Real",
                "O que as vendas pediam, contra o que a balança pesou", corpo);
    }

    /**
     * Monta a linha da tabela de variância, com a etiqueta de classificação.
     *
     * @param v     a variância a exibir
     * @param sigla a unidade do insumo, já com espaço à frente
     * @return a linha montada
     */
    private HBox linhaVariancia(VarianciaInsumo v, String sigla) {
        Label etiqueta = new Label(switch (v.getClassificacao()) {
            case "bom" -> "controlado";
            case "tipico" -> "aceitável";
            default -> "investigar";
        });
        etiqueta.getStyleClass().addAll("etiqueta", switch (v.getClassificacao()) {
            case "bom" -> "etiqueta-ok";
            case "tipico" -> "etiqueta-aviso";
            default -> "etiqueta-vencido";
        });

        HBox linha = new HBox(
                celula(v.getNome(), 210),
                celula(Formatos.quantidade(v.getTeorico()) + sigla, 110),
                celula(Formatos.quantidade(v.getReal()) + sigla, 110),
                celula(String.format("%+.3f", v.getDiferenca().doubleValue()) + sigla, 110),
                celula(String.format("%+.1f%%", v.getPercentual()), 90),
                celula(Formatos.dinheiro(v.getCustoDiferenca()), 100),
                etiqueta);
        linha.getStyleClass().add("tabela-linha");
        linha.setAlignment(Pos.CENTER_LEFT);
        linha.setPadding(new Insets(9, 12, 9, 12));
        return linha;
    }

    // ---------------------------------------------------------------- auxiliares

    /**
     * Monta uma linha de tabela com larguras fixas.
     *
     * @param cabecalho {@code true} para aplicar o estilo de cabeçalho
     * @param textos    o conteúdo de cada célula
     * @return a linha montada
     */
    private HBox linhaTabela(boolean cabecalho, String... textos) {
        double[] larguras = {210, 110, 110, 110, 90, 100, 120};

        HBox linha = new HBox();
        for (int i = 0; i < textos.length; i++) {
            linha.getChildren().add(celula(textos[i],
                    i < larguras.length ? larguras[i] : 100));
        }
        linha.getStyleClass().add(cabecalho ? "tabela-cabecalho" : "tabela-linha");
        linha.setAlignment(Pos.CENTER_LEFT);
        linha.setPadding(new Insets(cabecalho ? 10 : 9, 12, cabecalho ? 10 : 9, 12));
        return linha;
    }

    /**
     * Cria uma célula de largura fixa.
     *
     * @param texto   o conteúdo
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
     * Envolve um conteúdo em um cartão com título e legenda.
     *
     * @param titulo  o título do cartão
     * @param legenda a linha de contexto
     * @param corpo   o conteúdo
     * @return o cartão montado
     */
    private VBox cartao(String titulo, String legenda, javafx.scene.Node corpo) {
        Label t = new Label(titulo);
        t.getStyleClass().add("cartao-titulo");
        Label l = new Label(legenda);
        l.getStyleClass().add("cartao-legenda");
        l.setWrapText(true);

        VBox caixa = new VBox(12, new VBox(2, t, l), corpo);
        caixa.getStyleClass().add("cartao-painel");
        caixa.setPadding(new Insets(18));
        return caixa;
    }
}
