package br.com.custocerto.service;

import br.com.custocerto.dao.UsuarioDAO;
import br.com.custocerto.model.Perfil;
import br.com.custocerto.model.Usuario;
import br.com.custocerto.util.SenhaUtil;
import br.com.custocerto.util.Sessao;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Regras de negócio da administração de contas de acesso.
 *
 * <p>É o serviço por trás da tela de Usuários, disponível apenas ao perfil
 * {@link Perfil#ADMINISTRADOR}. Fica entre a tela e o {@link UsuarioDAO}, do mesmo
 * jeito que {@link EstoqueService} fica entre a tela de estoque e os seus DAOs: a
 * view entrega o que foi digitado no formulário e recebe de volta ou a operação
 * concluída ou uma {@link UsuarioException} com um texto que pode ser exibido.</p>
 *
 * <p>Três regras existem aqui e não no banco, porque dependem de quem está usando
 * o sistema no momento e o banco não tem como saber disso:</p>
 *
 * <ul>
 *   <li>ninguém inativa a própria conta — sairia do ar no meio da operação;</li>
 *   <li>ninguém rebaixa o próprio perfil de administrador, pelo mesmo motivo;</li>
 *   <li>o último administrador ativo não pode ser inativado nem rebaixado, senão
 *       o sistema fica sem ninguém capaz de administrar contas.</li>
 * </ul>
 *
 * <p>As demais restrições — e-mail único, formato do e-mail, tamanho mínimo do
 * nome, perfil dentro do {@code ENUM} — são garantidas pelo próprio MySQL. Este
 * serviço as verifica antes para dar uma mensagem melhor, mas quem tem a palavra
 * final é o banco.</p>
 *
 * @author Equipe Custo Certo - FATEC Indaiatuba
 * @version 1.0
 */
public class UsuarioService {

    /**
     * Formato aceito de e-mail. Equivale, de forma mais estrita, à restrição
     * {@code ck_usuarios_email} declarada na tabela {@code usuarios}.
     */
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    /** Tamanho mínimo da senha, o mesmo exigido por {@link AutenticacaoService}. */
    private static final int SENHA_MINIMA = 6;

    private final UsuarioDAO usuarioDAO;

    /**
     * Cria o serviço com o DAO padrão.
     */
    public UsuarioService() {
        this(new UsuarioDAO());
    }

    /**
     * Cria o serviço com um DAO informado por quem chama.
     *
     * @param usuarioDAO o DAO da tabela {@code usuarios}
     */
    public UsuarioService(UsuarioDAO usuarioDAO) {
        this.usuarioDAO = usuarioDAO;
    }

    /**
     * Lista todas as contas cadastradas, em ordem alfabética.
     *
     * @return as contas de acesso do sistema
     * @throws UsuarioException se o banco não responder
     */
    public List<Usuario> listar() throws UsuarioException {
        try {
            return usuarioDAO.listarTodos();
        } catch (SQLException e) {
            throw new UsuarioException(mensagemDeConexao(), e);
        }
    }

    /**
     * Cadastra uma conta nova, calculando salt e hash a partir da senha digitada.
     *
     * @param usuario a conta a criar, com nome, e-mail, perfil e situação
     * @param senha   a senha inicial, em texto puro
     * @return a conta criada, com a chave primária gerada pelo banco
     * @throws UsuarioException se algum campo estiver inválido ou o e-mail repetir
     */
    public Usuario cadastrar(Usuario usuario, String senha) throws UsuarioException {
        validarCadastro(usuario);
        validarSenha(senha);

        String[] saltEHash = SenhaUtil.gerarSaltEHash(senha);
        usuario.setSalt(saltEHash[0]);
        usuario.setSenhaHash(saltEHash[1]);

        try {
            return usuarioDAO.inserir(usuario);
        } catch (SQLException e) {
            throw traduzir(e, usuario.getEmail());
        }
    }

    /**
     * Altera nome, e-mail, perfil e situação de uma conta existente.
     *
     * <p>A senha não é tocada aqui. Para trocá-la existe
     * {@link #redefinirSenha(int, String)}.</p>
     *
     * @param usuario a conta com os dados novos e o {@code id} preenchido
     * @throws UsuarioException se algum campo estiver inválido, o e-mail repetir,
     *                          ou a alteração deixar o sistema sem administrador
     */
    public void atualizar(Usuario usuario) throws UsuarioException {
        validarCadastro(usuario);

        if (usuario.getId() == 0) {
            throw new UsuarioException("Esta conta ainda não foi cadastrada.");
        }

        Usuario antes = carregar(usuario.getId());

        if (ehOProprioUsuario(usuario.getId())) {
            if (antes.getPerfil() == Perfil.ADMINISTRADOR
                    && usuario.getPerfil() != Perfil.ADMINISTRADOR) {
                throw new UsuarioException(
                        "Você não pode rebaixar o seu próprio perfil — perderia o acesso a "
                        + "esta tela no meio da operação. Peça a outro administrador.");
            }
            if (!usuario.isAtivo()) {
                throw new UsuarioException("Você não pode inativar a sua própria conta.");
            }
        }

        boolean perdeAdministrador = antes.getPerfil() == Perfil.ADMINISTRADOR && antes.isAtivo()
                && (usuario.getPerfil() != Perfil.ADMINISTRADOR || !usuario.isAtivo());

        if (perdeAdministrador) {
            exigirOutroAdministrador();
        }

        try {
            usuarioDAO.atualizar(usuario);
        } catch (SQLException e) {
            throw traduzir(e, usuario.getEmail());
        }
    }

    /**
     * Liga ou desliga o acesso de uma conta, sem excluí-la.
     *
     * <p>Inativar preserva o histórico em {@code log_acesso} e o vínculo das
     * tentativas de login com quem as fez — é por isso que o sistema não tem
     * exclusão de usuário.</p>
     *
     * @param usuario a conta a alterar
     * @param ativo   {@code true} para liberar o acesso, {@code false} para bloquear
     * @throws UsuarioException se a conta for a do próprio usuário, se for o último
     *                          administrador ativo, ou se o banco não responder
     */
    public void alterarSituacao(Usuario usuario, boolean ativo) throws UsuarioException {
        Usuario antes = carregar(usuario.getId());

        if (!ativo && ehOProprioUsuario(usuario.getId())) {
            throw new UsuarioException("Você não pode inativar a sua própria conta.");
        }

        if (!ativo && antes.getPerfil() == Perfil.ADMINISTRADOR && antes.isAtivo()) {
            exigirOutroAdministrador();
        }

        try {
            usuarioDAO.alterarSituacao(usuario.getId(), ativo);
            usuario.setAtivo(ativo);
        } catch (SQLException e) {
            throw new UsuarioException(mensagemDeConexao(), e);
        }
    }

    /**
     * Define uma senha nova para uma conta, gerando salt e hash novos.
     *
     * <p>A senha anterior não é pedida: quem executa esta operação é o
     * administrador, justamente no caso em que o dono da conta esqueceu a dele.</p>
     *
     * @param usuarioId a chave primária da conta
     * @param novaSenha a senha nova, em texto puro
     * @throws UsuarioException se a senha for curta demais ou o banco não responder
     */
    public void redefinirSenha(int usuarioId, String novaSenha) throws UsuarioException {
        validarSenha(novaSenha);
        carregar(usuarioId);

        String[] saltEHash = SenhaUtil.gerarSaltEHash(novaSenha);

        try {
            usuarioDAO.atualizarSenha(usuarioId, saltEHash[1], saltEHash[0]);
        } catch (SQLException e) {
            throw new UsuarioException(mensagemDeConexao(), e);
        }
    }

    /**
     * Lê a conta no banco, garantindo que ela ainda existe.
     *
     * @param id a chave primária da conta
     * @return a conta lida do banco
     * @throws UsuarioException se a conta não existir mais ou o banco não responder
     */
    private Usuario carregar(int id) throws UsuarioException {
        try {
            Optional<Usuario> encontrado = usuarioDAO.buscarPorId(id);
            if (encontrado.isEmpty()) {
                throw new UsuarioException(
                        "Esta conta não existe mais. Atualize a lista e tente de novo.");
            }
            return encontrado.get();
        } catch (SQLException e) {
            throw new UsuarioException(mensagemDeConexao(), e);
        }
    }

    /**
     * Verifica se a conta informada é a do usuário autenticado nesta execução.
     *
     * @param id a chave primária da conta
     * @return {@code true} se for a própria conta de quem está usando o sistema
     */
    private boolean ehOProprioUsuario(int id) {
        Usuario logado = Sessao.getUsuarioLogado();
        return logado != null && logado.getId() == id;
    }

    /**
     * Recusa a operação se ela deixaria o sistema sem nenhum administrador ativo.
     *
     * @throws UsuarioException se este for o último administrador ativo
     */
    private void exigirOutroAdministrador() throws UsuarioException {
        try {
            if (usuarioDAO.contarAdministradoresAtivos() <= 1) {
                throw new UsuarioException(
                        "Este é o único administrador ativo. Promova outra conta a "
                        + "administrador antes de alterar esta, senão ninguém mais "
                        + "conseguirá gerenciar os acessos.");
            }
        } catch (SQLException e) {
            throw new UsuarioException(mensagemDeConexao(), e);
        }
    }

    /**
     * Valida os campos preenchidos no formulário de conta.
     *
     * @param u a conta a validar
     * @throws UsuarioException na primeira regra desrespeitada
     */
    private void validarCadastro(Usuario u) throws UsuarioException {
        if (u.getNome() == null || u.getNome().trim().length() < 3) {
            throw new UsuarioException("O nome precisa ter ao menos 3 caracteres.");
        }
        if (u.getEmail() == null || u.getEmail().isBlank()) {
            throw new UsuarioException("Informe o e-mail de acesso.");
        }

        String email = u.getEmail().trim().toLowerCase();
        if (!EMAIL.matcher(email).matches()) {
            throw new UsuarioException(
                    "O e-mail \"" + u.getEmail().trim() + "\" não tem um formato válido.");
        }
        if (u.getPerfil() == null) {
            throw new UsuarioException("Escolha o perfil de acesso.");
        }

        u.setNome(u.getNome().trim());
        u.setEmail(email);
    }

    /**
     * Valida o tamanho mínimo da senha.
     *
     * @param senha a senha digitada, em texto puro
     * @throws UsuarioException se a senha for curta demais
     */
    private void validarSenha(String senha) throws UsuarioException {
        if (senha == null || senha.length() < SENHA_MINIMA) {
            throw new UsuarioException(
                    "A senha deve ter ao menos " + SENHA_MINIMA + " caracteres.");
        }
    }

    /**
     * Traduz a falha do banco na mensagem que a tela deve exibir.
     *
     * @param e     a exceção devolvida pelo JDBC
     * @param email o e-mail que estava sendo gravado
     * @return a exceção de serviço correspondente
     */
    private UsuarioException traduzir(SQLException e, String email) {
        String texto = e.getMessage() == null ? "" : e.getMessage();

        if (texto.contains("uk_usuarios_email") || texto.contains("Duplicate entry")) {
            return new UsuarioException("Já existe uma conta com o e-mail " + email + ".");
        }
        if (texto.contains("ck_usuarios_nome")) {
            return new UsuarioException("O nome precisa ter ao menos 3 caracteres.");
        }
        if (texto.contains("ck_usuarios_email")) {
            return new UsuarioException("O e-mail informado não tem um formato válido.");
        }
        return new UsuarioException(mensagemDeConexao(), e);
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
