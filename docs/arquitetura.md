# Arquitetura do sistema — Custo Certo

Documento de entrega da matéria de **Programação Orientada a Objetos**
(Prof. Sérgio Furgeri).

Descreve como o sistema está organizado em camadas, o que cada uma faz, como a
aplicação Java manipula o banco de dados pela rede local, e onde encontrar a
documentação Javadoc.

---

## 1. Visão geral

O Custo Certo é hoje composto por **dois projetos independentes** e um
**dispositivo**, que compartilham um banco de dados:

| Peça | Repositório | O que é |
|---|---|---|
| **App desktop** | `versao_3semestre` (este) | interface Java/JavaFX, acesso ao MySQL por JDBC |
| **Painel web** | `Project_Custo_Certo` | servidor Node/Express e painel no navegador |
| **Balança** | firmware no repositório do painel | ESP32 + célula HX711, com API HTTP própria |

```
                  ┌──────────────────────────────┐
                  │  Balança  ESP32 + HX711      │
                  │  GET /peso    POST /tara     │
                  └───────┬──────────────┬───────┘
                     saída│              │saída
              ┌───────────┘              └───────────┐
     ┌────────┴────────┐                  ┌──────────┴──────────┐
     │  App desktop    │                  │  Painel web (Node)  │
     │  JavaFX + JDBC  │                  │  Express + mysql2   │
     │  (este projeto) │                  │  (outro projeto)    │
     └────────┬────────┘                  └──────────┬──────────┘
              │  JDBC, porta 3306                    │
              └──────────────┐        ┌──────────────┘
                        ┌────┴────────┴────┐
                        │  MySQL 8.4       │
                        │  custo_certo     │
                        │  7 tabelas       │
                        └──────────────────┘
```

**Nenhum dos dois depende do outro para funcionar.** Abrir o app desktop não
exige o servidor Node no ar. O que eles compartilham é externo a ambos: o banco e
a balança.

### A balança é um dispositivo de rede

Esta é a decisão de arquitetura mais recente, e vale explicar o antes e o depois.

Antes, o ESP32 **empurrava** a leitura para o servidor Node, e quem quisesse o
peso pedia a esse servidor. Isso amarrava o app desktop a um projeto que hoje
tem outro repositório e outro ciclo de vida: sem o Node no ar, a balança não
existia para o Java.

Agora o ESP32 sobe um servidor HTTP e **responde** a quem perguntar:

```
GET  /peso   →  {"peso":1.234,"estavel":true,"leituras":123,"uptime":45}
POST /tara   →  zera a balança
GET  /       →  página de status, para achar e testar o aparelho
```

Três consequências práticas:

1. **Os dois clientes fazem apenas conexão de saída.** Isso importa porque as
   máquinas do grupo não têm privilégio de administrador, e abrir porta de
   *entrada* no Firewall do Windows foi exatamente o que travou o acesso ao MySQL
   pela rede. Conexão de saída não precisa de regra.
2. **Sumiu o acoplamento firmware ↔ backend.** A tara era uma flag guardada no
   servidor que o firmware consumia na troca de mensagens seguinte, o que
   obrigava os dois a subirem juntos. Agora a tara é um POST direto no aparelho.
3. **O aparelho é descoberto por mDNS** (`custocerto-balanca.local`), com o IP
   como plano B, configurável em `balanca.url`.

O firmware continua empurrando para uma URL remota quando `SERVER_URL` está
preenchida, o que mantém viável um painel hospedado fora da rede da cafeteria.

---

## 2. As camadas do app desktop

A aplicação Java tem **54 classes**, cerca de 11.700 linhas, em cinco pacotes.
Cada camada só conhece a de baixo:

