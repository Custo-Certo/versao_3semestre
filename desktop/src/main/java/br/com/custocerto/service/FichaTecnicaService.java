package br.com.custocerto.service;

import br.com.custocerto.dao.ReceitaDAO;
import br.com.custocerto.model.ItemFicha;
import br.com.custocerto.model.Receita;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Regras de negócio do cardápio e das fichas técnicas.
 *
 * <p>É o serviço por trás da tela de Ficha Técnica. Fica entre a tela e o
 * {@link ReceitaDAO}, validando o que veio do formulário e traduzindo as recusas
 * do banco em frases que uma pessoa entende.</p>
 *
 * <p>Uma regra merece explicação: o serviço <b>permite salvar um produto sem ficha
 * técnica</b>. Seria fácil exigir ao menos um insumo, mas isso obrigaria a montar
 * a ficha inteira antes de conseguir cadastrar o produto — e na prática as duas
 * coisas acontecem em momentos diferentes. O preço da permissão é que o custo fica
 * zero, e é por isso que {@link Receita#temFicha()} existe: as telas avisam em vez
 * de exibir uma margem igual ao preço inteiro, que estaria errada.</p>
 *
 * @author Equipe Custo Certo - FATEC Indaiatuba
 * @version 1.0
 */
public class FichaTecnicaService {

    private final ReceitaDAO receitaDAO;

    /**
     * Cria o serviço com o DAO padrão.
     */
    public FichaTecnicaService() {
        this(new ReceitaDAO());
    }

    /**
     * Cria o serviço com um DAO informado por quem chama.
     *
     * @param receitaDAO o DAO das tabelas de receita
     */
    public FichaTecnicaService(ReceitaDAO receitaDAO) {
        this.receitaDAO = receitaDAO;
    }

    /**
     * Lista os produtos do cardápio, cada um com a ficha técnica preenchida.
     *
     * @return os produtos em ordem alfabética
     * @throws CardapioException se o banco não responder
     */
    public List<Receita> listar() throws CardapioException {
        try {
            return receitaDAO.listarTodas();
        } catch (SQLException e) {
            throw new CardapioException(mensagemDeConexao(), e);
        }
    }

    /**
     * Cadastra ou atualiza um produto, conforme ele já tenha chave primária.
     *
     * @param receita o produto vindo do formulário, com os itens da ficha
     * @throws CardapioException se algum campo estiver inválido ou o nome repetir
     */
    public void salvar(Receita receita) throws CardapioException {
        validar(receita);

        try {
            if (receita.getId() == 0) {
                receitaDAO.inserir(receita);
            } else {
                receitaDAO.atualizar(receita);
            }
        } catch (SQLException e) {
            throw traduzir(e, receita);
        }
    }

    /**
     * Exclui um produto do cardápio e, em cascata, a ficha técnica dele.
     *
     * <p>Consulta o histórico antes de tentar, para explicar o motivo em vez de
     * deixar o usuário esbarrar na recusa da chave estrangeira.</p>
     *
     * @param receita o produto a excluir
     * @throws CardapioException se o produto já tiver vendas ou o banco não responder
     */
    public void excluir(Receita receita) throws CardapioException {
        try {
            int vendas = receitaDAO.contarVendas(receita.getId());
            if (vendas > 0) {
                throw new CardapioException(
                        "\"" + receita.getNome() + "\" não pode ser excluído porque tem "
                        + vendas + (vendas == 1 ? " venda registrada" : " vendas registradas")
                        + ". Apagá-lo deixaria o faturamento desses dias sem explicação. "
                        + "Se ele saiu do cardápio, desmarque \"ativo\" em vez de excluir.");
            }
            receitaDAO.excluir(receita.getId());
        } catch (SQLException e) {
            throw new CardapioException(mensagemDeConexao(), e);
        }
    }

    /**
     * Valida o produto e a ficha técnica preenchidos no formulário.
     *
     * @param r o produto a validar
     * @throws CardapioException na primeira regra desrespeitada
     */
    private void validar(Receita r) throws CardapioException {
        if (r.getNome() == null || r.getNome().trim().length() < 2) {
            throw new CardapioException("Informe o nome do produto.");
        }
        if (r.getPrecoVenda() == null || r.getPrecoVenda().signum() < 0) {
            throw new CardapioException("O preço de venda não pode ser negativo.");
        }

        Set<Integer> vistos = new HashSet<>();
        for (ItemFicha item : r.getItens()) {
            if (item.getIngredienteId() <= 0) {
                throw new CardapioException("Escolha o insumo em todas as linhas da ficha.");
            }
            BigDecimal qtd = item.getQuantidade();
            if (qtd == null || qtd.signum() <= 0) {
                throw new CardapioException(
                        "A quantidade de \"" + item.getNomeIngrediente()
                        + "\" precisa ser maior que zero.");
            }
            if (!vistos.add(item.getIngredienteId())) {
                throw new CardapioException(
                        "O insumo \"" + item.getNomeIngrediente()
                        + "\" aparece duas vezes na ficha. Some as quantidades em uma linha só.");
            }
        }

        r.setNome(r.getNome().trim());
    }

    /**
     * Traduz a falha do banco na mensagem que a tela deve exibir.
     *
     * @param e       a exceção devolvida pelo JDBC
     * @param receita o produto que estava sendo gravado
     * @return a exceção de serviço correspondente
     */
    private CardapioException traduzir(SQLException e, Receita receita) {
        String texto = e.getMessage() == null ? "" : e.getMessage();

        if (texto.contains("uk_receitas_nome") || texto.contains("Duplicate entry")) {
            return new CardapioException(
                    "Já existe um produto chamado \"" + receita.getNome() + "\".");
        }
        if (texto.contains("uk_ri_receita_ingrediente")) {
            return new CardapioException(
                    "O mesmo insumo aparece duas vezes na ficha técnica.");
        }
        if (texto.contains("ck_ri_quantidade")) {
            return new CardapioException(
                    "As quantidades da ficha técnica precisam ser maiores que zero.");
        }
        if (texto.contains("ck_receitas_preco")) {
            return new CardapioException("O preço de venda não pode ser negativo.");
        }
        return new CardapioException(mensagemDeConexao(), e);
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
