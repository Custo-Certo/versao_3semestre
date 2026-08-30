# Arquitetura do sistema — Custo Certo

Documento de entrega da matéria de **Programação Orientada a Objetos**
(Prof. Sérgio Furgeri).

Descreve como o sistema está organizado em camadas, o que cada uma faz, como a
aplicação Java manipula o banco de dados pela rede local, e onde encontrar a
documentação Javadoc.

---

## 1. Visão geral

O Custo Certo tem três programas que conversam com um único banco MySQL:

```
                      ┌──────────────────────────────┐
                      │   Balança física             │
                      │   ESP32 + célula HX711       │
                      └──────────────┬───────────────┘
                                     │ HTTP: POST /balanca/peso
                                     ▼
   ┌───────────────────┐    ┌────────────────────────┐
   │  App desktop      │    │  Servidor Node/Express │
   │  JavaFX (Java 21) │    │  painel web + balança  │
   └─────────┬─────────┘    └───────────┬────────────┘
             │                          │
             │ JDBC                     │ mysql2
             │ (rede local, porta 3306) │
             ▼                          ▼
        ┌──────────────────────────────────────┐
        │       MySQL 8.4 — banco custo_certo  │
        │  usuarios · log_acesso               │
        │  ingredientes · movimentacoes_estoque│
        └──────────────────────────────────────┘
```

| Programa | Linguagem | Papel |
|---|---|---|
| **App desktop** | Java 21 + JavaFX | interface gráfica do sistema; login, estoque, indicadores, contas |
| **Servidor** | TypeScript / Node | recebe o peso do ESP32 e serve o painel web |
| **Firmware** | C++ / PlatformIO | lê a célula de carga e envia o peso |

O app desktop **não passa pelo servidor Node** para falar com o banco: ele abre
uma conexão JDBC direto no MySQL. Foi a decisão **D1-A** da migração. O servidor
Node continua existindo porque o ESP32 fala HTTP, não SQL — e porque o painel web
já estava pronto e em uso.

---

## 2. As camadas do app desktop

A aplicação Java tem **36 classes**, cerca de 6.500 linhas, organizadas em cinco
pacotes. Cada camada só conhece a de baixo:

```
  view       TelaLogin · TelaPrincipal            telas JavaFX
             PaginaDashboard · PaginaBalanca      sem SQL, sem regra de negócio
             PaginaEstoque · PaginaEvolucao
             PaginaUsuarios
             Dialogos · Formatos · Icones
                     │
                     ▼
  service    AutenticacaoService                  regras de negócio, validação,
             EstoqueService                       tradução de erro do banco em
             UsuarioService                       mensagem para a pessoa
             BalancaClient
                     │
                     ▼
  dao        UsuarioDAO · LogAcessoDAO            o ÚNICO lugar com SQL
             IngredienteDAO · MovimentacaoDAO
             ConexaoMySQL                         abre a conexão JDBC
                     │
                     ▼
  model      Usuario · Perfil                     dados que trafegam entre as
             Ingrediente · Unidade                camadas; sem banco, sem tela
             Movimentacao · TipoMovimentacao
             ResumoEstoque

  util       SenhaUtil · Sessao · ConfiguracaoBanco · GeradorDeSenhas
             DiagnosticoAmbiente                  apoio, usado por várias camadas
```

### O que cada camada pode e não pode fazer

| Camada | Faz | Não faz |
|---|---|---|
| `view` | monta a tela, lê o formulário, exibe mensagem | não escreve SQL, não valida regra de negócio |
| `service` | valida, aplica regra, traduz `SQLException` em mensagem | não conhece JavaFX, não escreve SQL |
| `dao` | escreve SQL, converte `ResultSet` em objeto | não decide regra, não fala com a tela |
| `model` | guarda e transporta dados | não conhece banco nem tela |

A regra prática: **a tela não sabe que existe MySQL.** Se o banco fosse trocado,
só o pacote `dao` precisaria ser reescrito. E a `EstoqueException` que a tela
recebe nunca carrega texto de erro do MySQL — sempre uma frase que a pessoa
entende.

### Um exemplo do caminho completo

Cadastrar uma conta de acesso, do clique ao banco:

```
PaginaUsuarios.abrirFormulario()          view    lê nome, e-mail, perfil, senha
        │
        ▼
UsuarioService.cadastrar(usuario, senha)  service valida nome, formato do e-mail,
        │                                         tamanho da senha
        ├── SenhaUtil.gerarSaltEHash()     util    gera salt e hash PBKDF2
        ▼
UsuarioDAO.inserir(usuario)               dao     INSERT com PreparedStatement
        │
        ▼
ConexaoMySQL.obter()                      dao     abre a conexão JDBC
        │
        ▼
     MySQL
```