```
  view (13)    TelaLogin · TelaPrincipal                 telas JavaFX
               PaginaDashboard · PaginaBalanca           sem SQL, sem regra
               PaginaEstoque · PaginaEvolucao
               PaginaFichaTecnica · PaginaVendas
               PaginaMargem · PaginaUsuarios
               Dialogos · Formatos · Icones
                       │
                       ▼
  service (11) AutenticacaoService · UsuarioService      regras de negócio,
               EstoqueService · FichaTecnicaService      validação, tradução
               VendaService · AnaliseService             de erro do banco
               BalancaClient
               + 4 exceções de negócio
                       │
                       ▼
  dao (8)      UsuarioDAO · LogAcessoDAO                 o ÚNICO lugar com SQL
               IngredienteDAO · MovimentacaoDAO
               ReceitaDAO · VendaDAO
               IndicadoresDAO
               ConexaoMySQL                              abre a conexão JDBC
                       │
                       ▼
  model (15)   Usuario · Perfil                          dados que trafegam;
               Ingrediente · Unidade                     sem banco, sem tela
               Movimentacao · TipoMovimentacao
               Receita · ItemFicha · Venda
               Periodo · ResumoEstoque · ResumoFinanceiro
               IndicadorInsumo · VarianciaInsumo
               DesempenhoProduto

  util (5)     SenhaUtil · Sessao · ConfiguracaoBanco
               GeradorDeSenhas · DiagnosticoAmbiente
```

### O que cada camada pode e não pode fazer

| Camada | Faz | Não faz |
|---|---|---|
| `view` | monta a tela, lê o formulário, exibe mensagem | não escreve SQL, não valida regra de negócio |
| `service` | valida, aplica regra, traduz `SQLException` em mensagem | não conhece JavaFX, não escreve SQL |
| `dao` | escreve SQL, converte `ResultSet` em objeto | não decide regra, não fala com a tela |
| `model` | guarda, transporta e **deriva** dados | não conhece banco nem tela |

A regra prática: **a tela não sabe que existe MySQL.** Se o banco fosse trocado,
só o pacote `dao` seria reescrito. E a exceção que a tela recebe nunca carrega
texto de erro do MySQL — sempre uma frase que a pessoa entende.

### Uma separação que vale explicar: `IndicadoresDAO` e `AnaliseService`

Os indicadores de negócio são o pedaço mais interessante do sistema, e o trabalho
foi dividido entre duas camadas por um critério claro:

- **`IndicadoresDAO`** faz o que o banco faz bem: somar, agrupar, cruzar tabelas.
  As consultas ali cruzam vendas, fichas técnicas e movimentações, com junções à
  esquerda para que um insumo sem movimento ainda apareça na lista.
- **`AnaliseService`** faz o que exige olhar o conjunto inteiro. A curva ABC
  precisa da lista ordenada e do percentual acumulado; a engenharia de cardápio
  precisa das médias do próprio cardápio para saber o que é "margem alta" naquele
  contexto. Nenhuma das duas caberia em SQL sem ficar ilegível.

---

## 3. Orientação a objetos na prática

### Encapsulamento

Todos os modelos têm atributos privados com acesso por getters e setters. A senha
nunca é atributo: o que existe são `senhaHash` e `salt`, e a senha em texto puro
não passa da camada de serviço.

### Objetos que calculam, e não só carregam

Os modelos do bloco de indicadores não são estruturas de dados passivas — eles
carregam o comportamento que lhes diz respeito:

```java
// VarianciaInsumo sabe julgar a si mesma
public boolean isSemBaixaRegistrada() {
    return teorico.signum() > 0 && real.signum() == 0;
}

public String getClassificacao() {
    if (isSemBaixaRegistrada() || teorico.signum() <= 0) return "sem-dado";
    double absoluto = Math.abs(getPercentual());
    if (absoluto <= LIMITE_BOM) return "bom";
    return absoluto <= LIMITE_TIPICO ? "tipico" : "ruim";
}
```

Isso mantém a tela burra: ela pergunta a classificação e escolhe uma cor, sem
saber que 2% é o limite de uma operação bem controlada.

### Enum com comportamento

`Perfil` não é uma lista de nomes — cada constante carrega as suas permissões:

```java
ADMINISTRADOR("Administrador", true,  true,  true),
GESTOR       ("Gestor",        false, true,  true),
OPERADOR     ("Operador",      false, false, true);
```

A tela não compara nomes de perfil; pergunta ao próprio perfil:

```java
if (usuario.getPerfil().podeGerenciarUsuarios()) {
    adicionarItem("Usuários", "usuarios", new PaginaUsuarios(servicoUsuarios));
}
```

