package br.com.custocerto.view;

import br.com.custocerto.model.IndicadorInsumo;
import br.com.custocerto.model.Ingrediente;
import br.com.custocerto.model.Periodo;
import br.com.custocerto.model.ResumoEstoque;
import br.com.custocerto.model.ResumoFinanceiro;
import br.com.custocerto.service.AnaliseService;
import br.com.custocerto.service.CardapioException;
import br.com.custocerto.service.EstoqueException;
import br.com.custocerto.service.EstoqueService;
import java.util.List;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.PieChart;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/**
 * Painel de indicadores — a primeira tela de quem entra como gestor.
 *
 * <p>Foi reescrito quando o sistema ganhou vendas e ficha técnica. A versão
 * anterior mostrava um "CMV estimado" de 28,6% que <b>nunca mudava</b>: a receita
 * era inventada multiplicando o valor em estoque por 3,5, e o CMV era o valor em
 * estoque dividido por essa receita — a mesma grandeza dos dois lados da divisão,
 * resultado sempre igual a 1 ÷ 3,5. O medidor apontava para o verde
 * independentemente do que acontecesse na cafeteria.</p>
 *
 * <p>Agora os números são medidos. O painel responde três perguntas, nesta ordem:</p>
 *
 * <ol>
 *   <li><b>Estou ganhando dinheiro?</b> — faturamento, CMV real e margem do período.</li>
 *   <li><b>Estou comprando bem?</b> — quanto foi comprado contra quanto foi
 *       consumido, e onde o custo está concentrado.</li>
 *   <li><b>Vou ficar sem alguma coisa?</b> — cobertura em dias, e não percentual
 *       de capacidade.</li>
 * </ol>
 *
 * @author Equipe Custo Certo - FATEC Indaiatuba
 * @version 2.0
 */
public class PaginaDashboard extends VBox {

    private final EstoqueService estoque;
    private final AnaliseService analise;

    private final ChoiceBox<OpcaoPeriodo> filtro = new ChoiceBox<>();
    private final FlowPane indicadores = new FlowPane(14, 14);
    private final VBox blocos = new VBox(14);
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
     * Monta o painel de indicadores.
     *
     * @param estoque o serviço de estoque
     * @param analise o serviço de análise financeira
     */
    public PaginaDashboard(EstoqueService estoque, AnaliseService analise) {
        this.estoque = estoque;
        this.analise = analise;
        setSpacing(16);

        Label titulo = new Label("Painel de Indicadores");
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

        ScrollPane rolagem = new ScrollPane(blocos);
        rolagem.setFitToWidth(true);
        rolagem.getStyleClass().add("rolagem");
        VBox.setVgrow(rolagem, Priority.ALWAYS);

        getChildren().addAll(cabecalho, indicadores, rolagem);
        atualizar();
    }

    /**
     * Recarrega os dados do banco e redesenha o painel.
     */
    public final void atualizar() {
        indicadores.getChildren().clear();
        blocos.getChildren().clear();

        Periodo periodo = filtro.getValue().periodo();
        subtitulo.setText(periodo.toString());

        try {
            List<Ingrediente> insumos = estoque.listarIngredientes();
            ResumoEstoque resumo = new ResumoEstoque(insumos);
            ResumoFinanceiro financeiro = analise.resumoDoPeriodo(periodo);
            List<IndicadorInsumo> giro = analise.indicadoresDeInsumo(periodo);

            montarIndicadores(resumo, financeiro);

            blocos.getChildren().addAll(
                    montarLinhaFinanceira(financeiro),
                    montarLinhaOperacional(giro));

        } catch (EstoqueException | CardapioException e) {
            Label erro = new Label(e.getMessage());
            erro.getStyleClass().add("mensagem-erro");
            erro.setWrapText(true);
            blocos.getChildren().add(erro);
        }
    }