Se o e-mail já existir, o MySQL recusa pela restrição `uk_usuarios_email`. O DAO
deixa a `SQLException` subir; o `UsuarioService` a reconhece e a converte em
*"Já existe uma conta com o e-mail ..."*; a tela apenas exibe. Nenhuma camada
faz o trabalho da outra.

---

## 3. Orientação a objetos na prática

Onde os conceitos da matéria aparecem no código, e para quê:

### Encapsulamento

`Usuario`, `Ingrediente` e `Movimentacao` têm todos os atributos privados, com
acesso por getters e setters. A senha nunca é atributo: o que existe são
`senhaHash` e `salt`, e a senha em texto puro não passa da camada de serviço.

### Enum com comportamento

`Perfil` não é uma lista de nomes — cada constante carrega as suas permissões:

```java
ADMINISTRADOR("Administrador", true,  true,  true),
GESTOR       ("Gestor",        false, true,  true),
OPERADOR     ("Operador",      false, false, true);
```

Assim, a tela não compara nomes de perfil para decidir o que mostrar; ela
pergunta ao próprio perfil:

```java
if (usuario.getPerfil().podeGerenciarUsuarios()) {
    adicionarItem("Usuários", "usuarios", new PaginaUsuarios(servicoUsuarios));
}
```

O ganho é concreto: **incluir um perfil novo não exige mexer em nenhuma tela.**
O mesmo vale para `Unidade` e `TipoMovimentacao`, que também guardam os valores
aceitos pelos `ENUM` correspondentes no MySQL.

### Composição

`TelaPrincipal` não herda das páginas: ela as compõe, guardando as instâncias e
trocando o conteúdo da área central. `ResumoEstoque` recebe a lista de
ingredientes no construtor e calcula os indicadores em cima dela.

### Herança

Usada onde o framework exige: `App extends Application` (JavaFX) e as páginas
estendem `VBox`. As exceções do sistema — `AutenticacaoException`,
`EstoqueException`, `UsuarioException` — estendem `Exception`, para serem
verificadas em tempo de compilação: quem chama um serviço é **obrigado** a decidir
o que fazer se ele falhar.

### Polimorfismo

Cada página é um `javafx.scene.Node`, e `TelaPrincipal` as trata todas do mesmo
jeito ao montar o menu, sem saber qual é qual.

---

## 4. Acesso ao banco pela rede local

Este é o requisito de *"manipulação de banco de dados e acesso via rede local"*.

### Como a conexão é montada

`ConfiguracaoBanco` lê o arquivo `desktop/config.properties`, que fica **fora do
versionamento** — cada máquina tem o seu, e nenhuma senha vai para o repositório:

```properties
db.host=192.168.15.5
db.porta=3306
db.nome=custo_certo
db.usuario=custocerto
db.senha=********
```

Com isso ela monta a URL JDBC:

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
| Porta 3306 aberta no Firewall do Windows | **pendente** — exige privilégio de administrador, que a máquina atual não tem |

> **Atenção para a apresentação.** A pendência do firewall pode bloquear o acesso
> de outra máquina. Vale testar antes, na rede em que a demonstração vai
> acontecer, e ter à mão alguém com privilégio de administrador. Isso se amarra
> com a decisão **D2**, sobre onde o MySQL vai rodar, ainda em aberto no grupo.

### Uma conexão por operação

`ConexaoMySQL.obter()` abre uma conexão nova a cada operação, e todo DAO a fecha
com *try-with-resources*. É o oposto do que o servidor Node faz — lá existe um
pool, porque um servidor HTTP atende muitos pedidos ao mesmo tempo. No desktop,
uma pessoa por vez, a conexão curta é mais simples e não deixa conexão pendurada
se a rede cair.

### Transação onde ela importa

Movimentar estoque não é uma operação só: soma ou subtrai o saldo em
`ingredientes` **e** grava a linha em `movimentacoes_estoque`. `MovimentacaoDAO`
faz as duas dentro de uma transação, com `SELECT ... FOR UPDATE` para travar a
linha do insumo enquanto o saldo é recalculado, e `rollback` se qualquer passo
falhar. Sem isso, duas retiradas simultâneas poderiam deixar o estoque negativo.

---

## 5. Segurança

