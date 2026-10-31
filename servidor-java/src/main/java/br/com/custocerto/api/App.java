package br.com.custocerto.api;

import br.com.custocerto.api.balanca.EstadoBalanca;
import br.com.custocerto.api.estoque.IngredienteDAO;
import br.com.custocerto.api.receita.ReceitaDAO;
import br.com.custocerto.api.modelo.ErroApp;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;

import java.util.Map;

/**
 * Servidor unico do Custo Certo: balanca (ESP32 + painel), estoque (MySQL) e o
 * proprio painel web (public/). As rotas tem exatamente os mesmos caminhos que
 * o public/script.js ja chama, entao o front funciona sem alteracao. Substitui
 * o backend Node/SQLite anterior na porta 3000.
 */
public class App {

    // ---- Corpos JSON recebidos (Jackson desserializa records nativamente) ----
    record PesoPayload(double peso) {}
    record ConfirmarPayload(int ingredienteId, double quantidadeConsumida, Integer execucaoId) {}
    record NovoIngrediente(String nome, String unidade, double preco,
                           double qtd, Double qtdMax, String validade) {}
    record CompraPayload(double quantidade, double precoUnitario,
                         String validade, String observacao) {}
    record RetiradaPayload(double quantidade) {}
    record AtualizarPayload(String nome, String unidade, Double preco,
                            Double qtdMax, String validade) {}
    record IniciarReceitaPayload(String nome) {}
    record FinalizarReceitaPayload(Integer porcoes, Double precoVenda) {}

