package br.com.custocerto.service;

import br.com.custocerto.dao.VendaDAO;
import br.com.custocerto.model.Periodo;
import br.com.custocerto.model.Venda;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/**
 * Regras de negócio do registro de vendas.
 *
 * <p>É o serviço por trás da tela de Vendas. O escopo é deliberadamente pequeno:
 * registrar o que foi vendido, quando e por quanto. Não é um ponto de venda, e não
 * pretende ser — o objetivo é ter o lado da receita, sem o qual nenhum indicador
 * de margem existe.</p>
 *
 * <p><b>Registrar uma venda não mexe no estoque.</b> Isso é intencional e é o que
 * torna possível o indicador mais valioso do sistema: a baixa dos insumos acontece
 * pela balança, em {@code movimentacoes_estoque}, de forma independente. Como os
 * dois registros são feitos por caminhos diferentes, o sistema pode comparar
 * quanto as vendas <em>pediam</em> com quanto de fato <em>saiu</em>. Se a venda
 * abatesse o estoque automaticamente, os dois números seriam iguais por
 * construção, e a comparação não diria nada.</p>
 *
 * @author Equipe Custo Certo - FATEC Indaiatuba
 * @version 1.0
 */
public class VendaService {

    /** Teto de linhas trazidas por consulta, para a tela não travar. */
    private static final int LIMITE_PADRAO = 300;

    private final VendaDAO vendaDAO;

    /**
     * Cria o serviço com o DAO padrão.
     */
    public VendaService() {
        this(new VendaDAO());
    }

    /**
     * Cria o serviço com um DAO informado por quem chama.
     *
     * @param vendaDAO o DAO da tabela {@code vendas}
     */
    public VendaService(VendaDAO vendaDAO) {
        this.vendaDAO = vendaDAO;
    }

    /**
     * Lista as vendas de um período, da mais recente para a mais antiga.
     *
     * @param periodo o intervalo consultado
     * @return as vendas encontradas
     * @throws CardapioException se o banco não responder
     */
    public List<Venda> listar(Periodo periodo) throws CardapioException {
        try {
            return vendaDAO.listar(periodo, LIMITE_PADRAO);
        } catch (SQLException e) {
            throw new CardapioException(mensagemDeConexao(), e);
        }
    }

    /**
     * Registra uma venda.
     *
     * @param venda a venda vinda do formulário
     * @return a venda gravada, com a chave primária preenchida
     * @throws CardapioException se algum campo estiver inválido ou o banco recusar
     */
    public Venda registrar(Venda venda) throws CardapioException {
        if (venda.getReceitaId() <= 0) {
            throw new CardapioException("Escolha o produto vendido.");
        }
        if (venda.getQuantidade() <= 0) {
            throw new CardapioException("A quantidade vendida precisa ser maior que zero.");
        }

        BigDecimal preco = venda.getPrecoUnitario();
        if (preco == null || preco.signum() < 0) {
            throw new CardapioException("Informe o preço praticado por unidade.");
        }
        if (venda.getData() == null) {
            throw new CardapioException("Informe a data da venda.");
        }
        if (venda.getData().isAfter(LocalDate.now())) {
            throw new CardapioException("A data da venda está no futuro.");
        }

        try {
            return vendaDAO.inserir(venda);
        } catch (SQLException e) {
            String texto = e.getMessage() == null ? "" : e.getMessage();
            if (texto.contains("fk_vendas_receita")) {
                throw new CardapioException(
                        "O produto escolhido não existe mais. Atualize a lista e tente de novo.", e);
            }
            throw new CardapioException(mensagemDeConexao(), e);
        }
    }

    /**
     * Remove um lançamento de venda.
     *
     * @param venda o lançamento a remover
     * @throws CardapioException se o banco não responder
     */
    public void excluir(Venda venda) throws CardapioException {
        try {
            vendaDAO.excluir(venda.getId());
        } catch (SQLException e) {
            throw new CardapioException(mensagemDeConexao(), e);
        }
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
