-- =====================================================================
-- Custo Certo - Insumos e movimentacoes de estoque
-- Projeto Integrador - FATEC Indaiatuba - 2o semestre de 2026
--
-- Migracao das tabelas de estoque do SQLite para o MySQL, seguindo as
-- conversoes de tipo do caminho D6-A:
--
--   REAL   -> DECIMAL(10,2) para dinheiro   (ponto flutuante acumula
--             erro de centavo, e o sistema existe para calcular custo)
--   REAL   -> DECIMAL(10,3) para quantidade (precisao de grama)
--   TEXT   -> DATE / DATETIME para datas
--   CHECK (... IN ...) -> ENUM para os dominios fechados
--
-- Executar DEPOIS de 01_schema_usuarios.sql, que cria o banco.
-- Idempotente: pode ser executado novamente sem quebrar.
-- =====================================================================

USE custo_certo;

-- =====================================================================
-- TABELA: ingredientes
-- ---------------------------------------------------------------------
-- Estoque ativo de cada insumo da cafeteria.
--   qtd     = estoque atual
--   qtd_max = capacidade de referencia, usada como 100% da barra de nivel
--
-- Restricoes aplicadas:
--   PRIMARY KEY .. id com AUTO_INCREMENT
--   UNIQUE ....... nome, para nao existir o mesmo insumo duas vezes
--   ENUM ......... unidade limitada as cinco medidas aceitas
--   CHECK ........ preco e quantidades nao podem ser negativos
--   DEFAULT ...... datas preenchidas pelo proprio banco
-- =====================================================================
CREATE TABLE IF NOT EXISTS ingredientes (
    id            INT           NOT NULL AUTO_INCREMENT,
    nome          VARCHAR(120)  NOT NULL,
    unidade       ENUM('kg', 'g', 'L', 'ml', 'un') NOT NULL,
    preco         DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    qtd           DECIMAL(10,3) NOT NULL DEFAULT 0.000,
    qtd_max       DECIMAL(10,3) NOT NULL DEFAULT 0.000,
    validade      DATE          NULL,
    criado_em     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT pk_ingredientes      PRIMARY KEY (id),
    CONSTRAINT uk_ingredientes_nome UNIQUE (nome),
    CONSTRAINT ck_ingredientes_preco   CHECK (preco >= 0),
    CONSTRAINT ck_ingredientes_qtd     CHECK (qtd >= 0),
    CONSTRAINT ck_ingredientes_qtd_max CHECK (qtd_max >= 0),

    INDEX idx_ingredientes_nome (nome),
    INDEX idx_ingredientes_validade (validade)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- =====================================================================
-- TABELA: movimentacoes_estoque
-- ---------------------------------------------------------------------
-- Historico de entradas (compras) e saidas (consumo pesado na balanca).
-- Cada compra guarda o preco pago naquele momento, o que alimenta o
-- grafico de evolucao de precos e a apuracao do CMV.
--
-- Restricoes aplicadas:
--   PRIMARY KEY .. id com AUTO_INCREMENT
--   FOREIGN KEY .. ingrediente_id referencia ingredientes(id)
--       ON DELETE CASCADE -> excluir o insumo apaga o historico dele,
--       porque uma movimentacao sem insumo nao significa nada
--   ENUM ......... tipo limitado a entrada ou saida
--   CHECK ........ quantidade tem de ser positiva
-- =====================================================================
CREATE TABLE IF NOT EXISTS movimentacoes_estoque (
    id             INT           NOT NULL AUTO_INCREMENT,
    ingrediente_id INT           NOT NULL,
    tipo           ENUM('entrada', 'saida') NOT NULL,
    quantidade     DECIMAL(10,3) NOT NULL,
    preco_unitario DECIMAL(10,2) NULL,
    validade       DATE          NULL,
    observacao     VARCHAR(255)  NULL,
    data           DATE          NOT NULL DEFAULT (CURRENT_DATE),
    criado_em      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_movimentacoes PRIMARY KEY (id),
    CONSTRAINT fk_mov_ingrediente
        FOREIGN KEY (ingrediente_id) REFERENCES ingredientes (id)
        ON DELETE CASCADE
        ON UPDATE CASCADE,
    CONSTRAINT ck_mov_quantidade CHECK (quantidade > 0),

    INDEX idx_mov_data (data),
    INDEX idx_mov_tipo (tipo)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- =====================================================================
-- CARGA INICIAL: ingredientes
-- ---------------------------------------------------------------------
-- Oito insumos, acima do minimo de 5 linhas por tabela. Os quatro
-- primeiros vieram do banco SQLite anterior; os demais completam um
-- cardapio de cafeteria plausivel.
--
-- Tres linhas existem para demonstrar comportamentos especificos:
--   id 2 (Leite)   -> validade JA VENCIDA, para o alerta de vencimento
--   id 3 (Acucar)  -> 0,8 de 5,0 = 16% do estoque, para o alerta de nivel baixo
--   id 8 (Copo)    -> sem validade, mostrando que a coluna aceita nulo
-- =====================================================================
INSERT IGNORE INTO ingredientes (id, nome, unidade, preco, qtd, qtd_max, validade) VALUES
    (1, 'Café em Grãos Arábica',   'kg', 52.90,   3.200,    5.000, '2026-12-31'),
    (2, 'Leite Integral',          'L',   5.60,   7.500,   10.000, '2026-08-10'),
    (3, 'Açúcar Cristal',          'kg',  4.90,   0.800,    5.000, '2027-06-01'),
    (4, 'Farinha de Trigo',        'kg',  3.20,   4.200,    8.000, '2026-09-15'),
    (5, 'Chocolate em Pó 50%',     'kg', 28.40,   1.500,    3.000, '2027-01-20'),
    (6, 'Creme de Leite Fresco',   'L',  18.90,   2.000,    4.000, '2026-09-05'),
    (7, 'Canela em Pó',            'g',   0.12, 450.000, 1000.000, '2027-03-10'),
    (8, 'Copo Descartável 300ml',  'un',  0.18, 320.000,  500.000, NULL);

-- =====================================================================
-- CARGA INICIAL: movimentacoes_estoque
-- ---------------------------------------------------------------------
-- Dez registros: seis entradas (compras) e quatro saidas (consumo).
-- As entradas do mesmo insumo com precos diferentes sao o que permite
-- mostrar a evolucao do custo de aquisicao ao longo do tempo.
-- =====================================================================
INSERT IGNORE INTO movimentacoes_estoque
    (id, ingrediente_id, tipo, quantidade, preco_unitario, observacao, data) VALUES
    ( 1, 1, 'entrada',  5.000, 49.90, 'Compra mensal - fornecedor Serra Negra', '2026-07-03'),
    ( 2, 2, 'entrada', 10.000,  5.40, 'Reposição semanal',                      '2026-07-06'),
    ( 3, 3, 'entrada',  5.000,  4.75, 'Compra mensal',                          '2026-07-03'),
    ( 4, 1, 'saida',    1.800,  NULL, 'Consumo pesado na balança',              '2026-07-20'),
    ( 5, 2, 'saida',    2.500,  NULL, 'Consumo pesado na balança',              '2026-07-22'),
    ( 6, 1, 'entrada',  2.000, 52.90, 'Reposição - preço subiu 6%',             '2026-08-04'),
    ( 7, 5, 'entrada',  3.000, 28.40, 'Primeira compra de chocolate',           '2026-08-04'),
    ( 8, 3, 'saida',    4.200,  NULL, 'Consumo do mês - confeitaria',           '2026-08-12'),
    ( 9, 6, 'entrada',  4.000, 18.90, 'Compra semanal',                         '2026-08-18'),
    (10, 5, 'saida',    1.500,  NULL, 'Consumo pesado na balança',              '2026-08-25');

-- =====================================================================
-- CONFERENCIA
-- =====================================================================
SELECT id, nome, unidade, preco, qtd, qtd_max, validade FROM ingredientes ORDER BY id;
SELECT COUNT(*) AS total_movimentacoes FROM movimentacoes_estoque;