    /**
     * Monta os quatro cartões de indicador do topo.
     *
     * @param resumo     os números de estoque
     * @param financeiro os números do período
     */
    private void montarIndicadores(ResumoEstoque resumo, ResumoFinanceiro financeiro) {
        indicadores.getChildren().add(criarIndicador("Faturamento do período",
                Formatos.dinheiro(financeiro.getReceita()),
                financeiro.temVendas()
                        ? financeiro.getUnidadesVendidas() + " unidades vendidas"
                        : "nenhuma venda registrada",
                "kpi-verde", "dinheiro", "#00a86b"));

        if (financeiro.temVendas()) {
            indicadores.getChildren().add(criarIndicador("CMV",
                    Formatos.percentual(financeiro.getCmvTeorico()),
                    financeiro.getRotuloCmv() + " · ideal até "
                            + Math.round(ResumoFinanceiro.CMV_IDEAL) + "%",
                    corDoCmv(financeiro), "evolucao", hexDoCmv(financeiro)));

            indicadores.getChildren().add(criarIndicador("Margem bruta",
                    Formatos.dinheiro(financeiro.getMargemBruta()),
                    "sobra depois de pagar os insumos",
                    "kpi-azul", "dinheiro", "#3a86ff"));
        } else {
            indicadores.getChildren().add(criarIndicador("CMV", "—",
                    "sem vendas no período não há CMV a calcular",
                    "kpi-azul", "evolucao", "#3a86ff"));

            indicadores.getChildren().add(criarIndicador("Valor em estoque",
                    Formatos.dinheiro(resumo.getValorEmEstoque()),
                    resumo.getTotalItens() + " insumos cadastrados",
                    "kpi-azul", "estoque", "#3a86ff"));
        }

        boolean temPerda = resumo.getValorVencido().signum() > 0;
        indicadores.getChildren().add(criarIndicador("Perda por vencimento",
                Formatos.dinheiro(resumo.getValorVencido()),
                temPerda
                        ? resumo.getItensVencidos() + " insumo(s) vencido(s) com saldo"
                        : "nenhum insumo vencido com saldo",
                temPerda ? "kpi-vermelho" : "kpi-laranja", "alerta",
                temPerda ? "#e63946" : "#f4a435"));
    }

    // ------------------------------------------------------------- financeiro

    /**
     * Monta a linha de cartões que responde "estou ganhando dinheiro".
     *
     * @param f os números do período
     * @return a linha montada
     */
    private HBox montarLinhaFinanceira(ResumoFinanceiro f) {
        VBox conta = new VBox(8);

        if (!f.temVendas()) {
            Label vazio = new Label(
                    "Nenhuma venda registrada neste período. Sem o lado da receita, "
                    + "CMV e margem não têm como ser calculados — registre as vendas na "
                    + "tela de Vendas.");
            vazio.getStyleClass().add("pagina-sub");
            vazio.setWrapText(true);
            conta.getChildren().add(vazio);
        } else {
            conta.getChildren().addAll(
                    linhaValor("Faturamento", Formatos.dinheiro(f.getReceita())),
                    linhaValor("Custo pela ficha técnica", Formatos.dinheiro(f.getCustoTeorico())),
                    linhaValor("Margem bruta", Formatos.dinheiro(f.getMargemBruta())),
                    linhaValor("Ticket médio", Formatos.dinheiro(f.getTicketMedio())),
                    linhaValor("CMV", Formatos.percentual(f.getCmvTeorico())
                            + "  ·  " + f.getRotuloCmv()));

            Label nota = new Label(
                    "O CMV usa o custo das fichas técnicas, que cobre todos os insumos. "
                    + "O custo medido pela balança no período foi "
                    + Formatos.dinheiro(f.getCustoReal())
                    + " — menor porque nem todo insumo passa pela balança. A diferença "
                    + "está destrinchada em Margem e Desperdício.");
            nota.getStyleClass().add("insumo-pct");
            nota.setWrapText(true);
            conta.getChildren().add(nota);
        }

        VBox compras = new VBox(8);
        compras.getChildren().addAll(
                linhaValor("Comprado no período", Formatos.dinheiro(f.getCompras())),
                linhaValor("Consumido no período", Formatos.dinheiro(f.getCustoReal())));

        double razao = f.getRazaoCompraConsumo();
        if (razao > 0) {
            Label leitura = new Label(String.format(
                    "Você comprou %.1f× o que consumiu.", razao)
                    + (razao > 2
                        ? " Isso é caixa parando na prateleira: cada real aí é um real "
                          + "que não está disponível para outra coisa."
                        : " Reposição em ritmo próximo ao consumo."));
            leitura.getStyleClass().add("insumo-valor");
            leitura.setWrapText(true);
            compras.getChildren().add(leitura);
        } else {
            Label leitura = new Label(
                    "Sem baixas de estoque no período, não dá para comparar compra com "
                    + "consumo.");
            leitura.getStyleClass().add("insumo-pct");
            leitura.setWrapText(true);
            compras.getChildren().add(leitura);
        }

        HBox linha = new HBox(14,
                cartao("Resultado do Período", "Receita, custo e o que sobrou", conta),
                cartao("Compra × Consumo", "Se o estoque está inflando", compras));
        linha.setAlignment(Pos.TOP_LEFT);
        return linha;
    }

