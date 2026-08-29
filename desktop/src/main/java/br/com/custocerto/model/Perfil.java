package br.com.custocerto.model;

/**
 * Perfis de acesso do sistema Custo Certo.
 *
 * <p>Os três perfis vêm da definição de escopo do projeto, item 4 (macro funções):
 * <em>"Autenticação e perfis de acesso: controle de acesso por perfil, distinguindo
 * as permissões do gestor, do operador e do administrador do sistema."</em></p>
 *
 * <p>Cada constante carrega as permissões do perfil, de modo que a camada de
 * apresentação não precisa comparar nomes de perfil para decidir o que exibir —
 * basta perguntar ao próprio perfil.</p>
 *
 * <p>Os nomes das constantes correspondem exatamente aos valores aceitos pela
 * coluna {@code perfil} da tabela {@code usuarios}, declarada como
 * {@code ENUM('ADMINISTRADOR','GESTOR','OPERADOR')} no MySQL.</p>
 *
 * @author Equipe Custo Certo - FATEC Indaiatuba
 * @version 1.0
 */
public enum Perfil {

    /**
     * Administrador do sistema. Responsável pela sustentação da aplicação:
     * gerencia usuários e perfis, além de ter acesso a tudo que os demais têm.
     */
    ADMINISTRADOR("Administrador", true, true, true),

    /**
     * Gestor do estabelecimento. Acompanha indicadores, custos e cadastros,
     * mas não administra as contas de acesso.
     */
    GESTOR("Gestor", false, true, true),

    /**
     * Operador / barista. Executa a rotina diária: pesagem de insumos na balança
     * e conferência das movimentações de estoque.
     */
    OPERADOR("Operador", false, false, true);

    private final String descricao;
    private final boolean gerenciaUsuarios;
    private final boolean acessaIndicadores;
    private final boolean operaBalanca;

    /**
     * Cria um perfil com o seu conjunto de permissões.
     *
     * @param descricao        nome legível, exibido na interface
     * @param gerenciaUsuarios se pode cadastrar, editar e inativar usuários
     * @param acessaIndicadores se pode ver o painel de CMV e os relatórios
     * @param operaBalanca     se pode registrar pesagens e movimentações
     */
    Perfil(String descricao, boolean gerenciaUsuarios, boolean acessaIndicadores, boolean operaBalanca) {
        this.descricao = descricao;
        this.gerenciaUsuarios = gerenciaUsuarios;
        this.acessaIndicadores = acessaIndicadores;
        this.operaBalanca = operaBalanca;
    }

    /**
     * Retorna o nome legível do perfil, próprio para exibição na interface.
     *
     * @return a descrição do perfil (ex.: {@code "Administrador"})
     */
    public String getDescricao() {
        return descricao;
    }

    /**
     * Indica se o perfil pode administrar as contas de acesso do sistema.
     *
     * @return {@code true} somente para {@link #ADMINISTRADOR}
     */
    public boolean podeGerenciarUsuarios() {
        return gerenciaUsuarios;
    }

    /**
     * Indica se o perfil pode consultar o painel de indicadores e o CMV.
     *
     * @return {@code true} para {@link #ADMINISTRADOR} e {@link #GESTOR}
     */
    public boolean podeAcessarIndicadores() {
        return acessaIndicadores;
    }

    /**
     * Indica se o perfil pode registrar pesagens e movimentações de estoque.
     *
     * @return {@code true} para todos os perfis
     */
    public boolean podeOperarBalanca() {
        return operaBalanca;
    }

    /**
     * Converte o valor gravado no banco para a constante correspondente.
     *
     * @param valor conteúdo da coluna {@code usuarios.perfil}
     * @return o perfil correspondente
     * @throws IllegalArgumentException se o valor não corresponder a nenhum perfil
     */
    public static Perfil deValorDoBanco(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("Perfil nulo lido do banco de dados.");
        }
        return Perfil.valueOf(valor.trim().toUpperCase());
    }

    /**
     * Retorna a descrição do perfil.
     *
     * @return a descrição legível
     */
    @Override
    public String toString() {
        return descricao;
    }
}
