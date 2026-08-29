/**
 * Regras de negócio para ingredientes.
 *
 * Orquestra o repository e aplica regras (ex: 404 se não existe,
 * conflito se estoque insuficiente). Os controllers só conhecem este service.
 */

import { ingredienteRepository } from '../repositories/ingrediente.repo.js';
import { NotFoundError, ConflictError } from '../errors/app-error.js';
import type {
  Ingrediente,
  IngredienteInput,
  IngredienteUpdateInput,
  CompraInput,
} from '../models/ingrediente.model.js';
import type { HistoricoItem } from '../models/pesagem.model.js';

export const ingredienteService = {
  async listar(): Promise<Ingrediente[]> {
    return ingredienteRepository.listarTodos();
  },

  async buscarPorId(id: number): Promise<Ingrediente> {
    const ing = await ingredienteRepository.buscarPorId(id);
    if (!ing) throw new NotFoundError('Ingrediente');
    return ing;
  },

  async criar(input: IngredienteInput): Promise<Ingrediente> {
    return ingredienteRepository.criar(input);
  },

  /**
   * Atualização parcial. Lança 404 se o ingrediente não existir.
   * A regra de "qtd não editável" é aplicada na camada de schema.
   */
  async atualizar(id: number, update: IngredienteUpdateInput): Promise<Ingrediente> {
    const atualizado = await ingredienteRepository.atualizar(id, update);
    if (!atualizado) throw new NotFoundError('Ingrediente');
    return atualizado;
  },

  async registrarCompra(id: number, compra: CompraInput): Promise<Ingrediente> {
    const atualizado = await ingredienteRepository.registrarCompra(id, compra);
    if (!atualizado) throw new NotFoundError('Ingrediente');
    return atualizado;
  },

  async deletar(id: number): Promise<void> {
    const ok = await ingredienteRepository.remover(id);
    if (!ok) throw new NotFoundError('Ingrediente');
  },

  async historicoCompleto(): Promise<HistoricoItem[]> {
    return ingredienteRepository.listarHistorico();
  },

  /**
   * Abate consumo do estoque com checagem.
   * Lança ConflictError(409) se a quantidade pedida for maior que o disponível.
   */
  async abaterConsumo(
    ingredienteId: number,
    quantidade: number,
    observacao?: string,
  ): Promise<Ingrediente> {
    const atual = await ingredienteRepository.buscarPorId(ingredienteId);
    if (!atual) throw new NotFoundError('Ingrediente');

    if (quantidade > atual.qtd) {
      throw new ConflictError(
        `Estoque insuficiente. Disponível: ${atual.qtd} ${atual.unidade}, solicitado: ${quantidade} ${atual.unidade}`,
      );
    }

    const atualizado = await ingredienteRepository.abaterEstoque(
      ingredienteId,
      quantidade,
      observacao,
    );
    if (!atualizado) throw new NotFoundError('Ingrediente');
    return atualizado;
  },
};