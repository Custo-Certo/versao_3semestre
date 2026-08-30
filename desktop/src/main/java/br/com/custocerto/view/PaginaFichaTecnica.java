package br.com.custocerto.view;

import br.com.custocerto.model.Ingrediente;
import br.com.custocerto.model.ItemFicha;
import br.com.custocerto.model.Receita;
import br.com.custocerto.service.CardapioException;
import br.com.custocerto.service.EstoqueException;
import br.com.custocerto.service.EstoqueService;
import br.com.custocerto.service.FichaTecnicaService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
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
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/**
 * Página da ficha técnica: o que a cafeteria vende e quanto cada produto custa.
 *
 * <p>É a peça que faltava para o sistema sair do controle de estoque e chegar na
 * margem. Antes dela, o custo de um cappuccino era palpite — nada ligava o produto
 * aos insumos que entram nele.</p>
 *
 * <p>O custo mostrado em cada cartão <b>não está guardado no banco</b>: é somado a
 * partir do preço que os insumos têm agora. Quando o fornecedor reajusta o café,
 * o custo do cappuccino muda sozinho, e a margem cai na frente de quem abrir esta
 * tela — sem ninguém reeditar receita nenhuma.</p>
 *
 * @author Equipe Custo Certo - FATEC Indaiatuba
 * @version 1.0
 */
public class PaginaFichaTecnica extends VBox {

    /** Cor dos ícones dos botões de ação, igual à das outras páginas. */
    private static final Color COR_ICONE = Color.web("#8892a4");

    private final FichaTecnicaService servico;
    private final EstoqueService estoque;
    private final FlowPane grade = new FlowPane(14, 14);
    private final Label contagem = new Label();
    private final Runnable aoMudarCardapio;

    /** Insumos disponíveis, recarregados junto com a página. */
    private List<Ingrediente> insumos = new ArrayList<>();

