package br.com.custocerto.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A diferença, para um insumo, entre o que as vendas pediam e o que a balança pesou.
 *
 * <p>É o indicador mais valioso da gestão de food service, e o mais difícil de
 * obter: quase todo sistema comercial <em>estima</em> o consumo multiplicando as
 * vendas pela ficha técnica, e assume que a cozinha seguiu a receita. O Custo
 * Certo mede o consumo real na balança, então consegue comparar os dois lados.</p>
 *
 * <ul>
 *   <li><b>Teórico</b> — vendas do período × ficha técnica. O que <em>deveria</em>
 *       ter saído do estoque.</li>
 *   <li><b>Real</b> — a soma das saídas registradas. O que de fato saiu.</li>
 * </ul>
 *
 * <p>A diferença entre eles não some no ar: é dose exagerada, desperdício, quebra
 * ou desvio. A referência do setor é variância abaixo de 2% para uma operação bem
 * controlada, e de 2% a 5% como o comum.</p>
 *
 * <p><b>Um caso que precisa de cuidado:</b> nem todo insumo passa pela balança.
 * Copo descartável, canela e creme de leite entram nas fichas mas não têm saída
 * registrada. Para eles não existe lado real, e a classe marca isso com
 * {@link #isSemBaixaRegistrada()} em vez de acusar 100% de variância — um número
 * que estaria errado e destruiria a confiança no indicador inteiro.</p>
 *
 * @author Equipe Custo Certo - FATEC Indaiatuba
 * @version 1.0
 */
public class VarianciaInsumo {

    /** Variância até este percentual indica operação bem controlada. */
    public static final double LIMITE_BOM = 2.0;

    /** Variância até este percentual é considerada típica pelo setor. */
    public static final double LIMITE_TIPICO = 5.0;

    private int ingredienteId;
    private String nome;
    private Unidade unidade;
    private BigDecimal precoUnitario = BigDecimal.ZERO;
    private BigDecimal teorico = BigDecimal.ZERO;
    private BigDecimal real = BigDecimal.ZERO;

    /**
     * Calcula quanto saiu além do previsto, na unidade do insumo.
     *
     * @return real menos teórico; negativo quando saiu menos que o previsto
     */
    public BigDecimal getDiferenca() {
        return real.subtract(teorico).setScale(3, RoundingMode.HALF_UP);
    }

    /**
     * Calcula quanto a diferença custou.
     *
     * @return a diferença × preço atual do insumo
     */
    public BigDecimal getCustoDiferenca() {
        return getDiferenca().multiply(precoUnitario).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calcula a variância percentual sobre o teórico.
     *
     * @return o percentual de desvio, ou zero quando não há base teórica
     */
    public double getPercentual() {
        if (teorico.signum() <= 0) {
            return 0;
        }
        return getDiferenca().doubleValue() / teorico.doubleValue() * 100.0;
    }

    /**
     * Informa se este insumo não tem consumo real medido.
     *
     * <p>Acontece com o que não passa pela balança. Sem o lado real, a variância
     * não significa nada e não deve ser exibida como número.</p>
     *
     * @return {@code true} quando há ficha técnica mas nenhuma saída registrada
     */
    public boolean isSemBaixaRegistrada() {
        return teorico.signum() > 0 && real.signum() == 0;
    }

    /**
     * Classifica a variância segundo as faixas usadas no setor.
     *
     * @return {@code "sem-dado"}, {@code "bom"}, {@code "tipico"} ou {@code "ruim"}
     */
    public String getClassificacao() {
        if (isSemBaixaRegistrada() || teorico.signum() <= 0) {
            return "sem-dado";
        }
        double absoluto = Math.abs(getPercentual());
        if (absoluto <= LIMITE_BOM) {
            return "bom";
        }
        return absoluto <= LIMITE_TIPICO ? "tipico" : "ruim";
    }

    /**
     * Monta a frase que explica o resultado ao usuário.
     *
     * @return o texto exibido junto do número
     */
    public String getRotulo() {
        return switch (getClassificacao()) {
            case "sem-dado" -> "Este insumo não passa pela balança — sem consumo real para comparar.";
            case "bom" -> "Dentro do esperado para uma operação controlada.";
            case "tipico" -> "Variação comum, mas vale acompanhar.";
            default -> getDiferenca().signum() > 0
                    ? "Saiu bem mais do que as vendas pediam. Investigar dose, desperdício ou perda."
                    : "Saiu bem menos do que as vendas pediam. Confira se as baixas estão sendo registradas.";
        };
    }

    /**
     * Retorna a chave do insumo.
     *
     * @return o identificador no banco
     */
    public int getIngredienteId() {
        return ingredienteId;
    }

    /**
     * Define a chave do insumo.
     *
     * @param ingredienteId o identificador no banco
     */
    public void setIngredienteId(int ingredienteId) {
        this.ingredienteId = ingredienteId;
    }

    /**
     * Retorna o nome do insumo.
     *
     * @return o nome
     */
    public String getNome() {
        return nome;
    }

    /**
     * Define o nome do insumo.
     *
     * @param nome o nome
     */
    public void setNome(String nome) {
        this.nome = nome;
    }

    /**
     * Retorna a unidade de medida.
     *
     * @return a unidade
     */
    public Unidade getUnidade() {
        return unidade;
    }

    /**
     * Define a unidade de medida.
     *
     * @param unidade a unidade
     */
    public void setUnidade(Unidade unidade) {
        this.unidade = unidade;
    }

    /**
     * Retorna o preço atual do insumo.
     *
     * @return o preço por unidade de medida
     */
    public BigDecimal getPrecoUnitario() {
        return precoUnitario;
    }

    /**
     * Define o preço atual do insumo.
     *
     * @param precoUnitario o preço por unidade de medida
     */
    public void setPrecoUnitario(BigDecimal precoUnitario) {
        this.precoUnitario = precoUnitario;
    }

    /**
     * Retorna quanto as vendas do período pediam deste insumo.
     *
     * @return a quantidade teórica
     */
    public BigDecimal getTeorico() {
        return teorico;
    }

    /**
     * Define quanto as vendas do período pediam deste insumo.
     *
     * @param teorico a quantidade teórica
     */
    public void setTeorico(BigDecimal teorico) {
        this.teorico = teorico;
    }

    /**
     * Retorna quanto de fato saiu do estoque no período.
     *
     * @return a quantidade real
     */
    public BigDecimal getReal() {
        return real;
    }

    /**
     * Define quanto de fato saiu do estoque no período.
     *
     * @param real a quantidade real
     */
    public void setReal(BigDecimal real) {
        this.real = real;
    }

    /**
     * Retorna a variância descrita em texto.
     *
     * @return o nome do insumo e o desvio percentual
     */
    @Override
    public String toString() {
        return nome + " — " + String.format("%+.1f%%", getPercentual());
    }
}
