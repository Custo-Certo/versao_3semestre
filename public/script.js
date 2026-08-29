/**
 * Custo Certo — Frontend v3
 * Persistência via API. Suporte a lotes com FIFO, alertas de vencimento e retirada manual.
 */

const API_BASE = '';

// ======= LOGO =======
document.getElementById('sidebar-logo').src =
  'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64"><circle cx="32" cy="32" r="28" fill="%2300a86b"/><text x="32" y="40" font-size="24" text-anchor="middle" fill="white" font-family="Arial" font-weight="bold">CC</text></svg>';

// ======= ESTADO =======
let estoque = [];
let historico = [];
let pesoAtual = 0;
let sseBalanca = null;
let chartComp, chartStatus, chartCMV, chartEvolucao;
let evolucaoFiltro = 'todos';
let compraIdAtual = null;
let retiradaIdAtual = null;

// ======= API =======
async function api(path, options = {}) {
  const res = await fetch(`${API_BASE}${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });
  if (!res.ok) {
    let msg = `Erro ${res.status}`;
    try { const d = await res.json(); msg = d.erro || msg; } catch (_) {}
    throw new Error(msg);
  }
  if (res.status === 204) return null;
  return res.json();
}

// ======= CARREGAMENTO =======
async function carregarDados() {
  try {
    const [ings, hist] = await Promise.all([
      api('/ingredientes'),
      api('/ingredientes/historico'),
    ]);
    estoque = ings;
    historico = hist;
  } catch (err) {
    console.error('Falha ao carregar dados:', err);
    showToast('Erro ao conectar ao servidor: ' + err.message, true);
  }
}

// ======= RELÓGIO =======
function updateClock() {
  document.getElementById('clock').textContent = new Date().toLocaleTimeString('pt-BR', {
    hour: '2-digit', minute: '2-digit',
  });
}
setInterval(updateClock, 1000);
updateClock();

// ======= NAVEGAÇÃO =======
const pageTitles = {
  dashboard: 'Dashboard',
  balanca: 'Balança Inteligente',
  estoque: 'Estoque',
  evolucao: 'Evolução de Preços',
};

async function showPage(page, btn) {
  document.querySelectorAll('.page').forEach(p => p.classList.remove('active'));
  document.querySelectorAll('.nav-btn').forEach(b => b.classList.remove('active'));
  document.getElementById('page-' + page).classList.add('active');
  btn.classList.add('active');
  document.getElementById('topbar-title').textContent = pageTitles[page];
  await carregarDados();
  if (page === 'balanca')   popularSelect();
  if (page === 'estoque')   renderEstoque();
  if (page === 'dashboard') renderDashboard();
  if (page === 'evolucao')  renderEvolucao();
}
window.showPage = showPage;

// ======= UTILITÁRIOS =======
function safeDestroy(chart) { try { if (chart) chart.destroy(); } catch (e) {} }

const COLORS = ['#00a86b','#3a86ff','#f4a435','#e63946','#7b5ea7','#2ec4b6','#ff6b6b'];

function fmtQtd(v, u) {
  return u === 'g' || u === 'ml' ? Math.round(v).toString() : parseFloat(v.toFixed(3)).toString();
}

function formatDate(d) {
  if (!d) return '—';
  const [y, m, dd] = d.split('-');
  return `${dd}/${m}/${y}`;
}

/**
 * Retorna info de vencimento de uma data ISO.
 * diasAviso: quantos dias antes de vencer para considerar "aviso"
 */
function getExpiryInfo(d, diasAviso = 5) {
  if (!d) return { cls: 'expiry-ok', label: 'sem validade', diff: Infinity, nivel: 'ok' };
  const diff = Math.ceil((new Date(d) - new Date()) / 86400000);
  if (diff < 0)           return { cls: 'expiry-bad',  label: 'VENCIDO',            diff, nivel: 'critico' };
  if (diff <= diasAviso)  return { cls: 'expiry-warn', label: `vence em ${diff}d`,  diff, nivel: 'aviso'   };
                          return { cls: 'expiry-ok',   label: `${diff}d p/ vencer`, diff, nivel: 'ok'      };
}

// Ordena lotes do mais antigo para o mais novo (FIFO visual)
function ordenarLotes(lotes) {
  if (!lotes || !lotes.length) return [];
  return [...lotes].sort((a, b) => {
    const da = a.dataEntrada || a.validade || '';
    const db = b.dataEntrada || b.validade || '';
    return da.localeCompare(db);
  });
}

// ======= ALERTA GLOBAL DE VENCIMENTO =======
function renderAlertaBanner() {
  const banner = document.getElementById('alert-vencimento');
  const lista  = document.getElementById('alert-lista');
  if (!banner || !lista) return;

  // Coleta lotes críticos de todos os ingredientes
  const criticos = [];
  estoque.forEach(ing => {
    const lotes = ordenarLotes(ing.lotes);
    lotes.forEach(lote => {
      const exp = getExpiryInfo(lote.validade);
      if (exp.nivel === 'critico' || exp.nivel === 'aviso') {
        criticos.push({
          nome: ing.nome,
          unidade: ing.unidade,
          qtd: lote.quantidade,
          validade: lote.validade,
          exp,
        });
      }
    });
    // Fallback: sem lotes, usa validade do próprio ingrediente
    if (!lotes.length && ing.validade) {
      const exp = getExpiryInfo(ing.validade);
      if (exp.nivel === 'critico' || exp.nivel === 'aviso') {
        criticos.push({ nome: ing.nome, unidade: ing.unidade, qtd: ing.qtd, validade: ing.validade, exp });
      }
    }
  });

  if (!criticos.length) {
    banner.classList.remove('visible');
    return;
  }

  // Ordena: vencidos primeiro, depois por proximidade
  criticos.sort((a, b) => a.exp.diff - b.exp.diff);

  lista.innerHTML = criticos.map(c =>
    `<div>• <strong>${c.nome}</strong> — ${fmtQtd(c.qtd, c.unidade)} ${c.unidade} — ${c.exp.label} (${formatDate(c.validade)})</div>`
  ).join('');

  banner.classList.add('visible');
}

// ======= ESTOQUE =======
function renderEstoque() {
  const countEl = document.getElementById('estoque-count');
  if (countEl) countEl.textContent = estoque.length + ' ingrediente(s) cadastrado(s)';

  renderAlertaBanner();

  const grid = document.getElementById('stock-grid');
  if (!estoque.length) {
    grid.innerHTML = `<div style="color:var(--muted);padding:40px;text-align:center;grid-column:1/-1">Nenhum ingrediente cadastrado.</div>`;
    return;
  }

  // Ordena cards: críticos primeiro, depois por validade mais próxima
  const sorted = [...estoque].sort((a, b) => {
    const lotesA = ordenarLotes(a.lotes);
    const lotesB = ordenarLotes(b.lotes);
    const valA = lotesA.length ? lotesA[0].validade : a.validade;
    const valB = lotesB.length ? lotesB[0].validade : b.validade;
    if (!valA && !valB) return 0;
    if (!valA) return 1;
    if (!valB) return -1;
    return valA.localeCompare(valB);
  });

  grid.innerHTML = sorted.map(item => renderStockCard(item)).join('');
}

function renderStockCard(item) {
  const lotes = ordenarLotes(item.lotes);

  // Determina nível de alerta geral do card
  let cardClass = '';
  if (lotes.length) {
    const piorLote = lotes[0]; // mais antigo = mais urgente
    const exp = getExpiryInfo(piorLote.validade);
    if (exp.nivel === 'critico') cardClass = 'expiry-critical';
    else if (exp.nivel === 'aviso') cardClass = 'expiry-warning';
  } else if (item.validade) {
    const exp = getExpiryInfo(item.validade);
    if (exp.nivel === 'critico') cardClass = 'expiry-critical';
    else if (exp.nivel === 'aviso') cardClass = 'expiry-warning';
  }

  const pct = item.qtdMax > 0 ? Math.max(0, Math.min(100, (item.qtd / item.qtdMax) * 100)) : 0;
  const barCls = pct > 50 ? 'bar-ok' : pct > 25 ? 'bar-mid' : 'bar-low';

  // Seção de lotes
  let lotesHtml = '';
  if (lotes.length) {
    const linhas = lotes.map((lote, idx) => {
      const exp = getExpiryInfo(lote.validade);
      const isFirst = idx === 0;
      let rowCls = '';
      let valCls = 'val-ok';
      let badge = '';
      if (exp.nivel === 'critico') { rowCls = 'lote-critico'; valCls = 'val-crit'; badge = `<span class="badge-vence">${exp.label}</span>`; }
      else if (exp.nivel === 'aviso') { rowCls = 'lote-aviso'; valCls = 'val-warn'; badge = `<span class="badge-aviso">${exp.label}</span>`; }

      return `
        <div class="lote-row ${rowCls}">
          <div class="lote-info">
            <div class="lote-qty">${fmtQtd(lote.quantidade, item.unidade)} ${item.unidade}</div>
            <div class="lote-data">Entrada: ${formatDate(lote.dataEntrada || lote.data)} · Val: ${formatDate(lote.validade)}</div>
          </div>
          <div style="display:flex;flex-direction:column;align-items:flex-end;gap:4px">
            ${isFirst ? '<span class="badge-usar">Usar primeiro</span>' : ''}
            ${badge}
            <span class="lote-val ${valCls}">${exp.nivel === 'ok' ? exp.label : ''}</span>
          </div>
        </div>`;
    }).join('');

    lotesHtml = `
      <div class="lotes-section">
        <div class="lotes-title"><i class="fas fa-layer-group" style="margin-right:4px"></i>Lotes em estoque</div>
        ${linhas}
      </div>`;
  } else {
    // Sem lotes: exibe validade simples
    const exp = getExpiryInfo(item.validade);
    lotesHtml = `
      <div class="stock-expiry ${exp.cls}" style="margin-top:12px;padding-top:12px;border-top:1px solid var(--border)">
        <i class="fas fa-calendar-alt"></i> Validade: ${formatDate(item.validade)} · ${exp.label}
      </div>`;
  }

  return `
    <div class="stock-card ${cardClass}">
      <div class="sc-header">
        <div class="sc-header-left">
          <div class="stock-name">${item.nome}</div>
          <div class="stock-unit-badge">${item.unidade}</div>
        </div>
        <div class="sc-header-right">
          <button class="btn-del-stock" onclick="deletarItem(${item.id})" title="Remover ingrediente">
            <i class="fas fa-trash-alt"></i>
          </button>
        </div>
      </div>

      <div class="stock-qty">${fmtQtd(item.qtd, item.unidade)} <span>${item.unidade}</span></div>
      <div class="stock-meta">
        <span>R$ ${item.preco.toFixed(2)}/${item.unidade}</span>
        <span>${pct.toFixed(0)}% do estoque</span>
      </div>
      <div class="stock-bar-bg"><div class="stock-bar-fill ${barCls}" style="width:${pct}%"></div></div>
      <div class="stock-bar-label"><span>0</span><span>${fmtQtd(item.qtdMax, item.unidade)} ${item.unidade} máx.</span></div>

      ${lotesHtml}

      <div class="btns-card">
        <button class="btn-retirada" onclick="abrirModalRetirada(${item.id})">
          <i class="fas fa-minus-circle"></i> Retirar
        </button>
        <button class="btn-compra" onclick="abrirModalCompra(${item.id})">
          <i class="fas fa-cart-plus"></i> Nova Compra
        </button>
      </div>
    </div>`;
}

// ======= DELETAR INGREDIENTE =======
async function deletarItem(id) {
  if (!confirm('Tem certeza que deseja remover este ingrediente?')) return;
  try {
    await api(`/ingredientes/${id}`, { method: 'DELETE' });
    showToast('Ingrediente removido.');
    await carregarDados();
    renderEstoque();
    popularSelect();
  } catch (err) {
    showToast('Erro: ' + err.message, true);
  }
}
window.deletarItem = deletarItem;

// ======= MODAL CADASTRO =======
function abrirModal() {
  document.getElementById('modal-overlay').classList.add('open');
  document.getElementById('f-nome').focus();
}
window.abrirModal = abrirModal;

function fecharModal() {
  document.getElementById('modal-overlay').classList.remove('open');
  ['f-nome','f-preco','f-qtd','f-validade'].forEach(id => document.getElementById(id).value = '');
  document.getElementById('f-unidade').value = 'kg';
}
window.fecharModal = fecharModal;

async function salvarIngrediente() {
  const nome    = document.getElementById('f-nome').value.trim();
  const unidade = document.getElementById('f-unidade').value;
  const preco   = parseFloat(document.getElementById('f-preco').value);
  const qtd     = parseFloat(document.getElementById('f-qtd').value);
  const validade = document.getElementById('f-validade').value || null;
  if (!nome || isNaN(preco) || isNaN(qtd)) { showToast('Preencha todos os campos!', true); return; }
  try {
    await api('/ingredientes', {
      method: 'POST',
      body: JSON.stringify({ nome, unidade, preco, qtd, qtdMax: qtd, validade }),
    });
    showToast(`"${nome}" adicionado ao estoque!`);
    fecharModal();
    await carregarDados();
    renderEstoque();
    popularSelect();
  } catch (err) {
    showToast('Erro: ' + err.message, true);
  }
}
window.salvarIngrediente = salvarIngrediente;

// ======= MODAL NOVA COMPRA =======
function abrirModalCompra(id) {
  const ing = estoque.find(i => i.id === id);
  if (!ing) return;
  compraIdAtual = id;
  document.getElementById('mc-nome-titulo').textContent = ing.nome;
  document.getElementById('mc-unidade-label').textContent = ing.unidade;
  document.getElementById('mc-estoque-atual').textContent =
    `Estoque atual: ${fmtQtd(ing.qtd, ing.unidade)} ${ing.unidade}  ·  Último preço: R$ ${ing.preco.toFixed(2)}/${ing.unidade}`;
  document.getElementById('mc-preco').value = ing.preco.toFixed(2);
  document.getElementById('mc-qtd').value = '';
  document.getElementById('mc-validade').value = '';   // validade em branco: nova compra = novo lote
  document.getElementById('modal-compra-overlay').classList.add('open');
  document.getElementById('mc-qtd').focus();
}
window.abrirModalCompra = abrirModalCompra;

function fecharModalCompra() {
  document.getElementById('modal-compra-overlay').classList.remove('open');
  compraIdAtual = null;
}
window.fecharModalCompra = fecharModalCompra;

async function confirmarNovaCompra() {
  if (!compraIdAtual) return;
  const ing = estoque.find(i => i.id === compraIdAtual);
  if (!ing) return;
  const qtdNova  = parseFloat(document.getElementById('mc-qtd').value);
  const precoNovo = parseFloat(document.getElementById('mc-preco').value);
  const validadeNova = document.getElementById('mc-validade').value || null;
  if (isNaN(qtdNova) || qtdNova <= 0 || isNaN(precoNovo) || precoNovo <= 0) {
    showToast('Preencha quantidade e custo corretamente!', true); return;
  }
  try {
    await api(`/ingredientes/${compraIdAtual}/compras`, {
      method: 'POST',
      body: JSON.stringify({ quantidade: qtdNova, precoUnitario: precoNovo, validade: validadeNova }),
    });
    showToast(`✓ +${fmtQtd(qtdNova, ing.unidade)} ${ing.unidade} adicionados como novo lote em "${ing.nome}"`);
    fecharModalCompra();
    await carregarDados();
    renderEstoque();
    popularSelect();
  } catch (err) {
    showToast('Erro: ' + err.message, true);
  }
}
window.confirmarNovaCompra = confirmarNovaCompra;

// ======= MODAL RETIRADA MANUAL =======
function abrirModalRetirada(id) {
  const ing = estoque.find(i => i.id === id);
  if (!ing) return;
  retiradaIdAtual = id;
  document.getElementById('mr-nome-titulo').textContent = ing.nome;
  document.getElementById('mr-unidade-label').textContent = ing.unidade;

  const lotes = ordenarLotes(ing.lotes);
  let infoTxt = `Estoque total: ${fmtQtd(ing.qtd, ing.unidade)} ${ing.unidade}`;
  if (lotes.length) {
    const l = lotes[0];
    infoTxt += ` · Lote mais antigo: ${fmtQtd(l.quantidade, ing.unidade)} ${ing.unidade} (val. ${formatDate(l.validade)})`;
  }
  document.getElementById('mr-info').textContent = infoTxt;
  document.getElementById('mr-qtd').value = '';
  document.getElementById('modal-retirada-overlay').classList.add('open');
  document.getElementById('mr-qtd').focus();
}
window.abrirModalRetirada = abrirModalRetirada;

function fecharModalRetirada() {
  document.getElementById('modal-retirada-overlay').classList.remove('open');
  retiradaIdAtual = null;
}
window.fecharModalRetirada = fecharModalRetirada;

async function confirmarRetirada() {
  if (!retiradaIdAtual) return;
  const ing = estoque.find(i => i.id === retiradaIdAtual);
  if (!ing) return;
  const qtd = parseFloat(document.getElementById('mr-qtd').value);
  if (isNaN(qtd) || qtd <= 0) { showToast('Informe uma quantidade válida!', true); return; }
  if (qtd > ing.qtd) { showToast('Quantidade maior que o estoque disponível!', true); return; }
  try {
    await api(`/ingredientes/${retiradaIdAtual}/retirada`, {
      method: 'POST',
      body: JSON.stringify({ quantidade: qtd }),
    });
    showToast(`✓ ${fmtQtd(qtd, ing.unidade)} ${ing.unidade} retirados de "${ing.nome}" (lote mais antigo)`);
    fecharModalRetirada();
    await carregarDados();
    renderEstoque();
    popularSelect();
  } catch (err) {
    showToast('Erro: ' + err.message, true);
  }
}
window.confirmarRetirada = confirmarRetirada;

// ======= BALANÇA (SSE) =======
function iniciarLeituraPeso() {
  if (sseBalanca) return;
  sseBalanca = new EventSource('/balanca/stream');
  sseBalanca.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data);
      if (typeof data.peso === 'number') { pesoAtual = data.peso; atualizarDisplay(); }
    } catch (err) { console.error('Erro SSE:', err); }
  };
  sseBalanca.onerror = () => console.warn('SSE reconectando…');
}

function getIngredienteSelecionado() {
  const id = Number(document.getElementById('select-ingrediente').value);
  return estoque.find(i => i.id === id) || null;
}

function atualizarDisplay() {
  const ing = getIngredienteSelecionado();
  let peso = Math.max(0, pesoAtual);
  let valor = peso.toFixed(3);
  let unidade = 'kg';
  if (ing) {
    if (ing.unidade === 'g')  { valor = (peso * 1000).toFixed(1); unidade = 'g'; }
    else if (ing.unidade === 'ml') { valor = (peso * 1000).toFixed(0); unidade = 'ml'; }
    else if (ing.unidade === 'un') { valor = Math.round(peso * 10); unidade = 'un'; }
    else unidade = ing.unidade;
  }
  document.getElementById('peso-display').textContent = valor;
  document.getElementById('unidade-display').textContent = unidade;
  document.getElementById('btn-confirmar').disabled = !(ing && peso > 0.001);
  if (ing) atualizarTabelaCusto(ing, peso);
}

function atualizarTabelaCusto(ing, peso) {
  const tbody = document.getElementById('custo-tbody');
  if (!tbody) return;
  let consumo = peso;
  if (ing.unidade === 'g' || ing.unidade === 'ml') consumo = peso * 1000;
  const custo = consumo * ing.preco;
  const pesoG = peso * 1000;

  let rows = '';
  if (ing.unidade === 'kg' || ing.unidade === 'g') {
    rows = `
      <tr><td>Por grama</td><td>${pesoG.toFixed(1)} g</td><td class="val-col">R$ ${(ing.preco/1000*pesoG).toFixed(4)}</td></tr>
      <tr><td>Por 100g</td><td>${(pesoG/100).toFixed(2)}×</td><td class="val-col">R$ ${(ing.preco/10).toFixed(3)}</td></tr>
      <tr class="highlight-row"><td><b>Porção pesada</b></td><td><b>${ing.unidade==='g'?pesoG.toFixed(1)+' g':peso.toFixed(3)+' kg'}</b></td><td class="val-col"><b>R$ ${custo.toFixed(2)}</b></td></tr>`;
  } else if (ing.unidade === 'L' || ing.unidade === 'ml') {
    const ml = peso * 1000;
    rows = `
      <tr><td>Por ml</td><td>${ml.toFixed(0)} ml</td><td class="val-col">R$ ${(ing.preco/1000*ml).toFixed(4)}</td></tr>
      <tr class="highlight-row"><td><b>Porção pesada</b></td><td><b>${ing.unidade==='ml'?ml.toFixed(0)+' ml':peso.toFixed(3)+' L'}</b></td><td class="val-col"><b>R$ ${custo.toFixed(2)}</b></td></tr>`;
  } else {
    const un = Math.round(peso * 10);
    rows = `<tr class="highlight-row"><td><b>Porção pesada</b></td><td><b>${un} unidades</b></td><td class="val-col"><b>R$ ${custo.toFixed(2)}</b></td></tr>`;
  }
  rows += `<tr><td colspan="3" style="font-size:11px;color:var(--muted);padding-top:4px">Base: R$ ${ing.preco.toFixed(2)}/${ing.unidade}</td></tr>`;
  tbody.innerHTML = rows;
}

function popularSelect() {
  iniciarLeituraPeso();
  const sel = document.getElementById('select-ingrediente');
  if (!sel) return;
  const atual = sel.value;
  sel.innerHTML = '<option value="">— Selecione —</option>';
  // Exclui ingredientes com unidade 'un'
  estoque
    .filter(item => item.unidade !== 'un')
    .forEach(item => {
      const opt = document.createElement('option');
      opt.value = item.id;
      opt.textContent = `${item.nome} (${item.unidade})`;
      sel.appendChild(opt);
    });
  if (atual) sel.value = atual;
  atualizarDisplay();
}

function abrirInsercaoManual() {
  const sel = document.getElementById('sel-manual');
  sel.innerHTML = '<option value="">— Selecione —</option>';
  // Apenas ingredientes 'un'
  estoque
    .filter(item => item.unidade === 'un')
    .forEach(item => {
      const opt = document.createElement('option');
      opt.value = item.id;
      opt.textContent = item.nome;
      sel.appendChild(opt);
    });
  document.getElementById('manual-qtd').value = '';
  document.getElementById('modal-manual-overlay').classList.add('open');
  sel.focus();
}
window.abrirInsercaoManual = abrirInsercaoManual;

function fecharInsercaoManual() {
  document.getElementById('modal-manual-overlay').classList.remove('open');
}
window.fecharInsercaoManual = fecharInsercaoManual;

async function confirmarInsercaoManual() {
  const id = parseInt(document.getElementById('sel-manual').value);
  const qtd = parseFloat(document.getElementById('manual-qtd').value);
  const ing = estoque.find(i => i.id === id);
  if (!ing) { showToast('Selecione um ingrediente!', true); return; }
  if (isNaN(qtd) || qtd <= 0) { showToast('Informe uma quantidade válida!', true); return; }
  if (qtd > ing.qtd) { showToast('Estoque insuficiente!', true); return; }
  try {
    await api(`/ingredientes/${id}/retirada`, {
      method: 'POST',
      body: JSON.stringify({ quantidade: qtd }),
    });
    showToast(`✓ ${qtd} un de "${ing.nome}" registradas`);
    fecharInsercaoManual();
    await carregarDados();
    popularSelect();
  } catch (err) {
    showToast('Erro: ' + err.message, true);
  }
}
window.confirmarInsercaoManual = confirmarInsercaoManual;

function onIngredientChange() { atualizarDisplay(); }
window.onIngredientChange = onIngredientChange;

async function tararBalanca() {
  try {
    await api('/balanca/tara', { method: 'POST' });
    showToast('Balança tarada');
  } catch (err) {
    showToast('Erro ao tarar: ' + err.message, true);
  }
}
window.tararBalanca = tararBalanca;

async function confirmarPeso() {
  const ing = getIngredienteSelecionado();
  if (!ing || pesoAtual <= 0.001) return;
  let consumo = pesoAtual;
  if (ing.unidade === 'g' || ing.unidade === 'ml') consumo = pesoAtual * 1000;
  if (consumo > ing.qtd) { showToast('⚠ Estoque insuficiente!', true); return; }
  try {
    const data = await api('/balanca/confirmar', {
      method: 'POST',
      body: JSON.stringify({ ingredienteId: ing.id, quantidadeConsumida: consumo }),
    });
    if (data && !data.ok) { showToast(data.erro || 'Erro na confirmação', true); return; }
    showToast(`✓ ${consumo.toFixed(3)} ${ing.unidade} abatidos (FIFO)`);
    await carregarDados();
    popularSelect();
  } catch (err) {
    showToast(err.message, true);
  }
}
window.confirmarPeso = confirmarPeso;

// ======= DASHBOARD =======
function renderDashboard() {
  const totalVal = estoque.reduce((s, i) => s + i.preco * i.qtd, 0);
  const alertas  = estoque.filter(i => i.qtdMax > 0 && i.qtd / i.qtdMax < 0.25).length;
  const vencendo = estoque.filter(i => {
    if (!i.validade) return false;
    const diff = Math.ceil((new Date(i.validade) - new Date()) / 86400000);
    return diff >= 0 && diff <= 5;
  }).length;

  document.getElementById('kpi-estoque').textContent =
    'R$ ' + totalVal.toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  document.getElementById('kpi-itens').textContent = estoque.length + ' itens cadastrados';
  document.getElementById('kpi-alerta').textContent = alertas;
  document.getElementById('kpi-alerta-sub').textContent =
    alertas > 0 ? alertas + ' abaixo de 25% do estoque' : 'Todos dentro do limite';
  document.getElementById('kpi-vencendo').textContent = vencendo;

  const receitaEst = totalVal * 3.5;
  const cmvPct = receitaEst > 0 ? (totalVal / receitaEst) * 100 : 0;
  const cmvClass = cmvPct <= 31 ? 'ok' : cmvPct <= 35 ? 'warn' : 'bad';
  document.getElementById('kpi-cmv').textContent = cmvPct.toFixed(1) + '%';
  document.getElementById('g-custo').textContent = 'R$ ' + totalVal.toLocaleString('pt-BR', { minimumFractionDigits: 2 });
  document.getElementById('g-receita').textContent = 'R$ ' + receitaEst.toLocaleString('pt-BR', { minimumFractionDigits: 2 });
  document.getElementById('g-cmv').textContent = cmvPct.toFixed(1) + '%';
  document.getElementById('g-cmv').className = 'val ' + cmvClass;
  document.getElementById('g-class').textContent =
    cmvClass === 'ok' ? '✓ Dentro do ideal' : cmvClass === 'warn' ? '⚠ Levemente alto' : '✗ Acima do limite';
  document.getElementById('g-class').className = 'val ' + cmvClass;

  const rl = document.getElementById('rotate-list');
  if (rl) {
    rl.innerHTML = estoque.map(i => {
      const pct = i.qtdMax > 0 ? Math.min(100, Math.round((i.qtd / i.qtdMax) * 100)) : 0;
      const cls = pct > 50 ? 'bar-ok' : pct > 25 ? 'bar-mid' : 'bar-low';
      return `<div class="rotate-item">
        <div class="rotate-header"><span>${i.nome}</span><span style="font-weight:700">${pct}%</span></div>
        <div class="rotate-bar-bg"><div class="rotate-bar-fill ${cls}" style="width:${pct}%"></div></div>
      </div>`;
    }).join('');
  }

  requestAnimationFrame(() => renderDashboardCharts(estoque));
}

function renderDashboardCharts(est) {
  const ctxComp = document.getElementById('chart-composicao');
  if (ctxComp) {
    safeDestroy(chartComp);
    chartComp = new Chart(ctxComp, {
      type: 'doughnut',
      data: {
        labels: est.map(i => i.nome),
        datasets: [{ data: est.map(i => i.preco * i.qtd), backgroundColor: est.map((_, idx) => COLORS[idx % COLORS.length]), borderWidth: 0 }],
      },
      options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { position: 'bottom', labels: { font: { family: 'DM Sans', size: 11 } } } } },
    });
  }
  const ctxStatus = document.getElementById('chart-status');
  if (ctxStatus) {
    safeDestroy(chartStatus);
    chartStatus = new Chart(ctxStatus, {
      type: 'bar',
      data: {
        labels: est.map(i => i.nome),
        datasets: [{
          label: '% do estoque',
          data: est.map(i => i.qtdMax > 0 ? Math.min(100, Math.round((i.qtd / i.qtdMax) * 100)) : 0),
          backgroundColor: est.map(i => { const p = i.qtdMax > 0 ? i.qtd / i.qtdMax : 0; return p > 0.5 ? 'rgba(0,168,107,0.75)' : p > 0.25 ? 'rgba(244,164,53,0.75)' : 'rgba(230,57,70,0.75)'; }),
          borderRadius: 6,
        }],
      },
      options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false } }, scales: { y: { beginAtZero: true, max: 100, ticks: { callback: v => v + '%' } } } },
    });
  }
}

// ======= EVOLUÇÃO DE PREÇOS =======
function renderEvolucao() {
  const ctx = document.getElementById('chart-evolucao');
  if (!ctx) return;

  const porProduto = {};
  historico.forEach(h => {
    if (!porProduto[h.produtoId]) porProduto[h.produtoId] = { nome: h.nome, unidade: h.unidade, pontos: [] };
    porProduto[h.produtoId].pontos.push({ data: h.data, preco: h.preco });
  });

  const datasetsTodos = Object.entries(porProduto).map(([pid, info], idx) => {
    info.pontos.sort((a, b) => a.data.localeCompare(b.data));
    return {
      label: info.nome,
      data: info.pontos.map(p => ({ x: p.data, y: p.preco })),
      borderColor: COLORS[idx % COLORS.length],
      backgroundColor: COLORS[idx % COLORS.length] + '33',
      tension: 0.3, fill: false,
    };
  });

  const datasets = evolucaoFiltro === 'todos'
    ? datasetsTodos
    : datasetsTodos.filter(d => d.label === evolucaoFiltro);

  safeDestroy(chartEvolucao);
  requestAnimationFrame(() => {
    chartEvolucao = new Chart(ctx, {
      type: 'line',
      data: { datasets },
      options: {
        responsive: true, maintainAspectRatio: false,
        plugins: {
          legend: { position: 'bottom', labels: { font: { family: 'DM Sans', size: 11 } } },
          tooltip: {
            callbacks: { title: items => 'Data: ' + items[0].label, label: item => `${item.dataset.label}: R$ ${item.parsed.y.toFixed(2)}/un` },
            backgroundColor: '#fff', titleColor: '#1a1d2e', bodyColor: '#1a1d2e', borderColor: '#e8eaf0', borderWidth: 1, padding: 10,
          },
        },
        scales: {
          x: { type: 'category', ticks: { font: { family: 'DM Sans', size: 11 } }, grid: { color: '#f0f2f8' } },
          y: { ticks: { callback: v => 'R$ ' + v.toFixed(2), font: { family: 'DM Sans', size: 11 } }, grid: { color: '#f0f2f8' } },
        },
      },
    });
  });

  // Filtros
  const filterEl = document.getElementById('price-filter');
  if (filterEl) {
    const nomes = [...new Set(historico.map(h => h.nome))];
    filterEl.innerHTML =
      `<button class="pill evolucao-filtro-btn ${evolucaoFiltro === 'todos' ? 'active' : ''}" onclick="setFiltroEvolucao('todos', this)">Todos</button>` +
      nomes.map(n => `<button class="pill evolucao-filtro-btn ${evolucaoFiltro === n ? 'active' : ''}" onclick="setFiltroEvolucao('${n}', this)">${n}</button>`).join('');
  }

  const tbody = document.getElementById('historico-tbody');
  if (tbody) {
    const sortedH = [...historico].sort((a, b) => b.data.localeCompare(a.data));
    tbody.innerHTML = sortedH.map(h => `
      <tr>
        <td>${formatDate(h.data)}</td>
        <td style="font-weight:600">${h.nome}</td>
        <td>${h.unidade}</td>
        <td>${h.qtd} ${h.unidade}</td>
        <td class="val-col">R$ ${h.preco.toFixed(2)}</td>
        <td class="val-col">R$ ${(h.preco * h.qtd).toFixed(2)}</td>
      </tr>`).join('');
  }
}

function setFiltroEvolucao(val, btn) {
  evolucaoFiltro = val;
  document.querySelectorAll('.evolucao-filtro-btn').forEach(b => b.classList.remove('active'));
  if (btn) btn.classList.add('active');
  renderEvolucao();
}
window.setFiltroEvolucao = setFiltroEvolucao;

// ======= TOAST =======
let toastTimer;
function showToast(msg, err = false) {
  const el = document.getElementById('toast');
  if (!el) { console.log(msg); return; }
  const ic = el.querySelector('i');
  document.getElementById('toast-msg').textContent = msg;
  el.style.borderColor = err ? 'var(--red)' : 'var(--green)';
  ic.style.color = err ? 'var(--red)' : 'var(--green)';
  ic.className = err ? 'fas fa-exclamation-circle' : 'fas fa-check-circle';
  el.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.classList.remove('show'), 3200);
}

// ======= INIT =======
(async function init() {
  await carregarDados();
  popularSelect();
  renderEstoque();
  renderDashboard();
})();