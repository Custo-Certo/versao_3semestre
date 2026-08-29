package br.com.custocerto.service;

import br.com.custocerto.dao.IngredienteDAO;
import br.com.custocerto.dao.MovimentacaoDAO;
import br.com.custocerto.model.Ingrediente;
import br.com.custocerto.model.Movimentacao;
import br.com.custocerto.model.ResumoEstoque;
import br.com.custocerto.model.TipoMovimentacao;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

/**
 * Regras de negócio do estoque.
 *
 * <p>Fica entre as telas e os DAOs: valida o que veio do formulário, decide o que
 * é uma operação válida e traduz as falhas do banco em mensagens que uma pessoa
 * entende. As telas não conhecem SQL nem exceções de JDBC — só
 * {@link EstoqueException}.</p>
 *
 * @author Equipe Custo Certo - FATEC Indaiatuba
 * @version 1.0
 */
public class EstoqueService {

    private final IngredienteDAO ingredienteDAO = new IngredienteDAO();
    private final MovimentacaoDAO movimentacaoDAO = new MovimentacaoDAO();

    /**
     * Lista todos os insumos cadastrados.
     *
     * @return os insumos em ordem alfabética
     * @throws EstoqueException se o banco não responder
     */
    public List<Ingrediente> listarIngredientes() throws EstoqueException {
        try {
            return ingredienteDAO.listarTodos();
        } catch (SQLException e) {
            throw new EstoqueException(mensagemDeConexao(), e);
        }
    }

    /**
     * Calcula os indicadores do painel a partir do estoque atual.
     *
     * @return o resumo com valor total, alertas, vencimentos e CMV
     * @throws EstoqueException se o banco não responder
     */
    public ResumoEstoque calcularResumo() throws EstoqueException {
        return new ResumoEstoque(listarIngredientes());
    }

    /**
     * Cadastra ou atualiza um insumo, conforme ele já tenha chave primária.
     *
     * @param ingrediente o insumo vindo do formulário
     * @throws EstoqueException se algum campo estiver inválido ou o nome repetir
     */
    public void salvar(Ingrediente ingrediente) throws EstoqueException {
        validar(ingrediente);
        try {
            if (ingrediente.getId() == 0) {
                ingredienteDAO.inserir(ingrediente);
            } else {
                ingredienteDAO.atualizar(ingrediente);
            }
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("uk_ingredientes_nome")) {
                throw new EstoqueException(
                        "Já existe um insumo com o nome \"" + ingrediente.getNome() + "\".", e);
            }
            throw new EstoqueException(mensagemDeConexao(), e);
        }
    }

    /**
     * Exclui um insumo e, por consequência da chave estrangeira, o histórico dele.
     *
     * @param id a chave do insumo
     * @throws EstoqueException se o banco não responder
     */
    public void excluir(int id) throws EstoqueException {
        try {
            ingredienteDAO.excluir(id);
        } catch (SQLException e) {
            throw new EstoqueException(mensagemDeConexao(), e);
        }
    }

    /**
     * Registra uma compra: soma ao estoque e guarda o preço pago.
     *
     * @param ingredienteId a chave do insumo
     * @param quantidade    quanto está entrando
     * @param precoUnitario o preço pago por unidade nesta compra
     * @param observacao    texto livre explicando a compra
     * @return o saldo do insumo depois da entrada
     * @throws EstoqueException se os valores forem inválidos ou o banco recusar
     */
    public BigDecimal registrarEntrada(int ingredienteId, BigDecimal quantidade,
                                       BigDecimal precoUnitario, String observacao)
            throws EstoqueException {

        if (quantidade == null || quantidade.signum() <= 0) {
            throw new EstoqueException("A quantidade da compra precisa ser maior que zero.");
        }
        if (precoUnitario == null || precoUnitario.signum() < 0) {
            throw new EstoqueException("Informe o preço pago por unidade.");
        }

        Movimentacao m = new Movimentacao(ingredienteId, TipoMovimentacao.ENTRADA,
                quantidade, precoUnitario, observacao);
        return gravar(m);
    }

    /**
     * Registra uma retirada: subtrai do estoque, sem preço associado.
     *
     * <p>É o que a balança faz quando o operador confirma uma pesagem, e o que a
     * inserção manual faz quando o insumo é contado por unidade.</p>
     *
     * @param ingredienteId a chave do insumo
     * @param quantidade    quanto está saindo
     * @param observacao    texto livre explicando a saída
     * @return o saldo do insumo depois da retirada
     * @throws EstoqueException se a quantidade for inválida ou maior que o estoque
     */
    public BigDecimal registrarSaida(int ingredienteId, BigDecimal quantidade, String observacao)
            throws EstoqueException {

        if (quantidade == null || quantidade.signum() <= 0) {
            throw new EstoqueException("A quantidade retirada precisa ser maior que zero.");
        }

        Movimentacao m = new Movimentacao(ingredienteId, TipoMovimentacao.SAIDA,
                quantidade, null, observacao);
        return gravar(m);
    }

    /**
     * Lista o histórico de movimentações.
     *
     * @param tipo   filtra por entrada ou saída; {@code null} traz as duas
     * @param limite número máximo de linhas
     * @return as movimentações, da mais recente para a mais antiga
     * @throws EstoqueException se o banco não responder
     */
    public List<Movimentacao> listarHistorico(TipoMovimentacao tipo, int limite)
            throws EstoqueException {
        try {
            return movimentacaoDAO.listar(tipo, limite);
        } catch (SQLException e) {
            throw new EstoqueException(mensagemDeConexao(), e);
        }
    }

    /**
     * Envia a movimentação ao DAO e traduz a falha do banco em mensagem legível.
     *
     * @param movimentacao a movimentação a gravar
     * @return o saldo resultante
     * @throws EstoqueException se a operação for recusada
     */
    private BigDecimal gravar(Movimentacao movimentacao) throws EstoqueException {
        try {
            return movimentacaoDAO.registrar(movimentacao);
        } catch (SQLException e) {
            String texto = e.getMessage() == null ? "" : e.getMessage();
            if (texto.startsWith("A retirada") || texto.startsWith("Insumo não encontrado")) {
                throw new EstoqueException(texto, e);
            }
            throw new EstoqueException(mensagemDeConexao(), e);
        }
    }

    /**
     * Valida os campos preenchidos no formulário de insumo.
     *
     * @param i o insumo a validar
     * @throws EstoqueException na primeira regra desrespeitada
     */
    private void validar(Ingrediente i) throws EstoqueException {
        if (i.getNome() == null || i.getNome().trim().length() < 2) {
            throw new EstoqueException("Informe o nome do insumo.");
        }
        if (i.getUnidade() == null) {
            throw new EstoqueException("Escolha a unidade de medida.");
        }
        if (i.getPreco() == null || i.getPreco().signum() < 0) {
            throw new EstoqueException("O preço não pode ser negativo.");
        }
        if (i.getQtd() == null || i.getQtd().signum() < 0) {
            throw new EstoqueException("A quantidade não pode ser negativa.");
        }
        if (i.getQtdMax() == null || i.getQtdMax().signum() < 0) {
            throw new EstoqueException("A capacidade não pode ser negativa.");
        }
        i.setNome(i.getNome().trim());
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
