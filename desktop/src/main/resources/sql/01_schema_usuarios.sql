-- =====================================================================
-- Custo Certo - Autenticacao e perfis de acesso
-- Projeto Integrador - FATEC Indaiatuba - 2o semestre de 2026
--
-- Script do MySQL para as tabelas de login. Corresponde a macro funcao
-- "Autenticacao e perfis de acesso" da definicao de escopo do sistema.
--
-- Como executar:
--   mysql -u root -p < 01_schema_usuarios.sql
--   ou abra o arquivo no MySQL Workbench e execute o script inteiro.
--
-- O script e idempotente: pode ser executado novamente sem quebrar.
-- =====================================================================

CREATE DATABASE IF NOT EXISTS custo_certo
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE custo_certo;

-- =====================================================================
-- TABELA: usuarios
-- ---------------------------------------------------------------------
-- Contas de acesso ao sistema. Cada conta pertence a um dos tres perfis
-- previstos no escopo do projeto: administrador, gestor e operador.
--
-- Restricoes aplicadas:
--   PRIMARY KEY  ....... id, com AUTO_INCREMENT
--   UNIQUE ............. email, que e o login e nao pode se repetir
--   NOT NULL ........... nos campos indispensaveis para autenticar
--   ENUM ............... limita o perfil aos tres valores validos
--   CHECK .............. garante formato minimo de e-mail
--   DEFAULT ............ conta nasce ativa e com data de criacao automatica
-- =====================================================================
CREATE TABLE IF NOT EXISTS usuarios (
    id            INT           NOT NULL AUTO_INCREMENT,
    nome          VARCHAR(120)  NOT NULL,
    email         VARCHAR(120)  NOT NULL,
    senha_hash    VARCHAR(255)  NOT NULL,
    salt          VARCHAR(64)   NOT NULL,
    perfil        ENUM('ADMINISTRADOR', 'GESTOR', 'OPERADOR') NOT NULL DEFAULT 'OPERADOR',
    ativo         BOOLEAN       NOT NULL DEFAULT TRUE,
    criado_em     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ultimo_acesso DATETIME      NULL,

    CONSTRAINT pk_usuarios       PRIMARY KEY (id),
    CONSTRAINT uk_usuarios_email UNIQUE (email),
    CONSTRAINT ck_usuarios_email CHECK (email LIKE '%_@_%._%'),
    CONSTRAINT ck_usuarios_nome  CHECK (CHAR_LENGTH(TRIM(nome)) >= 3),

    INDEX idx_usuarios_perfil (perfil)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- =====================================================================
-- TABELA: log_acesso
-- ---------------------------------------------------------------------
-- Historico de tentativas de login, aceitas e recusadas. Da ao perfil de
-- administrador a trilha de auditoria prevista no escopo do projeto.
--
-- Restricoes aplicadas:
--   PRIMARY KEY .. id, com AUTO_INCREMENT
--   FOREIGN KEY .. usuario_id referencia usuarios(id)
--       ON DELETE SET NULL -> excluir um usuario nao apaga o historico dele
--       ON UPDATE CASCADE  -> alteracao de id se propaga para o historico
--
-- A coluna usuario_id aceita NULL de proposito: quando alguem digita um
-- e-mail que nao existe nao ha usuario a referenciar, mas a tentativa
-- precisa ficar registrada mesmo assim.
-- =====================================================================
CREATE TABLE IF NOT EXISTS log_acesso (
    id             INT          NOT NULL AUTO_INCREMENT,
    usuario_id     INT          NULL,
    email_digitado VARCHAR(120) NOT NULL,
    sucesso        BOOLEAN      NOT NULL,
    origem         VARCHAR(120) NULL,
    data_hora      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_log_acesso PRIMARY KEY (id),
    CONSTRAINT fk_log_acesso_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuarios (id)
        ON DELETE SET NULL
        ON UPDATE CASCADE,

    INDEX idx_log_acesso_data (data_hora)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- =====================================================================
-- CARGA INICIAL: usuarios
-- ---------------------------------------------------------------------
-- Cinco contas, atendendo ao minimo de 5 linhas por tabela.
--
-- As senhas NAO estao em texto puro. Cada linha guarda um salt aleatorio
-- e o hash PBKDF2-HMAC-SHA256 (210.000 iteracoes) da senha somada a esse
-- salt, gerados pela classe br.com.custocerto.util.SenhaUtil.
--
-- Senhas correspondentes, para uso em sala:
--   admin@custocerto.com.br     -> admin123      (ADMINISTRADOR)
--   denilson@letscoffee.com.br  -> gestor123     (GESTOR)
--   marina@letscoffee.com.br    -> operador123   (OPERADOR)
--   rafael@letscoffee.com.br    -> operador123   (OPERADOR)
--   camila@letscoffee.com.br    -> gestor123     (GESTOR, conta inativa)
--
-- Para trocar qualquer senha, gere um novo par com:
--   mvnw.cmd exec:java -Dexec.mainClass=br.com.custocerto.util.GeradorDeSenhas -Dexec.args="novaSenha"
-- =====================================================================
-- INSERT IGNORE: reexecutar o script nao duplica as contas nem interrompe com
-- erro de chave duplicada. As linhas ja existentes sao simplesmente puladas.
INSERT IGNORE INTO usuarios (id, nome, email, senha_hash, salt, perfil, ativo) VALUES
    (1, 'Administrador do Sistema', 'admin@custocerto.com.br',
     'tKkW+eRJnASbk8AocaF24BqnEDKfRiNDrJ22j80U4A4=', 'fhn+BZgM+g5/CqiJPyBcmA==',
     'ADMINISTRADOR', TRUE),

    (2, 'Denilson Ribeiro', 'denilson@letscoffee.com.br',
     'EfzMFHgoxYxwnrnHTB/cexD49CEvs0S9yJgrXg8DB0k=', 'dhNDd3DkbrSM2FrPqFWciw==',
     'GESTOR', TRUE),

    (3, 'Marina Alves Prado', 'marina@letscoffee.com.br',
     '9EefU64ZGLTiyvjWuFUUVmTWC81fp2S0yuYcAqejgtE=', 'HVuOnFbk6u3NFtmeYAQl2w==',
     'OPERADOR', TRUE),

    (4, 'Rafael Souza Lima', 'rafael@letscoffee.com.br',
     'cgqJxlN4cNPCOV9xomPQfQhoeLQHpiUioYHWA1wOXh4=', '6RAkGOxsHmx3tU58i/MjzQ==',
     'OPERADOR', TRUE),

    (5, 'Camila Duarte', 'camila@letscoffee.com.br',
     'Zuj++d+T4cZSPPm19PMsMK0WgzQ10QQwdE0oSSDu3Us=', 'W+2aP0sq/+m3/5hnQIiZzg==',
     'GESTOR', FALSE);

-- =====================================================================
-- CARGA INICIAL: log_acesso
-- ---------------------------------------------------------------------
-- Seis registros, cobrindo os tres desfechos possiveis de uma tentativa:
-- acesso aceito, senha incorreta e e-mail inexistente (usuario_id nulo).
-- =====================================================================
INSERT IGNORE INTO log_acesso (id, usuario_id, email_digitado, sucesso, origem, data_hora) VALUES
    (1, 1, 'admin@custocerto.com.br',    TRUE,  'PC-ADMIN (192.168.0.10)',    '2026-08-24 08:12:41'),
    (2, 2, 'denilson@letscoffee.com.br', TRUE,  'PC-BALCAO (192.168.0.21)',   '2026-08-24 08:45:03'),
    (3, 3, 'marina@letscoffee.com.br',   TRUE,  'PC-COZINHA (192.168.0.22)',  '2026-08-24 09:02:17'),
    (4, 3, 'marina@letscoffee.com.br',   FALSE, 'PC-COZINHA (192.168.0.22)',  '2026-08-24 09:01:55'),
    (5, NULL, 'joao@letscoffee.com.br',  FALSE, 'PC-BALCAO (192.168.0.21)',   '2026-08-25 14:33:09'),
    (6, 5, 'camila@letscoffee.com.br',   FALSE, 'PC-ADMIN (192.168.0.10)',    '2026-08-26 10:20:44');

-- =====================================================================
-- CONFERENCIA
-- =====================================================================
SELECT id, nome, email, perfil, ativo FROM usuarios ORDER BY id;
SELECT COUNT(*) AS total_tentativas_registradas FROM log_acesso;
