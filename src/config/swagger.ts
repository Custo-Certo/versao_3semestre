/**
 * Documentação OpenAPI 3.0 da API do Custo Certo.
 *
 * Spec escrita à mão (sem swagger-jsdoc) para evitar problemas de glob/ESM.
 * Servida via swagger-ui-express em GET /api-docs e o JSON cru em /api-docs.json.
 *
 * As rotas são documentadas na raiz (ex: /ingredientes), que é como o
 * frontend e o ESP32 as consomem. O prefixo /api também funciona (o app
 * monta o mesmo router nos dois caminhos).
 */

import type { Express } from 'express';
import swaggerUi from 'swagger-ui-express';

export const swaggerSpec = {
  openapi: '3.0.3',
  info: {
    title: 'Custo Certo — API',
    version: '1.0.0',
    description:
      'API de controle de estoque e custos. ' +
      'Integra uma balança física (ESP32 + célula de carga HX711) para abater ' +
      'estoque por pesagem em tempo real.',
    contact: { name: 'Lucas T. Strinta' },
    license: { name: 'ISC' },
  },
  servers: [
    { url: 'https://custo-certo.onrender.com', description: 'Produção (Render)' },
    { url: 'http://localhost:3000', description: 'Desenvolvimento local' },
  ],
  tags: [
    { name: 'Ingredientes', description: 'CRUD e movimentações de estoque' },
    { name: 'Balança', description: 'Integração com a balança física (ESP32) e pesagem' },
    { name: 'Sistema', description: 'Health check' },
  ],
  components: {
    schemas: {
      Ingrediente: {
        type: 'object',
        properties: {
          id: { type: 'integer', example: 1 },
          nome: { type: 'string', example: 'Café em grãos' },
          unidade: { type: 'string', enum: ['kg', 'g', 'L', 'ml', 'un'], example: 'kg' },
          preco: { type: 'number', format: 'float', example: 42.9 },
          qtd: { type: 'number', format: 'float', example: 3.5 },
          qtdMax: { type: 'number', format: 'float', example: 10 },
          validade: {
            type: 'string',
            format: 'date',
            nullable: true,
            example: '2026-12-31',
          },
          criadoEm: { type: 'string', example: '2026-05-01 14:30:00' },
          atualizadoEm: { type: 'string', example: '2026-05-20 09:15:00' },
        },
      },
      IngredienteInput: {
        type: 'object',
        required: ['nome', 'unidade', 'preco', 'qtd'],
        properties: {
          nome: { type: 'string', maxLength: 150, example: 'Café em grãos' },
          unidade: { type: 'string', enum: ['kg', 'g', 'L', 'ml', 'un'], example: 'kg' },
          preco: { type: 'number', minimum: 0, example: 42.9 },
          qtd: { type: 'number', minimum: 0, example: 5 },
          qtdMax: {
            type: 'number',
            minimum: 0,
            nullable: true,
            description: 'Pico de estoque (100% da barra). Se omitido, assume = qtd.',
            example: 10,
          },
          validade: {
            type: 'string',
            format: 'date',
            nullable: true,
            example: '2026-12-31',
          },
        },
      },
      IngredienteUpdateInput: {
        type: 'object',
        description:
          'Atualização parcial: envie apenas os campos a alterar (ao menos um). ' +
          'O campo "qtd" NÃO é aceito aqui — estoque só muda via compra ou retirada. ' +
          'Enviar "validade" como null ou "" limpa a data.',
        properties: {
          nome: { type: 'string', maxLength: 150, example: 'Café em grãos premium' },
          unidade: { type: 'string', enum: ['kg', 'g', 'L', 'ml', 'un'], example: 'kg' },
          preco: { type: 'number', minimum: 0, example: 45.5 },
          qtdMax: { type: 'number', minimum: 0, example: 12 },
          validade: {
            type: 'string',
            format: 'date',
            nullable: true,
            example: '2027-01-15',
          },
        },
      },
      CompraInput: {
        type: 'object',
        required: ['quantidade', 'precoUnitario'],
        properties: {
          quantidade: { type: 'number', minimum: 0, example: 2 },
          precoUnitario: { type: 'number', minimum: 0, example: 41.0 },
          validade: {
            type: 'string',
            format: 'date',
            nullable: true,
            example: '2026-11-30',
          },
          observacao: { type: 'string', example: 'Compra fornecedor X' },
        },
      },
      RetiradaInput: {
        type: 'object',
        required: ['quantidade'],
        properties: {
          quantidade: { type: 'number', minimum: 0, example: 0.5 },
        },
      },
      PesoInput: {
        type: 'object',
        required: ['peso'],
        properties: {
          peso: {
            type: 'number',
            description: 'Peso lido pela balança (kg). Intervalo aceito: -100 a 1000.',
            example: 0.235,
          },
        },
      },
      ConfirmarInput: {
        type: 'object',
        required: ['ingredienteId', 'quantidadeConsumida'],
        properties: {
          ingredienteId: { type: 'integer', example: 1 },
          quantidadeConsumida: {
            type: 'number',
            minimum: 0,
            description: 'Quantidade já convertida para a unidade do ingrediente.',
            example: 0.235,
          },
        },
      },
      Erro: {
        type: 'object',
        properties: {
          erro: { type: 'string', example: 'Ingrediente não encontrado' },
          tipo: { type: 'string', example: 'NotFoundError' },
        },
      },
    },
    responses: {
      BadRequest: {
        description: 'Payload ou parâmetro inválido',
        content: { 'application/json': { schema: { $ref: '#/components/schemas/Erro' } } },
      },
      NotFound: {
        description: 'Recurso não encontrado',
        content: { 'application/json': { schema: { $ref: '#/components/schemas/Erro' } } },
      },
      Conflict: {
        description: 'Conflito de estado (ex: estoque insuficiente)',
        content: { 'application/json': { schema: { $ref: '#/components/schemas/Erro' } } },
      },
    },
    parameters: {
      IngredienteId: {
        name: 'id',
        in: 'path',
        required: true,
        description: 'ID do ingrediente',
        schema: { type: 'integer', minimum: 1 },
      },
    },
  },
  paths: {
    '/ingredientes': {
      get: {
        tags: ['Ingredientes'],
        summary: 'Lista todos os ingredientes',
        responses: {
          200: {
            description: 'Lista de ingredientes (com lotes de entrada)',
            content: {
              'application/json': {
                schema: { type: 'array', items: { $ref: '#/components/schemas/Ingrediente' } },
              },
            },
          },
        },
      },
      post: {
        tags: ['Ingredientes'],
        summary: 'Cadastra um novo ingrediente',
        requestBody: {
          required: true,
          content: {
            'application/json': { schema: { $ref: '#/components/schemas/IngredienteInput' } },
          },
        },
        responses: {
          201: {
            description: 'Ingrediente criado',
            content: { 'application/json': { schema: { $ref: '#/components/schemas/Ingrediente' } } },
          },
          400: { $ref: '#/components/responses/BadRequest' },
        },
      },
    },
    '/ingredientes/historico': {
      get: {
        tags: ['Ingredientes'],
        summary: 'Histórico de entradas (compras) de todos os ingredientes',
        responses: {
          200: {
            description: 'Lista de movimentações de entrada',
            content: {
              'application/json': {
                schema: {
                  type: 'array',
                  items: {
                    type: 'object',
                    properties: {
                      id: { type: 'integer' },
                      produtoId: { type: 'integer' },
                      nome: { type: 'string' },
                      unidade: { type: 'string' },
                      qtd: { type: 'number' },
                      preco: { type: 'number' },
                      data: { type: 'string', format: 'date' },
                    },
                  },
                },
              },
            },
          },
        },
      },
    },
    '/ingredientes/{id}': {
      get: {
        tags: ['Ingredientes'],
        summary: 'Busca um ingrediente por ID',
        parameters: [{ $ref: '#/components/parameters/IngredienteId' }],
        responses: {
          200: {
            description: 'Ingrediente encontrado',
            content: { 'application/json': { schema: { $ref: '#/components/schemas/Ingrediente' } } },
          },
          400: { $ref: '#/components/responses/BadRequest' },
          404: { $ref: '#/components/responses/NotFound' },
        },
      },
      put: {
        tags: ['Ingredientes'],
        summary: 'Atualiza parcialmente um ingrediente',
        description:
          'Atualiza apenas os campos enviados. NÃO altera `qtd` (estoque só muda via ' +
          'compra ou retirada). Enviar `qtd` no corpo retorna 400.',
        parameters: [{ $ref: '#/components/parameters/IngredienteId' }],
        requestBody: {
          required: true,
          content: {
            'application/json': { schema: { $ref: '#/components/schemas/IngredienteUpdateInput' } },
          },
        },
        responses: {
          200: {
            description: 'Ingrediente atualizado',
            content: { 'application/json': { schema: { $ref: '#/components/schemas/Ingrediente' } } },
          },
          400: { $ref: '#/components/responses/BadRequest' },
          404: { $ref: '#/components/responses/NotFound' },
        },
      },
      delete: {
        tags: ['Ingredientes'],
        summary: 'Remove um ingrediente',
        description: 'Remove o ingrediente e, em cascata, suas movimentações de estoque.',
        parameters: [{ $ref: '#/components/parameters/IngredienteId' }],
        responses: {
          204: { description: 'Removido com sucesso (sem corpo)' },
          400: { $ref: '#/components/responses/BadRequest' },
          404: { $ref: '#/components/responses/NotFound' },
        },
      },
    },
    '/ingredientes/{id}/compras': {
      post: {
        tags: ['Ingredientes'],
        summary: 'Registra uma compra (entrada de estoque)',
        description: 'Aumenta a quantidade, atualiza preço/qtd_max e grava no histórico.',
        parameters: [{ $ref: '#/components/parameters/IngredienteId' }],
        requestBody: {
          required: true,
          content: { 'application/json': { schema: { $ref: '#/components/schemas/CompraInput' } } },
        },
        responses: {
          200: {
            description: 'Ingrediente atualizado após a compra',
            content: { 'application/json': { schema: { $ref: '#/components/schemas/Ingrediente' } } },
          },
          400: { $ref: '#/components/responses/BadRequest' },
          404: { $ref: '#/components/responses/NotFound' },
        },
      },
    },
    '/ingredientes/{id}/retirada': {
      post: {
        tags: ['Ingredientes'],
        summary: 'Retirada manual de estoque (saída)',
        parameters: [{ $ref: '#/components/parameters/IngredienteId' }],
        requestBody: {
          required: true,
          content: { 'application/json': { schema: { $ref: '#/components/schemas/RetiradaInput' } } },
        },
        responses: {
          200: {
            description: 'Ingrediente atualizado após a retirada',
            content: { 'application/json': { schema: { $ref: '#/components/schemas/Ingrediente' } } },
          },
          400: { $ref: '#/components/responses/BadRequest' },
          404: { $ref: '#/components/responses/NotFound' },
          409: { $ref: '#/components/responses/Conflict' },
        },
      },
    },
    '/balanca/peso': {
      get: {
        tags: ['Balança'],
        summary: 'Lê o peso atual (polling — legado)',
        description: 'Mantido por compatibilidade. Prefira o stream SSE em /balanca/stream.',
        responses: {
          200: {
            description: 'Estado atual da balança',
            content: {
              'application/json': {
                schema: {
                  type: 'object',
                  properties: {
                    peso: { type: 'number', example: 0.235 },
                    online: { type: 'boolean', example: true },
                    ultimaAtualizacao: { type: 'integer', example: 1717500000000 },
                  },
                },
              },
            },
          },
        },
      },
      post: {
        tags: ['Balança'],
        summary: 'ESP32 envia uma leitura de peso',
        requestBody: {
          required: true,
          content: { 'application/json': { schema: { $ref: '#/components/schemas/PesoInput' } } },
        },
        responses: {
          200: {
            description: 'Leitura registrada',
            content: {
              'application/json': {
                schema: { type: 'object', properties: { ok: { type: 'boolean', example: true } } },
              },
            },
          },
          400: { $ref: '#/components/responses/BadRequest' },
        },
      },
    },
    '/balanca/stream': {
      get: {
        tags: ['Balança'],
        summary: 'Stream de peso em tempo real (Server-Sent Events)',
        description:
          'Conexão SSE (text/event-stream). Cada nova leitura do ESP32 é enviada como ' +
          '`data: {"peso":..,"online":..,"ultimaAtualizacao":..}`. O navegador reconecta ' +
          'automaticamente via EventSource.',
        responses: {
          200: {
            description: 'Fluxo de eventos SSE',
            content: { 'text/event-stream': { schema: { type: 'string' } } },
          },
        },
      },
    },
    '/balanca/tara': {
      get: {
        tags: ['Balança'],
        summary: 'ESP32 verifica se há pedido de tara (e zera a flag)',
        responses: {
          200: {
            description: 'Flag de tara',
            content: {
              'application/json': {
                schema: { type: 'object', properties: { tarar: { type: 'boolean', example: false } } },
              },
            },
          },
        },
      },
      post: {
        tags: ['Balança'],
        summary: 'Frontend solicita tara (zerar a balança)',
        responses: {
          200: {
            description: 'Tara solicitada',
            content: {
              'application/json': {
                schema: { type: 'object', properties: { ok: { type: 'boolean', example: true } } },
              },
            },
          },
        },
      },
    },
    '/balanca/confirmar': {
      post: {
        tags: ['Balança'],
        summary: 'Confirma a pesagem e abate o estoque',
        description: 'Recebe qual ingrediente e quanto abater; registra a saída no histórico.',
        requestBody: {
          required: true,
          content: { 'application/json': { schema: { $ref: '#/components/schemas/ConfirmarInput' } } },
        },
        responses: {
          200: {
            description: 'Pesagem confirmada e estoque abatido',
            content: {
              'application/json': {
                schema: {
                  type: 'object',
                  properties: {
                    ok: { type: 'boolean', example: true },
                    pesoConfirmado: { type: 'number', example: 0.235 },
                    ingredienteId: { type: 'integer', example: 1 },
                    novaQtd: { type: 'number', example: 3.265 },
                  },
                },
              },
            },
          },
          400: { $ref: '#/components/responses/BadRequest' },
          404: { $ref: '#/components/responses/NotFound' },
          409: { $ref: '#/components/responses/Conflict' },
        },
      },
    },
    '/health': {
      get: {
        tags: ['Sistema'],
        summary: 'Health check (usado pelo Render)',
        responses: {
          200: {
            description: 'Serviço no ar',
            content: {
              'application/json': {
                schema: {
                  type: 'object',
                  properties: {
                    status: { type: 'string', example: 'ok' },
                    timestamp: { type: 'string', format: 'date-time' },
                    uptime: { type: 'number', example: 1234.5 },
                  },
                },
              },
            },
          },
        },
      },
    },
  },
} as const;

/**
 * Monta o Swagger UI no app Express.
 * Deve ser chamado ANTES dos mounts de rota e do fallback SPA.
 */
export function mountSwagger(app: Express): void {
  app.use(
    '/api-docs',
    swaggerUi.serve,
    swaggerUi.setup(swaggerSpec as Record<string, unknown>, {
      customSiteTitle: 'Custo Certo — API Docs',
    }),
  );

  // JSON cru da spec (útil para importar no Postman/Insomnia)
  app.get('/api-docs.json', (_req, res) => {
    res.json(swaggerSpec);
  });
}