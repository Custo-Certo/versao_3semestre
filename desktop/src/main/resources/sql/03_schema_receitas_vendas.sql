-- =====================================================================
-- Custo Certo - Ficha tecnica e vendas
-- =====================================================================
-- Terceiro script do banco. Execute depois de 01_schema_usuarios.sql e
-- 02_schema_insumos.sql, porque receita_ingredientes referencia a tabela
-- ingredientes criada la.
--
--   mysql -u root -p < 03_schema_receitas_vendas.sql
--
-- POR QUE ESTAS TABELAS EXISTEM
-- ---------------------------------------------------------------------
-- Ate aqui o sistema sabia o que ENTRA e o que SAI do estoque, mas nao
-- sabia o que a cafeteria VENDE. Sem isso nao ha como calcular margem
-- nem CMV: o painel exibia um CMV fixo de 28,6%, porque estimava a
-- receita multiplicando o proprio custo por 3,5 -- os dois lados da
-- divisao eram a mesma grandeza, e o resultado nunca mudava.
--
-- Estas tres tabelas fecham a conta:
--
--   receitas ............. o que a cafeteria vende, e por quanto
--   receita_ingredientes . quanto de cada insumo entra em cada produto
--   vendas ............... o que foi vendido, quando e por qual preco
--
-- Com elas o sistema calcula, alem do CMV de verdade, a VARIANCIA entre
-- o consumo teorico (vendas x ficha tecnica) e o consumo real (o que a
-- balanca pesou e deu baixa). A diferenca entre os dois e desperdicio,
-- dose exagerada ou perda -- e e o indicador mais dificil de obter em
-- gestao de food service, porque exige medir o consumo real.
-- =====================================================================

CREATE DATABASE IF NOT EXISTS custo_certo
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE custo_certo;