    public static void main(String[] args) {
        Javalin app = Javalin.create(cfg -> {
            // Serve o front existente (pasta public/ da raiz do repo).
            cfg.staticFiles.add(s -> {
                s.directory = "../public";
                s.location = Location.EXTERNAL;
            });
        });

        // ---- Erros -> status HTTP + JSON no formato { "erro": "..." } ----
        app.exception(ErroApp.class, (e, ctx) ->
            ctx.status(e.status).json(Map.of("erro", e.getMessage())));
        app.exception(Exception.class, (e, ctx) -> {
            e.printStackTrace();
            ctx.status(500).json(Map.of("erro", "Erro interno"));
        });

        // ---- Saude ----
        app.get("/health", ctx -> ctx.json(Map.of("status", "ok")));

        // =============================================================
        // BALANCA
        // =============================================================
        // ESP32 -> servidor: envia peso e ja recebe se ha tara pendente.
        app.post("/balanca/peso", ctx -> {
            PesoPayload body = ctx.bodyAsClass(PesoPayload.class);
            EstadoBalanca.registrarLeitura(body.peso());
            ctx.json(Map.of("ok", true, "tarar", EstadoBalanca.consumirTara()));
        });
        // ESP32 -> servidor: consulta tara (compatibilidade; firmware atual nao usa).
        app.get("/balanca/tara", ctx ->
            ctx.json(Map.of("tarar", EstadoBalanca.consumirTara())));

        // Painel -> servidor: stream SSE com o peso em tempo real.
        app.sse("/balanca/stream", client -> {
            client.keepAlive();
            EstadoBalanca.adicionarCliente(client);
            client.onClose(() -> EstadoBalanca.removerCliente(client));
        });
        // Painel -> servidor: polling legado (o front usa SSE, mas fica disponivel).
        app.get("/balanca/peso", ctx -> ctx.json(Map.of(
            "peso", EstadoBalanca.peso(),
            "online", EstadoBalanca.online()
        )));
        // Painel -> servidor: pede tara.
        app.post("/balanca/tara", ctx -> {
            EstadoBalanca.solicitarTara();
            ctx.json(Map.of("ok", true));
        });
        // Painel -> servidor: confirma a pesagem e abate estoque.
        // Se vier execucaoId, a saida e vinculada a uma receita em andamento.
        app.post("/balanca/confirmar", ctx -> {
            ConfirmarPayload c = ctx.bodyAsClass(ConfirmarPayload.class);
            if (EstadoBalanca.peso() <= 0.001)
                throw new ErroApp("Peso invalido. Coloque o item na balanca.", 400);
            if (c.execucaoId() != null) {
                var exec = ReceitaDAO.adicionarItem(
                    c.execucaoId(), c.ingredienteId(), c.quantidadeConsumida(), "Pesagem (receita)");
                ctx.json(Map.of(
                    "ok", true,
                    "pesoConfirmado", EstadoBalanca.peso(),
                    "receita", exec));
            } else {
                var ing = IngredienteDAO.abaterEstoque(
                    c.ingredienteId(), c.quantidadeConsumida(),
                    "Pesagem de " + c.quantidadeConsumida());
                ctx.json(Map.of(
                    "ok", true,
                    "pesoConfirmado", EstadoBalanca.peso(),
                    "ingredienteId", ing.id(),
                    "novaQtd", ing.qtd()));
            }
        });

        // =============================================================
        // INGREDIENTES
        // =============================================================
        // Rota literal ANTES de qualquer {id} para nao ser capturada como id.
        app.get("/ingredientes/historico", ctx -> ctx.json(IngredienteDAO.listarHistorico()));

        app.get("/ingredientes", ctx -> ctx.json(IngredienteDAO.listarTodos()));

        app.post("/ingredientes", ctx -> {
            NovoIngrediente n = ctx.bodyAsClass(NovoIngrediente.class);
            var criado = IngredienteDAO.criar(
                n.nome(), n.unidade(), n.preco(), n.qtd(), n.qtdMax(), n.validade());
            ctx.status(201).json(criado);
        });

        app.put("/ingredientes/{id}", ctx -> {
            int id = Integer.parseInt(ctx.pathParam("id"));
            AtualizarPayload u = ctx.bodyAsClass(AtualizarPayload.class);
            var atualizado = IngredienteDAO.atualizar(
                id, u.nome(), u.unidade(), u.preco(), u.qtdMax(),
                u.validade(), u.validade() != null);
            ctx.json(atualizado);
        });

        app.delete("/ingredientes/{id}", ctx -> {
            int id = Integer.parseInt(ctx.pathParam("id"));
            if (!IngredienteDAO.remover(id))
                throw new ErroApp("Ingrediente nao encontrado", 404);
            ctx.status(204);
        });

        app.post("/ingredientes/{id}/compras", ctx -> {
            int id = Integer.parseInt(ctx.pathParam("id"));
            CompraPayload c = ctx.bodyAsClass(CompraPayload.class);
            if (c.quantidade() <= 0 || c.precoUnitario() <= 0)
                throw new ErroApp("Quantidade e preco devem ser positivos.", 400);
            var atualizado = IngredienteDAO.registrarCompra(
                id, c.quantidade(), c.precoUnitario(), c.validade(), c.observacao());
            ctx.json(atualizado);
        });

        app.post("/ingredientes/{id}/retirada", ctx -> {
            int id = Integer.parseInt(ctx.pathParam("id"));
            RetiradaPayload r = ctx.bodyAsClass(RetiradaPayload.class);
            if (r.quantidade() <= 0)
                throw new ErroApp("Quantidade invalida", 400);
            var atualizado = IngredienteDAO.abaterEstoque(id, r.quantidade(), "Retirada manual");
            ctx.json(atualizado);
        });

        // =============================================================
        // RECEITAS (CMV real a partir da pesagem)
        // =============================================================
        // Rotas literais ANTES de /receitas/{id} para nao serem capturadas como id.
        app.get("/receitas/nomes", ctx -> ctx.json(ReceitaDAO.nomesUsados()));
        app.get("/receitas/ativa", ctx -> {
            var a = ReceitaDAO.ativa();
            ctx.json(a != null ? a : Map.of("ativa", false));
        });
        app.post("/receitas/iniciar", ctx -> {
            var p = ctx.bodyAsClass(IniciarReceitaPayload.class);
            ctx.status(201).json(ReceitaDAO.iniciar(p.nome()));
        });

        app.get("/receitas", ctx -> ctx.json(ReceitaDAO.listar()));
        app.get("/receitas/{id}", ctx -> {
            var r = ReceitaDAO.buscar(Integer.parseInt(ctx.pathParam("id")));
            if (r == null) throw new ErroApp("Receita nao encontrada", 404);
            ctx.json(r);
        });
        app.post("/receitas/{id}/finalizar", ctx -> {
            int id = Integer.parseInt(ctx.pathParam("id"));
            var p = ctx.bodyAsClass(FinalizarReceitaPayload.class);
            ctx.json(ReceitaDAO.finalizar(id, p.porcoes(), p.precoVenda()));
        });
        app.post("/receitas/{id}/cancelar", ctx -> {
            int id = Integer.parseInt(ctx.pathParam("id"));
            ctx.json(ReceitaDAO.cancelar(id));
        });
        app.delete("/receitas/{id}/itens/{movId}", ctx -> {
            int id = Integer.parseInt(ctx.pathParam("id"));
            int movId = Integer.parseInt(ctx.pathParam("movId"));
            ctx.json(ReceitaDAO.estornarItem(id, movId));
        });

        app.start(3000);
        System.out.println("Custo Certo (Java) em http://localhost:3000");
    }
}
