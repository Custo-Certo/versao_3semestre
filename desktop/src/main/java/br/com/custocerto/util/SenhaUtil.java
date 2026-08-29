package br.com.custocerto.util;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Utilitário de proteção de senhas.
 *
 * <p>Nenhuma senha é gravada em texto puro no banco. O que se guarda são dois
 * valores: um <strong>salt</strong> aleatório, diferente para cada usuário, e o
 * <strong>hash</strong> resultante do algoritmo PBKDF2 aplicado sobre a senha
 * combinada com esse salt.</p>
 *
 * <p>O salt impede que dois usuários com a mesma senha tenham o mesmo hash, o que
 * inviabiliza a consulta a tabelas de hashes pré-calculados. As
 * {@value #ITERACOES} iterações tornam cada tentativa de adivinhação custosa,
 * dificultando ataques de força bruta caso o banco seja copiado.</p>
 *
 * <p>A verificação é feita por comparação em tempo constante
 * ({@link #saoIguais(String, String)}): o laço percorre todos os caracteres mesmo
 * após encontrar uma diferença, para que o tempo de resposta não revele quantos
 * caracteres do hash foram acertados.</p>
 *
 * <p>Todo o algoritmo vem da própria biblioteca padrão do Java
 * ({@code javax.crypto}), sem dependências externas.</p>
 *
 * @author Equipe Custo Certo - FATEC Indaiatuba
 * @version 1.0
 */
public final class SenhaUtil {

    /** Algoritmo de derivação de chave usado para gerar o hash da senha. */
    private static final String ALGORITMO = "PBKDF2WithHmacSHA256";

    /** Número de repetições do algoritmo. Quanto maior, mais lento o ataque. */
    private static final int ITERACOES = 210_000;

    /** Tamanho do hash gerado, em bits. */
    private static final int TAMANHO_HASH_BITS = 256;

    /** Tamanho do salt aleatório, em bytes. */
    private static final int TAMANHO_SALT_BYTES = 16;

    /** Gerador de números aleatórios criptograficamente seguro. */
    private static final SecureRandom ALEATORIO = new SecureRandom();

    /**
     * Classe utilitária: não deve ser instanciada.
     */
    private SenhaUtil() {
    }

    /**
     * Gera um salt aleatório para um novo usuário.
     *
     * @return o salt codificado em Base64, pronto para gravar na coluna {@code salt}
     */
    public static String gerarSalt() {
        byte[] salt = new byte[TAMANHO_SALT_BYTES];
        ALEATORIO.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    /**
     * Calcula o hash de uma senha a partir de um salt.
     *
     * @param senha    a senha em texto puro digitada pelo usuário
     * @param saltBase64 o salt do usuário, codificado em Base64
     * @return o hash da senha codificado em Base64
     * @throws IllegalArgumentException se a senha ou o salt forem nulos
     * @throws IllegalStateException    se o algoritmo PBKDF2 não estiver disponível na JVM
     */
    public static String gerarHash(String senha, String saltBase64) {
        if (senha == null || saltBase64 == null) {
            throw new IllegalArgumentException("Senha e salt não podem ser nulos.");
        }
        byte[] salt = Base64.getDecoder().decode(saltBase64);
        PBEKeySpec spec = new PBEKeySpec(senha.toCharArray(), salt, ITERACOES, TAMANHO_HASH_BITS);
        try {
            SecretKeyFactory fabrica = SecretKeyFactory.getInstance(ALGORITMO);
            byte[] hash = fabrica.generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Falha ao calcular o hash da senha: " + e.getMessage(), e);
        } finally {
            spec.clearPassword();
        }
    }

    /**
     * Verifica se uma senha digitada corresponde ao hash gravado no banco.
     *
     * @param senhaDigitada a senha informada na tela de login
     * @param saltBase64    o salt do usuário, lido do banco
     * @param hashEsperado  o hash gravado no banco
     * @return {@code true} se a senha estiver correta
     */
    public static boolean verificar(String senhaDigitada, String saltBase64, String hashEsperado) {
        if (senhaDigitada == null || saltBase64 == null || hashEsperado == null) {
            return false;
        }
        String hashCalculado = gerarHash(senhaDigitada, saltBase64);
        return saoIguais(hashCalculado, hashEsperado);
    }

    /**
     * Compara duas cadeias em tempo constante, sem interromper no primeiro
     * caractere diferente.
     *
     * @param a a primeira cadeia
     * @param b a segunda cadeia
     * @return {@code true} se forem idênticas
     */
    private static boolean saoIguais(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int diferenca = 0;
        for (int i = 0; i < a.length(); i++) {
            diferenca |= a.charAt(i) ^ b.charAt(i);
        }
        return diferenca == 0;
    }

    /**
     * Gera o par salt e hash para uma senha nova.
     *
     * <p>Usado no cadastro de usuários e pelo utilitário
     * {@code GeradorDeSenhas}, que produz as linhas de {@code INSERT} do banco.</p>
     *
     * @param senha a senha em texto puro
     * @return um vetor de duas posições: {@code [0]} o salt, {@code [1]} o hash
     */
    public static String[] gerarSaltEHash(String senha) {
        String salt = gerarSalt();
        return new String[] { salt, gerarHash(senha, salt) };
    }
}
