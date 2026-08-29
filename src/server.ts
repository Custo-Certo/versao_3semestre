/**
 * Bootstrap do servidor.
 *
 * Sequência:
 *  1. Carrega .env
 *  2. Inicializa banco (cria schema se necessário)
 *  3. Roda seed automático se AUTO_SEED=true
 *  4. Cria o app Express
 *  5. Escuta na porta configurada
 *
 * Em Render, a variável PORT é injetada automaticamente pelo platform.
 */

import 'dotenv/config';
import { createApp } from './app.js';
import { initSchema, closeDb } from './config/database.js';
import { runSeed } from './database/seed.js';

const PORT = Number(process.env.PORT) || 3000;
const HOST = '0.0.0.0';

async function bootstrap(): Promise<void> {
  try {
    // 1) Inicializa schema (idempotente)
    await initSchema();

    // 2) Seed automático se solicitado e banco vazio
    if (process.env.AUTO_SEED === 'true') {
      await runSeed();
    }

    // 3) Cria e sobe o app
    const app = createApp();
    const server = app.listen(PORT, HOST, () => {
      console.log('');
      console.log('🚀 ============================================');
      console.log(`🚀 Custo Certo rodando em http://${HOST}:${PORT}`);
      console.log(`🚀 Ambiente: ${process.env.NODE_ENV || 'development'}`);
      console.log('🚀 ============================================');
      console.log('');
    });

    // 4) Graceful shutdown
    const shutdown = (signal: string) => {
      console.log(`\n🛑 ${signal} recebido, encerrando...`);
      server.close(() => {
        closeDb();
        console.log('👋 Servidor encerrado');
        process.exit(0);
      });

      // Força saída se demorar mais de 10s
      setTimeout(() => {
        console.error('⚠️  Forçando shutdown');
        process.exit(1);
      }, 10_000);
    };

    process.on('SIGTERM', () => shutdown('SIGTERM'));
    process.on('SIGINT', () => shutdown('SIGINT'));
  } catch (err) {
    console.error('💥 Falha ao iniciar servidor:', err);
    process.exit(1);
  }
}

bootstrap();