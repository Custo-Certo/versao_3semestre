package br.com.custocerto.util;

import br.com.custocerto.model.Usuario;

/**
 * Guarda o usuário autenticado durante a execução do programa.
 *
 * <p>Depois que a tela de login confirma as credenciais, o usuário é registrado
 * aqui e passa a ficar disponível para todas as telas seguintes, que consultam o
 * perfil para decidir quais funções liberar.</p>
 *
 * <p>Implementada como classe estática porque a aplicação é desktop e atende um
 * único usuário por execução.</p>
 *
 * @author Equipe Custo Certo - FATEC Indaiatuba
 * @version 1.0
 */
public final class Sessao {

    /** Usuário autenticado na execução atual, ou {@code null} se ninguém entrou. */
    private static Usuario usuarioLogado;

    /**
     * Classe utilitária: não deve ser instanciada.
     */
    private Sessao() {
    }

    /**
     * Registra o usuário que acabou de se autenticar.
     *
     * @param usuario o usuário autenticado
     */
    public static void iniciar(Usuario usuario) {
        usuarioLogado = usuario;
    }

    /**
     * Retorna o usuário autenticado na execução atual.
     *
     * @return o usuário logado, ou {@code null} se não houver sessão ativa
     */
    public static Usuario getUsuarioLogado() {
        return usuarioLogado;
    }

    /**
     * Informa se existe um usuário autenticado.
     *
     * @return {@code true} se há sessão ativa
     */
    public static boolean estaAutenticado() {
        return usuarioLogado != null;
    }

    /**
     * Encerra a sessão, descartando o usuário autenticado.
     */
    public static void encerrar() {
        usuarioLogado = null;
    }
}
