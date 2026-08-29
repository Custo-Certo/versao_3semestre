package br.com.custocerto;

/**
 * Ponto de entrada alternativo, usado ao rodar a aplicação pela IDE.
 *
 * <p><b>Por que esta classe existe.</b> Desde o Java 11 o JavaFX deixou de vir
 * dentro do JDK e passou a ser uma dependência comum, resolvida aqui pelo Maven.
 * A JVM, porém, recusa iniciar uma classe que estenda
 * {@link javafx.application.Application} quando o JavaFX está no <em>classpath</em>
 * em vez do <em>module path</em>, e encerra com a mensagem
 * <em>"JavaFX runtime components are missing"</em>.</p>
 *
 * <p>Esta classe contorna isso pelo caminho mais simples: como ela <b>não</b>
 * estende {@code Application}, a verificação da JVM não se aplica, e o JavaFX é
 * iniciado normalmente quando ela chama {@link App#main(String[])}.</p>
 *
 * <p>Na prática: no Eclipse, no IntelliJ ou no NetBeans, use <b>Run As → Java
 * Application</b> sobre esta classe. Pela linha de comando continua valendo
 * {@code mvnw javafx:run}, que monta o module path sozinho e pode apontar
 * direto para {@link App}.</p>
 *
 * @author Equipe Custo Certo - FATEC Indaiatuba
 * @version 1.0
 * @see App
 */
public final class Launcher {

    /**
     * Classe utilitária de inicialização: não deve ser instanciada.
     */
    private Launcher() {
    }

    /**
     * Inicia a aplicação, repassando os argumentos recebidos.
     *
     * @param args argumentos de linha de comando, repassados ao JavaFX
     */
    public static void main(String[] args) {
        App.main(args);
    }
}
