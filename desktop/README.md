# Custo Certo — Desktop (JavaFX)

Interface gráfica em Java do Custo Certo, para o 2º semestre de 2026.
Atende aos requisitos das matérias de **Programação Orientada a Objetos**
(Prof. Sérgio Furgeri) e **Banco de Dados** (Prof. Marlon).

Esta entrega cobre a **autenticação e os perfis de acesso** — a macro função
descrita no item 4 da definição de escopo do sistema.

---

## Decisões de arquitetura

| Decisão | Escolha | Por quê |
|---|---|---|
| Acesso a dados (D1) | JDBC direto no MySQL | O requisito da POO pede que o sistema Java manipule o banco e acesse pela rede local. O backend Node/Express continua no ar para a balança e o painel web. |
| Interface (D7) | JavaFX | Sucessor do Swing, com estilização por CSS — o que permite repetir a paleta do painel web. Entra como dependência do Maven, sem SDK para instalar. |
| Build | Maven, via wrapper | Resolve o driver JDBC e o JavaFX sozinho e gera o Javadoc em um comando. O wrapper dispensa instalar o Maven em cada máquina. |

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

O banco `custo_certo` já está criado e carregado. Contas do MySQL:

- `root` — sem senha, só de `localhost`, para administrar
- `custocerto` / `custocerto2026` — de qualquer host, com permissão apenas no
  banco `custo_certo`. É a conta que a aplicação usa e a que funciona pela rede.

> **Antes de qualquer coisa, ligue o banco.** Se o `iniciar-mysql.bat` não
> estiver rodando, a aplicação abre mas o login não funciona.

**Nas outras máquinas do grupo:** instale o MySQL Community Server normalmente e
rode o script da seção seguinte. Não é preciso repetir a instalação portátil.

---

## Como rodar, passo a passo

### 1. Ligar o banco

Dê duplo clique em `iniciar-mysql.bat` e **deixe a janela aberta**.

### 2. Criar o banco (só na primeira vez, ou em outra máquina)

Com o MySQL rodando:

```bash
mysql -u root -p < src/main/resources/sql/01_schema_usuarios.sql
```

Ou abra o arquivo no MySQL Workbench e execute o script inteiro.
Ele cria o banco `custo_certo`, as tabelas `usuarios` e `log_acesso`, e carrega
as contas iniciais. Pode ser executado mais de uma vez sem duplicar nada —
isso foi testado.

### 3. Apontar para o servidor certo

Nesta máquina o `config.properties` **já está criado e funcionando**. Em outra
máquina, copie o exemplo e preencha:

```bash
cp config.properties.example config.properties     # Git Bash
copy config.properties.example config.properties   # Prompt / PowerShell
```

- Desenvolvendo sozinho: `db.host=localhost`
- Demonstrando o acesso via rede local: `db.host=` o IP da máquina que estiver
  servindo o banco (nesta máquina, hoje, é `192.168.15.5` — confirme com
  `ipconfig`, porque o IP muda de rede para rede)

O `config.properties` está no `.gitignore` — a sua senha do MySQL não vai para o
repositório.

### 4. Conferir o ambiente antes de apresentar

```bash
mvnw.cmd exec:java -Dexec.mainClass=br.com.custocerto.util.DiagnosticoAmbiente
```

Testa a configuração, a conexão, a leitura da tabela e cinco cenários de login
(dois aceitos, três recusados). Se tudo sair com `OK`, a apresentação está segura.

### 5. Executar

```bash
mvnw.cmd javafx:run     # Windows
./mvnw javafx:run       # Git Bash / Linux / macOS
```

Na primeira execução o wrapper baixa o Maven e as dependências; leva alguns
minutos. Das próximas vezes abre direto.

---

## Abrindo no Eclipse

**File → Import → Maven → Existing Maven Projects**, apontando para a pasta
`desktop` — não para a raiz do repositório, que é o projeto Node. O Eclipse lê o
`pom.xml` e resolve JavaFX e o driver MySQL sozinho.

Dois cuidados:

**O Eclipse precisa rodar sobre JDK 21 ou superior.** Confira em
*Window → Preferences → Java → Installed JREs*. Com um JDK anterior o projeto
não compila.

**Para executar, use a classe `Launcher`**, não a `App`:

*Botão direito em `Launcher.java` → Run As → Java Application*

A `App` estende `Application`, e a JVM recusa iniciar uma classe assim quando o
JavaFX está no classpath em vez do module path — o erro é
*"JavaFX runtime components are missing"*. A `Launcher` não estende nada, então
a verificação não se aplica e o JavaFX sobe normalmente. É por isso que ela
existe.

Pela linha de comando o `mvnw javafx:run` continua valendo e pode apontar direto
para a `App`, porque o plugin do JavaFX monta o module path por conta própria.

O mesmo vale para IntelliJ e NetBeans.

---

## Contas cadastradas pelo script

| E-mail | Senha | Perfil | Situação |
|---|---|---|---|
| `admin@custocerto.com.br` | `admin123` | Administrador | ativa |
| `denilson@letscoffee.com.br` | `gestor123` | Gestor | ativa |
| `marina@letscoffee.com.br` | `operador123` | Operador | ativa |
| `rafael@letscoffee.com.br` | `operador123` | Operador | ativa |
| `camila@letscoffee.com.br` | `gestor123` | Gestor | **inativa** |

Entre com o administrador e depois com o operador para ver o menu mudar: o
cartão "Usuários" só fica liberado para o administrador, e o de "Indicadores"
não aparece para o operador. A conta da Camila serve para demonstrar a recusa
de uma conta desativada.

