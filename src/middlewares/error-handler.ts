/**
 * Middleware global de tratamento de erros.
 *
 * No Express 5, erros lançados (sync ou async) em handlers caem aqui automaticamente.
 * AppError vira resposta com status correto; qualquer outro erro vira 500.
 */

import type { ErrorRequestHandler, Request, Response, NextFunction } from 'express';
import { AppError } from '../errors/app-error.js';

export const errorHandler: ErrorRequestHandler = (
  err: unknown,
  _req: Request,
  res: Response,
  _next: NextFunction,
) => {
  if (err instanceof AppError) {
    res.status(err.statusCode).json({
      erro: err.message,
      tipo: err.name,
    });
    return;
  }

  // Erro inesperado — loga stack mas não vaza para o cliente
  console.error('💥 Erro não tratado:', err);

  const message = err instanceof Error ? err.message : 'Erro interno do servidor';
  const isProd = process.env.NODE_ENV === 'production';

  res.status(500).json({
    erro: isProd ? 'Erro interno do servidor' : message,
    tipo: 'InternalServerError',
  });
};

/**
 * Middleware 404 — captura rotas não definidas.
 * Coloque no app *depois* de todas as rotas e *antes* do errorHandler.
 */
export const notFoundHandler = (req: Request, res: Response): void => {
  res.status(404).json({
    erro: `Rota não encontrada: ${req.method} ${req.originalUrl}`,
    tipo: 'NotFoundError',
  });
};