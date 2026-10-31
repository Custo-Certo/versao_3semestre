package br.com.custocerto.service;

import br.com.custocerto.dao.IndicadoresDAO;
import br.com.custocerto.dao.IngredienteDAO;
import br.com.custocerto.model.DesempenhoProduto;
import br.com.custocerto.model.IndicadorInsumo;
import br.com.custocerto.model.Ingrediente;
import br.com.custocerto.model.Periodo;
import br.com.custocerto.model.ResumoFinanceiro;
import br.com.custocerto.model.VarianciaInsumo;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * As regras de análise do negócio: classificar, ordenar e dar sentido aos números
 * que o {@link IndicadoresDAO} traz do banco.
 *
 * <p>A divisão de trabalho é deliberada. O DAO faz o que o banco faz bem — somar,
 * agrupar, cruzar tabelas. Este serviço faz o que exige olhar o conjunto inteiro:
 * a curva ABC precisa da lista ordenada e do total acumulado; a engenharia de
 * cardápio precisa das médias do próprio cardápio para saber o que é "margem alta"
 * naquele contexto. Nenhuma das duas caberia bem em SQL sem ficar ilegível.</p>
 *
 * @author Equipe Custo Certo - FATEC Indaiatuba
 * @version 1.0
 */
public class AnaliseService {

    /** Percentual acumulado até onde os insumos são classe A na curva ABC. */
    private static final double CORTE_A = 80.0;

    /** Percentual acumulado até onde os insumos são classe B na curva ABC. */
    private static final double CORTE_B = 95.0;

    /**
     * Fração da participação média a partir da qual um produto é considerado
     * popular na engenharia de cardápio.
     *
     * <p>Com seis produtos, a participação média é 16,7% das unidades. O corte
     * clássico da técnica não usa a média cheia, e sim 70% dela — aqui, 11,7%.
     * A folga existe porque exigir a média inteira reprovaria quase metade de
     * qualquer cardápio por definição, e a classificação perderia utilidade.</p>
     */
    private static final double FATOR_POPULARIDADE = 0.70;

    private final IndicadoresDAO indicadoresDAO;
    private final IngredienteDAO ingredienteDAO;

    /**
     * Cria o serviço com os DAOs padrão.
     */
    public AnaliseService() {
        this(new IndicadoresDAO(), new IngredienteDAO());
    }

    /**
     * Cria o serviço com DAOs informados por quem chama.
     *
     * @param indicadoresDAO o DAO das consultas analíticas
     * @param ingredienteDAO o DAO dos insumos
     */
    public AnaliseService(IndicadoresDAO indicadoresDAO, IngredienteDAO ingredienteDAO) {
        this.indicadoresDAO = indicadoresDAO;
        this.ingredienteDAO = ingredienteDAO;
    }

    /**
     * Apura receita, custos e compras de um período.
     *
     * @param periodo o intervalo analisado
     * @return o resumo financeiro
     * @throws CardapioException se o banco não responder
     */
    public ResumoFinanceiro resumoDoPeriodo(Periodo periodo) throws CardapioException {
        try {
            return indicadoresDAO.apurarResumo(periodo);
        } catch (SQLException e) {
            throw new CardapioException(mensagemDeConexao(), e);
        }
    }

    /**
     * Levanta os indicadores de giro por insumo e aplica a curva ABC.
     *
     * <p>A lista volta ordenada do insumo que mais consome dinheiro para o que
     * menos consome, que é a ordem em que a curva ABC faz sentido: os primeiros
     * itens concentram a maior parte do custo, e são onde negociar com o fornecedor
     * vale a pena.</p>
     *
     * @param periodo o intervalo analisado
     * @return os indicadores, já classificados e ordenados por custo consumido
     * @throws CardapioException se o banco não responder
     */
    public List<IndicadorInsumo> indicadoresDeInsumo(Periodo periodo) throws CardapioException {
        List<IndicadorInsumo> lista;
        try {
            lista = indicadoresDAO.levantarIndicadores(periodo);
        } catch (SQLException e) {
            throw new CardapioException(mensagemDeConexao(), e);
        }

        lista.sort(Comparator.comparing(IndicadorInsumo::getCustoConsumido).reversed());
        classificarAbc(lista);
        return lista;
    }

