/**
 * Modelos relacionados à balança e ao histórico de movimentações.
 */

/** Estado em tempo real da balança (vive em memória no service) */
export interface BalancaState {
  pesoAtual: number;       // em kg, vindo do ESP32
  precisaTarar: boolean;   // flag que o frontend liga e o ESP32 consome
  ultimaAtualizacao: number; // timestamp epoch ms da última leitura
}

/** Registro de uma movimentação no banco (entrada ou saída) */
export interface Movimentacao {
  id: number;
  ingredienteId: number;
  tipo: 'entrada' | 'saida';
  quantidade: number;
  precoUnitario: number | null;
  observacao: string | null;
  data: string;            // ISO date 'YYYY-MM-DD'
  criadoEm: string;
}

/** Linha do histórico enriquecida com dados do ingrediente (para o frontend) */
export interface HistoricoItem {
  id: number;
  produtoId: number;
  nome: string;
  unidade: string;
  qtd: number;
  preco: number;
  data: string;
}

/** Resultado de uma confirmação de pesagem */
export interface ConfirmacaoPesagem {
  ok: boolean;
  pesoConfirmado?: number;
  ingredienteId?: number;
  novaQtd?: number;
  erro?: string;
}