    // ------------------------------------------------------------ operacional

    /**
     * Monta a linha de cartões que responde "vou ficar sem alguma coisa".
     *
     * @param giro os indicadores de giro por insumo
     * @return a linha montada
     */
    private HBox montarLinhaOperacional(List<IndicadorInsumo> giro) {
        HBox linha = new HBox(14,
                cartao("Vai Acabar Primeiro",
                        "Dias que o saldo cobre, no ritmo de consumo do período",
                        montarCobertura(giro)),
                cartao("Onde o Dinheiro Está",
                        "Curva ABC pelo custo consumido no período",
                        montarCurvaAbc(giro)));
        linha.setAlignment(Pos.TOP_LEFT);
        return linha;
    }

    /**
     * Monta a lista de insumos ordenada por urgência de reposição.
     *
     * @param giro os indicadores de giro
     * @return a caixa montada
     */
    private VBox montarCobertura(List<IndicadorInsumo> giro) {
        VBox caixa = new VBox(9);

        List<IndicadorInsumo> comConsumo = giro.stream()
                .filter(IndicadorInsumo::temConsumo)
                .sorted((a, b) -> Double.compare(a.getDiasCobertura(), b.getDiasCobertura()))
                .limit(6)
                .toList();

        if (comConsumo.isEmpty()) {
            Label vazio = new Label(
                    "Nenhum insumo teve saída no período, então não há ritmo de consumo "
                    + "do qual projetar quanto tempo o estoque dura.");
            vazio.getStyleClass().add("pagina-sub");
            vazio.setWrapText(true);
            caixa.getChildren().add(vazio);
            return caixa;
        }

        for (IndicadorInsumo i : comConsumo) {
            Label nome = new Label(i.getNome());
            nome.getStyleClass().add("mini-rotulo");

            Region esp = new Region();
            HBox.setHgrow(esp, Priority.ALWAYS);

            Label etiqueta = new Label(i.getRotuloCobertura());
            etiqueta.getStyleClass().addAll("etiqueta", switch (i.getUrgencia()) {
                case "critico" -> "etiqueta-vencido";
                case "atencao" -> "etiqueta-aviso";
                default -> "etiqueta-ok";
            });

            HBox topo = new HBox(8, nome, esp, etiqueta);
            topo.setAlignment(Pos.CENTER_LEFT);

            Label detalhe = new Label("saldo "
                    + Formatos.quantidadeComUnidade(i.getSaldo(), i.getUnidade().getSigla())
                    + "  ·  sai "
                    + Formatos.quantidade(i.getConsumoMedioDiario())
                    + " " + i.getUnidade().getSigla() + " por dia");
            detalhe.getStyleClass().add("insumo-pct");

            caixa.getChildren().add(new VBox(2, topo, detalhe));
        }

        Label nota = new Label(
                "O alerta antigo disparava a 25% da capacidade, igual para todo mundo. "
                + "Um leite que acaba em dois dias e uma canela que dura sete meses "
                + "recebiam o mesmo aviso.");
        nota.getStyleClass().add("insumo-pct");
        nota.setWrapText(true);
        caixa.getChildren().add(nota);
        return caixa;
    }

