-- =====================================================
-- Custo Certo - Schema SQLite/libSQL
-- =====================================================
-- Idempotente: pode rodar repetidas vezes sem quebrar.
-- Compatível com SQLite local e Turso (libSQL).

-- Habilita foreign keys (SQLite desliga por padrão)
PRAGMA foreign_keys = ON;

-- =====================================================
-- INGREDIENTES
-- =====================================================
-- Estoque ativo de cada insumo da cafeteria.
-- "qtd" é o estoque atual; "qtd_max" é o pico (100% da barra).
CREATE TABLE IF NOT EXISTS ingredientes (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    nome        TEXT    NOT NULL,
    unidade     TEXT    NOT NULL CHECK (unidade IN ('kg', 'g', 'L', 'ml', 'un')),
    preco       REAL    NOT NULL DEFAULT 0,
    qtd         REAL    NOT NULL DEFAULT 0,
    qtd_max     REAL    NOT NULL DEFAULT 0,
    validade    TEXT,
    criado_em   TEXT    NOT NULL DEFAULT (datetime('now')),
    atualizado_em TEXT  NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_ingredientes_nome ON ingredientes(nome);

-- =====================================================
-- MOVIMENTAÇÕES DE ESTOQUE
-- =====================================================
-- Histórico de entradas (compras) e saídas (consumo via balança).
-- Cada compra registra preço pago naquele momento -> alimenta gráfico de evolução de preços.
CREATE TABLE IF NOT EXISTS movimentacoes_estoque (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    ingrediente_id  INTEGER NOT NULL,
    tipo            TEXT    NOT NULL CHECK (tipo IN ('entrada', 'saida')),
    quantidade      REAL    NOT NULL,
    preco_unitario  REAL,
    observacao      TEXT,
    data            TEXT    NOT NULL DEFAULT (date('now')),
    criado_em       TEXT    NOT NULL DEFAULT (datetime('now')),
    FOREIGN KEY (ingrediente_id) REFERENCES ingredientes(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_mov_ingrediente ON movimentacoes_estoque(ingrediente_id);
CREATE INDEX IF NOT EXISTS idx_mov_data ON movimentacoes_estoque(data);
CREATE INDEX IF NOT EXISTS idx_mov_tipo ON movimentacoes_estoque(tipo);

-- =====================================================
-- RECEITAS (estrutura preparada para uso futuro)
-- =====================================================
-- Cada receita (ex: "Cappuccino") consome quantidades específicas de ingredientes.
CREATE TABLE IF NOT EXISTS receitas (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    nome        TEXT    NOT NULL UNIQUE,
    descricao   TEXT,
    preco_venda REAL,
    criado_em   TEXT    NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS receita_ingredientes (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    receita_id      INTEGER NOT NULL,
    ingrediente_id  INTEGER NOT NULL,
    quantidade      REAL    NOT NULL,
    FOREIGN KEY (receita_id)     REFERENCES receitas(id)     ON DELETE CASCADE,
    FOREIGN KEY (ingrediente_id) REFERENCES ingredientes(id) ON DELETE RESTRICT,
    UNIQUE (receita_id, ingrediente_id)
);