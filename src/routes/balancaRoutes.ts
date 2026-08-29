import { Router } from 'express';
import { balancaController } from '../controllers/balancaController.js';

const router = Router();

// ESP32 → servidor
router.post('/peso', balancaController.receberPeso);
router.get('/tara',  balancaController.verificarTara);

// Frontend → servidor
router.get('/stream',   balancaController.stream);      // SSE — push de peso em tempo real
router.get('/peso',     balancaController.lerPeso);     // REST legado (compatibilidade)
router.post('/tara',    balancaController.solicitarTara);
router.post('/confirmar', balancaController.confirmar);

export default router;