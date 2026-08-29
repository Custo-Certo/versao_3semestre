/**
 * Validação dos payloads vindos da balança (ESP32) e do frontend usando Zod.
 */

import { z } from 'zod';
import { AppError } from '../errors/app-error.js';

// Schema para payload de peso (ESP32)
const PesoPayloadSchema = z.object({
  peso: z
    .number()
    .min(-100, 'Peso fora do intervalo aceito (-100 a 1000 kg)')
    .max(1000, 'Peso fora do intervalo aceito (-100 a 1000 kg)')
    .finite('Campo "peso" deve ser numérico'),
});

// Schema para payload de confirmação de pesagem (frontend)
const ConfirmarPayloadSchema = z.object({
  ingredienteId: z
    .number()
    .int('Campo "ingredienteId" deve ser um inteiro')
    .positive('Campo "ingredienteId" inválido'),
  quantidadeConsumida: z
    .number()
    .positive('Campo "quantidadeConsumida" deve ser > 0')
    .finite(),
});

interface PesoPayload {
  peso: number;
}

interface ConfirmarPayload {
  ingredienteId: number;
  quantidadeConsumida: number;
}

/**
 * Valida POST /balanca/peso (ESP32 enviando peso medido).
 * O ESP32 manda { peso: number } a cada loop.
 */
export function validatePesoPayload(body: unknown): PesoPayload {
  try {
    return PesoPayloadSchema.parse(body);
  } catch (error) {
    if (error instanceof z.ZodError) {
      const message = error.errors[0]?.message || 'Erro de validação';
      throw new AppError(message, 400);
    }
    throw new AppError('Payload inválido', 400);
  }
}

/**
 * Valida POST /balanca/confirmar — vem do frontend ao bater "Confirmar pesagem".
 * Recebe qual ingrediente e quanto vai abater do estoque.
 */
export function validateConfirmarPayload(body: unknown): ConfirmarPayload {
  try {
    return ConfirmarPayloadSchema.parse(body);
  } catch (error) {
    if (error instanceof z.ZodError) {
      const message = error.errors[0]?.message || 'Erro de validação';
      throw new AppError(message, 400);
    }
    throw new AppError('Payload inválido', 400);
  }
}