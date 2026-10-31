# Custo Certo — Desktop (JavaFX)

Interface gráfica em Java do Custo Certo, para o **3º semestre de 2026**.
Atende aos requisitos das matérias de **Programação Orientada a Objetos**
(Prof. Sérgio Furgeri) e **Banco de Dados** (Prof. Marlon).

Este é o projeto principal do semestre. O painel web e o firmware da balança
vivem em [`Project_Custo_Certo`](https://github.com/Custo-Certo/Project_Custo_Certo),
e os dois projetos **não dependem um do outro para rodar** — compartilham apenas
o banco MySQL e a balança.

---

## O que o sistema faz

| Módulo | O que entrega |
|---|---|
| **Login e perfis** | senha protegida por PBKDF2, auditoria de tentativas, menu montado pelas permissões |
| **Usuários** | cadastro, edição, redefinição de senha e inativação — só para o administrador |
| **Estoque** | insumos, compras, retiradas, alertas de nível e de validade |
| **Balança** | leitura de peso em tempo real e baixa automática no estoque |
| **Ficha Técnica** | o que a cafeteria vende e quanto cada produto custa |
| **Vendas** | registro do faturamento, sem o qual não existe margem |
| **Margem e Desperdício** | engenharia de cardápio e consumo teórico × real |
| **Evolução de Preços** | variação do custo de aquisição ao longo do tempo |

O indicador central — o **CMV** — é calculado sobre vendas reais e fichas
técnicas. Até o semestre passado ele era exibido como 28,6% fixos, porque a
receita era estimada multiplicando o próprio custo por 3,5: os dois lados da
divisão eram a mesma grandeza e o resultado nunca mudava.

---

## Decisões de arquitetura

| Decisão | Escolha | Por quê |
|---|---|---|
| Acesso a dados (D1) | JDBC direto no MySQL | O requisito da POO pede que o sistema Java manipule o banco e acesse pela rede local. |
| Interface (D7) | JavaFX | Sucessor do Swing, com estilização por CSS. Entra como dependência do Maven, sem SDK para instalar. |
| Build | Maven, via wrapper | Resolve o driver JDBC e o JavaFX sozinho e gera o Javadoc em um comando. |
| Balança | Dispositivo de rede | O ESP32 serve `GET /peso` e `POST /tara`; este app consulta direto, sem depender do servidor Node. |

Detalhes em [`docs/arquitetura.md`](../docs/arquitetura.md).

---

## O que é preciso ter instalado

- **JDK 21 ou superior** — `java -version` para conferir.
- **MySQL 8.x** — veja abaixo.
- Mais nada. O Maven, o JavaFX e o driver JDBC são baixados automaticamente.

---

## MySQL: já instalado nesta máquina

Foi instalado um **MySQL 8.4.0 portátil**, em `C:/Users/gabic/MySQL`, sem
precisar de privilégio de administrador. Ele não roda como serviço do Windows:
é iniciado por um atalho e para quando a janela é fechada.

| Ação | Arquivo |
|---|---|
| Ligar o banco | `C:/Users/gabic/MySQL/iniciar-mysql.bat` |
| Parar o banco | `C:/Users/gabic/MySQL/parar-mysql.bat` |
| Abrir o console SQL | `C:/Users/gabic/MySQL/console-mysql.bat` |

Contas do MySQL:

- `root` — sem senha, só de `localhost`, para rodar os scripts
- `custocerto` / `custocerto2026` — de qualquer host, com permissão apenas no
  banco `custo_certo`. É a conta que a aplicação usa e a que funciona pela rede.

> **Antes de qualquer coisa, ligue o banco.** Se o `iniciar-mysql.bat` não
> estiver rodando, a aplicação abre mas o login não funciona.

---

## Como rodar, passo a passo

### 1. Ligar o banco

Duplo clique em `iniciar-mysql.bat` e **deixe a janela aberta**.

### 2. Criar o banco (só na primeira vez, ou em outra máquina)

Os três scripts precisam rodar **nesta ordem** — o segundo referencia o primeiro,
e o terceiro referencia o segundo:

```bash
mysql -u root -p < src/main/resources/sql/01_schema_usuarios.sql
mysql -u root -p < src/main/resources/sql/02_schema_insumos.sql
mysql -u root -p < src/main/resources/sql/03_schema_receitas_vendas.sql
```

Eles criam as sete tabelas e carregam os dados iniciais. Podem ser executados
mais de uma vez sem duplicar nada — usam `INSERT IGNORE` com `id` explícito.

### 3. Apontar para o servidor certo

Nesta máquina o `config.properties` **já está criado e funcionando**. Em outra
máquina, copie o exemplo e preencha:

```bash
cp config.properties.example config.properties     # Git Bash
copy config.properties.example config.properties   # Prompt / PowerShell
```

- Desenvolvendo sozinho: `db.host=localhost`
- Demonstrando o acesso via rede local: `db.host=` o IP da máquina que estiver
  servindo o banco (confirme com `ipconfig`, porque o IP muda de rede para rede)
- `balanca.url=` o endereço da balança. O padrão usa mDNS
  (`http://custocerto-balanca.local`); se a rede não repassar mDNS, use o IP que
  aparece no monitor serial do ESP32.

O `config.properties` está no `.gitignore` — a sua senha não vai para o repositório.

### 4. Conferir o ambiente antes de apresentar

```bash
mvnw.cmd exec:java -Dexec.mainClass=br.com.custocerto.util.DiagnosticoAmbiente
```

Testa a configuração, a conexão, cinco cenários de login, os indicadores
financeiros do período, a variância por insumo e o rollback de uma transação
impossível. Se tudo sair com `OK`, a apresentação está segura.

### 5. Executar

```bash
mvnw.cmd javafx:run     # Windows
./mvnw javafx:run       # Git Bash / Linux / macOS
```

Ou duplo clique em `abrir-sistema.bat`, que liga o banco e abre o sistema.

### Executável para Windows

Duplo clique em `gerar-executavel.bat`. Ele gera, em `target/executavel/`:

- `Custo Certo/Custo Certo.exe`: o programa, com o Java embutido. Quem recebe não
  precisa instalar Java nem Maven.
- `Custo-Certo-Windows.zip`: a mesma pasta compactada, para publicar em
  **Releases** no GitHub. O zip não leva o `config.properties` de ninguém.

Quem baixa o zip copia `config.properties.example` para `config.properties`, na
mesma pasta do `.exe`, e preenche a conexão. As instruções vão junto, no
`LEIA-ME.txt`. Precisa de um JDK 21 ou mais novo para gerar.

---

## Abrindo no Eclipse

**File → Import → Maven → Existing Maven Projects**, apontando para a pasta
`desktop` — não para a raiz do repositório, que só tem documentação.

Dois cuidados:

**O Eclipse precisa rodar sobre JDK 21 ou superior.** Confira em
*Window → Preferences → Java → Installed JREs*.

**Para executar, use a classe `Launcher`**, não a `App`:
*Botão direito em `Launcher.java` → Run As → Java Application*

A `App` estende `Application`, e a JVM recusa iniciar uma classe assim quando o
JavaFX está no classpath em vez do module path — o erro é *"JavaFX runtime
components are missing"*. A `Launcher` não estende nada, então a verificação não
se aplica. Pela linha de comando o `mvnw javafx:run` continua valendo.

Passo a passo detalhado em
[`docs/como-abrir-no-eclipse.md`](../docs/como-abrir-no-eclipse.md).

---

## Contas cadastradas pelo script

| E-mail | Senha | Perfil | Situação |
|---|---|---|---|
| `admin@custocerto.com.br` | `admin123` | Administrador | ativa |
| `denilson@letscoffee.com.br` | `gestor123` | Gestor | ativa |
| `marina@letscoffee.com.br` | `operador123` | Operador | ativa |
| `rafael@letscoffee.com.br` | `operador123` | Operador | ativa |
| `camila@letscoffee.com.br` | `gestor123` | Gestor | **inativa** |

**As senhas não estão gravadas em texto puro.** Cada linha guarda um `salt`
aleatório e o hash PBKDF2-HMAC-SHA256 com 210.000 iterações. Para trocar uma
senha pela linha de comando:

```bash
mvnw.cmd exec:java -Dexec.mainClass=br.com.custocerto.util.GeradorDeSenhas -Dexec.args="novaSenha"
```

---

## Arquitetura em camadas

```
  view       10 telas JavaFX, sem SQL nem regra de negócio
    |
  service    regras: validação, senha, log, análise de margem
    |
  dao        único lugar com SQL · ConexaoMySQL abre a conexão JDBC
    |
  model      dados que trafegam entre as camadas
  util       senha, sessão, configuração, diagnóstico
```

54 classes, cerca de 11.700 linhas. Cada camada só conhece a de baixo: a tela não
sabe que existe MySQL, e se o banco mudar, só o pacote `dao` é reescrito.

Detalhamento — incluindo onde os conceitos de OO aparecem e por quê — em
[`docs/arquitetura.md`](../docs/arquitetura.md).

---

## Banco de dados

Sete tabelas em InnoDB e utf8mb4, com **27 restrições** declaradas:

| Tabela | O que guarda | Linhas |
|---|---|---|
| `usuarios` | contas de acesso | 5 |
| `log_acesso` | tentativas de login, aceitas e recusadas | 27 |
| `ingredientes` | estoque ativo | 8 |
| `movimentacoes_estoque` | entradas e saídas | 10 |
| `receitas` | o que a cafeteria vende | 6 |
| `receita_ingredientes` | ficha técnica (N:M) | 22 |
| `vendas` | faturamento | 12 |

Todas acima do mínimo de 5 linhas exigido pela matéria. O DER, o dicionário de
dados e a lista completa de restrições estão em
[`docs/modelo-de-dados.md`](../docs/modelo-de-dados.md).

---

## Documentação Javadoc

```bash
mvnw.cmd javadoc:javadoc
```

O HTML sai em `target/reports/apidocs/index.html`, sem avisos.

---

## Roteiro sugerido para a apresentação

**Parte 1 — o banco é real** (2 min)

1. Abrir o `console-mysql.bat` e rodar `SHOW TABLES;` — sete tabelas em MySQL.
2. Rodar a consulta de restrições da seção 4 do
   [modelo de dados](../docs/modelo-de-dados.md) — 27 restrições declaradas.

**Parte 2 — perfis de acesso** (3 min)

3. Entrar como **administrador**. Mostrar que o menu tem Usuários e as Análises.
4. Sair e entrar como **operador** (`marina@letscoffee.com.br`). O menu encolhe:
   sem Dashboard, sem Análises, sem Usuários.
5. Tentar a senha errada, e depois a conta inativa da Camila.
6. Voltar ao console e rodar
   `SELECT * FROM log_acesso ORDER BY id DESC LIMIT 5;` — as tentativas que
   acabaram de acontecer estão lá, inclusive a recusada.

**Parte 3 — o que o sistema descobre** (5 min)

7. Abrir **Ficha Técnica**. Mostrar o custo do cappuccino somado a partir dos
   insumos. Editar o preço do café no Estoque e voltar: a margem mudou sozinha.
8. Abrir **Margem e Desperdício**. Os quadrantes classificam o cardápio; o Bolo
   de Cenoura aparece como Estrela e o Café Expresso como Cavalo de carga.
9. Descer até **Consumo Teórico × Real**. É o ponto alto: o sistema aponta, por
   insumo e em reais, a diferença entre o que as vendas pediam e o que a balança
   pesou. Explicar que a maioria dos sistemas comerciais só consegue estimar isso,
   porque não mede o consumo real.
10. Mostrar o aviso de "sem baixa registrada" para copo, canela e creme — e
    explicar que exibir 100% de variância neles seria mentira.

**Se perguntarem sobre segurança:** mostrar a coluna `senha_hash` no banco, e
explicar o `PreparedStatement` em todos os DAOs.

---

## O que ainda falta

- Gravar e testar o firmware no ESP32. Ele compila, mas nunca rodou em placa —
  então a tela da Balança nunca recebeu leitura real.
- Abrir a porta 3306 no Firewall do Windows, para a demonstração de acesso via
  rede local.
- Lotes de validade como entidade, o que destravaria a baixa por FIFO.
