/**
 * Agregador de rotas da API.
 *
 * Centraliza o registro para que app.ts importe um único Router.
 */

import { Router } from 'express';
import ingredientesRoutes from './ingredientesRoutes.js';
import balancaRoutes from './balancaRoutes.js';

const router = Router();

router.use('/ingredientes', ingredientesRoutes);
router.use('/balanca', balancaRoutes);

// Health check — útil para o Render verificar que o serviço está vivo
router.get('/health', (_req, res) => {
  res.json({
    status: 'ok',
    timestamp: new Date().toISOString(),
    uptime: process.uptime(),
  });
});

export default router;