**As senhas não estão gravadas em texto puro.** Cada linha guarda um `salt`
aleatório e o hash PBKDF2-HMAC-SHA256 com 210.000 iterações. Para trocar uma
senha, gere um novo par e aplique um `UPDATE`:

```bash
mvnw.cmd exec:java -Dexec.mainClass=br.com.custocerto.util.GeradorDeSenhas -Dexec.args="novaSenha"
```

---

## Arquitetura em camadas

```
  view       TelaLogin, TelaPrincipal          telas JavaFX, sem SQL nem regra
    |
  service    AutenticacaoService               regras: validação, senha, log
    |
  dao        UsuarioDAO, LogAcessoDAO          único lugar com SQL
    |        ConexaoMySQL                      abre a conexão JDBC
    |
  model      Usuario, Perfil                   dados que trafegam entre as camadas

  util       SenhaUtil       hash PBKDF2 das senhas
             ConfiguracaoBanco  lê o endereço do servidor
             Sessao          guarda quem está logado
             GeradorDeSenhas utilitário de linha de comando
```

Cada camada só conhece a de baixo. A tela não sabe que existe MySQL; se o banco
mudar, só o pacote `dao` é reescrito.

---

## Banco de dados

Duas tabelas, ambas em InnoDB e utf8mb4:

**`usuarios`** — `PRIMARY KEY` em `id` com `AUTO_INCREMENT`, `UNIQUE` em `email`,
`ENUM` limitando o perfil aos três valores válidos, `CHECK` no formato do e-mail
e no tamanho do nome, `DEFAULT` para conta ativa e data de criação.

**`log_acesso`** — histórico de tentativas de login, aceitas e recusadas.
`FOREIGN KEY` de `usuario_id` para `usuarios(id)`, com `ON DELETE SET NULL`
(excluir um usuário não apaga o histórico) e `ON UPDATE CASCADE`. A coluna aceita
nulo de propósito: quando o e-mail digitado não existe, não há usuário a
referenciar, mas a tentativa precisa ficar registrada.

Cada tabela recebe pelo menos 5 linhas via `INSERT`, conforme o requisito da
matéria de Banco de Dados.

As restrições foram conferidas no banco em execução:

```
tabela      restricao                tipo
log_acesso  fk_log_acesso_usuario    FOREIGN KEY
log_acesso  PRIMARY                  PRIMARY KEY
usuarios    ck_usuarios_email        CHECK
usuarios    ck_usuarios_nome         CHECK
usuarios    PRIMARY                  PRIMARY KEY
usuarios    uk_usuarios_email        UNIQUE
```

> **Pendência:** estas duas tabelas precisam entrar no DER que será entregue ao
> Prof. Marlon, junto das tabelas de insumos e movimentações. O escopo do DER
> ainda é uma decisão aberta do grupo (D5).

---

## Documentação Javadoc

```bash
mvnw.cmd javadoc:javadoc
```

O HTML é gerado em `target/reports/apidocs/index.html`. Todas as classes,
métodos públicos e parâmetros estão documentados.

---

## Segurança da tela de login

- **Senhas com hash e salt** — o banco nunca guarda a senha legível.
- **`PreparedStatement` em todas as consultas** — os valores digitados viajam
  separados do texto do SQL, o que impede injeção de SQL. Sem isso, digitar
  `' OR '1'='1` no campo de senha derrubaria a autenticação.
- **Mensagem única para e-mail inexistente e senha errada** — mensagens
  diferentes permitiriam descobrir quais e-mails estão cadastrados.
- **Comparação de hash em tempo constante** — o tempo de resposta não revela
  quantos caracteres foram acertados.
- **Registro de todas as tentativas** em `log_acesso`, com a máquina de origem.

---

## Roteiro sugerido para a apresentação

1. Abrir o `console-mysql.bat` e rodar `SHOW TABLES;` e
   `SELECT id, nome, email, perfil, ativo FROM usuarios;` — mostra o banco em MySQL.
2. Abrir a aplicação e entrar como **administrador**. Mostrar que o cartão
   "Usuários" está liberado.
3. Sair e entrar como **operador** (`marina@letscoffee.com.br`). Mostrar que
   "Usuários" e "Indicadores" aparecem bloqueados — é o controle por perfil.
4. Tentar entrar com a senha errada e depois com a conta inativa da Camila.
5. Voltar ao console SQL e rodar
   `SELECT * FROM log_acesso ORDER BY id DESC LIMIT 5;` — as tentativas que
   acabaram de acontecer estão registradas, inclusive a recusada.
6. Se perguntarem sobre segurança: mostrar a coluna `senha_hash` no banco e
   explicar que a senha nunca é gravada legível.

---

## O que ainda falta

> Aviso: o restante deste README ainda descreve a entrega anterior, quando o app
> cobria apenas a autenticação. As telas de Dashboard, Balança, Estoque, Evolução
> de Preços e Usuários já existem, e as tabelas de insumos e movimentações já
> estão em MySQL. A descrição atualizada do sistema está em
> [docs/arquitetura.md](../docs/arquitetura.md) e
> [docs/modelo-de-dados.md](../docs/modelo-de-dados.md).

Pendências reais, hoje:

- Regravar o firmware no ESP32 — ele e o `balancaController` têm de subir juntos.
- Abrir a porta 3306 no Firewall do Windows, para a demonstração de acesso via rede.
- Decisões D2, D3, D4 e D5, ainda em aberto no grupo.
