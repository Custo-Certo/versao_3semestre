/**
 * Seed inicial do banco.
 *
 * Insere dados de demonstração SE a tabela estiver vazia.
 * Idempotente — rodar várias vezes não duplica nada.
 *
 * Pode ser invocado de duas formas:
 *  1) CLI:  npm run seed
 *  2) Bootstrap automático: AUTO_SEED=true no .env (chamado em app.ts)
 */

import 'dotenv/config';
import { getDb, initSchema, closeDb } from '../config/database.js';

interface SeedIngrediente {
  nome: string;
  unidade: 'kg' | 'g' | 'L' | 'ml' | 'un';
  preco: number;
  qtd: number;
  qtdMax: number;
  validade: string | null;
}

const SEED_INGREDIENTES: SeedIngrediente[] = [
  { nome: 'Café em Grãos Arábica', unidade: 'kg', preco: 52.90, qtd: 3.2, qtdMax: 5.0, validade: '2026-12-31' },
  { nome: 'Leite Integral',         unidade: 'L',  preco: 5.60,  qtd: 7.5, qtdMax: 10.0, validade: '2026-08-10' },
  { nome: 'Açúcar Cristal',         unidade: 'kg', preco: 4.90,  qtd: 0.8, qtdMax: 5.0,  validade: '2027-06-01' },
  { nome: 'Farinha de Trigo',       unidade: 'kg', preco: 3.20,  qtd: 4.2, qtdMax: 8.0,  validade: '2026-09-15' },
];

export async function runSeed(): Promise<{ inserted: number }> {
  const db = getDb();
  await initSchema();

  // Só popula se o banco estiver vazio
  const { rows } = await db.execute('SELECT COUNT(*) as total FROM ingredientes');
  const total = Number(rows[0]?.total ?? 0);

  if (total > 0) {
    console.log(`⏭️  Seed pulado: banco já tem ${total} ingrediente(s)`);
    return { inserted: 0 };
  }

  let inserted = 0;
  const hoje = new Date().toISOString().slice(0, 10);

  for (const ing of SEED_INGREDIENTES) {
    const result = await db.execute({
      sql: `INSERT INTO ingredientes (nome, unidade, preco, qtd, qtd_max, validade)
            VALUES (?, ?, ?, ?, ?, ?)`,
      args: [ing.nome, ing.unidade, ing.preco, ing.qtd, ing.qtdMax, ing.validade],
    });

    const ingredienteId = Number(result.lastInsertRowid);

    // Registra a "compra inicial" no histórico
   await db.execute({
  sql: `INSERT INTO movimentacoes_estoque
        (ingrediente_id, tipo, quantidade, preco_unitario, observacao, data, validade)
        VALUES (?, 'entrada', ?, ?, 'Estoque inicial', ?, ?)`,
  args: [ingredienteId, ing.qtdMax, ing.preco, hoje, ing.validade],
});

    inserted++;
  }

  console.log(`🌱 Seed concluído: ${inserted} ingrediente(s) inserido(s)`);
  return { inserted };
}

// Permite rodar como CLI: `npm run seed`
const isMainModule = import.meta.url === `file://${process.argv[1]}`;
if (isMainModule) {
  runSeed()
    .then(() => closeDb())
    .catch((err) => {
      console.error('❌ Erro no seed:', err);
      process.exit(1);
    });
}