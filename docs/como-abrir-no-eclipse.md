# Como abrir o projeto no Eclipse

Passo a passo para clonar o repositório e rodar o app desktop pela primeira vez.
Os quatro primeiros passos são de ambiente e só se faz uma vez.

> **Armadilha conhecida:** uma versão antiga do Eclipse **não abre este projeto**.
> Se o import não listar nada, quase sempre é isso. O Kepler (2013), por exemplo,
> foi feito para Java 7 e não entende `record`, *text blocks* nem o `pom.xml`
> moderno. É preciso o Eclipse **2024-03 ou mais novo** — o testado aqui foi o
> **2026-06**.

---

## 1. Confirmar o JDK 21 ou superior

```bash
java -version
```

Se acusar 17 ou menos, baixe um JDK 21+ (Temurin ou Oracle). O projeto compila
para Java 21 — versões anteriores não conseguem nem ler o código.

## 2. Instalar um Eclipse atual, se ainda não tiver

Baixe o pacote **Eclipse IDE for Java Developers** em
[eclipse.org/downloads/packages](https://www.eclipse.org/downloads/packages/).

É um `.zip`: basta extrair, não precisa de instalador nem de permissão de
administrador. O pacote já vem com um Java 21 próprio, então funciona mesmo que
o JDK do sistema seja outro.

## 3. Clonar num caminho curto, sem espaços e fora do OneDrive

```bash
git clone https://github.com/Custo-Certo/versao_3semestre.git C:\dev\custo-certo
```

Espaços no caminho e pastas sincronizadas pelo OneDrive causam falhas difíceis de
diagnosticar no Eclipse, que grava metadados o tempo todo.

## 4. Abrir o Eclipse num workspace também fora do OneDrive

Na primeira tela, escolha algo como `C:\dev\workspace`. Se o seu workspace atual
estiver dentro do OneDrive, troque em **File → Switch Workspace → Other**.

## 5. Importar como projeto Maven

**File → Import → Maven → Existing Maven Projects**

Não use *General → Existing Projects into Workspace*: aquele assistente procura
um arquivo `.project`, que projetos Maven não têm, e a lista vem vazia.

## 6. Apontar para a pasta `desktop`, não para a raiz

Em *Root Directory*, informe `C:\dev\custo-certo\desktop`. A raiz do repositório
é o projeto Node e não tem `pom.xml`.

A janela deve mostrar:

```
Projects:
  [x] /pom.xml   br.com.custocerto:custo-certo-desktop:1.0.0:jar
```

## 7. Esperar o m2e baixar as dependências

Na primeira importação o Eclipse resolve o JavaFX e o driver JDBC do MySQL
sozinho. Leva um ou dois minutos, com barra de progresso no canto inferior
direito. Ninguém precisa baixar o SDK do JavaFX à mão.

## 8. Criar o `config.properties` e preparar o banco

```bash
cd desktop
copy config.properties.example config.properties
```

Preencha com os dados do seu MySQL. Depois rode os dois scripts de
`src/main/resources/sql/`, **na ordem**:

```bash
mysql -u root -p < src/main/resources/sql/01_schema_usuarios.sql
mysql -u root -p < src/main/resources/sql/02_schema_insumos.sql
```

O `config.properties` não vai para o Git — cada pessoa tem o seu.

## 9. Executar pela classe `Launcher`

Botão direito em `Launcher.java` → **Run As → Java Application**.

**Não use a classe `App`** — veja o porquê na tabela abaixo. Com o MySQL ligado,
a tela de login abre.

Contas de teste:

| E-mail | Senha | Perfil |
|---|---|---|
| `admin@custocerto.com.br` | `admin123` | Administrador |
| `denilson@letscoffee.com.br` | `gestor123` | Gestor |
| `marina@letscoffee.com.br` | `operador123` | Operador |

---

## Se algo der errado

| O que acontece | Causa | Solução |
|---|---|---|
| A lista de import fica **vazia** | Eclipse antigo demais, ou assistente errado | Use um Eclipse 2024-03+ e o caminho *Maven → Existing Maven Projects*. Confirme que apontou para a pasta `desktop`. |
| A lista fica vazia mesmo com tudo certo | O projeto já está no workspace | O m2e esconde o que já foi importado. Confira o *Package Explorer*; se estiver lá, remova com *Delete* sem marcar "delete contents on disk" e importe de novo. |
| `JavaFX runtime components are missing` | Executou a classe `App` | Use a `Launcher`. A `App` estende `Application`, e a JVM recusa iniciar uma classe assim quando o JavaFX está no classpath em vez do module path. A `Launcher` não estende nada e existe só para contornar isso. |
| Erros de compilação em `record`, `switch` ou blocos de texto | JRE do projeto abaixo de 21 | *Window → Preferences → Java → Installed JREs* e selecione um JDK 21+. Depois *Project → Clean*. |
| Aviso de banco indisponível ao abrir | MySQL desligado ou endereço errado | Ligue o servidor e confira `db.host`, `db.usuario` e `db.senha` no `config.properties`. A janela abre mesmo assim, mas o login não funciona. |
| Eclipse lento, travando ou com erros estranhos de arquivo | Workspace ou projeto dentro do OneDrive | Mova os dois para um caminho local curto, como `C:\dev`. O OneDrive sincroniza os metadados do Eclipse e trava arquivos em uso. |

---

## Sem IDE também funciona

Pela linha de comando, dentro de `desktop/`:

```bash
mvnw javafx:run          # abre a aplicação
mvnw javadoc:javadoc     # gera a documentação
mvnw exec:java -Dexec.mainClass=br.com.custocerto.util.DiagnosticoAmbiente
```

O Maven Wrapper baixa tudo o que precisa — só o JDK precisa estar instalado.