**Incluir um perfil novo não exige mexer em nenhuma tela.** O mesmo vale para
`Unidade` e `TipoMovimentacao`, e para `DesempenhoProduto.Quadrante`, que guarda
junto de cada quadrante a ação recomendada — de modo que a tela exibe a
recomendação sem conhecer engenharia de cardápio.

### Composição

`TelaPrincipal` não herda das páginas: ela as compõe, guardando as instâncias e
trocando o conteúdo da área central. `Receita` compõe uma lista de `ItemFicha` e
soma o próprio custo a partir dela. `ResumoEstoque` recebe a lista de ingredientes
no construtor e calcula os indicadores em cima dela.

### Herança

Usada onde o framework exige: `App extends Application` e as páginas estendem
`VBox`. As exceções de negócio — `AutenticacaoException`, `EstoqueException`,
`UsuarioException`, `CardapioException` — estendem `Exception` para serem
verificadas em tempo de compilação: quem chama um serviço é **obrigado** a
decidir o que fazer se ele falhar.

### Polimorfismo

Cada página é um `javafx.scene.Node`, e `TelaPrincipal` as trata todas do mesmo
jeito ao montar o menu, sem saber qual é qual.

---

## 4. Acesso ao banco pela rede local

Este é o requisito de *"manipulação de banco de dados e acesso via rede local"*.

### Como a conexão é montada

`ConfiguracaoBanco` lê `desktop/config.properties`, que fica **fora do
versionamento** — cada máquina tem o seu, e nenhuma senha vai para o repositório:

```properties
db.host=192.168.15.5
db.porta=3306
db.nome=custo_certo
db.usuario=custocerto
db.senha=********
balanca.url=http://custocerto-balanca.local
```

Com isso monta a URL JDBC:

```
jdbc:mysql://192.168.15.5:3306/custo_certo?useSSL=false&serverTimezone=America/Sao_Paulo&connectTimeout=3000
```

Trocar `db.host` de `localhost` para o IP de outra máquina é **tudo** o que é
preciso para o mesmo programa passar a ler o banco pela rede. Nenhuma linha de
código muda.

### O que isso exige do servidor

| Item | Situação |
|---|---|
| `bind-address = 0.0.0.0` no `my.ini` | configurado |
| Conta liberada para acesso remoto | `custocerto@'%'`, com permissão só em `custo_certo` |
| Porta 3306 aberta no Firewall do Windows | **pendente** — exige privilégio de administrador |

> **Atenção para a apresentação.** A pendência do firewall pode bloquear o acesso
> de outra máquina. Vale testar antes, na rede em que a demonstração vai
> acontecer.

### Uma conexão por operação

`ConexaoMySQL.obter()` abre uma conexão nova a cada operação, e todo DAO a fecha
com *try-with-resources*. É o oposto do que o servidor Node faz — lá existe um
pool, porque um servidor HTTP atende muitos pedidos ao mesmo tempo. No desktop,
uma pessoa por vez, a conexão curta é mais simples e não deixa conexão pendurada
se a rede cair.

### Transação onde ela importa

Duas operações não são atômicas por natureza e ficam dentro de uma transação:

- **Movimentar estoque** soma ou subtrai o saldo em `ingredientes` *e* grava a
  linha em `movimentacoes_estoque`. `MovimentacaoDAO` usa `SELECT ... FOR UPDATE`
  para travar a linha do insumo enquanto o saldo é recalculado, com `rollback` se
  qualquer passo falhar. Sem isso, duas retiradas simultâneas poderiam deixar o
  estoque negativo.
- **Gravar uma ficha técnica** insere a receita, lê o id gerado e só então insere
  os itens que apontam para ele. `ReceitaDAO` faz os três passos na mesma
  transação, para que nenhum produto fique com a ficha pela metade.

---

## 5. Segurança

