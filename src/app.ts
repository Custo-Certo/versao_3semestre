/**
 * Configuração do Express.
 *
 * Mantém o app puro (sem listen) para facilitar testes.
 * O server.ts é quem inicializa o banco e sobe o servidor HTTP.
 */

import express, { type Express } from 'express';
import cors from 'cors';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import routes from './routes/index.js';
import { mountSwagger } from './config/swagger.js';
import { errorHandler, notFoundHandler } from './middlewares/error-handler.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

export function createApp(): Express {
  const app = express();

  // ---- CORS ----
  // Em produção, ALLOWED_ORIGINS é uma lista separada por vírgula.
  // Em dev (vazio) libera tudo — facilita testar do frontend local.
  const allowedOrigins = (process.env.ALLOWED_ORIGINS || '')
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean);

  app.use(
    cors({
      origin: allowedOrigins.length > 0 ? allowedOrigins : true,
      credentials: false,
    }),
  );

  // ---- Body parsing ----
  app.use(express.json({ limit: '1mb' }));
  app.use(express.urlencoded({ extended: true }));

  // ---- Logging simples (só em dev) ----
  if (process.env.NODE_ENV !== 'production') {
    app.use((req, _res, next) => {
      console.log(`${new Date().toISOString()} ${req.method} ${req.url}`);
      next();
    });
  }

  // ---- Documentação Swagger (UI em /api-docs, JSON em /api-docs.json) ----
  // Montado ANTES das rotas e do fallback SPA para não ser interceptado.
  mountSwagger(app);

  // ---- Estáticos do frontend ----
  // Em produção (build via tsc), __dirname é dist/. A pasta public/ fica
  // ao lado do dist/, então sobe um nível.
  const publicDir = path.resolve(__dirname, '..', 'public');
  app.use(express.static(publicDir));

  // ---- API ----
  app.use('/api', routes);

  // ---- Compatibilidade com versões antigas (sem prefixo /api) ----
  // O frontend e o ESP32 atuais batem direto em /balanca e /ingredientes.
  app.use('/', routes);

  // ---- Fallback SPA: qualquer GET não-API serve o index.html ----
  app.get(/^\/(?!api|ingredientes|balanca|health).*/, (_req, res) => {
    res.sendFile(path.join(publicDir, 'index.html'));
  });

  // ---- Tratamento de erros (sempre por último) ----
  app.use(notFoundHandler);
  app.use(errorHandler);

  return app;
}