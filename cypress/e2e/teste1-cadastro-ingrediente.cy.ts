/// <reference types="cypress" />
// ============================================================
// CASE 1 — Cadastro de Ingrediente
// Objetivo: verificar que o sistema cadastra um ingrediente
// corretamente e exibe no estoque
// ============================================================

describe('Case 1 — Cadastro de Ingrediente', () => {

  beforeEach(() => {
    // Acessa o sistema antes de cada teste
    cy.visit('/')
    // Navega para a aba Estoque
    cy.get('[onclick="showPage(\'estoque\',this)"]').click()
    cy.get('#page-estoque').should('have.class', 'active')
  })

  it('deve abrir o modal ao clicar em Novo Ingrediente', () => {
    cy.get('[onclick="abrirModal()"]').click()
    cy.get('#modal-overlay').should('have.class', 'open')
    cy.get('.modal-title').should('contain', 'Novo Ingrediente')
  })

  it('deve cadastrar um ingrediente e exibir no estoque', () => {
    cy.get('[onclick="abrirModal()"]').click()

    // Preenche o formulário
    cy.get('#f-nome').type('Farinha de Trigo')
    cy.get('#f-unidade').select('kg')
    cy.get('#f-preco').type('4.90')
    cy.get('#f-qtd').type('10')

    // Define validade 1 ano à frente
    const validade = new Date()
    validade.setFullYear(validade.getFullYear() + 1)
    const validadeStr = validade.toISOString().split('T')[0]
    cy.get('#f-validade').type(validadeStr)

    // Salva
    cy.get('[onclick="salvarIngrediente()"]').click()

    // Verifica que o modal fechou
    cy.get('#modal-overlay').should('not.have.class', 'open')

    // Verifica que o ingrediente aparece no grid
    cy.get('#stock-grid').should('contain', 'Farinha de Trigo')

    // Verifica que o toast de confirmação apareceu
    cy.get('#toast').should('have.class', 'show')
  })

  it('deve fechar o modal ao clicar em Cancelar', () => {
    cy.get('[onclick="abrirModal()"]').click()
    cy.get('#modal-overlay').should('have.class', 'open')
    cy.get('[onclick="fecharModal()"]').click()
    cy.get('#modal-overlay').should('not.have.class', 'open')
  })

  it('deve manter o contador de itens atualizado após cadastro', () => {
  cy.get('[onclick="abrirModal()"]').click()
  cy.get('#f-nome').type('Azeite Extra Virgem')
  cy.get('#f-unidade').select('L')
  cy.get('#f-preco').type('28.50')
  cy.get('#f-qtd').type('5')
  cy.get('[onclick="salvarIngrediente()"]').click()

  cy.get('#estoque-count').should('not.be.empty')
  cy.get('#stock-grid .stock-card').should('have.length.greaterThan', 0)
})

// ============================================================
// CASE 2 — Balança e Cálculo de Custo
// Objetivo: verificar que a balança exibe o peso corretamente
// e calcula o custo com base no ingrediente selecionado
// ============================================================ 
})
