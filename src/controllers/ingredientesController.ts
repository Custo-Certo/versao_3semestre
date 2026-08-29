/**
 * Controller HTTP para ingredientes.
 *
 * Apenas adapta req/res para o service. Sem regras de negócio.
 * Erros lançados (AppError, NotFoundError) caem no errorHandler global.
 */

import type { Request, Response } from 'express';
import { ingredienteService } from '../services/ingrediente.service.js';
import {
  validateIngredienteInput,
  validateIngredienteUpdate,
  validateCompraInput,
  validateId,
} from '../schemas/ingrediente.schema.js';

export const ingredientesController = {
  /** GET /ingredientes */
  async listar(_req: Request, res: Response): Promise<void> {
    const ingredientes = await ingredienteService.listar();
    res.json(ingredientes);
  },

  /** GET /ingredientes/historico */
  async historico(_req: Request, res: Response): Promise<void> {
    const historico = await ingredienteService.historicoCompleto();
    res.json(historico);
  },

  /** GET /ingredientes/:id */
  async buscar(req: Request<{ id: string }>, res: Response): Promise<void> {
    const id = validateId(req.params.id);
    const ingrediente = await ingredienteService.buscarPorId(id);
    res.json(ingrediente);
  },

  /** POST /ingredientes */
  async cadastrar(req: Request, res: Response): Promise<void> {
    const input = validateIngredienteInput(req.body);
    const novo = await ingredienteService.criar(input);
    res.status(201).json(novo);
  },

  /** PUT /ingredientes/:id — atualização parcial (não altera qtd) */
  async atualizar(req: Request<{ id: string }>, res: Response): Promise<void> {
    const id = validateId(req.params.id);
    const update = validateIngredienteUpdate(req.body);
    const atualizado = await ingredienteService.atualizar(id, update);
    res.json(atualizado);
  },

  /** POST /ingredientes/:id/compras */
  async registrarCompra(req: Request<{ id: string }>, res: Response): Promise<void> {
    const id = validateId(req.params.id);
    const compra = validateCompraInput(req.body);
    const atualizado = await ingredienteService.registrarCompra(id, compra);
    res.status(200).json(atualizado);
  },

  /** DELETE /ingredientes/:id */
  async deletar(req: Request<{ id: string }>, res: Response): Promise<void> {
    const id = validateId(req.params.id);
    await ingredienteService.deletar(id);
    res.status(204).send();
  },

  /** POST /ingredientes/:id/retirada */
  async retirar(req: Request<{ id: string }>, res: Response): Promise<void> {
    const id = validateId(req.params.id);
    const { quantidade } = req.body;
    if (!quantidade || quantidade <= 0) {
      res.status(400).json({ erro: 'Quantidade inválida' });
      return;
    }
    const atualizado = await ingredienteService.abaterConsumo(id, quantidade, 'Retirada manual');
    res.json(atualizado);
  },
};