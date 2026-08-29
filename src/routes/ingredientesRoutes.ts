import { Router } from 'express';
import { ingredientesController } from '../controllers/ingredientesController.js';

const router = Router();

// Ordem importa: rotas estáticas ANTES de :id
router.get('/historico', ingredientesController.historico);

router.get('/', ingredientesController.listar);
router.post('/', ingredientesController.cadastrar);

router.get('/:id', ingredientesController.buscar);
router.put('/:id', ingredientesController.atualizar);     // atualização parcial (não altera qtd)
router.delete('/:id', ingredientesController.deletar);

router.post('/:id/compras', ingredientesController.registrarCompra);
router.post('/:id/retirada', ingredientesController.retirar);

export default router;