| Medida | Onde | Contra o quê |
|---|---|---|
| `PreparedStatement` em **todas** as consultas | todos os DAOs | injeção de SQL — digitar `' OR '1'='1` no campo de senha não derruba a autenticação |
| Senha com hash PBKDF2-HMAC-SHA256, 210.000 iterações, salt por conta | `SenhaUtil` | vazamento do banco não entrega as senhas |
| Comparação de hash em tempo constante | `SenhaUtil.saoIguais` | ataque de temporização |
| Mensagem única para e-mail inexistente e senha errada | `AutenticacaoService` | descobrir quais e-mails estão cadastrados |
| Registro de toda tentativa, com a máquina de origem | `LogAcessoDAO` | auditoria |
| Conta de aplicação sem `CREATE`/`DROP` | privilégios do MySQL | limitar o estrago de uma eventual falha |
| Credenciais fora do versionamento | `config.properties` no `.gitignore` | senha no repositório |
| Menu montado pelas permissões do perfil | `TelaPrincipal` + `Perfil` | operador não alcança telas de gestão |

---

## 6. Regras que ficam na aplicação, não no banco

Quase toda restrição do modelo está declarada no MySQL — chaves, `UNIQUE`,
`CHECK`, `ENUM`. Três regras, porém, dependem de **quem está usando o sistema
naquele momento**, e o banco não tem como saber isso. Elas vivem em
`UsuarioService`:

| Regra | Por quê |
|---|---|
| Ninguém inativa a própria conta | sairia do ar no meio da operação |
| Ninguém rebaixa o próprio perfil de administrador | perderia o acesso à tela que está usando |
| O último administrador ativo não pode ser inativado nem rebaixado | o sistema ficaria sem ninguém capaz de gerenciar contas |

A terceira consulta o banco antes de decidir
(`UsuarioDAO.contarAdministradoresAtivos()`), justamente porque a resposta muda
conforme o estado das outras contas.

---

## 7. Documentação Javadoc

Todas as classes, métodos públicos e parâmetros estão documentados. Para gerar:

```bash
cd desktop
mvnw.cmd javadoc:javadoc     # Windows
./mvnw javadoc:javadoc       # Git Bash / Linux / macOS
```

O HTML sai em `desktop/target/reports/apidocs/index.html`.

Os comentários explicam **por que** o código é como é, não o que a linha faz —
por que `log_acesso.usuario_id` aceita nulo, por que existe a classe `Launcher`,
por que a mensagem de erro do login é a mesma em dois casos diferentes.

---

## 8. Aderência aos requisitos das matérias

| Requisito | Onde é atendido |
|---|---|
| Sistema desenvolvido em camadas | 5 pacotes, seção 2 deste documento |
| Interface gráfica em Java | JavaFX 21 — `view`, 10 classes, 6 telas |
| Manipulação de banco de dados | pacote `dao`, JDBC com `PreparedStatement` |
| Acesso via rede local | `ConfiguracaoBanco` + conta `custocerto@'%'`, seção 4 |
| Aderente aos requisitos do usuário | macro funções do escopo: autenticação e perfis, estoque, balança, indicadores |
| Aderente aos modelos elaborados | as classes de `model` espelham as tabelas do [modelo de dados](modelo-de-dados.md) |
| Arquitetura do sistema | este documento |
| Documentação Javadoc | seção 7 |
| Modelagem por DER | [modelo-de-dados.md](modelo-de-dados.md) |
| Banco relacional em MySQL | banco `custo_certo`, 4 tabelas |
| Restrições de PK, FK e demais | 14 restrições declaradas, conferidas no banco |
| Mínimo de 5 linhas por tabela | 5, 21, 8 e 10 linhas |

---

## 9. O que ainda não está pronto

- **A tela da balança nunca recebeu leitura real.** O `BalancaClient` compila e o
  consumo de eventos SSE está implementado, mas não houve placa disponível para
  testar. Dá para simular sem a balança: subir o servidor Node e fazer um
  `POST /balanca/peso`.
- **O firmware otimizado não foi regravado no ESP32.** A latência caiu de ~500 ms
  para ~100 ms por leitura, e o `balancaController` passou a devolver a tara na
  resposta do `POST`. As duas mudanças **têm de subir juntas**: com o servidor
  novo e o firmware antigo, o pedido de tara nunca chega à balança.
- **Porta 3306 no firewall**, como descrito na seção 4.
- **Decisões D2, D3, D4 e D5** continuam em aberto no grupo — ver
  [decisoes-da-migracao.html](decisoes-da-migracao.html).
