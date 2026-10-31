package br.com.custocerto.dao;

import br.com.custocerto.model.ItemFicha;
import br.com.custocerto.model.Receita;
import br.com.custocerto.model.Unidade;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Acesso às tabelas {@code receitas} e {@code receita_ingredientes}.
 *
 * <p>Trata as duas juntas de propósito: uma ficha técnica sem os seus itens não
 * significa nada, e gravar as duas metades separadamente abriria a possibilidade
 * de um produto ficar com a ficha pela metade se algo falhasse no meio. Por isso
 * {@link #inserir(Receita)} e {@link #atualizar(Receita)} rodam dentro de uma
 * transação.</p>
 *
 * <p>O custo do produto <b>não é gravado</b> em lugar nenhum. Ele é somado a cada
 * leitura, a partir do preço que o insumo tem agora — é o que faz o custo do
 * cappuccino mudar sozinho quando o café é reajustado.</p>
 *
 * @author Equipe Custo Certo - FATEC Indaiatuba
 * @version 1.0
 */
public class ReceitaDAO {

    /** Colunas da receita, repetidas nas consultas de leitura. */
    private static final String CAMPOS =
            "id, nome, descricao, preco_venda, ativo";

    /**
     * Lista todos os produtos, cada um com a sua ficha técnica preenchida.
     *
     * <p>Usa duas consultas em vez de uma junção só: uma junção traria a receita
     * repetida uma vez por insumo, e remontar os objetos a partir disso é mais
     * trabalhoso e mais fácil de errar do que carregar as fichas de uma vez e
     * distribuí-las por chave.</p>
     *
     * @return os produtos em ordem alfabética
     * @throws SQLException se houver falha de comunicação com o banco
     */
    public List<Receita> listarTodas() throws SQLException {
        String sql = "SELECT " + CAMPOS + " FROM receitas ORDER BY nome";

        Map<Integer, Receita> porId = new LinkedHashMap<>();

        try (Connection conexao = ConexaoMySQL.obter();
             PreparedStatement comando = conexao.prepareStatement(sql);
             ResultSet resultado = comando.executeQuery()) {

            while (resultado.next()) {
                Receita r = montar(resultado);
                porId.put(r.getId(), r);
            }
        }

        if (porId.isEmpty()) {
            return new ArrayList<>();
        }

        for (ItemFicha item : listarItens(null)) {
            Receita dona = porId.get(item.getReceitaId());
            if (dona != null) {
                dona.getItens().add(item);
            }
        }

        return new ArrayList<>(porId.values());
    }

    /**
     * Busca um produto pela chave primária, com a ficha técnica preenchida.
     *
     * @param id a chave do produto
     * @return o produto, ou {@link Optional#empty()} se não existir
     * @throws SQLException se houver falha de comunicação com o banco
     */
    public Optional<Receita> buscarPorId(int id) throws SQLException {
        String sql = "SELECT " + CAMPOS + " FROM receitas WHERE id = ?";

        Receita receita = null;
        try (Connection conexao = ConexaoMySQL.obter();
             PreparedStatement comando = conexao.prepareStatement(sql)) {

            comando.setInt(1, id);
            try (ResultSet resultado = comando.executeQuery()) {
                if (resultado.next()) {
                    receita = montar(resultado);
                }
            }
        }

        if (receita == null) {
            return Optional.empty();
        }

        receita.getItens().addAll(listarItens(id));
        return Optional.of(receita);
    }

    /**
     * Lê as linhas de ficha técnica, com nome, unidade e preço atual do insumo.
     *
     * @param receitaId a receita desejada, ou {@code null} para trazer todas
     * @return as linhas encontradas
     * @throws SQLException se houver falha de comunicação com o banco
     */
    private List<ItemFicha> listarItens(Integer receitaId) throws SQLException {
        StringBuilder sql = new StringBuilder("""
                SELECT ri.id, ri.receita_id, ri.ingrediente_id, ri.quantidade,
                       i.nome AS nome_ingrediente, i.unidade, i.preco
                  FROM receita_ingredientes ri
                  JOIN ingredientes i ON i.id = ri.ingrediente_id
                """);

        if (receitaId != null) {
            sql.append(" WHERE ri.receita_id = ?");
        }
        sql.append(" ORDER BY i.nome");

        List<ItemFicha> itens = new ArrayList<>();

        try (Connection conexao = ConexaoMySQL.obter();
             PreparedStatement comando = conexao.prepareStatement(sql.toString())) {

            if (receitaId != null) {
                comando.setInt(1, receitaId);
            }

            try (ResultSet resultado = comando.executeQuery()) {
                while (resultado.next()) {
                    ItemFicha item = new ItemFicha();
                    item.setId(resultado.getInt("id"));
                    item.setReceitaId(resultado.getInt("receita_id"));
                    item.setIngredienteId(resultado.getInt("ingrediente_id"));
                    item.setQuantidade(resultado.getBigDecimal("quantidade"));
                    item.setNomeIngrediente(resultado.getString("nome_ingrediente"));
                    item.setUnidade(Unidade.deSigla(resultado.getString("unidade")));
                    item.setPrecoUnitario(resultado.getBigDecimal("preco"));
                    itens.add(item);
                }
            }
        }
        return itens;
    }

    /**
     * Insere um produto e a sua ficha técnica, tudo ou nada.
     *
     * @param receita o produto a cadastrar, com os itens preenchidos
     * @return o mesmo produto, agora com o {@code id} gerado pelo banco
     * @throws SQLException se o nome já existir ou houver falha de comunicação
     */
    public Receita inserir(Receita receita) throws SQLException {
        String sql = """
                INSERT INTO receitas (nome, descricao, preco_venda, ativo)
                VALUES (?, ?, ?, ?)
                """;

        Connection conexao = null;
        try {
            conexao = ConexaoMySQL.obter();
            conexao.setAutoCommit(false);

            try (PreparedStatement comando =
                         conexao.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

                comando.setString(1, receita.getNome());
                comando.setString(2, receita.getDescricao());
                comando.setBigDecimal(3, receita.getPrecoVenda());
                comando.setBoolean(4, receita.isAtivo());
                comando.executeUpdate();

                try (ResultSet chaves = comando.getGeneratedKeys()) {
                    if (chaves.next()) {
                        receita.setId(chaves.getInt(1));
                    }
                }
            }

            gravarItens(conexao, receita);
            conexao.commit();
            return receita;

        } catch (SQLException e) {
            desfazer(conexao);
            throw e;
        } finally {
            fechar(conexao);
        }
    }

    /**
     * Atualiza um produto e substitui a ficha técnica inteira, tudo ou nada.
     *
     * <p>A ficha é apagada e regravada em vez de comparada linha a linha. Para uma
     * lista de meia dúzia de insumos, calcular o que mudou custaria mais código do
     * que a operação economiza — e daria mais chance de deixar uma linha órfã.</p>
     *
     * @param receita o produto com os dados novos e o {@code id} preenchido
     * @throws SQLException se o nome colidir com outro produto ou houver falha
     */
    public void atualizar(Receita receita) throws SQLException {
        String sql = """
                UPDATE receitas
                   SET nome = ?, descricao = ?, preco_venda = ?, ativo = ?
                 WHERE id = ?
                """;

        Connection conexao = null;
        try {
            conexao = ConexaoMySQL.obter();
            conexao.setAutoCommit(false);

            try (PreparedStatement comando = conexao.prepareStatement(sql)) {
                comando.setString(1, receita.getNome());
                comando.setString(2, receita.getDescricao());
                comando.setBigDecimal(3, receita.getPrecoVenda());
                comando.setBoolean(4, receita.isAtivo());
                comando.setInt(5, receita.getId());
                comando.executeUpdate();
            }

            try (PreparedStatement limpeza = conexao.prepareStatement(
                    "DELETE FROM receita_ingredientes WHERE receita_id = ?")) {
                limpeza.setInt(1, receita.getId());
                limpeza.executeUpdate();
            }

            gravarItens(conexao, receita);
            conexao.commit();

        } catch (SQLException e) {
            desfazer(conexao);
            throw e;
        } finally {
            fechar(conexao);
        }
    }

    /**
     * Grava as linhas da ficha técnica dentro da transação já aberta.
     *
     * @param conexao a conexão em transação
     * @param receita o produto cujos itens serão gravados
     * @throws SQLException se alguma linha for recusada
     */
    private void gravarItens(Connection conexao, Receita receita) throws SQLException {
        if (receita.getItens().isEmpty()) {
            return;
        }

        String sql = """
                INSERT INTO receita_ingredientes (receita_id, ingrediente_id, quantidade)
                VALUES (?, ?, ?)
                """;

        try (PreparedStatement comando = conexao.prepareStatement(sql)) {
            for (ItemFicha item : receita.getItens()) {
                comando.setInt(1, receita.getId());
                comando.setInt(2, item.getIngredienteId());
                comando.setBigDecimal(3, item.getQuantidade());
                comando.addBatch();
            }
            comando.executeBatch();
        }
    }

    /**
     * Exclui um produto e, em cascata, a ficha técnica dele.
     *
     * <p>O banco recusa a exclusão se o produto já tiver vendas registradas: a
     * chave estrangeira de {@code vendas} usa {@code ON DELETE RESTRICT}, para que
     * o histórico de faturamento não fique órfão.</p>
     *
     * @param id a chave do produto
     * @return {@code true} se alguma linha foi removida
     * @throws SQLException se o produto tiver vendas ou houver falha de comunicação
     */
    public boolean excluir(int id) throws SQLException {
        try (Connection conexao = ConexaoMySQL.obter();
             PreparedStatement comando =
                     conexao.prepareStatement("DELETE FROM receitas WHERE id = ?")) {

            comando.setInt(1, id);
            return comando.executeUpdate() > 0;
        }
    }

    /**
     * Conta quantas vendas existem para um produto.
     *
     * <p>Usada para explicar ao usuário, antes de tentar, por que a exclusão não
     * vai passar — em vez de deixá-lo esbarrar na mensagem de chave estrangeira.</p>
     *
     * @param receitaId a chave do produto
     * @return o número de lançamentos de venda
     * @throws SQLException se houver falha de comunicação com o banco
     */
    public int contarVendas(int receitaId) throws SQLException {
        try (Connection conexao = ConexaoMySQL.obter();
             PreparedStatement comando = conexao.prepareStatement(
                     "SELECT COUNT(*) AS total FROM vendas WHERE receita_id = ?")) {

            comando.setInt(1, receitaId);
            try (ResultSet resultado = comando.executeQuery()) {
                return resultado.next() ? resultado.getInt("total") : 0;
            }
        }
    }

    /**
     * Converte a linha atual do resultado em um produto, sem a ficha técnica.
     *
     * @param resultado o resultado posicionado sobre a linha
     * @return o produto correspondente
     * @throws SQLException se alguma coluna não puder ser lida
     */
    private Receita montar(ResultSet resultado) throws SQLException {
        Receita r = new Receita();
        r.setId(resultado.getInt("id"));
        r.setNome(resultado.getString("nome"));
        r.setDescricao(resultado.getString("descricao"));
        r.setPrecoVenda(resultado.getBigDecimal("preco_venda"));
        r.setAtivo(resultado.getBoolean("ativo"));
        return r;
    }

    /**
     * Desfaz a transação, engolindo falhas do próprio rollback.
     *
     * @param conexao a conexão a desfazer, possivelmente nula
     */
    private void desfazer(Connection conexao) {
        if (conexao == null) {
            return;
        }
        try {
            conexao.rollback();
        } catch (SQLException ignorada) {
            System.err.println("[dao] Falha ao desfazer a transação: " + ignorada.getMessage());
        }
    }

    /**
     * Devolve a conexão ao modo normal e a fecha.
     *
     * @param conexao a conexão a fechar, possivelmente nula
     */
    private void fechar(Connection conexao) {
        if (conexao == null) {
            return;
        }
        try {
            conexao.setAutoCommit(true);
            conexao.close();
        } catch (SQLException ignorada) {
            System.err.println("[dao] Falha ao fechar a conexão: " + ignorada.getMessage());
        }
    }
}
