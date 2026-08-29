package br.com.custocerto.dao;

import br.com.custocerto.model.Perfil;
import br.com.custocerto.model.Usuario;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Acesso à tabela {@code usuarios} do MySQL.
 *
 * <p>DAO significa <em>Data Access Object</em>: é a única camada que escreve SQL.
 * As camadas acima ({@code service} e {@code view}) trabalham apenas com objetos
 * {@link Usuario} e não sabem como os dados são gravados — se um dia o banco mudar,
 * só esta classe precisa ser reescrita.</p>
 *
 * <p>Todas as consultas usam {@link PreparedStatement} com marcadores {@code ?}.
 * Os valores digitados pelo usuário viajam separados do texto do comando SQL, e por
 * isso não conseguem alterar o seu significado. Sem isso, digitar
 * {@code ' OR '1'='1} no campo de senha derrubaria a autenticação — é a falha de
 * injeção de SQL.</p>
 *
 * @author Equipe Custo Certo - FATEC Indaiatuba
 * @version 1.0
 */
public class UsuarioDAO {

    /**
     * Busca um usuário pelo e-mail, que é o login do sistema.
     *
     * @param email o e-mail digitado na tela de login
     * @return o usuário encontrado, ou {@link Optional#empty()} se não existir
     * @throws SQLException se houver falha de comunicação com o banco
     */
    public Optional<Usuario> buscarPorEmail(String email) throws SQLException {
        String sql = """
                SELECT id, nome, email, senha_hash, salt, perfil, ativo, criado_em, ultimo_acesso
                  FROM usuarios
                 WHERE email = ?
                """;

        try (Connection conexao = ConexaoMySQL.obter();
             PreparedStatement comando = conexao.prepareStatement(sql)) {

            comando.setString(1, email);

            try (ResultSet resultado = comando.executeQuery()) {
                if (resultado.next()) {
                    return Optional.of(montar(resultado));
                }
                return Optional.empty();
            }
        }
    }

    /**
     * Lista todos os usuários cadastrados, em ordem alfabética.
     *
     * @return a lista de usuários
     * @throws SQLException se houver falha de comunicação com o banco
     */
    public List<Usuario> listarTodos() throws SQLException {
        String sql = """
                SELECT id, nome, email, senha_hash, salt, perfil, ativo, criado_em, ultimo_acesso
                  FROM usuarios
                 ORDER BY nome
                """;

        List<Usuario> usuarios = new ArrayList<>();
        try (Connection conexao = ConexaoMySQL.obter();
             PreparedStatement comando = conexao.prepareStatement(sql);
             ResultSet resultado = comando.executeQuery()) {

            while (resultado.next()) {
                usuarios.add(montar(resultado));
            }
        }
        return usuarios;
    }

    /**
     * Insere um novo usuário e devolve o objeto com a chave primária preenchida.
     *
     * @param usuario o usuário a cadastrar, já com salt e hash calculados
     * @return o mesmo usuário, agora com o {@code id} gerado pelo banco
     * @throws SQLException se o e-mail já existir ({@code UNIQUE}) ou houver falha
     *                      de comunicação
     */
    public Usuario inserir(Usuario usuario) throws SQLException {
        String sql = """
                INSERT INTO usuarios (nome, email, senha_hash, salt, perfil, ativo)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        try (Connection conexao = ConexaoMySQL.obter();
             PreparedStatement comando = conexao.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            comando.setString(1, usuario.getNome());
            comando.setString(2, usuario.getEmail());
            comando.setString(3, usuario.getSenhaHash());
            comando.setString(4, usuario.getSalt());
            comando.setString(5, usuario.getPerfil().name());
            comando.setBoolean(6, usuario.isAtivo());
            comando.executeUpdate();

            try (ResultSet chaves = comando.getGeneratedKeys()) {
                if (chaves.next()) {
                    usuario.setId(chaves.getInt(1));
                }
            }
        }
        return usuario;
    }

    /**
     * Grava a data e hora do login bem-sucedido na coluna {@code ultimo_acesso}.
     *
     * @param usuarioId a chave primária do usuário que entrou
     * @throws SQLException se houver falha de comunicação com o banco
     */
    public void registrarAcesso(int usuarioId) throws SQLException {
        String sql = "UPDATE usuarios SET ultimo_acesso = NOW() WHERE id = ?";

        try (Connection conexao = ConexaoMySQL.obter();
             PreparedStatement comando = conexao.prepareStatement(sql)) {

            comando.setInt(1, usuarioId);
            comando.executeUpdate();
        }
    }

    /**
     * Converte a linha atual do {@link ResultSet} em um objeto {@link Usuario}.
     *
     * @param resultado o resultado posicionado sobre a linha a converter
     * @return o usuário correspondente à linha
     * @throws SQLException se alguma coluna não puder ser lida
     */
    private Usuario montar(ResultSet resultado) throws SQLException {
        Usuario usuario = new Usuario();
        usuario.setId(resultado.getInt("id"));
        usuario.setNome(resultado.getString("nome"));
        usuario.setEmail(resultado.getString("email"));
        usuario.setSenhaHash(resultado.getString("senha_hash"));
        usuario.setSalt(resultado.getString("salt"));
        usuario.setPerfil(Perfil.deValorDoBanco(resultado.getString("perfil")));
        usuario.setAtivo(resultado.getBoolean("ativo"));

        Timestamp criadoEm = resultado.getTimestamp("criado_em");
        if (criadoEm != null) {
            usuario.setCriadoEm(criadoEm.toLocalDateTime());
        }

        Timestamp ultimoAcesso = resultado.getTimestamp("ultimo_acesso");
        if (ultimoAcesso != null) {
            usuario.setUltimoAcesso(ultimoAcesso.toLocalDateTime());
        }

        return usuario;
    }
}