    /**
     * Monta a pizza da curva ABC, com a lista de classe A ao lado.
     *
     * @param giro os indicadores de giro
     * @return a caixa montada
     */
    private HBox montarCurvaAbc(List<IndicadorInsumo> giro) {
        List<IndicadorInsumo> comCusto = giro.stream()
                .filter(i -> i.getCustoConsumido().signum() > 0)
                .toList();

        if (comCusto.isEmpty()) {
            Label vazio = new Label("Sem consumo no período para classificar.");
            vazio.getStyleClass().add("pagina-sub");
            vazio.setWrapText(true);
            return new HBox(vazio);
        }

        PieChart pizza = new PieChart();
        for (IndicadorInsumo i : comCusto) {
            pizza.getData().add(new PieChart.Data(
                    i.getNome(), i.getCustoConsumido().doubleValue()));
        }
        pizza.setLabelsVisible(false);
        pizza.setLegendVisible(false);
        pizza.setStartAngle(90);
        pizza.setAnimated(false);
        pizza.setPrefSize(210, 210);

        VBox lista = new VBox(7);
        for (IndicadorInsumo i : comCusto.stream().limit(6).toList()) {
            Label letra = new Label(i.getClasse().getLetra());
            letra.getStyleClass().addAll("etiqueta", switch (i.getClasse()) {
                case A -> "etiqueta-vencido";
                case B -> "etiqueta-aviso";
                default -> "etiqueta-ok";
            });

            Label nome = new Label(i.getNome());
            nome.getStyleClass().add("mini-rotulo");

            Region esp = new Region();
            HBox.setHgrow(esp, Priority.ALWAYS);

            Label valor = new Label(Formatos.dinheiro(i.getCustoConsumido())
                    + "  ·  " + Formatos.percentual(i.getParticipacaoCusto()));
            valor.getStyleClass().add("mini-valor");

            HBox linha = new HBox(8, letra, nome, esp, valor);
            linha.setAlignment(Pos.CENTER_LEFT);
            lista.getChildren().add(linha);
        }

        Label nota = new Label("Classe A concentra o custo. Um desconto de 5% aqui vale "
                + "mais do que zerar vários itens da classe C.");
        nota.getStyleClass().add("insumo-pct");
        nota.setWrapText(true);
        nota.setMaxWidth(260);
        lista.getChildren().add(nota);

        HBox caixa = new HBox(16, pizza, lista);
        caixa.setAlignment(Pos.CENTER_LEFT);
        return caixa;
    }

    // ------------------------------------------------------------- auxiliares

    /**
     * Escolhe a classe de cor do cartão conforme a saúde do CMV.
     *
     * @param f os números do período
     * @return a classe de estilo
     */
    private String corDoCmv(ResumoFinanceiro f) {
        return switch (f.getClassificacaoCmv()) {
            case "ok" -> "kpi-verde";
            case "atencao" -> "kpi-laranja";
            default -> "kpi-vermelho";
        };
    }

    /**
     * Escolhe a cor do ícone conforme a saúde do CMV.
     *
     * @param f os números do período
     * @return a cor em hexadecimal
     */
    private String hexDoCmv(ResumoFinanceiro f) {
        return switch (f.getClassificacaoCmv()) {
            case "ok" -> "#00a86b";
            case "atencao" -> "#f4a435";
            default -> "#e63946";
        };
    }

    /**
     * Cria um cartão de indicador, com rótulo, número e legenda.
     *
     * @param rotulo   o nome do indicador
     * @param valor    o número em destaque
     * @param legenda  a linha explicativa abaixo
     * @param cor      a classe de estilo que define a cor da faixa lateral
     * @param icone    a chave do ícone em {@link Icones}
     * @param corIcone a cor do ícone, em hexadecimal
     * @return o cartão montado
     */
    private VBox criarIndicador(String rotulo, String valor, String legenda,
                                String cor, String icone, String corIcone) {
        Label r = new Label(rotulo.toUpperCase());
        r.getStyleClass().add("kpi-rotulo");

        Region esp = new Region();
        HBox.setHgrow(esp, Priority.ALWAYS);

        StackPane marca = new StackPane(Icones.criar(icone, 15, Color.web(corIcone)));
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
        caixa.setPrefWidth(440);
        HBox.setHgrow(caixa, Priority.ALWAYS);
        return caixa;
    }

    /**
     * Monta uma linha de rótulo e valor.
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
}
