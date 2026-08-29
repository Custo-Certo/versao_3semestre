/// <reference types="cypress" />

// ============================================================
// CASE 3 — Cadastro em Massa (30 Ingredientes)
// Lógica:
//   - Se o ingrediente JÁ EXISTE no estoque → Nova Compra
//   - Se NÃO EXISTE → Cadastra como novo
//   - 10 produtos com data retroativa (vencidos)
// ============================================================

const proximoAno = () => {
  const d = new Date()
  d.setFullYear(d.getFullYear() + 1)
  return d.toISOString().split('T')[0]
}

const dataPassada = (diasAtras: number) => {
  const d = new Date()
  d.setDate(d.getDate() - diasAtras)
  return d.toISOString().split('T')[0]
}

const ingredientes = [
  // ✅ Ingredientes com validade normal
  { nome: 'Farinha de Trigo',    unidade: 'kg', preco: '4.90',  qtd: '25', validade: proximoAno()      },
  { nome: 'Açúcar Refinado',     unidade: 'kg', preco: '3.50',  qtd: '20', validade: proximoAno()      },
  { nome: 'Azeite Extra Virgem', unidade: 'L',  preco: '28.50', qtd: '10', validade: proximoAno()      },
  { nome: 'Manteiga',            unidade: 'kg', preco: '32.00', qtd: '8',  validade: proximoAno()      },
  { nome: 'Leite Integral',      unidade: 'L',  preco: '5.20',  qtd: '30', validade: proximoAno()      },
  { nome: 'Creme de Leite',      unidade: 'L',  preco: '8.90',  qtd: '12', validade: proximoAno()      },
  { nome: 'Queijo Parmesão',     unidade: 'kg', preco: '65.00', qtd: '5',  validade: proximoAno()      },
  { nome: 'Queijo Mussarela',    unidade: 'kg', preco: '32.00', qtd: '8',  validade: proximoAno()      },
  { nome: 'Frango Peito',        unidade: 'kg', preco: '18.00', qtd: '15', validade: proximoAno()      },
  { nome: 'Salmão Filé',         unidade: 'kg', preco: '89.00', qtd: '4',  validade: proximoAno()      },
  { nome: 'Tomate',              unidade: 'kg', preco: '6.50',  qtd: '10', validade: proximoAno()      },
  { nome: 'Cebola',              unidade: 'kg', preco: '4.20',  qtd: '12', validade: proximoAno()      },
  { nome: 'Alho',                unidade: 'kg', preco: '18.00', qtd: '2',  validade: proximoAno()      },
  { nome: 'Azeite de Dendê',     unidade: 'L',  preco: '22.00', qtd: '5',  validade: proximoAno()      },
  { nome: 'Vinagre de Maçã',     unidade: 'L',  preco: '9.80',  qtd: '6',  validade: proximoAno()      },
  { nome: 'Canela em Pó',        unidade: 'g',  preco: '0.15',  qtd: '100',validade: proximoAno()      },
  { nome: 'Pimenta do Reino',    unidade: 'g',  preco: '0.12',  qtd: '150',validade: proximoAno()      },
  { nome: 'Orégano Seco',        unidade: 'g',  preco: '0.10',  qtd: '100',validade: proximoAno()      },
  { nome: 'Água Mineral',        unidade: 'L',  preco: '2.50',  qtd: '20', validade: proximoAno()      },
  { nome: 'Camarão Limpo',       unidade: 'kg', preco: '75.00', qtd: '3',  validade: proximoAno()      },

  // ⚠️ Ingredientes VENCIDOS (data retroativa)
  { nome: 'Ovos Caipira',        unidade: 'un', preco: '0.90',  qtd: '60', validade: dataPassada(30)   },
  { nome: 'Ovos Brancos',        unidade: 'un', preco: '0.70',  qtd: '120',validade: dataPassada(15)   },
  { nome: 'Pão Francês',         unidade: 'un', preco: '0.80',  qtd: '50', validade: dataPassada(5)    },
  { nome: 'Carne Moída',         unidade: 'kg', preco: '35.00', qtd: '10', validade: dataPassada(10)   },
  { nome: 'Limão Tahiti',        unidade: 'un', preco: '0.50',  qtd: '40', validade: dataPassada(45)   },
  { nome: 'Banana Prata',        unidade: 'un', preco: '0.60',  qtd: '30', validade: dataPassada(7)    },
  { nome: 'Abacate',             unidade: 'un', preco: '3.50',  qtd: '10', validade: dataPassada(3)    },
  { nome: 'Molho de Soja',       unidade: 'ml', preco: '0.02',  qtd: '500',validade: dataPassada(60)   },
  { nome: 'Fermento Biológico',  unidade: 'g',  preco: '0.08',  qtd: '200',validade: dataPassada(20)   },
  { nome: 'Sal Grosso',          unidade: 'kg', preco: '1.80',  qtd: '15', validade: dataPassada(90)   },
]

describe('Case 3 — Cadastro em Massa (30 Ingredientes)', () => {

  beforeEach(() => {
    cy.intercept('GET', '/ingredientes*').as('getIngredientes')
    cy.visit('/')
    cy.get('[onclick="showPage(\'estoque\',this)"]').click()
    cy.wait('@getIngredientes')
    cy.get('#stock-grid .stock-card').should('have.length.greaterThan', 0)
  })

  ingredientes.forEach((ing, index) => {
    const vencido = new Date(ing.validade) < new Date()
    const label = vencido ? '⚠️ VENCIDO' : '✅ válido'

    it(`[${index + 1}/30] "${ing.nome}" (${ing.unidade}) — ${label}`, () => {

      cy.get('#stock-grid').then(($grid) => {
        const existe = $grid.find(`.stock-name:contains("${ing.nome}")`).length > 0

        if (existe) {
          // Ingrediente existe → Nova Compra
          cy.contains('.stock-name', ing.nome)
            .closest('.stock-card')
            .find('.btn-compra')
            .click()

          cy.get('#modal-compra-overlay').should('have.class', 'open')
          cy.get('#mc-qtd').clear().type(ing.qtd)
          cy.get('#mc-preco').clear().type(ing.preco)
          cy.get('#mc-validade').type(ing.validade)
          cy.get('[onclick="confirmarNovaCompra()"]').click()
          cy.get('#modal-compra-overlay').should('not.have.class', 'open')

        } else {
          // Ingrediente não existe → Cadastro novo
          cy.get('[onclick="abrirModal()"]').click()
          cy.get('#modal-overlay').should('have.class', 'open')
          cy.get('#f-nome').clear().type(ing.nome)
          cy.get('#f-unidade').select(ing.unidade)
          cy.get('#f-preco').clear().type(ing.preco)
          cy.get('#f-qtd').clear().type(ing.qtd)
          cy.get('#f-validade').type(ing.validade)
          cy.get('[onclick="salvarIngrediente()"]').click()
          cy.get('#modal-overlay').should('not.have.class', 'open')
        }

        cy.get('#stock-grid').should('contain', ing.nome)
      })
    })
  })

  it('[31/31] deve exibir alerta de vencimento para os produtos vencidos', () => {
    cy.get('#alert-vencimento').should('have.class', 'visible')
    cy.get('#alert-lista').should('not.be.empty')
  })

})