    /**
     * Monta a página de fichas técnicas.
     *
     * @param servico         o serviço do cardápio
     * @param estoque         o serviço de estoque, que fornece a lista de insumos
     * @param aoMudarCardapio ação executada quando o cardápio muda, para as outras
     *                        páginas se atualizarem
     */
    public PaginaFichaTecnica(FichaTecnicaService servico, EstoqueService estoque,
                              Runnable aoMudarCardapio) {
        this.servico = servico;
        this.estoque = estoque;
        this.aoMudarCardapio = aoMudarCardapio;

        setSpacing(16);

        Label titulo = new Label("Ficha Técnica");
        titulo.getStyleClass().add("pagina-titulo");
        contagem.getStyleClass().add("pagina-sub");

        VBox textos = new VBox(2, titulo, contagem);

        Button novo = new Button("Novo Produto");
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
     * Recarrega o cardápio e os insumos e redesenha os cartões.
     */
    public final void atualizar() {
        grade.getChildren().clear();
        try {
            insumos = estoque.listarIngredientes();
        } catch (EstoqueException e) {
            insumos = new ArrayList<>();
        }

        try {
            List<Receita> lista = servico.listar();
            long semFicha = lista.stream().filter(r -> !r.temFicha()).count();
            contagem.setText(lista.size() + " produtos no cardápio"
                    + (semFicha > 0 ? " · " + semFicha + " ainda sem ficha" : ""));

            for (Receita r : lista) {
                grade.getChildren().add(criarCartao(r));
            }
            if (lista.isEmpty()) {
                Label vazio = new Label(
                        "Nenhum produto cadastrado. Sem ficha técnica, o sistema não "
                        + "consegue calcular margem nem CMV.");
                vazio.getStyleClass().add("pagina-sub");
                vazio.setWrapText(true);
                grade.getChildren().add(vazio);
            }
        } catch (CardapioException e) {
            contagem.setText("");
            Label erro = new Label(e.getMessage());
            erro.getStyleClass().add("mensagem-erro");
            erro.setWrapText(true);
            grade.getChildren().add(erro);
        }
    }

    /**
     * Constrói o cartão de um produto, com a ficha e os números derivados dela.
     *
     * @param r o produto a exibir
     * @return o cartão montado
     */
    private VBox criarCartao(Receita r) {
        Label nome = new Label(r.getNome());
        nome.getStyleClass().add("insumo-nome");
        nome.setWrapText(true);

        Region esp = new Region();
        HBox.setHgrow(esp, Priority.ALWAYS);

        Label situacao = new Label(r.isAtivo() ? "No cardápio" : "Fora do cardápio");
        situacao.getStyleClass().addAll("etiqueta",
                r.isAtivo() ? "etiqueta-ok" : "etiqueta-vencido");

        HBox linhaTopo = new HBox(8, nome, esp, situacao);
        linhaTopo.setAlignment(Pos.TOP_LEFT);

        VBox corpo = new VBox(8, linhaTopo);

        if (r.getDescricao() != null && !r.getDescricao().isBlank()) {
            Label desc = new Label(r.getDescricao());
            desc.getStyleClass().add("insumo-pct");
            desc.setWrapText(true);
            corpo.getChildren().add(desc);
        }

        Label preco = new Label(Formatos.dinheiro(r.getPrecoVenda()));
        preco.getStyleClass().add("insumo-preco");
        corpo.getChildren().add(preco);

        if (r.temFicha()) {
            corpo.getChildren().add(montarLinhasDaFicha(r));

            Label custo = new Label("Custo dos insumos: " + Formatos.dinheiro(r.getCusto()));
            custo.getStyleClass().add("insumo-saldo");

            Label margem = new Label("Margem: " + Formatos.dinheiro(r.getMargem())
                    + "  ·  custo é " + Formatos.percentual(r.getPercentualCusto())
                    + " do preço");
            margem.getStyleClass().add("insumo-valor");
            margem.setWrapText(true);

            corpo.getChildren().addAll(custo, margem);
        } else {
            Label aviso = new Label(
                    "Sem ficha técnica. Enquanto ela não existir, este produto fica fora "
                    + "do cálculo de margem e de CMV.");
            aviso.getStyleClass().add("faixa-alerta-item");
            aviso.setWrapText(true);
            corpo.getChildren().add(aviso);
        }

        Button editar = criarBotaoAcao("Editar", "editar", false);
        editar.setOnAction(e -> abrirFormulario(r));

        Button excluir = criarBotaoAcao("Excluir", "excluir", true);
        excluir.setOnAction(e -> confirmarExclusao(r));

        HBox acoes = new HBox(6, editar, excluir);
        acoes.setAlignment(Pos.CENTER_LEFT);
        corpo.getChildren().add(acoes);

        corpo.getStyleClass().add("cartao-insumo");
        if (!r.temFicha()) {
            corpo.getStyleClass().add("cartao-insumo-aviso");
        }
        corpo.setPadding(new Insets(16));
        corpo.setPrefWidth(330);
        return corpo;
    }

    /**
     * Monta a lista de insumos da ficha, com o custo de cada linha.
     *
     * @param r o produto
     * @return a caixa com uma linha por insumo
     */
    private VBox montarLinhasDaFicha(Receita r) {
        VBox caixa = new VBox(3);
        for (ItemFicha item : r.getItens()) {
            Label texto = new Label(item.getNomeIngrediente() + "  "
                    + item.getQuantidadeFormatada());
            texto.getStyleClass().add("mini-rotulo");

            Region esp = new Region();
            HBox.setHgrow(esp, Priority.ALWAYS);

            Label valor = new Label(Formatos.dinheiro(
                    item.getCusto().setScale(2, RoundingMode.HALF_UP)));
            valor.getStyleClass().add("mini-rotulo");

            HBox linha = new HBox(6, texto, esp, valor);
            linha.setAlignment(Pos.CENTER_LEFT);
            caixa.getChildren().add(linha);
        }
        return caixa;
    }

    /**
     * Cria um dos botões de ação do cartão, com ícone.
     *
     * @param texto  o rótulo do botão
     * @param icone  a chave do ícone em {@link Icones}
     * @param perigo {@code true} para a ação destrutiva
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
     * Abre o formulário de cadastro ou edição de um produto e da sua ficha.
     *
     * @param existente o produto a editar, ou {@code null} para cadastrar um novo
     */
    private void abrirFormulario(Receita existente) {
        boolean edicao = existente != null;

        if (insumos.isEmpty()) {
            Dialogos.erro("Cadastre ao menos um insumo no estoque antes de montar "
                    + "uma ficha técnica.");
            return;
        }

        TextField campoNome = new TextField(edicao ? existente.getNome() : "");
        campoNome.setPromptText("Cappuccino");

        TextField campoDescricao = new TextField(
                edicao && existente.getDescricao() != null ? existente.getDescricao() : "");
        campoDescricao.setPromptText("Expresso, leite vaporizado e canela");

        TextField campoPreco = new TextField(
                edicao ? existente.getPrecoVenda().toPlainString() : "0");

        CheckBox campoAtivo = new CheckBox("Produto no cardápio");
        campoAtivo.setSelected(!edicao || existente.isAtivo());

        VBox linhas = new VBox(6);
        Label resumoCusto = new Label();
        resumoCusto.getStyleClass().add("insumo-valor");

        Runnable recalcular = () -> resumoCusto.setText(
                "Custo dos insumos: " + Formatos.dinheiro(somarLinhas(linhas))
                + "   ·   Preço: " + Formatos.dinheiro(lerPrecoSilencioso(campoPreco))
                + "   ·   Margem: " + Formatos.dinheiro(
                        lerPrecoSilencioso(campoPreco).subtract(somarLinhas(linhas))));

        if (edicao) {
            for (ItemFicha item : existente.getItens()) {
                linhas.getChildren().add(criarLinhaFicha(item, linhas, recalcular));
            }
        }

        Button adicionar = new Button("Adicionar insumo");
        adicionar.getStyleClass().add("botao-secundario");
        adicionar.setGraphic(Icones.criar("mais", 13, COR_ICONE));
        adicionar.setGraphicTextGap(6);
        adicionar.setOnAction(e -> {
            linhas.getChildren().add(criarLinhaFicha(null, linhas, recalcular));
            recalcular.run();
        });

        campoPreco.textProperty().addListener((o, a, b) -> recalcular.run());
        recalcular.run();

        Label ajuda = new Label(
                "A quantidade vai na unidade do próprio insumo. Café cadastrado em kg, "
                + "18 gramas por dose, se escreve 0,018.");
        ajuda.getStyleClass().add("pagina-sub");
        ajuda.setWrapText(true);
        ajuda.setMaxWidth(430);

        VBox corpo = new VBox(10,
                rotulado("Nome do produto", campoNome),
                rotulado("Descrição", campoDescricao),
                rotulado("Preço de venda (R$)", campoPreco),
                new Label(" "),
                rotuloSecao("Ficha técnica"),
                ajuda,
                linhas,
                adicionar,
                resumoCusto,
                campoAtivo);
        corpo.setPadding(new Insets(4, 2, 4, 2));

        ScrollPane rolagem = new ScrollPane(corpo);
        rolagem.setFitToWidth(true);
        rolagem.setPrefViewportHeight(430);
        rolagem.getStyleClass().add("rolagem");

        Dialog<ButtonType> dialogo = Dialogos.criar(
                edicao ? "Editar produto" : "Novo produto",
                edicao ? "Alterar " + existente.getNome() : "Cadastrar um produto do cardápio",
                rolagem);

        if (dialogo.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        try {
            Receita alvo = edicao ? existente : new Receita();
            alvo.setNome(campoNome.getText());
            alvo.setDescricao(campoDescricao.getText());
            alvo.setPrecoVenda(lerDecimal(campoPreco.getText(), "preço de venda"));
            alvo.setAtivo(campoAtivo.isSelected());
            alvo.setItens(coletarLinhas(linhas));

            servico.salvar(alvo);
            atualizar();
            aoMudarCardapio.run();
        } catch (CardapioException e) {
            Dialogos.erro(e.getMessage());
        }
    }

    /**
     * Cria uma linha do editor de ficha técnica.
     *
     * @param existente   o item a preencher, ou {@code null} para linha vazia
     * @param container   a caixa que guarda as linhas, para o botão de remover
     * @param aoRecalcular ação disparada quando a linha muda
     * @return a linha montada
     */
    private HBox criarLinhaFicha(ItemFicha existente, VBox container, Runnable aoRecalcular) {
        ChoiceBox<Ingrediente> campoInsumo = new ChoiceBox<>();
        campoInsumo.getItems().addAll(insumos);
        campoInsumo.setPrefWidth(210);

        if (existente != null) {
            for (Ingrediente i : insumos) {
                if (i.getId() == existente.getIngredienteId()) {
                    campoInsumo.setValue(i);
                    break;
                }
            }
        }

        TextField campoQtd = new TextField(
                existente == null ? "" : existente.getQuantidade().toPlainString());
        campoQtd.setPromptText("0,018");
        campoQtd.setPrefWidth(95);

        Label unidade = new Label();
        unidade.getStyleClass().add("mini-rotulo");
        unidade.setPrefWidth(34);

        Runnable atualizarUnidade = () -> {
            Ingrediente i = campoInsumo.getValue();
            unidade.setText(i == null ? "" : i.getUnidade().getSigla());
        };
        atualizarUnidade.run();

        campoInsumo.valueProperty().addListener((o, a, b) -> {
            atualizarUnidade.run();
            aoRecalcular.run();
        });
        campoQtd.textProperty().addListener((o, a, b) -> aoRecalcular.run());

        Button remover = new Button();
        remover.getStyleClass().addAll("botao-mini", "botao-mini-perigo");
        remover.setGraphic(Icones.criar("excluir", 12, COR_ICONE));

        HBox linha = new HBox(6, campoInsumo, campoQtd, unidade, remover);
        linha.setAlignment(Pos.CENTER_LEFT);

        remover.setOnAction(e -> {
            container.getChildren().remove(linha);
            aoRecalcular.run();
        });

        linha.setUserData(new Object[] {campoInsumo, campoQtd});
        return linha;
    }

    /**
     * Lê as linhas do editor e devolve os itens da ficha.
     *
     * @param container a caixa com as linhas
     * @return os itens preenchidos
     * @throws CardapioException se alguma linha estiver incompleta ou inválida
     */
    private List<ItemFicha> coletarLinhas(VBox container) throws CardapioException {
        List<ItemFicha> itens = new ArrayList<>();

        for (javafx.scene.Node no : container.getChildren()) {
            Object[] campos = (Object[]) no.getUserData();
            if (campos == null) {
                continue;
            }

            @SuppressWarnings("unchecked")
            ChoiceBox<Ingrediente> campoInsumo = (ChoiceBox<Ingrediente>) campos[0];
            TextField campoQtd = (TextField) campos[1];

            Ingrediente insumo = campoInsumo.getValue();
            if (insumo == null) {
                throw new CardapioException("Escolha o insumo em todas as linhas da ficha.");
            }

            ItemFicha item = new ItemFicha();
            item.setIngredienteId(insumo.getId());
            item.setNomeIngrediente(insumo.getNome());
            item.setUnidade(insumo.getUnidade());
            item.setPrecoUnitario(insumo.getPreco());
            item.setQuantidade(lerDecimal(campoQtd.getText(),
                    "quantidade de " + insumo.getNome()));
            itens.add(item);
        }
        return itens;
    }

    /**
     * Soma o custo das linhas preenchidas, ignorando as incompletas.
     *
     * <p>Silencia erros de propósito: esta soma roda a cada tecla digitada, para
     * atualizar a prévia. Reclamar de campo pela metade enquanto a pessoa ainda
     * está digitando seria hostil — a validação de verdade acontece ao salvar.</p>
     *
     * @param container a caixa com as linhas
     * @return o custo somado até agora
     */
    private BigDecimal somarLinhas(VBox container) {
        BigDecimal total = BigDecimal.ZERO;

        for (javafx.scene.Node no : container.getChildren()) {
            Object[] campos = (Object[]) no.getUserData();
            if (campos == null) {
                continue;
            }

            @SuppressWarnings("unchecked")
            ChoiceBox<Ingrediente> campoInsumo = (ChoiceBox<Ingrediente>) campos[0];
            TextField campoQtd = (TextField) campos[1];

            Ingrediente insumo = campoInsumo.getValue();
            if (insumo == null) {
                continue;
            }
            try {
                BigDecimal qtd = new BigDecimal(
                        campoQtd.getText().trim().replace(".", "").replace(",", "."));
                total = total.add(qtd.multiply(insumo.getPreco()));
            } catch (RuntimeException incompleta) {
                // linha ainda pela metade; não entra na prévia
            }
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Lê o preço sem lançar exceção, para a prévia de margem.
     *
     * @param campo o campo de preço
     * @return o valor digitado, ou zero se ainda não for um número
     */
    private BigDecimal lerPrecoSilencioso(TextField campo) {
        try {
            return new BigDecimal(campo.getText().trim().replace(".", "").replace(",", "."));
        } catch (RuntimeException incompleto) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * Pede confirmação antes de excluir um produto.
     *
     * @param r o produto a excluir
     */
    private void confirmarExclusao(Receita r) {
        Alert confirma = new Alert(Alert.AlertType.CONFIRMATION);
        confirma.setTitle("Excluir produto");
        confirma.setHeaderText("Excluir \"" + r.getNome() + "\"?");
        confirma.setContentText("A ficha técnica dele será apagada junto. "
                + "Se o produto já teve vendas, o sistema vai recusar — nesse caso, "
                + "tire-o do cardápio em vez de excluir.");

        if (confirma.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        try {
            servico.excluir(r);
            atualizar();
            aoMudarCardapio.run();
        } catch (CardapioException e) {
            Dialogos.erro(e.getMessage());
        }
    }

    /**
     * Cria o rótulo que separa as seções do formulário.
     *
     * @param texto o nome da seção
     * @return o rótulo montado
     */
    private Label rotuloSecao(String texto) {
        Label l = new Label(texto.toUpperCase());
        l.getStyleClass().add("menu-secao");
        return l;
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
            r.setPrefWidth(430);
        }
        return new VBox(4, rotulo, campo);
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
            return new BigDecimal(texto.trim().replace(".", "").replace(",", "."));
        } catch (NumberFormatException e) {
            throw new CardapioException("O valor de " + campo + " não é um número válido.");
        }
    }
}
