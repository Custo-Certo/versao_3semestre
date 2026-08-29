/// <reference types="cypress" />

// ============================================================
// CASE 4 — Remoção de Ingredientes Vencidos
// ============================================================

describe('Case 4 — Remoção de Ingredientes Vencidos', () => {

  beforeEach(() => {
    cy.intercept('GET', '/ingredientes*').as('getIngredientes')
    cy.intercept('DELETE', '/ingredientes/*').as('deleteIngrediente')
    cy.visit('/')
    cy.get('[onclick="showPage(\'estoque\',this)"]').click()
    cy.wait('@getIngredientes')
    // Espera o grid renderizar de verdade
    cy.get('#stock-grid').children().should('have.length.greaterThan', 0)
  })

  it('deve identificar ingredientes vencidos pelo alerta', () => {
    cy.get('#alert-vencimento').then(($alert) => {
      if ($alert.hasClass('visible')) {
        cy.get('#alert-lista').should('not.be.empty')
        cy.get('.stock-card.expiry-critical').should('have.length.greaterThan', 0)
        cy.log('⚠️ Ingredientes vencidos encontrados')
      } else {
        cy.log('✅ Nenhum ingrediente vencido no estoque')
      }
    })
  })

  it('deve remover todos os ingredientes com lotes vencidos', () => {
    cy.get('#alert-vencimento').then(($alert) => {
      if (!$alert.hasClass('visible')) {
        cy.log('✅ Nenhum vencido para remover')
        return
      }

      // Conta quantos cards vencidos existem
      cy.get('.stock-card.expiry-critical').then(($cards) => {
        const total = $cards.length
        cy.log(`🗑️ Removendo ${total} ingrediente(s) vencido(s)...`)

        // Remove um por um esperando a API confirmar cada remoção
        Cypress._.times(total, () => {
          cy.get('.stock-card.expiry-critical').first().then(($card) => {
            const nome = $card.find('.stock-name').text().trim()
            cy.log(`🗑️ Removendo: ${nome}`)
            cy.wrap($card).find('.btn-del-stock').click({ force: true })
            cy.wait('@deleteIngrediente')
            cy.wrap($card).should('not.exist')
          })
        })
      })
    })
  })

  it('deve verificar que nenhum ingrediente vencido permanece', () => {
    cy.get('#alert-vencimento').should('not.have.class', 'visible')
    cy.get('.stock-card.expiry-critical').should('have.length', 0)
    cy.log('✅ Estoque limpo — sem ingredientes vencidos')
  })

})