# Feature Receita (CMV) — leia de manhã

Implementei a feature de **Receita** no **painel web** enquanto você dormia. O código
já está na sua máquina e no repositório local. Falta só **você rodar o deploy** (eu não
consigo mexer no MySQL nem recompilar daqui).

## Suba em um comando

```bash
cd ~/Custo-Certo.java
bash subir-receita.sh
```

Ele: (1) aplica a migração do banco, (2) recompila o servidor, (3) reinicia.
Vai pedir a senha do MySQL (`CustoCerto2026`).

## Rede de segurança (importante)

- A migração é **aditiva** (nova tabela + nova coluna). O jar antigo continua funcionando
  mesmo com o banco migrado — então aplicar a migração **não quebra** nada.
- Se o `mvn package` **falhar** (eu escrevi o Java às cegas, sem conseguir compilar aqui),
  o **jar atual continua intacto** em `target/`. O script avisa e você volta pra ele:
  ```bash
  cd ~/Custo-Certo.java/servidor-java
  java -jar target/custo-certo-api-1.0.0.jar
  ```
  Aí me manda o erro de compilação que eu corrijo rapidinho.
- Se o **painel** aparecer quebrado, reverta só o front sem perder o resto:
  ```bash
  cd ~/Custo-Certo.java
  git checkout -- public/index.html public/script.js
  ```

## Como usar (painel → aba Balança)

1. Abra `http://localhost:3000`, aba **Balança**. Em cima aparece a barra **Receita**.
2. Digite o nome (ex.: "Cappuccino Especial") → **Iniciar receita**.
3. Selecione o ingrediente, pese, **Confirmar** — ele abate o estoque e soma à receita.
   A lista de ingredientes e o custo acumulado aparecem ao lado. O ✕ estorna uma linha
   (devolve ao estoque).
4. Informe **Porções** e **Preço de venda** (opcional) → **Finalizar**. Mostra o CMV.
5. A aba **Receitas** tem o histórico com CMV %, margem e status.

## O que foi criado/alterado

- `desktop/src/main/resources/sql/03_schema_receitas.sql` — migração (nova).
- `servidor-java/.../modelo/ExecucaoReceita.java`, `ItemReceita.java` — modelos (novos).
- `servidor-java/.../receita/ReceitaDAO.java` — DAO da receita (novo).
- `servidor-java/.../api/App.java` — endpoints de receita + `confirmar` com `execucaoId`.
- `public/index.html`, `public/script.js` — UI da receita + aba Receitas.

Endpoints novos: `POST /receitas/iniciar`, `GET /receitas`, `GET /receitas/{id}`,
`GET /receitas/ativa`, `GET /receitas/nomes`, `POST /receitas/{id}/finalizar`,
`POST /receitas/{id}/cancelar`, `DELETE /receitas/{id}/itens/{movId}`.

> A versão **desktop (JavaFX)** não foi mexida — fazemos juntos quando você quiser.
