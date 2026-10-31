package br.com.custocerto.view;

import br.com.custocerto.model.ExecucaoReceita;
import br.com.custocerto.model.Ingrediente;
import br.com.custocerto.model.ItemExecucao;
import br.com.custocerto.model.Unidade;
import br.com.custocerto.service.BalancaClient;
import br.com.custocerto.service.CardapioException;
import br.com.custocerto.service.EstoqueException;
import br.com.custocerto.service.EstoqueService;
import br.com.custocerto.service.ExecucaoReceitaService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
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
 * Página de receitas: produções pesadas na balança, com o CMV real de cada leva.
 *
 * <p>O fluxo é o mesmo do painel web. O operador inicia uma receita com um nome,
 * pesa cada ingrediente — que sai do estoque na hora — e, ao terminar, informa
 * quantas porções rendeu e por quanto cada uma é vendida. O sistema então mostra
 * o CMV daquela leva, calculado com o preço que cada insumo tinha no momento da
 * pesagem.</p>
 *
 * <p>Só existe uma receita em andamento por vez, e ela é compartilhada: uma
 * receita iniciada no painel web aparece aqui, e o contrário também.</p>
 *
 * @author Equipe Custo Certo - FATEC Indaiatuba
 * @version 1.0
 */
public class PaginaReceitas extends VBox {

    /** Cor dos ícones dos botões de ação. */
    private static final Color COR_ICONE = Color.web("#8892a4");

    /** Formato de data e hora usado no histórico. */
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private final ExecucaoReceitaService servico;
    private final EstoqueService estoque;
    private final Runnable aoMudarEstoque;

    private final Button iniciar = new Button("Iniciar Receita");
    private final VBox andamento = new VBox(12);
    private final VBox historico = new VBox(0);
    private final Label contagem = new Label();

    /**
     * Monta a página de receitas.
     *
     * @param servico        o serviço de receitas pesadas
     * @param estoque        o serviço de estoque, que fornece os insumos
     * @param aoMudarEstoque ação executada quando o estoque muda, para as outras
     *                       páginas se atualizarem
     */
    public PaginaReceitas(ExecucaoReceitaService servico, EstoqueService estoque,
                          Runnable aoMudarEstoque) {
        this.servico = servico;
        this.estoque = estoque;
        this.aoMudarEstoque = aoMudarEstoque;

        setSpacing(16);

        Label titulo = new Label("Receitas");
        titulo.getStyleClass().add("pagina-titulo");
        contagem.getStyleClass().add("pagina-sub");
        VBox textos = new VBox(2, titulo, contagem);

        iniciar.getStyleClass().add("botao-principal");
        iniciar.setGraphic(Icones.criar("mais", 15, Color.WHITE));
        iniciar.setGraphicTextGap(8);
        iniciar.setOnAction(e -> iniciarReceita());

        Region espaco = new Region();
        HBox.setHgrow(espaco, Priority.ALWAYS);
        HBox cabecalho = new HBox(12, textos, espaco, iniciar);
        cabecalho.setAlignment(Pos.CENTER_LEFT);

        andamento.getStyleClass().add("cartao-painel");
        andamento.setPadding(new Insets(16));

        Label tituloHistorico = new Label("Histórico");
        tituloHistorico.getStyleClass().add("cartao-titulo");

        VBox conteudo = new VBox(16, andamento, tituloHistorico, historico);
        ScrollPane rolagem = new ScrollPane(conteudo);
        rolagem.setFitToWidth(true);
        rolagem.getStyleClass().add("rolagem");
        VBox.setVgrow(rolagem, Priority.ALWAYS);

        getChildren().addAll(cabecalho, rolagem);
        atualizar();
    }

