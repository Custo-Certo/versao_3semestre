/**
 * Camada de acesso a dados de Ingredientes.
 *
 * Responsável APENAS por SQL e mapeamento snake_case <-> camelCase.
 * Sem regras de negócio — isso fica nos services.
 */

import { getDb } from '../config/database.js';
import type {
  Ingrediente,
  IngredienteInput,
  IngredienteUpdateInput,
  CompraInput,
} from '../models/ingrediente.model.js';
import type { HistoricoItem, Movimentacao } from '../models/pesagem.model.js';

/** Converte linha do banco (snake_case) para o modelo (camelCase) */
function rowToIngrediente(row: Record<string, unknown>): Ingrediente {
  return {
    id: Number(row.id),
    nome: String(row.nome),
    unidade: row.unidade as Ingrediente['unidade'],
    preco: Number(row.preco),
    qtd: Number(row.qtd),
    qtdMax: Number(row.qtd_max),
    validade: row.validade ? String(row.validade) : null,
    criadoEm: row.criado_em ? String(row.criado_em) : undefined,
    atualizadoEm: row.atualizado_em ? String(row.atualizado_em) : undefined,
  };
}

export const ingredienteRepository = {
  /** Lista todos, mais recentes primeiro */
  async listarTodos(): Promise<Ingrediente[]> {
    const db = getDb();
    const { rows } = await db.execute('SELECT * FROM ingredientes ORDER BY id DESC');
    const ingredientes = rows.map((r) => rowToIngrediente(r as Record<string, unknown>));

    const { rows: movRows } = await db.execute(`
      SELECT id, ingrediente_id, quantidade, preco_unitario, data, validade
      FROM movimentacoes_estoque
      WHERE tipo = 'entrada'
      ORDER BY data ASC, id ASC
    `);

    const lotesPorId: Record<number, object[]> = {};
    for (const row of movRows) {
      const r = row as Record<string, unknown>;
      const ingId = Number(r.ingrediente_id);
      if (!lotesPorId[ingId]) lotesPorId[ingId] = [];
      lotesPorId[ingId].push({
        id:            Number(r.id),
        quantidade:    Number(r.quantidade),
        precoUnitario: Number(r.preco_unitario ?? 0),
        dataEntrada:   String(r.data),
        validade:      r.validade ? String(r.validade) : null,
      });
    }

    return ingredientes.map((ing) => ({
      ...ing,
      lotes: lotesPorId[ing.id] ?? [],
    }));
  },

  /** Busca por ID. Retorna null se não existir. */
  async buscarPorId(id: number): Promise<Ingrediente | null> {
    const db = getDb();
    const { rows } = await db.execute({
      sql: 'SELECT * FROM ingredientes WHERE id = ? LIMIT 1',
      args: [id],
    });
    if (rows.length === 0) return null;
    return rowToIngrediente(rows[0] as Record<string, unknown>);
  },

  /**
   * Cria um ingrediente E registra a compra inicial no histórico,
   * dentro de uma transação para manter consistência.
   */
  async criar(input: IngredienteInput): Promise<Ingrediente> {
    const db = getDb();
    const qtdMax = input.qtdMax ?? input.qtd;
    const hoje = new Date().toISOString().slice(0, 10);

    // libSQL aceita batch transacional
    const results = await db.batch(
      [
        {
          sql: `INSERT INTO ingredientes (nome, unidade, preco, qtd, qtd_max, validade)
                VALUES (?, ?, ?, ?, ?, ?)`,
          args: [
            input.nome,
            input.unidade,
            input.preco,
            input.qtd,
            qtdMax,
            input.validade ?? null,
          ],
        },
      ],
      'write',
    );

    const id = Number(results[0].lastInsertRowid);

    // Registra a entrada inicial no histórico (se qtd > 0)
    if (input.qtd > 0) {
      await db.execute({
        sql: `INSERT INTO movimentacoes_estoque
              (ingrediente_id, tipo, quantidade, preco_unitario, observacao, data, validade)
              VALUES (?, 'entrada', ?, ?, 'Cadastro inicial', ?, ?)`,
        args: [id, input.qtd, input.preco, hoje, input.validade ?? null],
      });
    }

    const criado = await this.buscarPorId(id);
    if (!criado) throw new Error('Falha ao recuperar ingrediente recém-criado');
    return criado;
  },

  /**
   * Atualização parcial de um ingrediente (PUT /ingredientes/:id).
   *
   * Monta o SET dinamicamente a partir dos campos enviados. NUNCA toca em `qtd`
   * (estoque só muda via movimentação). Retorna null se o ingrediente não existir.
   */
  async atualizar(id: number, update: IngredienteUpdateInput): Promise<Ingrediente | null> {
    const db = getDb();
    const atual = await this.buscarPorId(id);
    if (!atual) return null;

    const sets: string[] = [];
    const args: (string | number | null)[] = [];

    if (update.nome !== undefined)     { sets.push('nome = ?');     args.push(update.nome); }
    if (update.unidade !== undefined)  { sets.push('unidade = ?');  args.push(update.unidade); }
    if (update.preco !== undefined)    { sets.push('preco = ?');    args.push(update.preco); }
    if (update.qtdMax !== undefined)   { sets.push('qtd_max = ?');  args.push(update.qtdMax); }
    if (update.validade !== undefined) { sets.push('validade = ?'); args.push(update.validade); }

    // Schema garante que há ao menos um campo; mas se nada chegou, devolve o atual.
    if (sets.length === 0) return atual;

    sets.push("atualizado_em = datetime('now')");
    args.push(id);

    await db.execute({
      sql: `UPDATE ingredientes SET ${sets.join(', ')} WHERE id = ?`,
      args,
    });

    return this.buscarPorId(id);
  },

  /**
   * Registra uma nova compra: aumenta qtd, atualiza qtd_max e preço,
   * opcionalmente atualiza validade, e grava entrada no histórico.
   * Tudo em transação.
   */
  async registrarCompra(id: number, compra: CompraInput): Promise<Ingrediente | null> {
    const db = getDb();
    const atual = await this.buscarPorId(id);
    if (!atual) return null;

    const novaQtd = atual.qtd + compra.quantidade;
    const validade = compra.validade !== undefined ? compra.validade : atual.validade;
    const hoje = new Date().toISOString().slice(0, 10);

    await db.batch(
      [
        {
          sql: `UPDATE ingredientes
                SET qtd = ?,
                    qtd_max = ?,
                    preco = ?,
                    validade = ?,
                    atualizado_em = datetime('now')
                WHERE id = ?`,
          args: [novaQtd, novaQtd, compra.precoUnitario, validade, id],
        },
        {
          sql: `INSERT INTO movimentacoes_estoque
                (ingrediente_id, tipo, quantidade, preco_unitario, observacao, data, validade)
                VALUES (?, 'entrada', ?, ?, ?, ?, ?)`,
          args: [
            id,
            compra.quantidade,
            compra.precoUnitario,
            compra.observacao ?? 'Nova compra',
            hoje,
            compra.validade ?? null,
          ],
        },
      ],
      'write',
    );

    return this.buscarPorId(id);
  },

  /**
   * Abate quantidade do estoque (consumo via balança).
   * Registra saída no histórico. Não decresce abaixo de zero.
   */
  async abaterEstoque(id: number, quantidade: number, observacao?: string): Promise<Ingrediente | null> {
    const db = getDb();
    const atual = await this.buscarPorId(id);
    if (!atual) return null;

    const novaQtd = Math.max(0, atual.qtd - quantidade);
    const hoje = new Date().toISOString().slice(0, 10);

    await db.batch(
      [
        {
          sql: `UPDATE ingredientes
                SET qtd = ?, atualizado_em = datetime('now')
                WHERE id = ?`,
          args: [novaQtd, id],
        },
        {
          sql: `INSERT INTO movimentacoes_estoque
                (ingrediente_id, tipo, quantidade, preco_unitario, observacao, data)
                VALUES (?, 'saida', ?, ?, ?, ?)`,
          args: [id, quantidade, atual.preco, observacao ?? 'Consumo balança', hoje],
        },
      ],
      'write',
    );

    return this.buscarPorId(id);
  },

  /** Remove ingrediente. ON DELETE CASCADE limpa as movimentações. */
  async remover(id: number): Promise<boolean> {
    const db = getDb();
    const result = await db.execute({
      sql: 'DELETE FROM ingredientes WHERE id = ?',
      args: [id],
    });
    return Number(result.rowsAffected) > 0;
  },

  /**
   * Histórico completo de movimentações com nome do ingrediente.
   * Formato compatível com o que o frontend já consome (campo "produtoId").
   */
  async listarHistorico(): Promise<HistoricoItem[]> {
    const db = getDb();
    const { rows } = await db.execute(`
      SELECT
        m.id,
        m.ingrediente_id   AS produto_id,
        i.nome             AS nome,
        i.unidade          AS unidade,
        m.quantidade       AS qtd,
        m.preco_unitario   AS preco,
        m.data             AS data,
        m.tipo             AS tipo
      FROM movimentacoes_estoque m
      JOIN ingredientes i ON i.id = m.ingrediente_id
      WHERE m.tipo = 'entrada'
      ORDER BY m.data DESC, m.id DESC
    `);

    return rows.map((row) => {
      const r = row as Record<string, unknown>;
      return {
        id: Number(r.id),
        produtoId: Number(r.produto_id),
        nome: String(r.nome),
        unidade: String(r.unidade),
        qtd: Number(r.qtd),
        preco: Number(r.preco ?? 0),
        data: String(r.data),
      };
    });
  },

  /** Movimentações de um ingrediente específico (entradas e saídas) */
  async listarMovimentacoesPorIngrediente(ingredienteId: number): Promise<Movimentacao[]> {
    const db = getDb();
    const { rows } = await db.execute({
      sql: `SELECT * FROM movimentacoes_estoque
            WHERE ingrediente_id = ?
            ORDER BY data DESC, id DESC`,
      args: [ingredienteId],
    });

    return rows.map((row) => {
      const r = row as Record<string, unknown>;
      return {
        id: Number(r.id),
        ingredienteId: Number(r.ingrediente_id),
        tipo: r.tipo as 'entrada' | 'saida',
        quantidade: Number(r.quantidade),
        precoUnitario: r.preco_unitario != null ? Number(r.preco_unitario) : null,
        observacao: r.observacao ? String(r.observacao) : null,
        data: String(r.data),
        criadoEm: String(r.criado_em),
      };
    });
  },
};