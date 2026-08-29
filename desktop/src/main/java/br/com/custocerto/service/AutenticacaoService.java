package br.com.custocerto.service;

import br.com.custocerto.dao.LogAcessoDAO;
import br.com.custocerto.dao.UsuarioDAO;
import br.com.custocerto.model.Usuario;
import br.com.custocerto.util.SenhaUtil;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Regras de negócio da autenticação.
 *
 * <p>Esta é a camada intermediária entre a tela e o banco. A tela de login não
 * conversa com o {@link UsuarioDAO} diretamente: ela entrega e-mail e senha a este
 * serviço e recebe de volta um {@link Usuario} autenticado ou uma
 * {@link AutenticacaoException}. Assim, se amanhã a interface mudar, ou se o
 * sistema ganhar uma segunda forma de entrada, a regra continua em um só lugar.</p>
 *
 * <p>O serviço aplica, nesta ordem: validação dos campos, busca do usuário,
 * verificação de conta ativa, conferência da senha contra o hash gravado, registro
 * da tentativa em {@code log_acesso} e atualização do último acesso.</p>
 *
 * <p>Quando o e-mail não existe e quando a senha está errada, a mensagem devolvida
 * é a mesma. Isso é intencional: mensagens diferentes permitiriam descobrir quais
 * e-mails estão cadastrados no sistema.</p>
 *
 * @author Equipe Custo Certo - FATEC Indaiatuba
 * @version 1.0
 */
public class AutenticacaoService {

    /** Mensagem única para e-mail inexistente e para senha incorreta. */
    private static final String CREDENCIAIS_INVALIDAS = "E-mail ou senha incorretos.";

    private final UsuarioDAO usuarioDAO;
    private final LogAcessoDAO logAcessoDAO;

    /**
     * Cria o serviço com os DAOs padrão.
     */
    public AutenticacaoService() {
        this(new UsuarioDAO(), new LogAcessoDAO());
    }

    /**
     * Cria o serviço com DAOs informados por quem chama.
     *
     * @param usuarioDAO   o DAO da tabela {@code usuarios}
     * @param logAcessoDAO o DAO da tabela {@code log_acesso}
     */
    public AutenticacaoService(UsuarioDAO usuarioDAO, LogAcessoDAO logAcessoDAO) {
        this.usuarioDAO = usuarioDAO;
        this.logAcessoDAO = logAcessoDAO;
    }

    /**
     * Autentica um usuário a partir das credenciais digitadas na tela de login.
     *
     * @param email a identificação informada
     * @param senha a senha informada, em texto puro
     * @return o usuário autenticado, com o perfil de acesso preenchido
     * @throws AutenticacaoException se os campos estiverem vazios, as credenciais
     *                               não conferirem, a conta estiver inativa ou o
     *                               banco de dados não responder
     */
    public Usuario autenticar(String email, String senha) throws AutenticacaoException {
        String emailLimpo = email == null ? "" : email.trim().toLowerCase();

        if (emailLimpo.isEmpty()) {
            throw new AutenticacaoException("Informe o seu e-mail.");
        }
        if (senha == null || senha.isEmpty()) {
            throw new AutenticacaoException("Informe a sua senha.");
        }

        try {
            Optional<Usuario> encontrado = usuarioDAO.buscarPorEmail(emailLimpo);

            if (encontrado.isEmpty()) {
                registrar(null, emailLimpo, false);
                throw new AutenticacaoException(CREDENCIAIS_INVALIDAS);
            }

            Usuario usuario = encontrado.get();

            if (!usuario.isAtivo()) {
                registrar(usuario.getId(), emailLimpo, false);
                throw new AutenticacaoException(
                        "Esta conta está inativa. Procure o administrador do sistema.");
            }

            if (!SenhaUtil.verificar(senha, usuario.getSalt(), usuario.getSenhaHash())) {
                registrar(usuario.getId(), emailLimpo, false);
                throw new AutenticacaoException(CREDENCIAIS_INVALIDAS);
            }

            registrar(usuario.getId(), emailLimpo, true);
            usuarioDAO.registrarAcesso(usuario.getId());
            return usuario;

        } catch (SQLException e) {
            throw new AutenticacaoException(
                    "Não foi possível conectar ao banco de dados. Verifique se o servidor MySQL "
                    + "está ligado e se a máquina está na mesma rede.", e);
        }
    }

    /**
     * Cadastra um novo usuário, calculando salt e hash a partir da senha em texto puro.
     *
     * @param usuario o usuário a cadastrar, sem os campos de senha preenchidos
     * @param senha   a senha inicial, em texto puro
     * @return o usuário cadastrado, com a chave primária gerada pelo banco
     * @throws AutenticacaoException se a senha for curta demais ou o banco recusar
     *                               a inclusão
     */
    public Usuario cadastrar(Usuario usuario, String senha) throws AutenticacaoException {
        if (senha == null || senha.length() < 6) {
            throw new AutenticacaoException("A senha deve ter ao menos 6 caracteres.");
        }

        String[] saltEHash = SenhaUtil.gerarSaltEHash(senha);
        usuario.setSalt(saltEHash[0]);
        usuario.setSenhaHash(saltEHash[1]);
        usuario.setEmail(usuario.getEmail().trim().toLowerCase());

        try {
            return usuarioDAO.inserir(usuario);
        } catch (SQLException e) {
            throw new AutenticacaoException(
                    "Não foi possível cadastrar o usuário. Verifique se o e-mail já está em uso.", e);
        }
    }

    /**
     * Grava a tentativa de login no histórico, sem interromper o fluxo caso a
     * gravação falhe — uma falha de auditoria não deve impedir alguém de entrar.
     *
     * @param usuarioId a chave do usuário, ou {@code null} se o e-mail não existe
     * @param email     o e-mail digitado
     * @param sucesso   se a tentativa foi aceita
     */
    private void registrar(Integer usuarioId, String email, boolean sucesso) {
        try {
            logAcessoDAO.registrar(usuarioId, email, sucesso, identificarMaquina());
        } catch (SQLException e) {
            System.err.println("[log] Não foi possível registrar a tentativa de acesso: " + e.getMessage());
        }
    }

    /**
     * Identifica a máquina de onde partiu a tentativa, para a trilha de auditoria.
     *
     * @return o nome do host e o endereço IP na rede local
     */
    private String identificarMaquina() {
        try {
            InetAddress local = InetAddress.getLocalHost();
            return local.getHostName() + " (" + local.getHostAddress() + ")";
        } catch (UnknownHostException e) {
            return "desconhecida";
        }
    }
}
