/**
 * Regras de negócio da balança.
 *
 * Faz a ponte entre o estado em memória (peso/tara) e o estoque persistente.
 * Quando o frontend confirma uma pesagem, chama o ingredienteService para
 * abater do estoque — e aí sim isso vira uma movimentação registrada no banco.
 */

import { pesagemRepository } from '../repositories/pesagem.repo.js';
import type { PesoSnapshot } from '../repositories/pesagem.repo.js';
import { ingredienteService } from './ingrediente.service.js';
import { AppError } from '../errors/app-error.js';
import type {
  BalancaState,
  ConfirmacaoPesagem,
} from '../models/pesagem.model.js';

export const balancaService = {
  /** ESP32 chama com peso medido */
  registrarLeitura(peso: number): void {
    pesagemRepository.setPeso(Math.max(0, peso));
  },

  /** Frontend lê peso atual + status de conexão */
  getEstadoAtual(): BalancaState & { online: boolean } {
    return {
      ...pesagemRepository.getState(),
      online: pesagemRepository.estaOnline(),
    };
  },

  /** Frontend solicita tara */
  solicitarTara(): void {
    pesagemRepository.solicitarTara();
  },

  /** ESP32 verifica se deve tarar (consome a flag) */
  verificarTara(): boolean {
    return pesagemRepository.consumirTara();
  },

  /**
   * Registra callback chamado a cada nova leitura do ESP32.
   * Retorna função de unsubscribe — chame-a quando a conexão SSE fechar.
   */
  addSseListener(fn: (snapshot: PesoSnapshot) => void): () => void {
    return pesagemRepository.addListener(fn);
  },

  /**
   * Confirma pesagem: valida peso > 0, abate do estoque e
   * registra a saída como movimentação.
   */
  async confirmarPesagem(
    ingredienteId: number,
    quantidadeConsumida: number,
  ): Promise<ConfirmacaoPesagem> {
    const peso = pesagemRepository.getState().pesoAtual;

    if (peso <= 0.001) {
      throw new AppError('Peso inválido. Coloque o item na balança.', 400);
    }

    const atualizado = await ingredienteService.abaterConsumo(
      ingredienteId,
      quantidadeConsumida,
      `Pesagem de ${quantidadeConsumida.toFixed(3)}`,
    );

    return {
      ok: true,
      pesoConfirmado: peso,
      ingredienteId: atualizado.id,
      novaQtd: atualizado.qtd,
    };
  },
};