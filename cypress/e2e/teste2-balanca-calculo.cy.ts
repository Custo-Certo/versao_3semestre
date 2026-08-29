/// <reference types="cypress" />
// ============================================================
// CASE 2 — Balança: Seleção de Ingrediente e Cálculo de Custo
// Objetivo: verificar que ao selecionar um ingrediente na
// balança a tabela de custo é preenchida corretamente
// ============================================================

describe('Case 2 — Balança e Cálculo de Custo', () => {

  before(() => {
    // Cadastra um ingrediente de base para os testes da balança
    cy.visit('/')
    cy.get('[onclick="showPage(\'estoque\',this)"]').click()
    cy.get('[onclick="abrirModal()"]').click()
    cy.get('#f-nome').type('Queijo Mussarela')
    cy.get('#f-unidade').select('kg')
    cy.get('#f-preco').type('32.00')
    cy.get('#f-qtd').type('5')
    const validade = new Date()
    validade.setFullYear(validade.getFullYear() + 1)
    cy.get('#f-validade').type(validade.toISOString().split('T')[0])
    cy.get('[onclick="salvarIngrediente()"]').click()
  })

  beforeEach(() => {
    cy.visit('/')
    // A aba Balança já é a padrão (active), mas garantimos
    cy.get('[onclick="showPage(\'balanca\',this)"]').click()
    cy.get('#page-balanca').should('have.class', 'active')
  })

  it('deve exibir o display de peso zerado ao carregar', () => {
    cy.get('#peso-display').should('contain', '0')
    cy.get('#btn-confirmar').should('be.disabled')
  })

  it('deve listar ingredientes cadastrados no select da balança', () => {
    cy.get('#select-ingrediente option').should('have.length.greaterThan', 1)
    cy.get('#select-ingrediente').should('contain', 'Queijo Mussarela')
  })

  it('deve preencher a tabela de custo ao selecionar ingrediente e simular peso', () => {
    // Seleciona o ingrediente
   cy.get('#select-ingrediente option').not('[value=""]').first().then(($opt) => {
  cy.get('#select-ingrediente').select($opt.val() as string)
})

    // Simula peso via JavaScript (como se a balança lesse 0.250 kg)
    cy.window().then((win: any) => {
  win.pesoAtual = 0.250
  const select = win.document.getElementById('select-ingrediente')
  if (select) select.dispatchEvent(new win.Event('change'))
})

    // O botão confirmar deve estar habilitado após seleção de ingrediente
    // (o sistema habilita quando há ingrediente selecionado)
    cy.get('#custo-tbody').should('not.contain', 'Selecione um ingrediente')
  })

  it('deve navegar entre todas as abas corretamente', () => {
    const paginas = [
      { btn: 'dashboard', page: '#page-dashboard', titulo: 'Dashboard' },
      { btn: 'estoque',   page: '#page-estoque',   titulo: 'Estoque' },
      { btn: 'evolucao',  page: '#page-evolucao',  titulo: 'Evolução' },
      { btn: 'balanca',   page: '#page-balanca',   titulo: 'Balança' },
    ]

    paginas.forEach(({ btn, page }) => {
      cy.get(`[onclick="showPage('${btn}',this)"]`).click()
      cy.get(page).should('have.class', 'active')

      // As outras páginas devem estar ocultas
      paginas
        .filter(p => p.btn !== btn)
        .forEach(outro => {
          cy.get(outro.page).should('not.have.class', 'active')
        })
    })
  })

  it('deve abrir o modal de inserção manual ao clicar no botão azul', () => {
    cy.get('[onclick="abrirInsercaoManual()"]').click()
    cy.get('#modal-manual-overlay').should('have.class', 'open')
    cy.get('#modal-manual-overlay .modal-title').should('contain', 'Inserção Manual')
    // Fecha
    cy.get('[onclick="fecharInsercaoManual()"]').click()
    cy.get('#modal-manual-overlay').should('not.have.class', 'open')
  })

})