    /**
     * Recarrega a receita em andamento e o histórico.
     */
    public final void atualizar() {
        andamento.getChildren().clear();
        historico.getChildren().clear();

        try {
            ExecucaoReceita aberta = servico.buscarAberta();
            List<ExecucaoReceita> lista = servico.listar();

            iniciar.setDisable(aberta != null);
            andamento.setVisible(aberta != null);
            andamento.setManaged(aberta != null);
            if (aberta != null) {
                montarAndamento(aberta);
            }

            long finalizadas = lista.stream()
                    .filter(r -> r.getStatus() == ExecucaoReceita.Status.FINALIZADA).count();
            contagem.setText("Produções pesadas na balança · " + finalizadas
                    + (finalizadas == 1 ? " finalizada" : " finalizadas"));

            historico.getChildren().add(montarCabecalhoHistorico());
            for (ExecucaoReceita r : lista) {
                historico.getChildren().add(montarLinhaHistorico(r));
            }
            if (lista.isEmpty()) {
                historico.getChildren().add(vazio(
                        "Nenhuma receita ainda. Clique em Iniciar Receita para começar."));
            }
        } catch (CardapioException e) {
            iniciar.setDisable(true);
            andamento.setVisible(false);
            andamento.setManaged(false);
            contagem.setText("");
            Label erro = new Label(e.getMessage());
            erro.getStyleClass().add("mensagem-erro");
            erro.setWrapText(true);
            historico.getChildren().add(erro);
        }
    }

    // ------------------------------------------------------------ andamento

    /**
     * Monta o cartão da receita em andamento: ingredientes pesados e ações.
     *
     * @param r a receita aberta, com os itens
     */
    private void montarAndamento(ExecucaoReceita r) {
        Label selo = new Label("EM ANDAMENTO");
        selo.getStyleClass().addAll("etiqueta", "etiqueta-aviso");

        Label nome = new Label(r.getNome());
        nome.getStyleClass().add("cartao-titulo");
        nome.setStyle("-fx-font-size: 17px;");

        Label desde = new Label("Iniciada em " + dataHora(r.getInicio()));
        desde.getStyleClass().add("pagina-sub");

        Button adicionar = new Button("Pesar ingrediente");
        adicionar.getStyleClass().add("botao-principal");
        adicionar.setGraphic(Icones.criar("balanca", 15, Color.WHITE));
        adicionar.setGraphicTextGap(8);
        adicionar.setOnAction(e -> adicionarIngrediente(r));

        Button finalizar = new Button("Finalizar");
        finalizar.getStyleClass().add("botao-secundario");
        finalizar.setOnAction(e -> finalizarReceita(r));

        Button cancelar = new Button("Cancelar receita");
        cancelar.getStyleClass().add("botao-secundario");
        cancelar.setOnAction(e -> cancelarReceita(r));

        Region espaco = new Region();
        HBox.setHgrow(espaco, Priority.ALWAYS);
        HBox topo = new HBox(10, new VBox(4, selo, nome, desde), espaco,
                cancelar, finalizar, adicionar);
        topo.setAlignment(Pos.CENTER_LEFT);

        VBox tabela = new VBox(0);
        tabela.getChildren().add(linha(true,
                celula("Ingrediente", 240), celula("Quantidade", 130),
                celula("Preço unit.", 130), celula("Custo", 120), celula("", 44)));

        for (ItemExecucao item : r.getItens()) {
            Button estornar = new Button();
            estornar.getStyleClass().addAll("botao-mini", "botao-mini-perigo");
            estornar.setGraphic(Icones.criar("excluir", 12, COR_ICONE));
            estornar.setOnAction(e -> estornar(r, item));
            HBox acao = new HBox(estornar);
            acao.setPrefWidth(44);

            tabela.getChildren().add(linha(false,
                    celula(item.nomeIngrediente(), 240),
                    celula(quantidade(item.quantidade(), item.unidade()), 130),
                    celula(Formatos.dinheiro(item.precoUnitario()) + "/" + item.unidade().getSigla(), 130),
                    celula(Formatos.dinheiro(item.getCusto()), 120),
                    acao));
        }
        if (r.getItens().isEmpty()) {
            tabela.getChildren().add(vazio(
                    "Nenhum ingrediente pesado ainda. Clique em Pesar ingrediente."));
        }

        Label total = new Label("Custo até agora: " + Formatos.dinheiro(r.getCustoTotal()));
        total.getStyleClass().add("insumo-valor");
        total.setStyle("-fx-font-weight: bold;");

        andamento.getChildren().addAll(topo, tabela, total);
    }

