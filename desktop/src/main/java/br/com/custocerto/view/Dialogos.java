package br.com.custocerto.view;

import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;

/**
 * Fábrica das janelas de diálogo do sistema.
 *
 * <p>Existe para que todos os formulários da aplicação tenham a mesma aparência
 * e os mesmos botões, sem repetir a montagem em cada tela.</p>
 *
 * @author Equipe Custo Certo - FATEC Indaiatuba
 * @version 1.0
 */
public final class Dialogos {

    /**
     * Classe utilitária: não deve ser instanciada.
     */
    private Dialogos() {
    }

    /**
     * Cria um diálogo de formulário com os botões Salvar e Cancelar.
     *
     * @param titulo   o título da janela
     * @param cabecalho a linha de contexto abaixo do título
     * @param corpo    o conteúdo do formulário
     * @return o diálogo pronto para {@code showAndWait()}
     */
    public static Dialog<ButtonType> criar(String titulo, String cabecalho, Node corpo) {
        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle(titulo);
        dialogo.setHeaderText(cabecalho);

        DialogPane painel = dialogo.getDialogPane();
        painel.setContent(corpo);
        painel.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        painel.lookupButton(ButtonType.OK).getStyleClass().add("botao-principal");
        aplicarEstilo(painel);

        return dialogo;
    }

    /**
     * Exibe uma mensagem de erro em janela modal.
     *
     * @param mensagem o texto explicando o que deu errado
     */
    public static void erro(String mensagem) {
        Alert alerta = new Alert(Alert.AlertType.ERROR);
        alerta.setTitle("Não foi possível concluir");
        alerta.setHeaderText(null);
        alerta.setContentText(mensagem);
        aplicarEstilo(alerta.getDialogPane());
        alerta.showAndWait();
    }

    /**
     * Exibe uma confirmação de operação bem-sucedida.
     *
     * @param mensagem o texto descrevendo o que aconteceu
     */
    public static void sucesso(String mensagem) {
        Alert alerta = new Alert(Alert.AlertType.INFORMATION);
        alerta.setTitle("Pronto");
        alerta.setHeaderText(null);
        alerta.setContentText(mensagem);
        aplicarEstilo(alerta.getDialogPane());
        alerta.showAndWait();
    }

    /**
     * Aplica a folha de estilos do sistema a um painel de diálogo.
     *
     * @param painel o painel a estilizar
     */
    private static void aplicarEstilo(DialogPane painel) {
        painel.getStylesheets().add(
                Dialogos.class.getResource("/css/estilo.css").toExternalForm());
        painel.getStyleClass().add("fundo-app");
    }
}
