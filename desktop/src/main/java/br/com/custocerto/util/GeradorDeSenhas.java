package br.com.custocerto.util;

/**
 * Utilitário de linha de comando que gera o par salt e hash de uma senha.
 *
 * <p>Como as senhas nunca são gravadas em texto puro, as linhas de {@code INSERT}
 * do arquivo {@code src/main/resources/sql/01_schema_usuarios.sql} precisam conter
 * valores já processados pelo PBKDF2. Esta classe produz esses valores.</p>
 *
 * <p>Também é a forma de trocar a senha de um usuário enquanto a tela de gestão de
 * contas não existe: gere o novo par e aplique um {@code UPDATE} na tabela.</p>
 *
 * <p>Uso:</p>
 * <pre>
 * mvnw.cmd compile
 * mvnw.cmd exec:java -Dexec.mainClass=br.com.custocerto.util.GeradorDeSenhas -Dexec.args="minhaSenha"
 * </pre>
 *
 * <p>Cada execução gera um salt novo, então o hash muda a cada chamada mesmo para
 * a mesma senha — esse é exatamente o efeito pretendido pelo salt.</p>
 *
 * @author Equipe Custo Certo - FATEC Indaiatuba
 * @version 1.0
 */
public final class GeradorDeSenhas {

    /**
     * Classe utilitária de linha de comando: não deve ser instanciada.
     */
    private GeradorDeSenhas() {
    }

    /**
     * Gera e imprime o salt e o hash de cada senha informada como argumento.
     *
     * @param args as senhas em texto puro a processar
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Informe ao menos uma senha como argumento.");
            System.out.println("Exemplo: -Dexec.args=\"admin123 gestor123\"");
            return;
        }

        for (String senha : args) {
            String[] par = SenhaUtil.gerarSaltEHash(senha);
            System.out.println("senha......: " + senha);
            System.out.println("salt.......: " + par[0]);
            System.out.println("senha_hash.: " + par[1]);
            System.out.println();
        }
    }
}