    /**
     * Pede o nome e inicia uma receita.
     */
    private void iniciarReceita() {
        TextField campoNome = new TextField();
        campoNome.setPromptText("Ex.: Bolo de Cenoura — fornada da manhã");

        Dialog<ButtonType> dialogo = Dialogos.criar("Iniciar receita",
                "Cada ingrediente pesado depois sai do estoque e entra no custo desta receita.",
                new VBox(10, rotulado("Nome da receita", campoNome)));
        Platform.runLater(campoNome::requestFocus);

        if (dialogo.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }
        try {
            servico.iniciar(campoNome.getText());
            atualizar();
        } catch (CardapioException e) {
            Dialogos.erro(e.getMessage());
        }
    }

    /**
     * Abre o formulário de pesagem de um ingrediente, com a leitura da balança
     * ao vivo, e abate o estoque ao confirmar.
     *
     * @param r a receita aberta
     */
    private void adicionarIngrediente(ExecucaoReceita r) {
        List<Ingrediente> insumos;
        try {
            insumos = estoque.listarIngredientes();
        } catch (EstoqueException e) {
            Dialogos.erro(e.getMessage());
            return;
        }
        if (insumos.isEmpty()) {
            Dialogos.erro("Nenhum insumo cadastrado. Cadastre os insumos no Estoque primeiro.");
            return;
        }

        ChoiceBox<Ingrediente> campoInsumo = new ChoiceBox<>();
        campoInsumo.getItems().addAll(insumos);
        campoInsumo.setValue(insumos.get(0));

        TextField campoQtd = new TextField();
        campoQtd.setPromptText("0");
        ChoiceBox<Unidade> campoUnidade = new ChoiceBox<>();
        campoUnidade.setPrefWidth(70);

        Label saldo = new Label();
        saldo.getStyleClass().add("pagina-sub");

        Label leitura = new Label("Balança: aguardando leitura…");
        leitura.getStyleClass().add("insumo-valor");
        Button usarPeso = new Button("Usar peso da balança");
        usarPeso.getStyleClass().add("botao-secundario");
        usarPeso.setGraphic(Icones.criar("balanca", 14, COR_ICONE));
        usarPeso.setGraphicTextGap(7);
        usarPeso.setDisable(true);

        BigDecimal[] pesoAtual = {null};

        Runnable aoTrocarInsumo = () -> {
            Ingrediente i = campoInsumo.getValue();
            campoUnidade.getItems().setAll(i.getUnidade().getEquivalentes());
            campoUnidade.setValue(i.getUnidade().melhorParaExibir(new BigDecimal("0.5")));
            saldo.setText("Em estoque: " + quantidade(i.getQtd(), i.getUnidade())
                    + " · " + Formatos.dinheiro(i.getPreco()) + "/" + i.getUnidade().getSigla());
        };
        aoTrocarInsumo.run();
        campoInsumo.valueProperty().addListener((o, a, b) -> aoTrocarInsumo.run());

        Runnable aoMudarLeitura = () -> {
            Unidade u = campoUnidade.getValue();
            boolean pesavel = u == Unidade.KG || u == Unidade.G;
            usarPeso.setDisable(pesoAtual[0] == null || !pesavel);
            if (pesoAtual[0] != null) {
                leitura.setText("Balança: " + Formatos.quantidade(pesoAtual[0]) + " kg"
                        + (pesavel ? "" : "  (este insumo não é medido por peso)"));
            }
        };
        campoUnidade.valueProperty().addListener((o, a, b) -> aoMudarLeitura.run());

        usarPeso.setOnAction(e -> {
            BigDecimal kg = pesoAtual[0];
            if (kg != null) {
                BigDecimal valor = campoUnidade.getValue() == Unidade.G
                        ? kg.multiply(new BigDecimal("1000")) : kg;
                campoQtd.setText(Formatos.paraCampo(valor.stripTrailingZeros()));
            }
        });

        BalancaClient balanca = new BalancaClient();
        balanca.conectar(
                l -> Platform.runLater(() -> {
                    pesoAtual[0] = l.peso();
                    aoMudarLeitura.run();
                }),
                online -> Platform.runLater(() -> {
                    if (!online) {
                        pesoAtual[0] = null;
                        leitura.setText("Balança indisponível — digite a quantidade.");
                        aoMudarLeitura.run();
                    }
                }));

        HBox quantidade = new HBox(6, campoQtd, campoUnidade);
        HBox.setHgrow(campoQtd, Priority.ALWAYS);
        HBox balancaLinha = new HBox(10, leitura, usarPeso);
        balancaLinha.setAlignment(Pos.CENTER_LEFT);

        VBox corpo = new VBox(10,
                rotulado("Ingrediente", campoInsumo), saldo,
                rotulado("Quantidade", quantidade), balancaLinha);
        corpo.setPadding(new Insets(4, 2, 4, 2));

        Dialog<ButtonType> dialogo = Dialogos.criar("Pesar ingrediente",
                "Receita: " + r.getNome() + " — a quantidade sai do estoque ao salvar.", corpo);

        ButtonType resposta;
        try {
            resposta = dialogo.showAndWait().orElse(ButtonType.CANCEL);
        } finally {
            balanca.desconectar();
        }
        if (resposta != ButtonType.OK) {
            return;
        }

        try {
            Ingrediente i = campoInsumo.getValue();
            BigDecimal digitado = lerDecimal(campoQtd.getText(), "quantidade");
            BigDecimal naBase = digitado.multiply(i.getUnidade().fatorDe(campoUnidade.getValue()));
            servico.adicionarItem(r.getId(), i.getId(), naBase);
            atualizar();
            aoMudarEstoque.run();
        } catch (CardapioException e) {
            Dialogos.erro(e.getMessage());
        }
    }

