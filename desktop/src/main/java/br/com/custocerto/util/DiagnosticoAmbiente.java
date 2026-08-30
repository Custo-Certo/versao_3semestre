package br.com.custocerto.util;

import br.com.custocerto.dao.ConexaoMySQL;
import br.com.custocerto.dao.UsuarioDAO;
import br.com.custocerto.model.Usuario;
import br.com.custocerto.service.AutenticacaoException;
import br.com.custocerto.service.AutenticacaoService;
import java.sql.SQLException;
import java.util.List;

/**
 * Verifica, pela linha de comando, se o ambiente está pronto para a apresentação.
 *
 * <p>Percorre a mesma pilha que a tela de login usa — configuração, conexão JDBC,
 * DAO e serviço de autenticação — e informa em qual etapa um problema aparece.
 * Rodar isto antes de abrir a interface evita descobrir um servidor fora do ar
 * na frente da turma.</p>
 *
 * <p>Uso:</p>
 * <pre>
 * mvnw.cmd exec:java -Dexec.mainClass=br.com.custocerto.util.DiagnosticoAmbiente
 * </pre>
 *
 * @author Equipe Custo Certo - FATEC Indaiatuba
 * @version 1.0
 */
public final class DiagnosticoAmbiente {

    /**
     * Classe utilitária de linha de comando: não deve ser instanciada.
     */
    private DiagnosticoAmbiente() {
    }

