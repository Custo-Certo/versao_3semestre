package br.com.custocerto.service;

import br.com.custocerto.dao.ExecucaoReceitaDAO;
import br.com.custocerto.model.ExecucaoReceita;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

/**
 * Regras das receitas pesadas na balança.
 *
 * <p>Valida o que o usuário digitou antes de chegar ao banco e traduz as falhas
 * do banco em mensagens que explicam o que fazer. As regras que dependem do
 * estado do banco — uma receita aberta por vez, estoque suficiente — ficam no
 * {@link ExecucaoReceitaDAO}, dentro da transação, porque só ali a resposta é
 * confiável quando várias máquinas usam o sistema ao mesmo tempo.</p>
 *
 * @author Equipe Custo Certo - FATEC Indaiatuba
 * @version 1.0
 */
public class ExecucaoReceitaService {

    /** Quantas execuções o histórico mostra. */
    private static final int LIMITE_HISTORICO = 200;

    private final ExecucaoReceitaDAO dao;

    /**
     * Cria o serviço com o DAO padrão.
     */
    public ExecucaoReceitaService() {
        this(new ExecucaoReceitaDAO());
    }

    /**
     * Cria o serviço com um DAO específico.
     *
     * @param dao o acesso a dados
     */
    public ExecucaoReceitaService(ExecucaoReceitaDAO dao) {
        this.dao = dao;
    }

    /**
     * Lista o histórico de receitas, das mais recentes para as mais antigas.
     *
     * @return as receitas, sem os itens
     * @throws CardapioException se o banco falhar
     */
    public List<ExecucaoReceita> listar() throws CardapioException {
        try {
            return dao.listar(LIMITE_HISTORICO);
        } catch (SQLException e) {
            throw traduzir(e);
        }
    }

    /**
     * Busca uma receita com todos os ingredientes pesados.
     *
     * @param id a chave da receita
     * @return a receita completa, ou {@code null}
     * @throws CardapioException se o banco falhar
     */
    public ExecucaoReceita buscar(int id) throws CardapioException {
        try {
            return dao.buscar(id);
        } catch (SQLException e) {
            throw traduzir(e);
        }
    }

    /**
     * Busca a receita em andamento, iniciada aqui ou no painel web.
     *
     * @return a receita aberta, ou {@code null}
     * @throws CardapioException se o banco falhar
     */
    public ExecucaoReceita buscarAberta() throws CardapioException {
        try {
            return dao.buscarAberta();
        } catch (SQLException e) {
            throw traduzir(e);
        }
    }

    /**
     * Inicia uma receita.
     *
     * @param nome o nome da receita
     * @throws CardapioException se o nome for inválido ou já houver uma aberta
     */
    public void iniciar(String nome) throws CardapioException {
        String limpo = nome == null ? "" : nome.trim();
        if (limpo.length() < 2) {
            throw new CardapioException("Informe o nome da receita.");
        }
        if (limpo.length() > 120) {
            throw new CardapioException("O nome da receita pode ter no máximo 120 caracteres.");
        }
        try {
            dao.iniciar(limpo);
        } catch (SQLException e) {
            throw traduzir(e);
        }
    }

    /**
     * Pesa um ingrediente para a receita aberta, abatendo o estoque.
     *
     * @param execucaoId    a receita aberta
     * @param ingredienteId o insumo
     * @param quantidade    quanto saiu, na unidade do insumo
     * @throws CardapioException se a quantidade for inválida ou faltar estoque
     */
    public void adicionarItem(int execucaoId, int ingredienteId, BigDecimal quantidade)
            throws CardapioException {
        if (ingredienteId <= 0) {
            throw new CardapioException("Escolha o ingrediente.");
        }
        if (quantidade == null || quantidade.signum() <= 0) {
            throw new CardapioException("A quantidade precisa ser maior que zero.");
        }
        try {
            dao.adicionarItem(execucaoId, ingredienteId, quantidade);
        } catch (SQLException e) {
            throw traduzir(e);
        }
    }

    /**
     * Desfaz a pesagem de um ingrediente, devolvendo-o ao estoque.
     *
     * @param execucaoId     a receita aberta
     * @param movimentacaoId a linha a desfazer
     * @throws CardapioException se a linha não puder ser desfeita
     */
    public void estornarItem(int execucaoId, int movimentacaoId) throws CardapioException {
        try {
            dao.estornarItem(execucaoId, movimentacaoId);
        } catch (SQLException e) {
            throw traduzir(e);
        }
    }

    /**
     * Finaliza a receita aberta.
     *
     * @param execucao   a receita aberta, com os itens carregados
     * @param porcoes    quantas porções rendeu, ou {@code null}
     * @param precoVenda o preço de cada porção, ou {@code null}
     * @throws CardapioException se os valores forem inválidos
     */
    public void finalizar(ExecucaoReceita execucao, Integer porcoes, BigDecimal precoVenda)
            throws CardapioException {
        if (execucao.getItens().isEmpty()) {
            throw new CardapioException("Pese pelo menos um ingrediente antes de finalizar, "
                    + "ou cancele a receita.");
        }
        if (porcoes != null && porcoes <= 0) {
            throw new CardapioException("As porções precisam ser maiores que zero.");
        }
        if (precoVenda != null && precoVenda.signum() < 0) {
            throw new CardapioException("O preço de venda não pode ser negativo.");
        }
        try {
            dao.finalizar(execucao.getId(), porcoes, precoVenda);
        } catch (SQLException e) {
            throw traduzir(e);
        }
    }

    /**
     * Cancela a receita aberta e devolve tudo ao estoque.
     *
     * @param execucaoId a receita aberta
     * @throws CardapioException se não puder ser cancelada
     */
    public void cancelar(int execucaoId) throws CardapioException {
        try {
            dao.cancelar(execucaoId);
        } catch (SQLException e) {
            throw traduzir(e);
        }
    }

    /**
     * Converte uma falha do banco na mensagem exibida ao usuário.
     *
     * <p>Separa três casos: regra de negócio, que já vem com o texto pronto;
     * tabela ausente, que acontece quando o banco não recebeu o script de
     * receitas; e o resto, tratado como falha de conexão.</p>
     *
     * @param e a falha original
     * @return a exceção com a mensagem adequada
     */
    private CardapioException traduzir(SQLException e) {
        if (e instanceof ExecucaoReceitaDAO.RegraException) {
            return new CardapioException(e.getMessage(), e);
        }
        String texto = e.getMessage() == null ? "" : e.getMessage();
        if (texto.contains("execucoes_receita") || texto.contains("execucao_id")) {
            return new CardapioException("O banco ainda não tem as tabelas de receitas. "
                    + "Rode o script 03_schema_receitas.sql no servidor MySQL.", e);
        }
        return new CardapioException("Não foi possível falar com o banco de dados. "
                + "Verifique se o servidor MySQL está ligado e se esta máquina está na "
                + "mesma rede.", e);
    }
}
