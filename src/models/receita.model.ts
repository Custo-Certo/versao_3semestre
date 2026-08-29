/**
 * Modelos de Receita — estrutura preparada para uso futuro.
 *
 * Cada receita (ex: "Cappuccino", "Bolo de Cenoura") é composta por
 * ingredientes em quantidades específicas. Permite calcular o custo
 * de produção e abater o estoque automaticamente quando uma receita
 * é vendida.
 */

import type { Unidade } from './ingrediente.model.js';

export interface Receita {
  id: number;
  nome: string;
  descricao: string | null;
  precoVenda: number | null;
  criadoEm?: string;
}

export interface ReceitaIngrediente {
  id: number;
  receitaId: number;
  ingredienteId: number;
  quantidade: number;
}

/** Receita com ingredientes embutidos — pronta para exibir no frontend */
export interface ReceitaCompleta extends Receita {
  ingredientes: Array<{
    ingredienteId: number;
    nome: string;
    unidade: Unidade;
    quantidade: number;
    custoUnitario: number;
    custoTotal: number;
  }>;
  custoTotal: number;
  margem: number | null;
}

export interface ReceitaInput {
  nome: string;
  descricao?: string;
  precoVenda?: number;
  ingredientes: Array<{
    ingredienteId: number;
    quantidade: number;
  }>;
}