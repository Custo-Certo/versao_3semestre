package br.com.custocerto.service;

/**
 * Sinaliza que uma tentativa de login não foi aceita.
 *
 * <p>A mensagem carregada por esta exceção é escrita para ser exibida diretamente
 * ao usuário na tela de login, sem termos técnicos.</p>
 *
 * @author Equipe Custo Certo - FATEC Indaiatuba
 * @version 1.0
 */
public class AutenticacaoException extends Exception {

    /**
     * Cria a exceção com a mensagem que será mostrada na tela.
     *
     * @param mensagem a explicação da recusa, em linguagem para o usuário final
     */
    public AutenticacaoException(String mensagem) {
        super(mensagem);
    }

    /**
     * Cria a exceção com mensagem e a causa original.
     *
     * @param mensagem a explicação da recusa
     * @param causa    a exceção que originou a falha
     */
    public AutenticacaoException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
