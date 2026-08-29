<div align="center">

<img src="./.github/banner.svg" alt="Custo Certo" width="320"/>

### Gestão inteligente de estoque e custos

*Plataforma full-stack que integra uma balança inteligente (ESP32 + HX711) a um sistema de controle de insumos, movimentações e custo de mercadoria vendida (CMV) em tempo real.*

<br/>

[![TypeScript](https://img.shields.io/badge/TypeScript-3178C6?style=flat-square&logo=typescript&logoColor=white)](https://www.typescriptlang.org/)
[![HTML5](https://img.shields.io/badge/HTML5-E34F26?style=flat-square&logo=html5&logoColor=white)](https://developer.mozilla.org/docs/Web/HTML)
[![Node.js](https://img.shields.io/badge/Node.js-%3E%3D20-339933?style=flat-square&logo=node.js&logoColor=white)](https://nodejs.org/)
[![Express](https://img.shields.io/badge/Express-5.x-000000?style=flat-square&logo=express&logoColor=white)](https://expressjs.com/)
[![SQLite](https://img.shields.io/badge/SQLite-libSQL-003B57?style=flat-square&logo=sqlite&logoColor=white)](https://turso.tech/)
[![Turso](https://img.shields.io/badge/Turso-4FF8D2?style=flat-square&logo=turso&logoColor=black)](https://turso.tech/)
[![ESP32](https://img.shields.io/badge/ESP32-E7352C?style=flat-square&logo=espressif&logoColor=white)](https://www.espressif.com/)
[![PlatformIO](https://img.shields.io/badge/PlatformIO-F5822A?style=flat-square&logo=platformio&logoColor=white)](https://platformio.org/)
[![Render](https://img.shields.io/badge/Deploy-Render-46E3B7?style=flat-square&logo=render&logoColor=white)](https://render.com/)
[![License](https://img.shields.io/badge/License-ISC-blue?style=flat-square)](#-licença)

<br/>

[**Sobre**](#-sobre-o-projeto) ·
[**Funcionalidades**](#-funcionalidades) ·
[**Arquitetura**](#-arquitetura) ·
[**Instalação**](#-começando) ·
[**API**](#-api) ·
[**Deploy**](#-deploy) ·
[**Equipe**](#-equipe)

</div>

---

## 📖 Sobre o Projeto

Trabalho desenvolvido dentro do Projeto Integrador do curso de Análise e Desenvolvimento de Sistemas da **FATEC Indaiatuba**. O sistema **Custo Certo** nasceu de um problema real de uma cafeteria: a dificuldade de controlar o consumo de insumos e calcular com precisão o **CMV (Custo de Mercadoria Vendida)**.

A solução combina **hardware e software** em um fluxo único:

> Um operador coloca o produto sobre a **balança inteligente** → o **ESP32** envia o peso em tempo real para o servidor → o sistema dá baixa automática no estoque, atualiza o histórico de movimentações e recalcula os custos no painel web.

<br/>

<div align="center">

| 🎯 Problema | 💡 Solução |
|:---|:---|
| Controle manual de estoque sujeito a erros | Baixa automática via balança conectada |
| Cálculo de CMV impreciso | Custo recalculado a cada movimentação |
| Falta de histórico de preços de compra | Registro de cada entrada com preço pago |
| Insumos vencendo sem aviso | Alertas de validade com lógica FIFO |

</div>

---

## ✨ Funcionalidades

- 📦 **CRUD completo de insumos** — cadastro, edição, consulta e remoção de ingredientes com unidade, preço, quantidade e validade.
- ⚖️ **Pesagem em tempo real** — leitura contínua da balança via **Server-Sent Events (SSE)**, com push para o navegador.
- 🔄 **Movimentações de estoque** — registro de entradas (compras) e saídas (consumo), cada uma com data e preço unitário.
- 📊 **Dashboards interativos** — gráficos de composição de estoque, status, CMV e evolução de preços (Chart.js).
- 🗓️ **Alertas de validade (FIFO)** — destaque de lotes próximos do vencimento e baixa pelo lote mais antigo.
- 🎯 **Tara remota** — comando de tara enviado do painel diretamente para a balança física.
- ☁️ **Banco híbrido local ↔ nuvem** — mesma base de código roda em SQLite local ou Turso (cloud) trocando apenas uma variável de ambiente.

---

## 🏗️ Arquitetura

```
┌──────────────────┐         HTTP POST /balanca/peso         ┌─────────────────────────┐
│                  │  ────────────────────────────────────►  │                         │
│   ESP32 + HX711  │                                          │   Servidor Node.js      │
│  (Balança IoT)   │  ◄────────────────────────────────────  │   Express + TypeScript  │
│                  │         GET /balanca/tara (poll)         │                         │
└──────────────────┘                                          │  ┌───────────────────┐  │
                                                              │  │  Controllers      │  │
┌──────────────────┐          SSE /balanca/stream            │  │  Services         │  │
│                  │  ◄────────────────────────────────────  │  │  Repositories     │  │
│  Frontend Web    │                                          │  └─────────┬─────────┘  │
│  (HTML/CSS/JS)   │          REST /api/ingredientes          │            │            │
│  + Chart.js      │  ◄────────────────────────────────────► │            ▼            │
│                  │                                          │     @libsql/client      │
└──────────────────┘                                          └────────────┬────────────┘
                                                                            │
                                                              ┌─────────────┴─────────────┐
                                                              │  SQLite (file:)  /  Turso  │
                                                              │       (libsql://)          │
                                                              └────────────────────────────┘
```

A camada de backend segue uma **arquitetura em camadas** com responsabilidades bem separadas:

```
Rotas → Controllers → Services → Repositories → Banco de Dados
```

---

## 🛠️ Tecnologias

<div align="center">

| Camada | Tecnologias |
|:---|:---|
| **Backend** | Node.js · TypeScript · Express 5 · Zod |
| **Banco de Dados** | SQLite (local) · Turso (cloud) |
| **Frontend** | HTML5 · CSS3 · JavaScript (vanilla) · Chart.js |
| **Hardware** | ESP32 · Célula de carga · HX711 · Arduino Framework |
| **IoT / Build** | PlatformIO |
| **Docs / Testes** | Swagger (OpenAPI) · Cypress (E2E) |
| **Deploy** | Render (servidor) · Turso (banco em nuvem) |

</div>

---

## 📂 Estrutura do Projeto

```
Project_Custo_Certo/
├── src/                          # Backend (TypeScript)
│   ├── app.ts                    # Configuração do Express (sem listen)
│   ├── server.ts                 # Bootstrap: schema → seed → listen
│   ├── config/
│   │   ├── database.ts           # Singleton @libsql/client
│   │   └── swagger.ts            # Documentação OpenAPI
│   ├── database/
│   │   ├── schema.sql            # Schema idempotente (CREATE IF NOT EXISTS)
│   │   └── seed.ts               # Dados de demonstração
│   ├── controllers/              # Tratamento de requisições HTTP
│   ├── services/                 # Regras de negócio
│   ├── repositories/             # Acesso ao banco
│   ├── routes/                   # Definição das rotas
│   ├── schemas/                  # Validação (Zod)
│   ├── models/                   # Tipagens / interfaces
│   ├── errors/                   # AppError customizado
│   └── middlewares/              # Tratamento global de erros
│
├── public/                       # Frontend
│   ├── index.html
│   ├── script.js                 # Consumo da API via fetch + SSE
│   └── style.css
│
├── firmware/                     # Código do ESP32
│   ├── platformio.ini            # Ambientes dev / prod
│   ├── secrets.ini.example       # Modelo de credenciais
│   └── src/main.cpp              # Leitura HX711 + envio HTTP
│
├── cypress/                      # Testes end-to-end
│
├── .env.example                  # Modelo de variáveis de ambiente
├── render.yaml                   # Infraestrutura como código (Render)
├── tsconfig.json
└── package.json
```

---

## 🚀 Começando

### Pré-requisitos

- [Node.js](https://nodejs.org/) **>= 20**
- [npm](https://www.npmjs.com/) (vem com o Node)
- *(Opcional, para o hardware)* [PlatformIO](https://platformio.org/) no VS Code

### Instalação

```bash
# 1. Clone o repositório
git clone https://github.com/Custo-Certo/Project_Custo_Certo.git
cd Project_Custo_Certo

# 2. Instale as dependências
npm install

# 3. Crie o arquivo de ambiente a partir do modelo
cp .env.example .env

# 4. Rode em modo de desenvolvimento (hot reload)
npm run dev
```

O servidor sobe em **http://localhost:3000** e o frontend já fica disponível na raiz.

### Scripts disponíveis

| Comando | Descrição |
|:---|:---|
| `npm run dev` | Inicia o servidor com hot reload (`tsx watch`) |
| `npm run build` | Compila o TypeScript e copia o schema para `dist/` |
| `npm start` | Roda a versão compilada (produção) |
| `npm run seed` | Popula o banco com dados de demonstração |
| `npm run cy:open` | Abre o Cypress (modo interativo) |
| `npm test` | Executa os testes E2E |

---

## ⚙️ Variáveis de Ambiente

Crie um arquivo `.env` baseado em `.env.example`:

```env
NODE_ENV=development
PORT=3000
AUTO_SEED=true

# --- Banco de dados ---
# Local (SQLite em arquivo):
DB_URL=file:./data/local.db
DB_AUTH_TOKEN=

# Turso (cloud) — alternativa para produção:
# DB_URL=libsql://SEU-BANCO.turso.io
# DB_AUTH_TOKEN=token-gerado-pelo-turso-cli

# Domínios autorizados (CORS) em produção, separados por vírgula
ALLOWED_ORIGINS=
```

> 💡 **Destaque arquitetural:** trocar entre SQLite local e Turso na nuvem exige **apenas alterar a `DB_URL`** — nenhuma linha de código de aplicação precisa mudar.

---

## 🔌 API

Documentação interativa disponível em **`/api-docs`** (Swagger UI) com o servidor em execução.

### Ingredientes

| Método | Rota | Descrição |
|:---:|:---|:---|
| `GET` | `/ingredientes` | Lista todos os insumos |
| `GET` | `/ingredientes/historico` | Histórico de movimentações |
| `GET` | `/ingredientes/:id` | Busca um insumo |
| `POST` | `/ingredientes` | Cadastra um insumo |
| `PUT` | `/ingredientes/:id` | Atualiza dados (não altera quantidade) |
| `DELETE` | `/ingredientes/:id` | Remove um insumo |
| `POST` | `/ingredientes/:id/compras` | Registra entrada (compra) |
| `POST` | `/ingredientes/:id/retirada` | Registra saída (consumo) |

### Balança

| Método | Rota | Origem | Descrição |
|:---:|:---|:---|:---|
| `POST` | `/balanca/peso` | ESP32 | Recebe leitura de peso |
| `GET` | `/balanca/tara` | ESP32 | Verifica se há tara pendente |
| `GET` | `/balanca/stream` | Frontend | Push de peso em tempo real (SSE) |
| `POST` | `/balanca/tara` | Frontend | Solicita tara da balança |
| `POST` | `/balanca/confirmar` | Frontend | Confirma a pesagem atual |

### Saúde

| Método | Rota | Descrição |
|:---:|:---|:---|
| `GET` | `/health` | Status do serviço (usado pelo Render) |

---

## 🗄️ Modelo de Dados

```sql
ingredientes
├── id, nome, unidade (kg|g|L|ml|un)
├── preco, qtd, qtd_max, validade
└── criado_em, atualizado_em

movimentacoes_estoque
├── id, ingrediente_id (FK)
├── tipo (entrada|saida), quantidade
├── preco_unitario, observacao, data
└── criado_em

receitas  +  receita_ingredientes
└── relacionamento N:N para cálculo de CMV por produto
```

---

## 🤖 Hardware (ESP32)

O firmware fica em `firmware/` e usa **PlatformIO**. As credenciais ficam isoladas em `secrets.ini` (ignorado pelo Git).

```bash
# 1. Configure as credenciais
cd firmware
cp secrets.ini.example secrets.ini
# edite: wifi_ssid, wifi_pass, server_url_dev, server_url_prod

# 2. Compile e grave (ambiente de desenvolvimento — IP local)
pio run -e dev -t upload

# 3. Ou para produção (aponta para o Render)
pio run -e prod -t upload
```

**Ligações da balança:**

| HX711 | ESP32 |
|:---:|:---:|
| `DT` | GPIO **32** |
| `SCK` | GPIO **33** |

> O fator de calibração (`SCALE_FACTOR`) deve ser ajustado com um peso conhecido.

---

## ☁️ Deploy

O projeto está preparado para deploy em **Render + Turso** via *Infrastructure as Code* (`render.yaml`).

```bash
# 1. Crie o banco na nuvem (Turso)
turso db create custo-certo
turso db show custo-certo --url        # copie a DB_URL
turso db tokens create custo-certo     # copie o DB_AUTH_TOKEN

# 2. No painel do Render, conecte o repositório.
#    O render.yaml configura build, start e healthcheck automaticamente.

# 3. Adicione as variáveis secretas no painel do Render:
#    DB_URL, DB_AUTH_TOKEN, ALLOWED_ORIGINS

# 4. Aponte o firmware para a URL de produção e regrave o ESP32.
```

---

## 🗺️ Roadmap

- [x] Estrutura do projeto e `package.json`
- [x] Banco unificado SQLite / Turso (`@libsql/client`)
- [x] Migração do firmware para PlatformIO
- [x] Integração frontend ↔ backend (CRUD via API)
- [x] Artefatos de deploy (Render + Turso)
- [ ] Refatoração para **WebSockets** (substituir polling do ESP32)
- [ ] CRUD completo de **Receitas** com cálculo de CMV
- [ ] Acesso mobile
- [ ] 'And loading'...

---

## 🧪 Testes

Os testes end-to-end são escritos em **Cypress** e cobrem fluxos como cadastro de ingredientes, cálculo da balança, cadastro em massa e remoção de itens vencidos.

```bash
npm run cy:open   # modo interativo
npm test          # modo headless (CI)
```

---

## 👥 Equipe

Projeto desenvolvido por estudantes da **FATEC Indaiatuba**:

<div align="center">

| Integrantes |
|:---|
| Gabriel Marchezoni |
| Gustavo Kauhan |
| Lucas Strinta |
| Vinicius Sterza |
| Warley Alves |

</div>

---

## 📄 Licença

Distribuído sob a licença **ISC**. Veja o arquivo de licença para mais detalhes.

---

<div align="center">

**Feito com ☕ e dedicação na FATEC Indaiatuba**

⭐ Se este projeto te ajudou, considere deixar uma estrela!

</div>