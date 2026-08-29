package br.com.custocerto.dao;

import br.com.custocerto.model.Ingrediente;
import br.com.custocerto.model.Unidade;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Acesso à tabela {@code ingredientes} do MySQL.
 *
 * <p>Como todo DAO deste projeto, é a única camada que conhece SQL. Todas as
 * consultas usam {@link PreparedStatement}, o que impede injeção de SQL nos
 * campos preenchidos pelo usuário.</p>
 *
 * @author Equipe Custo Certo - FATEC Indaiatuba
 * @version 1.0
 */
public class IngredienteDAO {

    /** Colunas lidas em todas as consultas, na mesma ordem. */
    private static final String CAMPOS =
            "id, nome, unidade, preco, qtd, qtd_max, validade";

    /**
     * Lista todos os insumos em ordem alfabética.
     *
     * @return a lista de insumos cadastrados
     * @throws SQLException se houver falha de comunicação com o banco
     */
    public List<Ingrediente> listarTodos() throws SQLException {
        String sql = "SELECT " + CAMPOS + " FROM ingredientes ORDER BY nome";

        List<Ingrediente> lista = new ArrayList<>();
        try (Connection conexao = ConexaoMySQL.obter();
             PreparedStatement comando = conexao.prepareStatement(sql);
             ResultSet resultado = comando.executeQuery()) {

            while (resultado.next()) {
                lista.add(montar(resultado));
            }
        }
        return lista;
    }

    /**
     * Busca um insumo pela chave primária.
     *
     * @param id a chave do insumo
     * @return o insumo encontrado, ou {@link Optional#empty()} se não existir
     * @throws SQLException se houver falha de comunicação com o banco
     */
    public Optional<Ingrediente> buscarPorId(int id) throws SQLException {
        String sql = "SELECT " + CAMPOS + " FROM ingredientes WHERE id = ?";

        try (Connection conexao = ConexaoMySQL.obter();
             PreparedStatement comando = conexao.prepareStatement(sql)) {

            comando.setInt(1, id);
            try (ResultSet resultado = comando.executeQuery()) {
                return resultado.next() ? Optional.of(montar(resultado)) : Optional.empty();
            }
        }
    }

    /**
     * Insere um insumo novo e devolve o objeto com a chave gerada pelo banco.
     *
     * @param ingrediente o insumo a cadastrar
     * @return o mesmo insumo, agora com o {@code id} preenchido
     * @throws SQLException se o nome já existir ({@code UNIQUE}) ou houver falha
     *                      de comunicação
     */
    public Ingrediente inserir(Ingrediente ingrediente) throws SQLException {
        String sql = """
                INSERT INTO ingredientes (nome, unidade, preco, qtd, qtd_max, validade)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        try (Connection conexao = ConexaoMySQL.obter();
             PreparedStatement comando = conexao.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            preencher(comando, ingrediente);
            comando.executeUpdate();

            try (ResultSet chaves = comando.getGeneratedKeys()) {
                if (chaves.next()) {
                    ingrediente.setId(chaves.getInt(1));
                }
            }
        }
        return ingrediente;
    }

    /**
     * Atualiza os dados de um insumo já cadastrado.
     *
     * @param ingrediente o insumo com os novos valores e o {@code id} preenchido
     * @throws SQLException se houver falha de comunicação com o banco
     */
    public void atualizar(Ingrediente ingrediente) throws SQLException {
        String sql = """
                UPDATE ingredientes
                   SET nome = ?, unidade = ?, preco = ?, qtd = ?, qtd_max = ?, validade = ?
                 WHERE id = ?
                """;

        try (Connection conexao = ConexaoMySQL.obter();
             PreparedStatement comando = conexao.prepareStatement(sql)) {

            preencher(comando, ingrediente);
            comando.setInt(7, ingrediente.getId());
            comando.executeUpdate();
        }
    }

    /**
     * Exclui um insumo.
     *
     * <p>A chave estrangeira de {@code movimentacoes_estoque} está declarada com
     * {@code ON DELETE CASCADE}, então o histórico do insumo é apagado junto —
     * uma movimentação sem insumo não significa nada.</p>
     *
     * @param id a chave do insumo a excluir
     * @throws SQLException se houver falha de comunicação com o banco
     */
    public void excluir(int id) throws SQLException {
        String sql = "DELETE FROM ingredientes WHERE id = ?";

        try (Connection conexao = ConexaoMySQL.obter();
             PreparedStatement comando = conexao.prepareStatement(sql)) {

            comando.setInt(1, id);
            comando.executeUpdate();
        }
    }

    /**
     * Preenche os seis primeiros parâmetros comuns ao INSERT e ao UPDATE.
     *
     * @param comando     o comando preparado
     * @param ingrediente o insumo de origem dos valores
     * @throws SQLException se algum parâmetro não puder ser definido
     */
    private void preencher(PreparedStatement comando, Ingrediente ingrediente) throws SQLException {
        comando.setString(1, ingrediente.getNome());
        comando.setString(2, ingrediente.getUnidade().getSigla());
        comando.setBigDecimal(3, ingrediente.getPreco());
        comando.setBigDecimal(4, ingrediente.getQtd());
        comando.setBigDecimal(5, ingrediente.getQtdMax());
        if (ingrediente.getValidade() == null) {
            comando.setNull(6, Types.DATE);
        } else {
            comando.setDate(6, Date.valueOf(ingrediente.getValidade()));
        }
    }

    /**
     * Converte a linha atual do resultado em um objeto de domínio.
     *
     * @param resultado o resultado posicionado sobre a linha
     * @return o insumo correspondente
     * @throws SQLException se alguma coluna não puder ser lida
     */
    private Ingrediente montar(ResultSet resultado) throws SQLException {
        Ingrediente i = new Ingrediente();
        i.setId(resultado.getInt("id"));
        i.setNome(resultado.getString("nome"));
        i.setUnidade(Unidade.deSigla(resultado.getString("unidade")));
        i.setPreco(resultado.getBigDecimal("preco"));
        i.setQtd(resultado.getBigDecimal("qtd"));
        i.setQtdMax(resultado.getBigDecimal("qtd_max"));

        Date validade = resultado.getDate("validade");
        i.setValidade(validade == null ? null : validade.toLocalDate());
        return i;
    }
}
