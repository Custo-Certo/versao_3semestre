/**
 * Erro de aplicação com status HTTP associado.
 *
 * Use sempre que quiser sinalizar uma falha esperada (validação, regra de negócio,
 * recurso não encontrado, etc). O middleware error-handler converte isso na
 * resposta HTTP correta.
 *
 * Para falhas inesperadas (bug, banco fora do ar) deixe o erro normal subir —
 * o middleware traduz em 500.
 */

export class AppError extends Error {
  public readonly statusCode: number;
  public readonly isOperational: boolean;

  constructor(message: string, statusCode = 400) {
    super(message);
    this.name = 'AppError';
    this.statusCode = statusCode;
    this.isOperational = true;

    // Mantém stack trace correto
    Error.captureStackTrace?.(this, this.constructor);
  }
}

/** Atalho para 404 */
export class NotFoundError extends AppError {
  constructor(resource = 'Recurso') {
    super(`${resource} não encontrado`, 404);
    this.name = 'NotFoundError';
  }
}

/** Atalho para 409 (conflito de estado, ex: estoque insuficiente) */
export class ConflictError extends AppError {
  constructor(message: string) {
    super(message, 409);
    this.name = 'ConflictError';
  }
}