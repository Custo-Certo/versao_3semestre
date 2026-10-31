-- =====================================================================
-- Custo Certo - Execucoes de receita e CMV
-- Projeto Integrador - FATEC Indaiatuba
--
-- Nova feature: ao pesar ingredientes, cada abatimento pode ser vinculado
-- a uma "execucao de receita". Ao finalizar, calcula-se o CMV real do que
-- foi pesado. Executar DEPOIS de 01 e 02. Idempotente.
-- =====================================================================

USE custo_certo;

-- ---------------------------------------------------------------------
-- TABELA: execucoes_receita
-- Uma producao/receita que acumula os ingredientes pesados.
-- status: aberta (em andamento) | finalizada | cancelada
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS execucoes_receita (
    id          INT           NOT NULL AUTO_INCREMENT,
    nome        VARCHAR(120)  NOT NULL,
    status      ENUM('aberta','finalizada','cancelada') NOT NULL DEFAULT 'aberta',
    porcoes     INT           NULL,
    preco_venda DECIMAL(10,2) NULL,
    observacao  VARCHAR(255)  NULL,
    inicio      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fim         DATETIME      NULL,

    CONSTRAINT pk_execucoes_receita PRIMARY KEY (id),
    INDEX idx_exec_status (status),
    INDEX idx_exec_nome (nome)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- COLUNA: movimentacoes_estoque.execucao_id
-- Vincula uma saida (consumo) a uma execucao de receita. NULL = avulsa.
-- Bloco idempotente: so adiciona se ainda nao existir (MySQL 8 nao tem
-- ADD COLUMN IF NOT EXISTS).
-- ---------------------------------------------------------------------
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'custo_certo'
      AND TABLE_NAME   = 'movimentacoes_estoque'
      AND COLUMN_NAME  = 'execucao_id'
);

SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE movimentacoes_estoque
        ADD COLUMN execucao_id INT NULL,
        ADD CONSTRAINT fk_mov_execucao
            FOREIGN KEY (execucao_id) REFERENCES execucoes_receita(id)
            ON DELETE SET NULL ON UPDATE CASCADE,
        ADD INDEX idx_mov_execucao (execucao_id)',
    'SELECT ''Coluna execucao_id ja existe'' AS info');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Conferencia
SELECT COUNT(*) AS execucoes_existentes FROM execucoes_receita;