-- =====================================================================
-- TABELA: receitas
-- ---------------------------------------------------------------------
-- Um produto do cardapio. E a "ficha tecnica" da gestao de restaurante.
--
-- preco_venda fica aqui porque e o preco DE TABELA, o que esta no
-- cardapio hoje. O preco efetivamente praticado em cada venda fica na
-- tabela vendas -- eles divergem em promocao, cortesia ou reajuste.
--
-- Restricoes aplicadas:
--   PRIMARY KEY .. id com AUTO_INCREMENT
--   UNIQUE ....... nome, para nao existir o mesmo produto duas vezes
--   CHECK ........ preco nao negativo e nome com ao menos 2 caracteres
--   DEFAULT ...... produto nasce ativo, datas preenchidas pelo banco
-- =====================================================================
CREATE TABLE IF NOT EXISTS receitas (
    id            INT           NOT NULL AUTO_INCREMENT,
    nome          VARCHAR(120)  NOT NULL,
    descricao     VARCHAR(255)  NULL,
    preco_venda   DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    ativo         BOOLEAN       NOT NULL DEFAULT TRUE,
    criado_em     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT pk_receitas       PRIMARY KEY (id),
    CONSTRAINT uk_receitas_nome  UNIQUE (nome),
    CONSTRAINT ck_receitas_preco CHECK (preco_venda >= 0),
    CONSTRAINT ck_receitas_nome  CHECK (CHAR_LENGTH(TRIM(nome)) >= 2),

    INDEX idx_receitas_ativo (ativo)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- =====================================================================
-- TABELA: receita_ingredientes
-- ---------------------------------------------------------------------
-- Quanto de cada insumo entra em uma unidade do produto. E a tabela
-- associativa que resolve o relacionamento N:M entre receitas e
-- ingredientes.
--
-- A quantidade esta SEMPRE na unidade do proprio ingrediente: se o cafe
-- e cadastrado em kg, 18 gramas viram 0.018.
--
-- Restricoes aplicadas:
--   FOREIGN KEY .. receita_id -> receitas
--       ON DELETE CASCADE -> apagar o produto apaga a ficha dele, que
--       sozinha nao significa nada
--   FOREIGN KEY .. ingrediente_id -> ingredientes
--       ON DELETE RESTRICT -> NAO deixa excluir um insumo que faz parte
--       de alguma receita. E de proposito: excluir o cafe em silencio
--       deixaria metade do cardapio com a ficha tecnica furada
--   UNIQUE ....... o mesmo insumo nao pode aparecer duas vezes na mesma
--                  receita; seriam duas linhas dizendo coisas diferentes
--   CHECK ........ quantidade tem de ser positiva
-- =====================================================================
CREATE TABLE IF NOT EXISTS receita_ingredientes (
    id             INT           NOT NULL AUTO_INCREMENT,
    receita_id     INT           NOT NULL,
    ingrediente_id INT           NOT NULL,
    quantidade     DECIMAL(10,3) NOT NULL,

    CONSTRAINT pk_receita_ingredientes PRIMARY KEY (id),
    CONSTRAINT fk_ri_receita
        FOREIGN KEY (receita_id) REFERENCES receitas (id)
        ON DELETE CASCADE
        ON UPDATE CASCADE,
    CONSTRAINT fk_ri_ingrediente
        FOREIGN KEY (ingrediente_id) REFERENCES ingredientes (id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,
    CONSTRAINT uk_ri_receita_ingrediente UNIQUE (receita_id, ingrediente_id),
    CONSTRAINT ck_ri_quantidade CHECK (quantidade > 0),

    INDEX idx_ri_ingrediente (ingrediente_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- =====================================================================
-- TABELA: vendas
-- ---------------------------------------------------------------------
-- Uma linha por produto vendido em um dia. Nao e um PDV: o objetivo e
-- registrar o suficiente para calcular receita, margem e CMV.
--
-- preco_unitario guarda o preco PRATICADO naquela venda, e nao aponta
-- para receitas.preco_venda. E a mesma razao pela qual movimentacoes_
-- estoque guarda o preco pago em cada compra: reajustar o cardapio nao
-- pode reescrever o faturamento do mes passado.
--
-- Restricoes aplicadas:
--   FOREIGN KEY .. receita_id -> receitas
--       ON DELETE RESTRICT -> nao deixa excluir um produto que ja foi
--       vendido; o historico de faturamento ficaria orfao
--   CHECK ........ quantidade positiva, preco nao negativo
-- =====================================================================
CREATE TABLE IF NOT EXISTS vendas (
    id             INT           NOT NULL AUTO_INCREMENT,
    receita_id     INT           NOT NULL,
    quantidade     INT           NOT NULL,
    preco_unitario DECIMAL(10,2) NOT NULL,
    observacao     VARCHAR(255)  NULL,
    data           DATE          NOT NULL DEFAULT (CURRENT_DATE),
    criado_em      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_vendas PRIMARY KEY (id),
    CONSTRAINT fk_vendas_receita
        FOREIGN KEY (receita_id) REFERENCES receitas (id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,
    CONSTRAINT ck_vendas_quantidade CHECK (quantidade > 0),
    CONSTRAINT ck_vendas_preco      CHECK (preco_unitario >= 0),

    INDEX idx_vendas_data (data),
    INDEX idx_vendas_receita (receita_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- =====================================================================
-- CARGA INICIAL: receitas
-- ---------------------------------------------------------------------
-- Seis produtos, acima do minimo de 5 linhas por tabela. Os precos e as
-- fichas foram montados sobre os oito insumos ja cadastrados em
-- 02_schema_insumos.sql -- nenhuma receita usa insumo que nao existe.
-- =====================================================================
INSERT IGNORE INTO receitas (id, nome, descricao, preco_venda) VALUES
    (1, 'Café Expresso',     'Dose única de café arábica',                    6.00),
    (2, 'Cappuccino',        'Expresso, leite vaporizado, chocolate e canela', 11.00),
    (3, 'Latte',             'Expresso com leite vaporizado',                 10.00),
    (4, 'Mocha',             'Expresso, leite, chocolate e creme de leite',   12.50),
    (5, 'Chocolate Quente',  'Leite, chocolate 50% e creme de leite',          9.50),
    (6, 'Bolo de Cenoura',   'Fatia com cobertura de chocolate',               8.00);

-- =====================================================================
-- CARGA INICIAL: receita_ingredientes
-- ---------------------------------------------------------------------
-- Vinte e duas linhas. As quantidades estao na unidade de cada insumo:
-- cafe, acucar, farinha e chocolate em kg; leite e creme em L; canela em
-- g; copo em unidades. Por isso 18 gramas de cafe aparecem como 0.018.
-- =====================================================================
INSERT IGNORE INTO receita_ingredientes (id, receita_id, ingrediente_id, quantidade) VALUES
    -- 1 Café Expresso
    ( 1, 1, 1,   0.018),   -- café em grãos
    ( 2, 1, 8,   1.000),   -- copo
    -- 2 Cappuccino
    ( 3, 2, 1,   0.018),
    ( 4, 2, 2,   0.120),   -- leite
    ( 5, 2, 5,   0.005),   -- chocolate em pó
    ( 6, 2, 7,   0.500),   -- canela (g)
    ( 7, 2, 8,   1.000),
    -- 3 Latte
    ( 8, 3, 1,   0.018),
    ( 9, 3, 2,   0.180),
    (10, 3, 8,   1.000),
    -- 4 Mocha
    (11, 4, 1,   0.018),
    (12, 4, 2,   0.150),
    (13, 4, 5,   0.012),
    (14, 4, 6,   0.020),   -- creme de leite
    (15, 4, 8,   1.000),
    -- 5 Chocolate Quente
    (16, 5, 2,   0.200),
    (17, 5, 5,   0.020),
    (18, 5, 6,   0.030),
    (19, 5, 8,   1.000),
    -- 6 Bolo de Cenoura
    (20, 6, 4,   0.060),   -- farinha
    (21, 6, 3,   0.040),   -- açúcar
    (22, 6, 5,   0.015);

-- =====================================================================
-- CARGA INICIAL: vendas
-- ---------------------------------------------------------------------
-- Doze linhas, cobrindo julho e agosto de 2026 -- os mesmos meses das
-- movimentacoes de estoque, para que a comparacao entre consumo teorico
-- e consumo real tenha os dois lados.
--
-- As quantidades foram escolhidas para produzir uma variancia realista
-- contra as saidas ja registradas em 02_schema_insumos.sql:
--
--   cafe     julho: teorico 1,620 kg  x  real 1,800 kg  -> +11,1%
--   leite    julho: teorico 2,130 L   x  real 2,500 L   -> +17,4%
--   acucar   agosto: teorico 3,800 kg x  real 4,200 kg  -> +10,5%
--   chocolate agosto: teorico 1,499 kg x real 1,500 kg  ->  +0,1%
--
-- Os tres primeiros mostram consumo acima da ficha, que e o caso que o
-- indicador existe para pegar. O quarto mostra uma operacao sob
-- controle, para a tela nao parecer que so sabe acusar problema.
--
-- Copo, canela, creme e farinha aparecem nas fichas mas nao tem saida
-- registrada: sao insumos que nao passam pela balanca. O sistema trata
-- esse caso como "sem baixa registrada", e nao como consumo zero.
-- =====================================================================
INSERT IGNORE INTO vendas (id, receita_id, quantidade, preco_unitario, observacao, data) VALUES
    ( 1, 1, 25,  6.00, 'Movimento da semana',      '2026-07-05'),
    ( 2, 1, 25,  6.00, 'Movimento da semana',      '2026-07-12'),
    ( 3, 2,  8, 11.00, 'Movimento da semana',      '2026-07-10'),
    ( 4, 3,  4, 10.00, 'Movimento da semana',      '2026-07-15'),
    ( 5, 1, 25,  6.00, 'Movimento da semana',      '2026-07-19'),
    ( 6, 4,  3, 12.50, 'Movimento da semana',      '2026-07-18'),
    ( 7, 1, 20,  6.00, 'Movimento da semana',      '2026-08-05'),
    ( 8, 2,  6, 11.00, 'Movimento da semana',      '2026-08-08'),
    ( 9, 6, 50,  8.00, 'Encomenda de aniversário', '2026-08-12'),
    (10, 6, 45,  8.00, 'Encomenda de aniversário', '2026-08-14'),
    (11, 4,  2, 12.50, 'Movimento da semana',      '2026-08-16'),
    (12, 5,  1,  9.50, 'Movimento da semana',      '2026-08-20');

-- =====================================================================
-- CONFERENCIA
-- =====================================================================
SELECT 'receitas' AS tabela, COUNT(*) AS linhas FROM receitas
UNION ALL SELECT 'receita_ingredientes', COUNT(*) FROM receita_ingredientes
UNION ALL SELECT 'vendas',               COUNT(*) FROM vendas;

-- Custo de cada produto, calculado a partir dos precos atuais dos insumos.
-- E este numero que deixa de ser palpite: reajustou o fornecedor, muda aqui.
SELECT r.nome                                        AS produto,
       ROUND(SUM(ri.quantidade * i.preco), 4)        AS custo,
       r.preco_venda,
       ROUND(r.preco_venda - SUM(ri.quantidade * i.preco), 2) AS margem
  FROM receitas r
  JOIN receita_ingredientes ri ON ri.receita_id = r.id
  JOIN ingredientes i          ON i.id = ri.ingrediente_id
 GROUP BY r.id, r.nome, r.preco_venda
 ORDER BY r.id;
