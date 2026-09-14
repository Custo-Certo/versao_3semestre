# Custo Certo — Servidor Java (balança + estoque + painel)

Servidor único em **Java 21 + Javalin 6 + JDBC + MySQL**. Substitui o backend
Node/SQLite anterior: recebe o peso do ESP32, abate estoque no **mesmo** banco
`custo_certo` que o app desktop JavaFX usa, e serve o painel web (`../public`).
Tudo na porta **3000** — o firmware e o front não mudam.

## Antes de rodar

1. **MySQL no ar** com os scripts já executados:
   - `desktop/src/main/resources/sql/01_schema_usuarios.sql`
   - `desktop/src/main/resources/sql/02_schema_insumos.sql`
2. **Credenciais:** o servidor lê o **mesmo** `desktop/config.properties` do app
   desktop. Se ainda não existe, crie a partir do exemplo e preencha a senha:
   ```bash
   cp desktop/config.properties.example desktop/config.properties
   # edite desktop/config.properties: db.usuario / db.senha
   ```
   Sem esse arquivo, o servidor usa variáveis de ambiente e, por fim, os padrões
   (`localhost` / `root` / `custo_certo`).

## Rodar

```bash
cd servidor-java

# Desenvolvimento
mvn compile exec:java -Dexec.mainClass=br.com.custocerto.api.App

# Ou jar único
mvn package
java -jar target/custo-certo-api-1.0.0.jar
```

Servidor em http://localhost:3000 (painel) — e é o mesmo endereço do ESP32.

### Sobrescrever credenciais por ambiente (útil na demonstração em rede)

```bash
export DB_HOST=192.168.0.11
export DB_USUARIO=root
export DB_SENHA=suaSenha
# ou aponte direto para o arquivo:
export CONFIG_DESKTOP=/caminho/para/desktop/config.properties
```

## Testar sem a balança

```bash
curl -X POST http://localhost:3000/balanca/peso \
  -H "Content-Type: application/json" -d '{"peso":0.250}'

curl http://localhost:3000/ingredientes            # 8 insumos do MySQL, com lotes
curl http://localhost:3000/ingredientes/historico  # entradas (evolução de preço)
```

## Endpoints

| Método | Rota | Quem chama |
|---|---|---|
| POST | `/balanca/peso` | ESP32 (envia peso, recebe `tarar`) |
| GET  | `/balanca/tara` | ESP32 (compat.; firmware atual não usa) |
| GET  | `/balanca/stream` | Painel (SSE em tempo real) |
| GET  | `/balanca/peso` | Painel (polling legado) |
| POST | `/balanca/tara` | Painel (pede tara) |
| POST | `/balanca/confirmar` | Painel (confirma pesagem → abate estoque) |
| GET  | `/ingredientes` | Painel (lista + lotes) |
| GET  | `/ingredientes/historico` | Painel (evolução de preços) |
| POST | `/ingredientes` | Painel (cadastro) |
| PUT  | `/ingredientes/{id}` | atualização parcial (não usado hoje pelo front) |
| DELETE | `/ingredientes/{id}` | Painel (remover) |
| POST | `/ingredientes/{id}/compras` | Painel (nova compra) |
| POST | `/ingredientes/{id}/retirada` | Painel (retirada manual) |

> **Atenção:** não rode o Node (`npm run dev`) e o Java ao mesmo tempo — ambos
> usam a porta 3000. O servidor da balança agora é o Java.
