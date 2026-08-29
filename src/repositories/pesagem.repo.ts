/**
 * "Repository" de pesagem.
 *
 * O estado da balança (peso atual + flag de tara) é volátil por design:
 * - Atualizado várias vezes por segundo pelo ESP32 — persistir no DB
 *   seria desperdício e geraria carga inútil em Turso.
 * - Quando o servidor reinicia, a balança zera junto. Comportamento desejado.
 *
 * Mantemos um repository mesmo assim para preservar a separação de camadas:
 * o service e o controller não sabem onde o estado vive.
 *
 * O padrão de listeners permite que a camada de serviço registre callbacks
 * que são acionados a cada nova leitura do ESP32 — base do push via SSE.
 */

import type { BalancaState } from '../models/pesagem.model.js';

const state: BalancaState = {
  pesoAtual: 0,
  precisaTarar: false,
  ultimaAtualizacao: 0,
};

/** Snapshot enviado aos listeners a cada nova leitura */
export interface PesoSnapshot {
  peso: number;
  online: boolean;
  ultimaAtualizacao: number;
}

type PesoListener = (snapshot: PesoSnapshot) => void;

const listeners = new Set<PesoListener>();

export const pesagemRepository = {
  /** Lê o estado completo */
  getState(): Readonly<BalancaState> {
    return { ...state };
  },

  /** ESP32 atualiza o peso e notifica listeners SSE */
  setPeso(peso: number): void {
    state.pesoAtual = peso;
    state.ultimaAtualizacao = Date.now();

    const snapshot: PesoSnapshot = {
      peso: state.pesoAtual,
      online: true, // acabou de receber leitura — definitivamente online
      ultimaAtualizacao: state.ultimaAtualizacao,
    };
    listeners.forEach((fn) => {
      try { fn(snapshot); } catch { /* cliente desconectado; será limpo via req.close */ }
    });
  },

  /**
   * Registra um listener SSE. Retorna função de cleanup para usar em req.on('close').
   */
  addListener(fn: PesoListener): () => void {
    listeners.add(fn);
    return () => listeners.delete(fn);
  },

  /** Quantidade de clientes SSE conectados (útil para debug/health) */
  sseClientCount(): number {
    return listeners.size;
  },

  /** Frontend pede tara */
  solicitarTara(): void {
    state.precisaTarar = true;
  },

  /** ESP32 consome a flag de tara (read-and-clear) */
  consumirTara(): boolean {
    const precisava = state.precisaTarar;
    state.precisaTarar = false;
    return precisava;
  },

  /** Indica se a balança recebeu leitura recente (< 5s) */
  estaOnline(): boolean {
    if (state.ultimaAtualizacao === 0) return false;
    return Date.now() - state.ultimaAtualizacao < 5000;
  },
};