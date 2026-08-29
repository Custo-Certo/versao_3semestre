package br.com.custocerto.model;

import java.time.LocalDateTime;

/**
 * Representa um usuário do sistema, espelhando uma linha da tabela {@code usuarios}.
 *
 * <p>Esta classe pertence à camada de modelo: ela apenas transporta dados entre as
 * demais camadas e não conhece banco de dados nem interface gráfica.</p>
 *
 * <p>A senha nunca é armazenada em texto puro. O que fica guardado são dois campos
 * gerados por {@link br.com.custocerto.util.SenhaUtil}: o {@code salt} (valor
 * aleatório único por usuário) e o {@code senhaHash} (resultado do algoritmo
 * PBKDF2 aplicado sobre a senha somada ao salt).</p>
 *
 * @author Equipe Custo Certo - FATEC Indaiatuba
 * @version 1.0
 */
public class Usuario {

    private int id;
    private String nome;
    private String email;
    private String senhaHash;
    private String salt;
    private Perfil perfil;
    private boolean ativo;
    private LocalDateTime criadoEm;
    private LocalDateTime ultimoAcesso;

    /**
     * Cria um usuário vazio, a ser preenchido pelos métodos de escrita.
     */
    public Usuario() {
    }

    /**
     * Cria um usuário com os dados de identificação e o perfil de acesso.
     *
     * @param id     chave primária na tabela {@code usuarios}
     * @param nome   nome completo do usuário
     * @param email  e-mail usado como login (único no banco)
     * @param perfil perfil de acesso que define as permissões
     */
    public Usuario(int id, String nome, String email, Perfil perfil) {
        this.id = id;
        this.nome = nome;
        this.email = email;
        this.perfil = perfil;
        this.ativo = true;
    }

    /**
     * Retorna o primeiro nome do usuário, usado nas saudações da interface.
     *
     * @return o primeiro nome, ou o nome completo se não houver espaço
     */
    public String getPrimeiroNome() {
        if (nome == null || nome.isBlank()) {
            return "";
        }
        int espaco = nome.trim().indexOf(' ');
        return espaco > 0 ? nome.trim().substring(0, espaco) : nome.trim();
    }

    /**
     * Retorna a chave primária do usuário.
     *
     * @return o identificador no banco de dados
     */
    public int getId() {
        return id;
    }

    /**
     * Define a chave primária do usuário.
     *
     * @param id o identificador no banco de dados
     */
    public void setId(int id) {
        this.id = id;
    }

    /**
     * Retorna o nome completo do usuário.
     *
     * @return o nome completo
     */
    public String getNome() {
        return nome;
    }

    /**
     * Define o nome completo do usuário.
     *
     * @param nome o nome completo
     */
    public void setNome(String nome) {
        this.nome = nome;
    }

    /**
     * Retorna o e-mail, que também é o login do sistema.
     *
     * @return o e-mail do usuário
     */
    public String getEmail() {
        return email;
    }

    /**
     * Define o e-mail do usuário.
     *
     * @param email o e-mail, único na tabela {@code usuarios}
     */
    public void setEmail(String email) {
        this.email = email;
    }

    /**
     * Retorna o hash da senha em Base64.
     *
     * @return o hash PBKDF2 da senha
     */
    public String getSenhaHash() {
        return senhaHash;
    }

    /**
     * Define o hash da senha.
     *
     * @param senhaHash o hash PBKDF2 em Base64
     */
    public void setSenhaHash(String senhaHash) {
        this.senhaHash = senhaHash;
    }

    /**
     * Retorna o salt usado no cálculo do hash da senha.
     *
     * @return o salt em Base64
     */
    public String getSalt() {
        return salt;
    }

    /**
     * Define o salt usado no cálculo do hash da senha.
     *
     * @param salt o salt em Base64
     */
    public void setSalt(String salt) {
        this.salt = salt;
    }

    /**
     * Retorna o perfil de acesso do usuário.
     *
     * @return o perfil de acesso
     */
    public Perfil getPerfil() {
        return perfil;
    }

    /**
     * Define o perfil de acesso do usuário.
     *
     * @param perfil o perfil de acesso
     */
    public void setPerfil(Perfil perfil) {
        this.perfil = perfil;
    }

    /**
     * Indica se a conta está ativa. Contas inativas não conseguem fazer login.
     *
     * @return {@code true} se a conta está ativa
     */
    public boolean isAtivo() {
        return ativo;
    }

    /**
     * Define se a conta está ativa.
     *
     * @param ativo {@code true} para conta ativa
     */
    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }

    /**
     * Retorna a data e hora de criação da conta.
     *
     * @return o momento do cadastro
     */
    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    /**
     * Define a data e hora de criação da conta.
     *
     * @param criadoEm o momento do cadastro
     */
    public void setCriadoEm(LocalDateTime criadoEm) {
        this.criadoEm = criadoEm;
    }

    /**
     * Retorna a data e hora do último login bem-sucedido.
     *
     * @return o momento do último acesso, ou {@code null} se nunca acessou
     */
    public LocalDateTime getUltimoAcesso() {
        return ultimoAcesso;
    }

    /**
     * Define a data e hora do último login bem-sucedido.
     *
     * @param ultimoAcesso o momento do último acesso
     */
    public void setUltimoAcesso(LocalDateTime ultimoAcesso) {
        this.ultimoAcesso = ultimoAcesso;
    }

    /**
     * Retorna uma representação textual do usuário, sem expor dados de senha.
     *
     * @return o nome, o e-mail e o perfil do usuário
     */
    @Override
    public String toString() {
        return nome + " <" + email + "> [" + perfil + "]";
    }
}