| Medida | Onde | Contra o quê |
|---|---|---|
| `PreparedStatement` em **todas** as consultas | todos os DAOs | injeção de SQL |
| Senha com PBKDF2-HMAC-SHA256, 210.000 iterações, salt por conta | `SenhaUtil` | vazamento do banco não entrega as senhas |
| Comparação de hash em tempo constante | `SenhaUtil.saoIguais` | ataque de temporização |
| Mensagem única para e-mail inexistente e senha errada | `AutenticacaoService` | descobrir quais e-mails existem |
| Registro de toda tentativa, com a máquina de origem | `LogAcessoDAO` | auditoria |
| Conta de aplicação sem `CREATE`/`DROP` | privilégios do MySQL | limitar o estrago de uma eventual falha |
| Credenciais fora do versionamento | `config.properties` no `.gitignore` | senha no repositório |
| Menu montado pelas permissões do perfil | `TelaPrincipal` + `Perfil` | operador não alcança telas de gestão |

---

## 6. Regras que ficam na aplicação, não no banco

Quase toda restrição do modelo está declarada no MySQL — são 27. Algumas, porém,
dependem de **quem está usando o sistema** ou do **estado de outras linhas**, e
uma restrição declarativa não alcança isso:

| Regra | Onde | Por quê |
|---|---|---|
| Ninguém inativa a própria conta | `UsuarioService` | sairia do ar no meio da operação |
| Ninguém rebaixa o próprio perfil de administrador | `UsuarioService` | perderia o acesso à tela que está usando |
| O último administrador ativo é intocável | `UsuarioService` | o sistema ficaria sem quem gerencie contas |
| Insumo repetido na mesma ficha é recusado | `FichaTecnicaService` | seriam duas linhas dizendo coisas diferentes |
| Produto com vendas não é excluído | `FichaTecnicaService` | o banco recusaria; o serviço explica antes |

A terceira e a quinta consultam o banco antes de decidir, justamente porque a
resposta depende do estado das outras linhas.

---

## 7. Documentação Javadoc

Todas as classes, métodos públicos e parâmetros estão documentados. Para gerar:

```bash
cd desktop
mvnw.cmd javadoc:javadoc     # Windows
./mvnw javadoc:javadoc       # Git Bash / Linux / macOS
```

O HTML sai em `desktop/target/reports/apidocs/index.html`, **sem avisos**.

Os comentários explicam **por que** o código é como é, não o que a linha faz — por
que `log_acesso.usuario_id` aceita nulo, por que registrar uma venda não abate o
estoque, por que o custo do produto não é uma coluna.

---

## 8. Aderência aos requisitos das matérias

| Requisito | Onde é atendido |
|---|---|
| Sistema desenvolvido em camadas | 5 pacotes, seção 2 |
| Interface gráfica em Java | JavaFX 21 — `view`, 13 classes, 8 telas |
| Manipulação de banco de dados | pacote `dao`, JDBC com `PreparedStatement` |
| Acesso via rede local | `ConfiguracaoBanco` + conta `custocerto@'%'`, seção 4 |
| Aderente aos requisitos do usuário | autenticação e perfis, estoque, balança, ficha técnica, vendas, indicadores |
| Aderente aos modelos elaborados | as classes de `model` espelham as tabelas do [modelo de dados](modelo-de-dados.md) |
| Arquitetura do sistema | este documento |
| Documentação Javadoc | seção 7 |
| Modelagem por DER | [modelo-de-dados.md](modelo-de-dados.md) |
| Banco relacional em MySQL | banco `custo_certo`, 7 tabelas |
| Restrições de PK, FK e demais | 27 restrições declaradas, conferidas no banco |
| Mínimo de 5 linhas por tabela | 5, 27, 8, 10, 6, 22 e 12 linhas |

---

## 9. O que ainda não está pronto

- **O firmware não foi gravado no ESP32.** Ele compila para `esp32dev` — RAM
  14,4%, Flash 62,3% — mas não houve placa disponível para testar. Gravar e testar
  é a única forma de validar o mDNS, o CORS e a tolerância de estabilidade da
  leitura, que hoje está em 2 gramas e precisa ser conferida junto com o
  `SCALE_FACTOR`.
- **A tela da Balança nunca recebeu leitura real** pelo mesmo motivo. O
  `BalancaClient` consulta o aparelho a cada 400 ms e trata a ausência de resposta,
  mas isso só foi exercitado com a balança fora do ar.
- **Porta 3306 no firewall**, como descrito na seção 4.
- **Lotes de validade** não são uma entidade, o que impede a baixa por FIFO — ver
  a seção 7 do [modelo de dados](modelo-de-dados.md).