    /**
     * Aplica a curva ABC sobre a lista já ordenada por custo consumido.
     *
     * @param lista os indicadores, do maior custo para o menor
     */
    private void classificarAbc(List<IndicadorInsumo> lista) {
        BigDecimal total = BigDecimal.ZERO;
        for (IndicadorInsumo i : lista) {
            total = total.add(i.getCustoConsumido());
        }

        if (total.signum() <= 0) {
            for (IndicadorInsumo i : lista) {
                i.setParticipacaoCusto(0);
                i.setClasse(IndicadorInsumo.ClasseAbc.C);
            }
            return;
        }

        double acumulado = 0;
        for (IndicadorInsumo i : lista) {
            double participacao = i.getCustoConsumido().doubleValue()
                    / total.doubleValue() * 100.0;
            i.setParticipacaoCusto(participacao);

            acumulado += participacao;
            if (acumulado <= CORTE_A) {
                i.setClasse(IndicadorInsumo.ClasseAbc.A);
            } else if (acumulado <= CORTE_B) {
                i.setClasse(IndicadorInsumo.ClasseAbc.B);
            } else {
                i.setClasse(IndicadorInsumo.ClasseAbc.C);
            }
        }
    }

    /**
     * Compara o consumo teórico com o real, insumo por insumo.
     *
     * <p>A ordenação é pelo <b>valor em reais</b> da diferença, e não pelo
     * percentual. Um desvio de 40% em canela custa centavos; um de 11% em café
     * custa dezenas de reais. Ordenar por percentual colocaria o problema
     * irrelevante no topo da tela.</p>
     *
     * @param periodo o intervalo analisado
     * @return as variâncias, da mais cara para a mais barata
     * @throws CardapioException se o banco não responder
     */
    public List<VarianciaInsumo> variancias(Periodo periodo) throws CardapioException {
        List<VarianciaInsumo> lista;
        try {
            lista = indicadoresDAO.compararConsumo(periodo);
        } catch (SQLException e) {
            throw new CardapioException(mensagemDeConexao(), e);
        }

        lista.sort(Comparator.comparing(
                (VarianciaInsumo v) -> v.getCustoDiferenca().abs()).reversed());
        return lista;
    }

    /**
     * Levanta o desempenho dos produtos e os distribui pelos quadrantes da
     * engenharia de cardápio.
     *
     * @param periodo o intervalo analisado
     * @return os produtos, do que mais contribuiu para o que menos contribuiu
     * @throws CardapioException se o banco não responder
     */
    public List<DesempenhoProduto> desempenhoDoCardapio(Periodo periodo)
            throws CardapioException {

        List<DesempenhoProduto> lista;
        try {
            lista = indicadoresDAO.levantarDesempenho(periodo);
        } catch (SQLException e) {
            throw new CardapioException(mensagemDeConexao(), e);
        }

        classificarQuadrantes(lista);
        lista.sort(Comparator.comparing(DesempenhoProduto::getMargemTotal).reversed());
        return lista;
    }

