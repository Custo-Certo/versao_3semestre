package br.com.custocerto.service;

/**
 * Sinaliza que uma operação de estoque não pôde ser concluída.
 *
 * <p>A mensagem é escrita para ser exibida diretamente ao usuário, sem termos
 * técnicos — "A retirada é maior do que o estoque disponível" em vez do texto
 * de erro do banco.</p>
 *
 * @author Equipe Custo Certo - FATEC Indaiatuba
 * @version 1.0
 */
public class EstoqueException extends Exception {

    /**
     * Cria a exceção com a mensagem exibida ao usuário.
     *
     * @param mensagem a explicação da falha, em linguagem para o usuário final
     */
    public EstoqueException(String mensagem) {
        super(mensagem);
    }

    /**
     * Cria a exceção com mensagem e a causa original.
     *
     * @param mensagem a explicação da falha
     * @param causa    a exceção que originou o problema
     */
    public EstoqueException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
