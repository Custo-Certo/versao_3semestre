/**
 * Controller HTTP para a balança.
 *
 * Endpoints divididos em dois consumidores:
 *  - ESP32:    POST /balanca/peso, GET /balanca/tara
 *  - Frontend: GET /balanca/peso,  POST /balanca/tara, POST /balanca/confirmar
 */

import type { Request, Response } from 'express';
import { balancaService } from '../services/balanca.service.js';
import {
  validatePesoPayload,
  validateConfirmarPayload,
} from '../schemas/balanca.schema.js';

export const balancaController = {
  /** POST /balanca/peso — ESP32 envia leitura */
  receberPeso(req: Request, res: Response): void {
    const { peso } = validatePesoPayload(req.body);
    balancaService.registrarLeitura(peso);

    // A resposta carrega o pedido de tara pendente. Assim o ESP32 descobre que
    // precisa zerar sem fazer uma segunda requisição — antes ele consultava
    // GET /balanca/tara a cada segundo, o que dobrava as idas ao servidor e
    // atrasava o envio do peso, porque a chamada é bloqueante no firmware.
    //
    // ATENÇÃO — firmware e backend passam a andar juntos: verificarTara()
    // CONSOME a flag, então quem ler primeiro fica com ela. Um ESP32 com o
    // firmware antigo (que só faz GET /balanca/tara) nunca mais receberia a
    // tara, porque este POST a consumiria antes. Ao publicar esta versão do
    // backend, é obrigatório regravar o firmware.
    res.json({ ok: true, tarar: balancaService.verificarTara() });
  },

  /** GET /balanca/peso — Frontend faz polling (mantido para compatibilidade) */
  lerPeso(_req: Request, res: Response): void {
    const estado = balancaService.getEstadoAtual();
    res.json({
      peso: estado.pesoAtual,
      online: estado.online,
      ultimaAtualizacao: estado.ultimaAtualizacao,
    });
  },

  /**
   * GET /balanca/stream — SSE: frontend abre uma conexão persistente e
   * recebe o peso em push sempre que o ESP32 enviar nova leitura.
   *
   * Protocolo SSE padrão (text/event-stream):
   *   data: {"peso":0.123,"online":true,"ultimaAtualizacao":1234567890}\n\n
   *
   * O EventSource do browser reconecta automaticamente se cair.
   */
  stream(req: Request, res: Response): void {
    // Cabeçalhos obrigatórios do SSE
    res.setHeader('Content-Type', 'text/event-stream');
    res.setHeader('Cache-Control', 'no-cache');
    res.setHeader('Connection', 'keep-alive');
    // Evita que proxies/Render buffer a resposta
    res.setHeader('X-Accel-Buffering', 'no');
    res.flushHeaders();

    // Envia o estado atual imediatamente (sem esperar próxima leitura do ESP32)
    const estadoAtual = balancaService.getEstadoAtual();
    const snapshotInicial = JSON.stringify({
      peso: estadoAtual.pesoAtual,
      online: estadoAtual.online,
      ultimaAtualizacao: estadoAtual.ultimaAtualizacao,
    });
    res.write(`data: ${snapshotInicial}\n\n`);

    // Heartbeat a cada 15s para manter a conexão viva em proxies com timeout
    const heartbeat = setInterval(() => {
      try { res.write(': heartbeat\n\n'); } catch { clearInterval(heartbeat); }
    }, 15_000);

    // Registra listener: cada nova leitura do ESP32 dispara o push
    const unsubscribe = balancaService.addSseListener((snapshot) => {
      try {
        res.write(`data: ${JSON.stringify(snapshot)}\n\n`);
      } catch {
        unsubscribe();
      }
    });

    // Limpeza ao fechar a aba / navegar para outra página
    req.on('close', () => {
      clearInterval(heartbeat);
      unsubscribe();
    });
  },

  /** POST /balanca/tara — Frontend solicita */
  solicitarTara(_req: Request, res: Response): void {
    balancaService.solicitarTara();
    res.json({ ok: true });
  },

  /** GET /balanca/tara — ESP32 verifica e zera flag */
  verificarTara(_req: Request, res: Response): void {
    const tarar = balancaService.verificarTara();
    res.json({ tarar });
  },

  /** POST /balanca/confirmar — Frontend confirma pesagem e abate estoque */
  async confirmar(req: Request, res: Response): Promise<void> {
    const { ingredienteId, quantidadeConsumida } = validateConfirmarPayload(req.body);
    const resultado = await balancaService.confirmarPesagem(ingredienteId, quantidadeConsumida);
    res.json(resultado);
  },
};