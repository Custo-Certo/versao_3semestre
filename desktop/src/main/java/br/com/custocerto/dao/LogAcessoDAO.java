package br.com.custocerto.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

/**
 * Acesso à tabela {@code log_acesso}, que registra cada tentativa de login.
 *
 * <p>A tabela guarda tanto os acessos bem-sucedidos quanto os recusados, e serve a
 * duas finalidades: dar ao administrador do sistema uma trilha de auditoria — uma
 * das responsabilidades previstas para esse perfil no escopo do projeto — e
 * atender ao requisito de <strong>chave estrangeira</strong> da matéria de Banco de
 * Dados, já que {@code log_acesso.usuario_id} referencia {@code usuarios.id}.</p>
 *
 * <p>A coluna {@code usuario_id} aceita nulo de propósito: quando alguém digita um
 * e-mail que não existe, não há usuário a referenciar, mas a tentativa precisa ficar
 * registrada assim mesmo.</p>
 *
 * @author Equipe Custo Certo - FATEC Indaiatuba
 * @version 1.0
 */
public class LogAcessoDAO {

    /**
     * Registra uma tentativa de login.
     *
     * @param usuarioId    a chave primária do usuário, ou {@code null} quando o
     *                     e-mail digitado não corresponde a nenhuma conta
     * @param emailDigitado o e-mail informado na tela de login
     * @param sucesso      {@code true} se as credenciais foram aceitas
     * @param origem       identificação da máquina de onde partiu a tentativa
     * @throws SQLException se houver falha de comunicação com o banco
     */
    public void registrar(Integer usuarioId, String emailDigitado, boolean sucesso, String origem)
            throws SQLException {

        String sql = """
                INSERT INTO log_acesso (usuario_id, email_digitado, sucesso, origem)
                VALUES (?, ?, ?, ?)
                """;

        try (Connection conexao = ConexaoMySQL.obter();
             PreparedStatement comando = conexao.prepareStatement(sql)) {

            if (usuarioId == null) {
                comando.setNull(1, Types.INTEGER);
            } else {
                comando.setInt(1, usuarioId);
            }
            comando.setString(2, emailDigitado);
            comando.setBoolean(3, sucesso);
            comando.setString(4, origem);
            comando.executeUpdate();
        }
    }
}