    /**
     * Desfaz a pesagem de um ingrediente, depois de confirmar.
     *
     * @param r    a receita aberta
     * @param item o ingrediente a devolver ao estoque
     */
    private void estornar(ExecucaoReceita r, ItemExecucao item) {
        if (!confirmar("Desfazer pesagem",
                "Devolver " + quantidade(item.quantidade(), item.unidade()) + " de "
                + item.nomeIngrediente() + " ao estoque?",
                "A linha sai da receita e o saldo do insumo volta ao que era.")) {
            return;
        }
        try {
            servico.estornarItem(r.getId(), item.movimentacaoId());
            atualizar();
            aoMudarEstoque.run();
        } catch (CardapioException e) {
            Dialogos.erro(e.getMessage());
        }
    }

    /**
     * Pede porções e preço de venda, mostra a prévia do CMV e finaliza.
     *
     * @param r a receita aberta
     */
    private void finalizarReceita(ExecucaoReceita r) {
        TextField campoPorcoes = new TextField("1");
        TextField campoPreco = new TextField();
        campoPreco.setPromptText("opcional");

        Label previa = new Label();
        previa.getStyleClass().add("insumo-valor");
        previa.setWrapText(true);

        Runnable recalcular = () -> {
            ExecucaoReceita simulada = new ExecucaoReceita();
            simulada.setCustoTotal(r.getCustoTotal());
            try {
                simulada.setPorcoes(Integer.parseInt(campoPorcoes.getText().trim()));
            } catch (NumberFormatException ignorada) {
                simulada.setPorcoes(null);
            }
            try {
                simulada.setPrecoVenda(campoPreco.getText().isBlank()
                        ? null : Formatos.lerNumero(campoPreco.getText()));
            } catch (NumberFormatException ignorada) {
                simulada.setPrecoVenda(null);
            }
            String texto = "Custo total " + Formatos.dinheiro(r.getCustoTotal())
                    + " · por porção " + Formatos.dinheiro(simulada.getCustoPorcao());
            if (simulada.getCmvPercentual() != null) {
                texto += "\nCMV " + Formatos.percentual(simulada.getCmvPercentual())
                        + " · margem " + Formatos.dinheiro(simulada.getMargem());
            }
            previa.setText(texto);
        };
        recalcular.run();
        campoPorcoes.textProperty().addListener((o, a, b) -> recalcular.run());
        campoPreco.textProperty().addListener((o, a, b) -> recalcular.run());

        VBox corpo = new VBox(10,
                rotulado("Porções produzidas", campoPorcoes),
                rotulado("Preço de venda por porção (R$)", campoPreco),
                previa);
        corpo.setPadding(new Insets(4, 2, 4, 2));

        Dialog<ButtonType> dialogo = Dialogos.criar("Finalizar receita",
                r.getNome() + " — " + r.getItens().size() + " ingrediente(s) pesado(s)", corpo);
        if (dialogo.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        try {
            Integer porcoes;
            try {
                porcoes = campoPorcoes.getText().isBlank()
                        ? null : Integer.parseInt(campoPorcoes.getText().trim());
            } catch (NumberFormatException e) {
                throw new CardapioException("As porções precisam ser um número inteiro.");
            }
            BigDecimal preco = campoPreco.getText().isBlank()
                    ? null : lerDecimal(campoPreco.getText(), "preço de venda");
            servico.finalizar(r, porcoes, preco);
            atualizar();
        } catch (CardapioException e) {
            Dialogos.erro(e.getMessage());
        }
    }

    /**
     * Cancela a receita aberta, depois de confirmar, devolvendo tudo ao estoque.
     *
     * @param r a receita aberta
     */
    private void cancelarReceita(ExecucaoReceita r) {
        if (!confirmar("Cancelar receita", "Cancelar \"" + r.getNome() + "\"?",
                "Os " + r.getItens().size() + " ingrediente(s) pesado(s) voltam ao estoque. "
                + "A receita fica no histórico como cancelada.")) {
            return;
        }
        try {
            servico.cancelar(r.getId());
            atualizar();
            aoMudarEstoque.run();
        } catch (CardapioException e) {
            Dialogos.erro(e.getMessage());
        }
    }

    // ------------------------------------------------------------ histórico

    /**
     * Monta o cabeçalho da tabela de histórico.
     *
     * @return a linha de cabeçalho
     */
    private HBox montarCabecalhoHistorico() {
        return linha(true,
                celula("Início", 100), celula("Receita", 210), celula("Status", 110),
                celula("Porções", 70), celula("Custo", 100), celula("Por porção", 100),
                celula("CMV", 70), celula("Margem", 100), celula("", 44));
    }

    /**
     * Monta uma linha do histórico.
     *
     * @param r a receita, sem os itens
     * @return a linha montada
     */
    private HBox montarLinhaHistorico(ExecucaoReceita r) {
        Label status = new Label(r.getStatus().getDescricao());
        status.getStyleClass().addAll("etiqueta", switch (r.getStatus()) {
            case FINALIZADA -> "etiqueta-ok";
            case ABERTA -> "etiqueta-aviso";
            case CANCELADA -> "etiqueta-vencido";
        });
        HBox caixaStatus = new HBox(status);
        caixaStatus.setPrefWidth(110);
        caixaStatus.setMinWidth(110);

        boolean finalizada = r.getStatus() == ExecucaoReceita.Status.FINALIZADA;
        Double cmv = r.getCmvPercentual();

        Button ver = new Button();
        ver.getStyleClass().add("botao-mini");
        ver.setGraphic(Icones.criar("ficha", 12, COR_ICONE));
        ver.setOnAction(e -> mostrarDetalhe(r.getId()));
        HBox acao = new HBox(ver);
        acao.setPrefWidth(44);

        return linha(false,
                celula(dataHora(r.getInicio()), 100),
                celula(r.getNome(), 210),
                caixaStatus,
                celula(r.getPorcoes() == null ? "—" : String.valueOf(r.getPorcoes()), 70),
                celula(Formatos.dinheiro(r.getCustoTotal()), 100),
                celula(finalizada ? Formatos.dinheiro(r.getCustoPorcao()) : "—", 100),
                celula(finalizada && cmv != null ? Formatos.percentual(cmv) : "—", 70),
                celula(finalizada && r.getMargem() != null
                        ? Formatos.dinheiro(r.getMargem()) : "—", 100),
                acao);
    }

    /**
     * Mostra os ingredientes pesados de uma receita do histórico.
     *
     * @param id a chave da receita
     */
    private void mostrarDetalhe(int id) {
        ExecucaoReceita r;
        try {
            r = servico.buscar(id);
        } catch (CardapioException e) {
            Dialogos.erro(e.getMessage());
            return;
        }
        if (r == null) {
            Dialogos.erro("Esta receita não existe mais. Atualize a página.");
            return;
        }

        VBox corpo = new VBox(0);
        corpo.getChildren().add(linha(true,
                celula("Ingrediente", 200), celula("Quantidade", 110), celula("Custo", 100)));
        for (ItemExecucao item : r.getItens()) {
            corpo.getChildren().add(linha(false,
                    celula(item.nomeIngrediente(), 200),
                    celula(quantidade(item.quantidade(), item.unidade()), 110),
                    celula(Formatos.dinheiro(item.getCusto()), 100)));
        }
        if (r.getItens().isEmpty()) {
            corpo.getChildren().add(vazio("Nenhum ingrediente nesta receita."));
        }

        String resumo = "Custo total " + Formatos.dinheiro(r.getCustoTotal());
        if (r.getStatus() == ExecucaoReceita.Status.FINALIZADA) {
            resumo += " · " + (r.getPorcoes() == null ? 1 : r.getPorcoes()) + " porção(ões) a "
                    + Formatos.dinheiro(r.getCustoPorcao());
            if (r.getCmvPercentual() != null) {
                resumo += "\nCMV " + Formatos.percentual(r.getCmvPercentual())
                        + " · margem " + Formatos.dinheiro(r.getMargem());
            }
        }
        Label rodape = new Label(resumo);
        rodape.getStyleClass().add("insumo-valor");
        rodape.setPadding(new Insets(12, 0, 0, 0));
        corpo.getChildren().add(rodape);

        Dialog<ButtonType> dialogo = Dialogos.criar(r.getNome(),
                r.getStatus().getDescricao() + " · iniciada em " + dataHora(r.getInicio())
                + (r.getFim() == null ? "" : " · encerrada em " + dataHora(r.getFim())), corpo);
        dialogo.getDialogPane().getButtonTypes().setAll(ButtonType.CLOSE);
        dialogo.showAndWait();
    }

    // ------------------------------------------------------------ auxiliares

    /**
     * Monta uma linha de tabela no estilo das outras páginas.
     *
     * @param cabecalho {@code true} para a linha de títulos
     * @param celulas   o conteúdo, na ordem das colunas
     * @return a linha montada
     */
    private HBox linha(boolean cabecalho, Node... celulas) {
        HBox l = new HBox(celulas);
        l.getStyleClass().add(cabecalho ? "tabela-cabecalho" : "tabela-linha");
        l.setAlignment(Pos.CENTER_LEFT);
        l.setPadding(cabecalho ? new Insets(10, 12, 10, 12) : new Insets(8, 12, 8, 12));
        return l;
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
     * Cria a mensagem exibida quando uma lista está vazia.
     *
     * @param texto a mensagem
     * @return o rótulo
     */
    private Label vazio(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("pagina-sub");
        l.setPadding(new Insets(12, 0, 0, 12));
        return l;
    }

    /**
     * Envolve um campo com o seu rótulo.
     *
     * @param texto o rótulo
     * @param campo o controle
     * @return a caixa com rótulo e campo
     */
    private VBox rotulado(String texto, Node campo) {
        Label rotulo = new Label(texto);
        rotulo.getStyleClass().add("rotulo-campo");
        if (campo instanceof Region r) {
            r.setPrefWidth(340);
        }
        return new VBox(4, rotulo, campo);
    }

    /**
     * Exibe uma quantidade na unidade mais legível: 0,018 kg vira 18 g.
     *
     * @param valor   a quantidade na unidade do insumo
     * @param unidade a unidade do insumo
     * @return o texto formatado com a sigla
     */
    private String quantidade(BigDecimal valor, Unidade unidade) {
        Unidade exibir = unidade.melhorParaExibir(valor);
        BigDecimal convertido = valor.divide(unidade.fatorDe(exibir));
        return Formatos.quantidadeComUnidade(convertido, exibir.getSigla());
    }

    /**
     * Formata data e hora para o histórico.
     *
     * @param momento o instante
     * @return o texto no formato dia/mês hora:minuto, ou um traço
     */
    private String dataHora(LocalDateTime momento) {
        return momento == null ? "—" : momento.format(DATA_HORA);
    }

    /**
     * Pede confirmação ao usuário.
     *
     * @param titulo    o título da janela
     * @param pergunta  a pergunta em destaque
     * @param explicacao o texto explicando a consequência
     * @return {@code true} se o usuário confirmou
     */
    private boolean confirmar(String titulo, String pergunta, String explicacao) {
        Alert alerta = new Alert(Alert.AlertType.CONFIRMATION);
        alerta.setTitle(titulo);
        alerta.setHeaderText(pergunta);
        alerta.setContentText(explicacao);
        return alerta.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
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