    /**
     * Distribui os produtos pelos quatro quadrantes da engenharia de cardápio.
     *
     * <p>Os cortes são as médias do próprio cardápio no período, e não valores
     * fixos: a margem de referência é a <b>margem média ponderada pelas unidades
     * vendidas</b>, e a popularidade de referência é a participação média
     * multiplicada por {@link #FATOR_POPULARIDADE}.</p>
     *
     * <p>Ficam de fora da conta os produtos sem venda no período e os sem ficha
     * técnica. Um produto sem ficha tem custo zero, e entraria como margem máxima
     * — viraria uma estrela falsa e ainda puxaria a média para cima, bagunçando a
     * classificação de todos os outros.</p>
     *
     * @param lista os produtos levantados
     */
    private void classificarQuadrantes(List<DesempenhoProduto> lista) {
        List<DesempenhoProduto> classificaveis = new ArrayList<>();
        int unidadesTotais = 0;

        for (DesempenhoProduto p : lista) {
            if (p.getUnidadesVendidas() > 0 && p.isTemFicha()) {
                classificaveis.add(p);
                unidadesTotais += p.getUnidadesVendidas();
            }
        }

        if (classificaveis.isEmpty() || unidadesTotais == 0) {
            return;
        }

        BigDecimal margemAcumulada = BigDecimal.ZERO;
        for (DesempenhoProduto p : classificaveis) {
            margemAcumulada = margemAcumulada.add(p.getMargemTotal());
        }

        BigDecimal margemReferencia = margemAcumulada.divide(
                BigDecimal.valueOf(unidadesTotais), 4, RoundingMode.HALF_UP);

        double participacaoReferencia =
                100.0 / classificaveis.size() * FATOR_POPULARIDADE;

        for (DesempenhoProduto p : classificaveis) {
            double participacao = p.getUnidadesVendidas() * 100.0 / unidadesTotais;
            p.setParticipacaoVendas(participacao);

            boolean margemAlta = p.getMargemUnitaria().compareTo(margemReferencia) >= 0;
            boolean popular = participacao >= participacaoReferencia;

            if (margemAlta && popular) {
                p.setQuadrante(DesempenhoProduto.Quadrante.ESTRELA);
            } else if (!margemAlta && popular) {
                p.setQuadrante(DesempenhoProduto.Quadrante.CAVALO);
            } else if (margemAlta) {
                p.setQuadrante(DesempenhoProduto.Quadrante.ENIGMA);
            } else {
                p.setQuadrante(DesempenhoProduto.Quadrante.ABACAXI);
            }
        }
    }

    /**
     * Lista os insumos que já venceram e ainda têm saldo em estoque.
     *
     * <p>É a perda que o painel antigo contava em número de itens. Contar itens não
     * ajuda a decidir nada: dois potes de canela vencidos e dois quilos de café
     * vencidos apareciam como o mesmo "2". O que interessa é quanto dinheiro está
     * parado ali, e é isso que {@link #valorPerdido(List)} soma.</p>
     *
     * @return os insumos vencidos com saldo, do mais caro para o mais barato
     * @throws CardapioException se o banco não responder
     */
    public List<Ingrediente> perdasPorVencimento() throws CardapioException {
        List<Ingrediente> vencidos = new ArrayList<>();
        try {
            for (Ingrediente i : ingredienteDAO.listarTodos()) {
                if (i.isVencido() && i.getQtd().signum() > 0) {
                    vencidos.add(i);
                }
            }
        } catch (SQLException e) {
            throw new CardapioException(mensagemDeConexao(), e);
        }

        vencidos.sort(Comparator.comparing(Ingrediente::getValorEmEstoque).reversed());
        return vencidos;
    }

    /**
     * Soma quanto dinheiro está parado nos insumos informados.
     *
     * @param insumos a lista de insumos
     * @return a soma de preço × quantidade
     */
    public BigDecimal valorPerdido(List<Ingrediente> insumos) {
        BigDecimal total = BigDecimal.ZERO;
        for (Ingrediente i : insumos) {
            total = total.add(i.getValorEmEstoque());
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Mensagem única para falhas de comunicação com o banco.
     *
     * @return o texto exibido ao usuário quando o servidor não responde
     */
    private String mensagemDeConexao() {
        return "Não foi possível falar com o banco de dados. Verifique se o servidor "
                + "MySQL está ligado e se esta máquina está na mesma rede.";
    }
}
