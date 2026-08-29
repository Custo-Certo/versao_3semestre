# Documentação do Custo Certo

| Documento | Para quê |
|---|---|
| [Como abrir o projeto no Eclipse](como-abrir-no-eclipse.md) | Passo a passo para clonar, importar e rodar o app desktop. Comece por aqui. |
| [Decisões da Migração](decisoes-da-migracao.html) | Documento completo do semestre: as 8 decisões, a arquitetura em camadas, o modelo de dados em MySQL, o fluxo do login, a balança em tempo real e a rastreabilidade dos requisitos das duas matérias. |
| [README do app desktop](../desktop/README.md) | Instalação, contas de teste, estrutura das camadas e roteiro de apresentação. |

## Sobre o arquivo `decisoes-da-migracao.html`

O GitHub não renderiza HTML direto na interface do repositório — ele mostra o
código-fonte. Para ler o documento formatado, com os diagramas, há duas opções:

- **Baixar e abrir no navegador:** clique no arquivo, depois em *Raw*, e salve
  com <kbd>Ctrl</kbd>+<kbd>S</kbd>. Ele é autocontido, não depende de internet.
- **Abrir a versão publicada:**
  https://claude.ai/code/artifact/5eb2e4b0-30e8-4ea9-85d1-50cd0f66d7d9

A cópia neste repositório existe para o documento ficar versionado junto do
código a que ele se refere, e continuar acessível mesmo se o link publicado sair
do ar.

## Decisões ainda em aberto

O documento lista 8 decisões da migração para MySQL + Java. Quatro foram
fechadas pelo código durante o desenvolvimento (D1, D6, D7 e D8) e **precisam do
aval do grupo**. Continuam em aberto:

- **D2** — onde o MySQL vai rodar na apresentação (trava a demonstração de acesso via rede local)
- **D3** — o que acontece com o deploy no Render e o banco Turso
- **D4** — quem recebe o peso do ESP32
- **D5** — o escopo do DER a ser entregue
