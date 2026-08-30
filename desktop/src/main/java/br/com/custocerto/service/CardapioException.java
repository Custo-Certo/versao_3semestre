package br.com.custocerto.service;

/**
 * Sinaliza que uma operação sobre o cardápio ou sobre as vendas não pôde ser
 * concluída.
 *
 * <p>Segue o mesmo contrato de {@link EstoqueException} e {@link UsuarioException}:
 * a mensagem é escrita para ir direto à tela. Quando o banco recusa a exclusão de
 * um produto que já foi vendido, o que chega aqui não é o texto da chave
 * estrangeira, e sim uma frase que explica que existe faturamento ligado a ele.</p>
 *
 * @author Equipe Custo Certo - FATEC Indaiatuba
 * @version 1.0
 */
public class CardapioException extends Exception {

    /**
     * Cria a exceção com a mensagem exibida ao usuário.
     *
     * @param mensagem a explicação da falha, em linguagem para o usuário final
     */
    public CardapioException(String mensagem) {
        super(mensagem);
    }

    /**
     * Cria a exceção com mensagem e a causa original.
     *
     * @param mensagem a explicação da falha
     * @param causa    a exceção que originou o problema
     */
    public CardapioException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
