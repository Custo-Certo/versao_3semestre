package br.com.custocerto.view;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

/**
 * Ícones vetoriais da interface.
 *
 * <p>O painel web usa Font Awesome, que é uma fonte carregada da internet. No
 * desktop isso traria uma dependência a mais e um ponto de falha quando não
 * houver rede, então os ícones aqui são desenhados como caminhos vetoriais
 * ({@link SVGPath}), que o próprio JavaFX renderiza.</p>
 *
 * <p>Todos os traçados foram desenhados numa grade de 24 por 24 e são
 * redimensionados por escala, o que mantém a espessura proporcional em qualquer
 * tamanho. Como são vetores, não perdem nitidez em telas de alta resolução.</p>
 *
 * @author Equipe Custo Certo - FATEC Indaiatuba
 * @version 1.0
 */
public final class Icones {

    /** Lado da grade em que os traçados foram desenhados. */
    private static final double GRADE = 24.0;

    /**
     * Classe utilitária: não deve ser instanciada.
     */
    private Icones() {
    }

    /**
     * Cria o ícone indicado, no tamanho e na cor pedidos.
     *
     * @param nome     a chave do ícone: {@code painel}, {@code balanca},
     *                 {@code estoque}, {@code evolucao}, {@code mais},
     *                 {@code atualizar}, {@code sair}, {@code editar},
     *                 {@code excluir}, {@code entrada}, {@code saida},
     *                 {@code alerta}, {@code dinheiro}, {@code relogio} ou
     *                 {@code cadeado}
     * @param tamanho  o lado do ícone em pixels
     * @param cor      a cor do traçado
     * @return o nó pronto para ser posto em qualquer painel
     */
    public static Node criar(String nome, double tamanho, Color cor) {
        SVGPath caminho = new SVGPath();
        caminho.setContent(traco(nome));
        caminho.setFill(null);
        caminho.setStroke(cor);
        caminho.setStrokeWidth(1.7);
        caminho.setStrokeLineCap(StrokeLineCap.ROUND);
        caminho.setStrokeLineJoin(StrokeLineJoin.ROUND);

        double escala = tamanho / GRADE;
        caminho.setScaleX(escala);
        caminho.setScaleY(escala);

        // O Group recorta o desenho ao tamanho final, para que o ícone ocupe
        // no layout exatamente o espaço pedido.
        Group grupo = new Group(caminho);
        grupo.setManaged(true);
        return grupo;
    }

    /**
     * Cria um ícone na cor de texto apagado, o padrão da interface.
     *
     * @param nome    a chave do ícone
     * @param tamanho o lado do ícone em pixels
     * @return o nó pronto para uso
     */
    public static Node criar(String nome, double tamanho) {
        return criar(nome, tamanho, Color.web("#8892a4"));
    }

    /**
     * Devolve o traçado vetorial correspondente ao nome do ícone.
     *
     * @param nome a chave do ícone
     * @return os comandos de caminho SVG
     */
    private static String traco(String nome) {
        return switch (nome) {
            case "painel" ->
                "M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z";
            case "balanca" ->
                "M12 3v18 M7 21h10 M4 8h16 M4 8l-2 6a2.6 2.6 0 0 0 5.2 0z "
                + "M20 8l2 6a2.6 2.6 0 0 1-5.2 0z M12 5.5a1 1 0 1 0 0-.01";
            case "estoque" ->
                "M4 7.5l8-4 8 4v9l-8 4-8-4z M4 7.5l8 4 8-4 M12 11.5v9";
            case "evolucao" ->
                "M3 17l5.5-5.5 3.5 3.5L21 6 M15 6h6v6";
            case "mais" ->
                "M12 5v14 M5 12h14";
            case "atualizar" ->
                "M20.5 12a8.5 8.5 0 1 1-2.6-6.1 M20.5 3.5v6h-6";
            case "sair" ->
                "M9.5 21H5.5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4 M16 16.5l4.5-4.5L16 7.5 M20.5 12H9.5";
            case "editar" ->
                "M12 20.5h8.5 M16.4 3.6a2.05 2.05 0 0 1 2.9 2.9L7.6 18.2l-4 1.1 1.1-4z";
            case "excluir" ->
                "M3.5 6h17 M8.5 6V3.5h7V6 M5.8 6l1.1 14.5h10.2L18.2 6";
            case "entrada" ->
                "M12 3.2a8.8 8.8 0 1 0 0 17.6 8.8 8.8 0 0 0 0-17.6z M12 16.2V7.8 M8.4 11.4L12 7.8l3.6 3.6";
            case "saida" ->
                "M12 3.2a8.8 8.8 0 1 0 0 17.6 8.8 8.8 0 0 0 0-17.6z M12 7.8v8.4 M8.4 12.6L12 16.2l3.6-3.6";
            case "alerta" ->
                "M12 3.5l9 16.5H3z M12 9.5v5 M12 17.5h.01";
            case "dinheiro" ->
                "M3 7.5h18v11H3z M3 7.5L6 4h12l3 3.5 M16.5 13h1.5";
            case "relogio" ->
                "M12 3.2a8.8 8.8 0 1 0 0 17.6 8.8 8.8 0 0 0 0-17.6z M12 7v5.4l3.4 2";
            case "cadeado" ->
                "M5.5 10.5h13v9.5h-13z M8.5 10.5V7a3.5 3.5 0 0 1 7 0v3.5";
            case "usuarios" ->
                "M15.5 20.5v-1.9a4.1 4.1 0 0 0-4.1-4.1H6.1A4.1 4.1 0 0 0 2 18.6v1.9 M8.75 10.9a3.7 3.7 0 1 0 0-7.4 3.7 3.7 0 0 0 0 7.4 M22 20.5v-1.9a4.1 4.1 0 0 0-3.1-3.97 M16.2 3.72a4.1 4.1 0 0 1 0 7.46";
            case "ficha" ->
                "M7 3.5h7.5L19 8v12.5H7z M14 3.5V8h5 M9.8 12h6.4 M9.8 15.4h6.4";
            case "vendas" ->
                "M3 4.5h2.2l2.3 11h9.4l2.1-7.6H6.2 M10 20a1.15 1.15 0 1 0 0-.01 M17 20a1.15 1.15 0 1 0 0-.01";
            case "receita" ->
                "M5 3.5h14v17l-2.3-1.5-2.4 1.5-2.3-1.5-2.3 1.5-2.4-1.5L5 20.5z "
                + "M8.5 8h7 M8.5 11.5h7 M8.5 15h4";
            case "margem" ->
                "M4 20V9.5h4V20z M10 20V4h4v16z M16 20v-7h4v7z M2.5 20h19";
            default ->
                "M12 3.2a8.8 8.8 0 1 0 0 17.6 8.8 8.8 0 0 0 0-17.6z";
        };
    }
}
