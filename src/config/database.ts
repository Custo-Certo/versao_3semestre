/**
 * Singleton de conexão com o banco.
 *
 * Usa @libsql/client, que aceita tanto SQLite local (file:) quanto
 * Turso cloud (libsql://). A migração de dev → produção é apenas
 * uma troca de variável de ambiente — nenhum código precisa mudar.
 *
 * - Local: DB_URL=file:./data/local.db
 * - Turso: DB_URL=libsql://seu-banco.turso.io + DB_AUTH_TOKEN=...
 */

import { createClient, type Client } from '@libsql/client';
import { readFileSync, mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));

let client: Client | null = null;
let initialized = false;

/**
 * Garante que a pasta do arquivo SQLite local existe (file:./data/local.db)
 */
function ensureLocalDbDir(dbUrl: string): void {
  if (!dbUrl.startsWith('file:')) return;
  const filePath = dbUrl.replace(/^file:/, '');
  const absolute = resolve(filePath);
  mkdirSync(dirname(absolute), { recursive: true });
}

/**
 * Retorna a instância única do client. Cria na primeira chamada.
 */
export function getDb(): Client {
  if (client) return client;

  const url = process.env.DB_URL;
  if (!url) {
    throw new Error(
      '❌ DB_URL não definida. Crie um .env baseado em .env.example.',
    );
  }

  ensureLocalDbDir(url);

  client = createClient({
    url,
    authToken: process.env.DB_AUTH_TOKEN || undefined,
  });

  console.log(`🗄️  Banco conectado: ${url.startsWith('libsql:') ? 'Turso (cloud)' : 'SQLite (local)'}`);
  return client;
}

/**
 * Aplica o schema.sql. Idempotente — usa CREATE TABLE IF NOT EXISTS.
 * Roda automaticamente no bootstrap.
 *
 * Procura o schema em duas localizações para suportar tanto execução
 * via tsx (src/) quanto via node após build (dist/).
 */
export async function initSchema(): Promise<void> {
  if (initialized) return;

  const db = getDb();

  // Tenta primeiro relativo ao arquivo compilado (dist/config → dist/database)
  // e depois relativo ao código fonte (src/config → src/database)
  const candidates = [
    resolve(__dirname, '..', 'database', 'schema.sql'),
    resolve(process.cwd(), 'src', 'database', 'schema.sql'),
    resolve(process.cwd(), 'dist', 'database', 'schema.sql'),
  ];

  let schemaSql: string | null = null;
  for (const path of candidates) {
    try {
      schemaSql = readFileSync(path, 'utf-8');
      break;
    } catch {
      // tenta próximo
    }
  }

  if (!schemaSql) {
    throw new Error(
      `❌ schema.sql não encontrado em nenhum dos caminhos: ${candidates.join(', ')}`,
    );
  }

  // libSQL aceita executeMultiple para rodar várias instruções de uma vez
 await db.executeMultiple(schemaSql);

// Migration: adiciona coluna validade em movimentacoes_estoque (idempotente)
try {
  await db.execute(
    `ALTER TABLE movimentacoes_estoque ADD COLUMN validade TEXT`
  );
  console.log('✅ Migration: coluna validade adicionada');
} catch (e: any) {
  // Coluna já existe — ignora silenciosamente
  if (!e.message?.includes('duplicate column')) throw e;
}

initialized = true;
console.log('✅ Schema aplicado');
}
export function closeDb(): void {
  if (client) {
    client.close();
    client = null;
    initialized = false;
  }
}