    /**
     * Executa a bateria de verificações e imprime o resultado de cada uma.
     *
     * @param args não utilizados
     */
    public static void main(String[] args) {
        System.out.println("=== Diagnóstico do ambiente - Custo Certo ===\n");

        System.out.println("1. Configuração");
        System.out.println("   URL JDBC: " + ConfiguracaoBanco.getUrl());
        System.out.println("   Usuário.: " + ConfiguracaoBanco.getUsuario());

        System.out.println("\n2. Conexão com o MySQL");
        if (!ConexaoMySQL.testar()) {
            System.out.println("   FALHOU. O servidor não respondeu — verifique se o MySQL está ligado.");
            return;
        }
        System.out.println("   OK, servidor respondeu.");

        System.out.println("\n3. Leitura da tabela usuarios");
        try {
            List<Usuario> usuarios = new UsuarioDAO().listarTodos();
            System.out.println("   " + usuarios.size() + " contas encontradas:");
            for (Usuario u : usuarios) {
                System.out.println("     - " + u.getEmail()
                        + "  [" + u.getPerfil().getDescricao() + "]"
                        + (u.isAtivo() ? "" : "  (inativa)"));
            }
        } catch (SQLException e) {
            System.out.println("   FALHOU: " + e.getMessage());
            return;
        }

        System.out.println("\n4. Autenticação");
        tentar("admin@custocerto.com.br", "admin123", true);
        tentar("marina@letscoffee.com.br", "operador123", true);
        tentar("admin@custocerto.com.br", "senhaErrada", false);
        tentar("camila@letscoffee.com.br", "gestor123", false);
        tentar("naoexiste@custocerto.com.br", "qualquer", false);

        System.out.println("\n5. Estoque em MySQL");
        try {
            var estoque = new br.com.custocerto.service.EstoqueService();
            var insumos = estoque.listarIngredientes();
            System.out.println("   " + insumos.size() + " insumos cadastrados:");
            for (var i : insumos) {
                System.out.printf("     - %-26s %8s %-3s  %5.0f%%  %s%n",
                        i.getNome(),
                        i.getQtd().stripTrailingZeros().toPlainString(),
                        i.getUnidade().getSigla(),
                        i.getPercentualEstoque(),
                        i.getRotuloValidade());
            }

            var resumo = estoque.calcularResumo();
            System.out.println("\n   Indicadores do painel:");
            System.out.println("     valor em estoque..: R$ " + resumo.getValorEmEstoque());
            System.out.println("     itens em alerta...: " + resumo.getItensEmAlerta());
            System.out.println("     vencidos..........: " + resumo.getItensVencidos());
            System.out.println("     vencendo em 5 dias: " + resumo.getItensVencendo());
            System.out.println("     perda por venc....: R$ " + resumo.getValorVencido());

            // Indicadores financeiros do periodo. Antes havia aqui um "CMV
            // estimado" que era constante: a receita era o proprio custo vezes
            // 3,5, entao a divisao dava sempre 1/3,5. Agora os dois lados sao
            // medidos, e por isso o diagnostico so os exibe se houver venda.
            var analise = new br.com.custocerto.service.AnaliseService();
            var periodo = br.com.custocerto.model.Periodo.ultimosDias(90);
            var financeiro = analise.resumoDoPeriodo(periodo);

            System.out.println("\n   Financeiro nos ultimos 90 dias (" + periodo + "):");
            System.out.println("     faturamento.......: R$ " + financeiro.getReceita());
            System.out.println("     custo pela ficha..: R$ " + financeiro.getCustoTeorico());
            System.out.println("     custo medido......: R$ " + financeiro.getCustoReal());
            System.out.println("     compras...........: R$ " + financeiro.getCompras());

            if (financeiro.temVendas()) {
                System.out.printf ("     CMV...............: %.1f%% (%s)%n",
                        financeiro.getCmvTeorico(), financeiro.getRotuloCmv());
                System.out.println("     margem bruta......: R$ " + financeiro.getMargemBruta());
                System.out.println("     ticket medio......: R$ " + financeiro.getTicketMedio());
            } else {
                System.out.println("     CMV...............: sem vendas no periodo");
            }

            var variancias = analise.variancias(periodo);
            System.out.println("\n   Consumo teorico x real:");
            if (variancias.isEmpty()) {
                System.out.println("     nenhum insumo com movimento no periodo");
            }
            for (var v : variancias) {
                if (v.isSemBaixaRegistrada()) {
                    System.out.printf("     - %-26s nao passa pela balanca%n", v.getNome());
                } else {
                    System.out.printf("     - %-26s teorico %8s  real %8s  %+.1f%%  R$ %s%n",
                            v.getNome(),
                            v.getTeorico().stripTrailingZeros().toPlainString(),
                            v.getReal().stripTrailingZeros().toPlainString(),
                            v.getPercentual(),
                            v.getCustoDiferenca());
                }
            }

            System.out.println("\n6. Transação: retirada maior que o estoque deve ser desfeita");
            var alvo = insumos.get(0);
            var saldoAntes = alvo.getQtd();
            var exagero = saldoAntes.add(java.math.BigDecimal.valueOf(999));
            try {
                estoque.registrarSaida(alvo.getId(), exagero, "teste de rollback");
                System.out.println("   INESPERADO a retirada impossível foi aceita.");
            } catch (br.com.custocerto.service.EstoqueException e) {
                System.out.println("   OK   recusou: " + e.getMessage());
            }

            var depois = estoque.listarIngredientes().stream()
                    .filter(i -> i.getId() == alvo.getId()).findFirst().orElseThrow();
            boolean intacto = depois.getQtd().compareTo(saldoAntes) == 0;
            System.out.println("   " + (intacto ? "OK  " : "INESPERADO")
                    + " saldo de " + alvo.getNome() + " continua "
                    + depois.getQtd().stripTrailingZeros().toPlainString()
                    + " " + alvo.getUnidade().getSigla() + " — nada foi gravado pela metade.");

        } catch (br.com.custocerto.service.EstoqueException
                 | br.com.custocerto.service.CardapioException e) {
            System.out.println("   FALHOU: " + e.getMessage());
        }

        System.out.println("\n=== Fim do diagnóstico ===");
    }

    /**
     * Executa uma tentativa de login e informa se o resultado foi o esperado.
     *
     * @param email            o e-mail a testar
     * @param senha            a senha a testar
     * @param esperaSucesso    {@code true} se a tentativa deveria ser aceita
     */
    private static void tentar(String email, String senha, boolean esperaSucesso) {
        try {
            Usuario u = new AutenticacaoService().autenticar(email, senha);
            String marca = esperaSucesso ? "OK  " : "INESPERADO";
            System.out.println("   " + marca + " entrou: " + u.getNome()
                    + " [" + u.getPerfil().getDescricao() + "]"
                    + " · gerencia usuários: " + (u.getPerfil().podeGerenciarUsuarios() ? "sim" : "não")
                    + " · indicadores: " + (u.getPerfil().podeAcessarIndicadores() ? "sim" : "não"));
        } catch (AutenticacaoException e) {
            String marca = esperaSucesso ? "INESPERADO" : "OK  ";
            System.out.println("   " + marca + " recusou " + email + ": " + e.getMessage());
        }
    }
}
