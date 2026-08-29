/**
 * Validação de payloads de ingrediente usando Zod.
 */

import { z } from 'zod';
import { AppError } from '../errors/app-error.js';
import type {
  IngredienteInput,
  IngredienteUpdateInput,
  CompraInput,
  Unidade,
} from '../models/ingrediente.model.js';

// Enum de unidades válidas
const UnidadeSchema = z.enum(['kg', 'g', 'L', 'ml', 'un']);

// Schema para data ISO (YYYY-MM-DD)
const IsoDateSchema = z
  .string()
  .regex(/^\d{4}-\d{2}-\d{2}$/, 'Data deve estar no formato YYYY-MM-DD')
  .refine(
    (date) => !Number.isNaN(Date.parse(date)),
    'Data inválida'
  );

// Schema para criação de ingrediente
const IngredienteInputSchema = z.object({
  nome: z
    .string()
    .min(1, 'Campo "nome" é obrigatório')
    .max(150, 'Campo "nome" deve ter no máximo 150 caracteres')
    .transform((v) => v.trim()),
  unidade: UnidadeSchema.refine(
    (u) => ['kg', 'g', 'L', 'ml', 'un'].includes(u),
    'Campo "unidade" deve ser um de: kg, g, L, ml, un'
  ),
  preco: z
    .number()
    .min(0, 'Campo "preco" deve ser um número >= 0')
    .finite(),
  qtd: z
    .number()
    .min(0, 'Campo "qtd" deve ser um número >= 0')
    .finite(),
  qtdMax: z
    .number()
    .positive('Campo "qtdMax" deve ser um número > 0')
    .finite()
    .optional(),
  validade: IsoDateSchema.nullable().optional(),
});

// Schema para atualização de ingrediente
const IngredienteUpdateInputSchema = z
  .object({
    nome: z
      .string()
      .min(1, 'Campo "nome" não pode ser vazio')
      .max(150, 'Campo "nome" deve ter no máximo 150 caracteres')
      .transform((v) => v.trim())
      .optional(),
    unidade: UnidadeSchema.optional(),
    preco: z
      .number()
      .min(0, 'Campo "preco" deve ser um número >= 0')
      .finite()
      .optional(),
    qtdMax: z
      .number()
      .positive('Campo "qtdMax" deve ser um número > 0')
      .finite()
      .optional(),
    validade: IsoDateSchema.nullable().optional(),
  })
  .refine(
    (obj) => Object.keys(obj).length > 0,
    'Envie ao menos um campo para atualizar'
  )
  .refine(
    (obj) => !('qtd' in obj),
    'Campo "qtd" não pode ser editado diretamente. Use POST /ingredientes/:id/compras (entrada) ou /retirada (saída).'
  );

// Schema para compra (entrada de estoque)
const CompraInputSchema = z.object({
  quantidade: z
    .number()
    .positive('Campo "quantidade" deve ser um número > 0')
    .finite(),
  precoUnitario: z
    .number()
    .positive('Campo "precoUnitario" deve ser um número > 0')
    .finite(),
  validade: IsoDateSchema.nullable().optional(),
  observacao: z.string().optional(),
});

// Schema para ID
const IdSchema = z
  .string()
  .regex(/^\d+$/, 'Parâmetro "id" inválido')
  .transform((v) => parseInt(v, 10))
  .refine((n) => n > 0, 'Parâmetro "id" deve ser um inteiro positivo');

/**
 * Valida e normaliza payload de criação de ingrediente.
 * Lança AppError(400) se inválido.
 */
export function validateIngredienteInput(body: unknown): IngredienteInput {
  try {
    return IngredienteInputSchema.parse(body);
  } catch (error) {
    if (error instanceof z.ZodError) {
      const message = error.errors[0]?.message || 'Erro de validação';
      throw new AppError(message, 400);
    }
    throw new AppError('Corpo da requisição inválido', 400);
  }
}

/**
 * Valida payload de atualização parcial (PUT /ingredientes/:id).
 */
export function validateIngredienteUpdate(body: unknown): IngredienteUpdateInput {
  try {
    return IngredienteUpdateInputSchema.parse(body);
  } catch (error) {
    if (error instanceof z.ZodError) {
      const message = error.errors[0]?.message || 'Erro de validação';
      throw new AppError(message, 400);
    }
    throw new AppError('Corpo da requisição inválido', 400);
  }
}

/**
 * Valida payload de uma nova compra (entrada de estoque).
 */
export function validateCompraInput(body: unknown): CompraInput {
  try {
    return CompraInputSchema.parse(body);
  } catch (error) {
    if (error instanceof z.ZodError) {
      const message = error.errors[0]?.message || 'Erro de validação';
      throw new AppError(message, 400);
    }
    throw new AppError('Corpo da requisição inválido', 400);
  }
}

/**
 * Valida e converte param :id de URL.
 */
export function validateId(rawId: string): number {
  try {
    return IdSchema.parse(rawId);
  } catch (error) {
    if (error instanceof z.ZodError) {
      throw new AppError('Parâmetro "id" inválido', 400);
    }
    throw new AppError('Parâmetro "id" inválido', 400);
  }
}