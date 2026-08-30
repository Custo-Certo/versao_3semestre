package br.com.custocerto.service;

/**
 * Sinaliza que uma operação sobre as contas de acesso não pôde ser concluída.
 *
 * <p>Assim como {@link EstoqueException}, a mensagem é escrita para ir direto à
 * tela — "Já existe uma conta com este e-mail" em vez do texto bruto que o MySQL
 * devolve quando a restrição {@code uk_usuarios_email} é violada.</p>
 *
 * @author Equipe Custo Certo - FATEC Indaiatuba
 * @version 1.0
 */
public class UsuarioException extends Exception {

    /**
     * Cria a exceção com a mensagem exibida ao usuário.
     *
     * @param mensagem a explicação da falha, em linguagem para o usuário final
     */
    public UsuarioException(String mensagem) {
        super(mensagem);
    }

    /**
     * Cria a exceção com mensagem e a causa original.
     *
     * @param mensagem a explicação da falha
     * @param causa    a exceção que originou o problema
     */
    public UsuarioException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
