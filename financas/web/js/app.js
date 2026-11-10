/* ==========================================================
   Finanças Pessoais — lógica do painel (dashboard)
   ========================================================== */
(() => {
  'use strict';

  // ---------------- utilidades ----------------
  const $ = (id) => document.getElementById(id);
  const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
  }[c]));

  // Ícones (Lucide, embutidos) para conteúdo dinâmico
  const ICONS = {
    pencil: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z"/></svg>',
    trash: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M3 6h18"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6"/><path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/><line x1="10" y1="11" x2="10" y2="17"/><line x1="14" y1="11" x2="14" y2="17"/></svg>',
    check: '<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><polyline points="20 6 9 17 4 12"/></svg>',
    undo: '<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/><path d="M3 3v5h5"/></svg>',
  };

  const MESES = ['Janeiro', 'Fevereiro', 'Março', 'Abril', 'Maio', 'Junho',
    'Julho', 'Agosto', 'Setembro', 'Outubro', 'Novembro', 'Dezembro'];
  const MESES_ABR = ['Jan', 'Fev', 'Mar', 'Abr', 'Mai', 'Jun', 'Jul', 'Ago', 'Set', 'Out', 'Nov', 'Dez'];

  const fmtMoney = (v) => new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })
    .format(Number(v) || 0);
  const fmtDate = (iso) => (iso ? iso.split('-').reverse().join('/') : '—');
  const isoMonth = (d) => d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0');
  const todayISO = () => {
    const d = new Date();
    return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
  };
  const monthLabel = (ym) => {
    const [y, m] = ym.split('-');
    return MESES[Number(m) - 1] + ' ' + y;
  };
  const shiftMonth = (ym, delta) => {
    const [y, m] = ym.split('-').map(Number);
    return isoMonth(new Date(y, m - 1 + delta, 1));
  };

  function showToast(msg, isError = false) {
    const toast = $('toast');
    $('toast-msg').textContent = msg;
    toast.style.background = isError ? '#D9534F' : 'var(--green-900)';
    toast.hidden = false;
    clearTimeout(showToast._t);
    showToast._t = setTimeout(() => { toast.hidden = true; }, 2800);
  }

  async function api(path, options = {}) {
    const res = await fetch(path, {
      headers: { 'Content-Type': 'application/json' },
      credentials: 'same-origin',
      ...options,
    });
    if (res.status === 401) {
      location.href = '/';
      return;
    }
    const data = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(data.error || 'Erro inesperado.');
    return data;
  }

  // ---------------- estado ----------------
  const state = {
    view: 'resumo',
    month: isoMonth(new Date()),
    categories: [],
    charts: {},
    editing: null, // { kind: 'transaction'|'bill'|'category', data }
  };

  const VIEW_TITLES = {
    resumo: 'Resumo',
    transacoes: 'Transações',
    contas: 'Contas a Pagar',
    categorias: 'Categorias',
    relatorios: 'Relatórios Mensais',
  };

  // ============================================================
  // Navegação
  // ============================================================
  function showView(name) {
    state.view = name;
    document.querySelectorAll('.view').forEach((v) => { v.hidden = v.id !== 'view-' + name; });
    document.querySelectorAll('.nav-item').forEach((b) => b.classList.toggle('active', b.dataset.view === name));
    $('view-title').textContent = VIEW_TITLES[name];
    $('month-nav').style.visibility = name === 'categorias' ? 'hidden' : 'visible';
    $('month-label').textContent = monthLabel(state.month);
    loadView(name);
  }

  async function loadView(name) {
    try {
      if (name === 'resumo') await loadResumo();
      else if (name === 'transacoes') await loadTransacoes();
      else if (name === 'contas') await loadContas();
      else if (name === 'categorias') await loadCategorias();
      else if (name === 'relatorios') await loadRelatorios();
    } catch (err) {
      showToast(err.message, true);
    }
  }

  // ============================================================
  // Resumo (gráficos + cards)
  // ============================================================
  async function loadResumo() {
    const [summary, yearly, txs] = await Promise.all([
      api('/api/reports/summary?month=' + state.month),
      api('/api/reports/yearly?year=' + state.month.slice(0, 4)),
      api('/api/transactions?month=' + state.month),
    ]);

    $('sum-receitas').textContent = fmtMoney(summary.totalReceitas);
    $('sum-despesas').textContent = fmtMoney(summary.totalDespesas);
    $('sum-receitas').className = 'card-value income';
    $('sum-despesas').className = 'card-value expense';

    const saldo = $('sum-saldo');
    saldo.textContent = fmtMoney(summary.saldo);
    saldo.className = 'card-value ' + (Number(summary.saldo) < 0 ? 'expense' : 'income');

    const vencidas = summary.bills || {};
    $('sum-vencidas').textContent = String(vencidas.vencidasCount ?? 0) + ' conta(s)';
    $('sum-vencidas').className = 'card-value ' + (Number(vencidas.vencidasCount) > 0 ? 'expense' : '');
    $('sum-vencidas-total').textContent = 'Total em aberto: ' + fmtMoney(vencidas.vencidasTotal ?? 0);

    renderBarChart(summary.month, yearly);
    renderCategoryChart(summary.despesasPorCategoria || []);
    renderRecentes(txs || []);
  }

  let chartDefaultsSet = false;
  function ensureChartDefaults() {
    if (chartDefaultsSet || typeof Chart === 'undefined') return;
    Chart.defaults.font.family = getComputedStyle(document.body).fontFamily;
    Chart.defaults.font.size = 12;
    Chart.defaults.color = '#5C6862';
    chartDefaultsSet = true;
  }

  function renderBarChart(ym, yearly) {
    if (typeof Chart === 'undefined') return;
    ensureChartDefaults();
    const canvas = $('chart-meses');
    if (state.charts.bar) state.charts.bar.destroy();
    const months = yearly.months || [];
    state.charts.bar = new Chart(canvas, {
      type: 'bar',
      data: {
        labels: MESES_ABR,
        datasets: [
          {
            label: 'Receitas',
            data: months.map((m) => Number(m.receitas)),
            backgroundColor: '#477B65',
            borderRadius: 4,
            maxBarThickness: 26,
          },
          {
            label: 'Despesas',
            data: months.map((m) => Number(m.despesas)),
            backgroundColor: '#D9534F',
            borderRadius: 4,
            maxBarThickness: 26,
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        scales: {
          x: { grid: { display: false }, border: { display: false } },
          y: {
            beginAtZero: true,
            grid: { color: '#ECEAE2' },
            border: { display: false },
            ticks: { callback: (v) => 'R$ ' + v, maxTicksLimit: 6 },
          },
        },
        plugins: {
          legend: {
            position: 'top',
            align: 'end',
            labels: { usePointStyle: true, pointStyle: 'circle', boxWidth: 7, boxHeight: 7 },
          },
          tooltip: {
            callbacks: { label: (c) => c.dataset.label + ': ' + fmtMoney(c.parsed.y) },
          },
        },
      },
    });
  }

  function renderCategoryChart(categorias) {
    if (typeof Chart === 'undefined') return;
    const canvas = $('chart-categorias');
    if (state.charts.pie) {
      state.charts.pie.destroy();
      state.charts.pie = null;
    }
    const wrap = canvas.parentElement;
    let msg = wrap.querySelector('.chart-empty-msg');
    if (!categorias.length) {
      if (!msg) {
        msg = document.createElement('p');
        msg.className = 'empty chart-empty-msg';
        msg.textContent = 'Nenhuma despesa registrada neste mês.';
        wrap.appendChild(msg);
      }
      msg.hidden = false;
      canvas.style.display = 'none';
      return;
    }
    if (msg) msg.hidden = true;
    canvas.style.display = '';
    ensureChartDefaults();
    state.charts.pie = new Chart(canvas, {
      type: 'doughnut',
      data: {
        labels: categorias.map((c) => c.name),
        datasets: [{
          data: categorias.map((c) => Number(c.total)),
          backgroundColor: categorias.map((c) => c.color || '#93A099'),
          borderColor: '#ffffff',
          borderWidth: 2,
        }],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        cutout: '60%',
        plugins: {
          legend: {
            position: 'right',
            labels: { usePointStyle: true, pointStyle: 'circle', boxWidth: 7, boxHeight: 7 },
          },
          tooltip: {
            callbacks: { label: (c) => c.label + ': ' + fmtMoney(c.parsed) },
          },
        },
      },
    });
  }

  function renderRecentes(txs) {
    const tbody = $('table-recentes').querySelector('tbody');
    tbody.innerHTML = txs.slice(0, 8).map((t) => {
      const isIncome = t.type === 'receita';
      return `<tr>
        <td>${fmtDate(t.date)}</td>
        <td>${esc(t.description)}</td>
        <td><div class="cell-category"><span class="color-dot" style="background:${esc(t.category_color || '#93A099')}"></span>${esc(t.category_name || 'Sem categoria')}</div></td>
        <td class="col-right money-${isIncome ? 'income' : 'expense'}">${isIncome ? '+' : '−'} ${fmtMoney(t.amount)}</td>
      </tr>`;
    }).join('') || '<tr><td colspan="4" style="text-align:center;color:var(--text-3)">Sem transações neste mês.</td></tr>';
  }

  // ============================================================
  // Transações
  // ============================================================
  async function loadTransacoes() {
    await refreshCategories();
    const tipo = $('filtro-tipo').value;
    const categoria = $('filtro-categoria').value;
    const params = new URLSearchParams({ month: state.month });
    if (tipo) params.set('type', tipo);
    if (categoria) params.set('category_id', categoria);

    const txs = await api('/api/transactions?' + params);
    const tbody = $('table-transacoes').querySelector('tbody');
    $('transacoes-empty').hidden = txs.length > 0;
    tbody.innerHTML = txs.map((t) => {
      const isIncome = t.type === 'receita';
      return `<tr>
        <td>${fmtDate(t.date)}</td>
        <td>${esc(t.description)}</td>
        <td><div class="cell-category"><span class="color-dot" style="background:${esc(t.category_color || '#93A099')}"></span>${esc(t.category_name || 'Sem categoria')}</div></td>
        <td><span class="badge badge-${t.type}">${isIncome ? 'Receita' : 'Despesa'}</span></td>
        <td class="col-right money-${isIncome ? 'income' : 'expense'}">${isIncome ? '+' : '−'} ${fmtMoney(t.amount)}</td>
        <td class="col-right"><div class="table-actions">
          <button class="btn btn-icon" data-edit-tx="${t.id}" title="Editar" aria-label="Editar transação">${ICONS.pencil}</button>
          <button class="btn btn-icon danger" data-del-tx="${t.id}" title="Excluir" aria-label="Excluir transação">${ICONS.trash}</button>
        </div></td>
      </tr>`;
    }).join('');

    tbody.querySelectorAll('[data-edit-tx]').forEach((b) => b.addEventListener('click', () => {
      const tx = txs.find((t) => t.id === Number(b.dataset.editTx));
      if (tx) openTransactionModal(tx);
    }));
    tbody.querySelectorAll('[data-del-tx]').forEach((b) => b.addEventListener('click', async () => {
      if (!confirm('Excluir esta transação?')) return;
      try {
        await api('/api/transactions/' + b.dataset.delTx, { method: 'DELETE' });
        showToast('Transação excluída.');
        loadView(state.view);
      } catch (err) { showToast(err.message, true); }
    }));
  }

  function fillCategorySelect(select, type, selectedId) {
    select.innerHTML = '<option value="">Sem categoria</option>' + state.categories
      .filter((c) => !type || c.type === type)
      .map((c) => `<option value="${c.id}" ${Number(selectedId) === c.id ? 'selected' : ''}>${esc(c.name)}</option>`)
      .join('');
  }

  function openTransactionModal(tx = null) {
    const fields = `
      <div class="form-field"><label>Tipo</label>
        <select name="type" id="tx-type">
          <option value="despesa">Despesa</option>
          <option value="receita">Receita</option>
        </select></div>
      <div class="form-field"><label>Descrição</label>
        <input type="text" name="description" required maxlength="200" placeholder="Ex.: Mercado, salário…"></div>
      <div class="form-field"><label>Valor (R$)</label>
        <input type="number" name="amount" required min="0.01" step="0.01" placeholder="0,00"></div>
      <div class="form-field"><label>Data</label>
        <input type="date" name="date" required></div>
      <div class="form-field"><label>Categoria (opcional)</label>
        <select name="category_id" id="tx-category"></select></div>`;

    openModal(tx ? 'Editar transação' : 'Nova transação', fields, async (form) => {
      const fd = new FormData(form);
      const payload = {
        type: fd.get('type'),
        description: fd.get('description'),
        amount: fd.get('amount'),
        date: fd.get('date'),
        category_id: fd.get('category_id') || null,
      };
      if (tx) await api('/api/transactions/' + tx.id, { method: 'PUT', body: JSON.stringify(payload) });
      else await api('/api/transactions', { method: 'POST', body: JSON.stringify(payload) });
      showToast(tx ? 'Transação atualizada.' : 'Transação registrada.');
      loadView(state.view);
    });

    const form = $('modal-form');
    form.querySelector('[name=type]').value = tx ? tx.type : 'despesa';
    form.querySelector('[name=description]').value = tx ? tx.description : '';
    form.querySelector('[name=amount]').value = tx ? Number(tx.amount) : '';
    form.querySelector('[name=date]').value = tx ? tx.date : defaultDate();

    const typeSel = form.querySelector('#tx-type');
    const catSel = form.querySelector('#tx-category');
    const repopulate = () => fillCategorySelect(catSel, typeSel.value, tx ? tx.category_id : null);
    typeSel.addEventListener('change', repopulate);
    repopulate();
  }

  const defaultDate = () => {
    if (state.month === isoMonth(new Date())) return todayISO();
    return state.month + '-01';
  };

  // ============================================================
  // Contas a pagar
  // ============================================================
  async function loadContas() {
    const status = $('filtro-status').value;
    const params = new URLSearchParams({ month: state.month });
    if (status) params.set('status', status);

    const [bills, summary] = await Promise.all([
      api('/api/bills?' + params),
      api('/api/reports/summary?month=' + state.month),
    ]);
    const billsAll = summary.bills || {};
    $('contas-pendentes').textContent = String(billsAll.pendentesCount ?? 0) + ' conta(s)';
    $('contas-pendentes-total').textContent = fmtMoney(billsAll.pendentesTotal ?? 0);
    $('contas-vencidas').textContent = String(billsAll.vencidasCount ?? 0) + ' conta(s)';

    const tbody = $('table-contas').querySelector('tbody');
    const hoje = todayISO();
    $('contas-empty').hidden = bills.length > 0;
    tbody.innerHTML = bills.map((b) => {
      const vencida = b.status === 'pendente' && b.due_date < hoje;
      const badge = b.status === 'pago'
        ? '<span class="badge badge-pago">Pago</span>'
        : (vencida ? '<span class="badge badge-vencida">Vencida</span>'
                   : '<span class="badge badge-pendente">Pendente</span>');
      return `<tr>
        <td>${fmtDate(b.due_date)}</td>
        <td>${esc(b.description)}</td>
        <td class="col-right money-expense">${fmtMoney(b.amount)}</td>
        <td>${badge}${b.paid_date ? `<br><small style="color:var(--text-3)">em ${fmtDate(b.paid_date)}</small>` : ''}</td>
        <td class="col-right"><div class="table-actions">
          ${b.status === 'pendente'
            ? `<button class="btn btn-success btn-sm" data-pay-bill="${b.id}">${ICONS.check} Pagar</button>`
            : `<button class="btn btn-ghost btn-sm" data-unpay-bill="${b.id}">${ICONS.undo} Reverter</button>`}
          <button class="btn btn-icon" data-edit-bill="${b.id}" title="Editar" aria-label="Editar conta">${ICONS.pencil}</button>
          <button class="btn btn-icon danger" data-del-bill="${b.id}" title="Excluir" aria-label="Excluir conta">${ICONS.trash}</button>
        </div></td>
      </tr>`;
    }).join('');

    const reload = () => loadView(state.view);
    tbody.querySelectorAll('[data-pay-bill]').forEach((b) => b.addEventListener('click', async () => {
      try { await api('/api/bills/' + b.dataset.payBill + '/pay', { method: 'POST' }); showToast('Conta marcada como paga.'); reload(); }
      catch (err) { showToast(err.message, true); }
    }));
    tbody.querySelectorAll('[data-unpay-bill]').forEach((b) => b.addEventListener('click', async () => {
      try { await api('/api/bills/' + b.dataset.unpayBill + '/unpay', { method: 'POST' }); showToast('Pagamento revertido.'); reload(); }
      catch (err) { showToast(err.message, true); }
    }));
    tbody.querySelectorAll('[data-edit-bill]').forEach((b) => b.addEventListener('click', () => {
      const bill = bills.find((x) => x.id === Number(b.dataset.editBill));
      if (bill) openBillModal(bill);
    }));
    tbody.querySelectorAll('[data-del-bill]').forEach((b) => b.addEventListener('click', async () => {
      if (!confirm('Excluir esta conta?')) return;
      try { await api('/api/bills/' + b.dataset.delBill, { method: 'DELETE' }); showToast('Conta excluída.'); reload(); }
      catch (err) { showToast(err.message, true); }
    }));
  }

  function openBillModal(bill = null) {
    const fields = `
      <div class="form-field"><label>Descrição</label>
        <input type="text" name="description" required maxlength="200" placeholder="Ex.: Aluguel, fatura do cartão…"></div>
      <div class="form-field"><label>Valor (R$)</label>
        <input type="number" name="amount" required min="0.01" step="0.01" placeholder="0,00"></div>
      <div class="form-field"><label>Data de vencimento</label>
        <input type="date" name="due_date" required></div>
      ${bill ? `<div class="form-field"><label>Status</label>
        <select name="status">
          <option value="pendente">Pendente</option>
          <option value="pago">Pago</option>
        </select></div>` : ''}`;

    openModal(bill ? 'Editar conta a pagar' : 'Nova conta a pagar', fields, async (form) => {
      const fd = new FormData(form);
      const payload = {
        description: fd.get('description'),
        amount: fd.get('amount'),
        due_date: fd.get('due_date'),
      };
      if (bill) {
        payload.status = fd.get('status') || 'pendente';
        await api('/api/bills/' + bill.id, { method: 'PUT', body: JSON.stringify(payload) });
      } else {
        await api('/api/bills', { method: 'POST', body: JSON.stringify(payload) });
      }
      showToast(bill ? 'Conta atualizada.' : 'Conta registrada.');
      loadView(state.view);
    });

    const form = $('modal-form');
    form.querySelector('[name=description]').value = bill ? bill.description : '';
    form.querySelector('[name=amount]').value = bill ? Number(bill.amount) : '';
    form.querySelector('[name=due_date]').value = bill ? bill.due_date : state.month + '-15';
    if (bill) form.querySelector('[name=status]').value = bill.status;
  }

  // ============================================================
  // Categorias
  // ============================================================
  async function refreshCategories() {
    state.categories = await api('/api/categories');
    const sel = $('filtro-categoria');
    const atual = sel.value;
    sel.innerHTML = '<option value="">Todas as categorias</option>' + state.categories
      .map((c) => `<option value="${c.id}">${esc(c.name)}</option>`).join('');
    sel.value = atual;
    if (sel.value !== atual) sel.value = '';
  }

  async function loadCategorias() {
    await refreshCategories();
    const render = (listEl, type) => {
      const items = state.categories.filter((c) => c.type === type);
      listEl.innerHTML = items.map((c) => `<li class="category-item">
        <span class="color-dot" style="background:${esc(c.color)}"></span>
        <span class="category-name">${esc(c.name)}</span>
        <button class="btn btn-icon" data-edit-cat="${c.id}" title="Editar" aria-label="Editar categoria">${ICONS.pencil}</button>
        <button class="btn btn-icon danger" data-del-cat="${c.id}" title="Excluir" aria-label="Excluir categoria">${ICONS.trash}</button>
      </li>`).join('') || '<li class="empty">Nenhuma categoria.</li>';

      listEl.querySelectorAll('[data-edit-cat]').forEach((b) => b.addEventListener('click', () => {
        const cat = state.categories.find((c) => c.id === Number(b.dataset.editCat));
        if (cat) openCategoryModal(cat);
      }));
      listEl.querySelectorAll('[data-del-cat]').forEach((b) => b.addEventListener('click', async () => {
        if (!confirm('Excluir a categoria? As transações vinculadas ficarão sem categoria.')) return;
        try {
          await api('/api/categories/' + b.dataset.delCat, { method: 'DELETE' });
          showToast('Categoria excluída.');
          loadCategorias();
        } catch (err) { showToast(err.message, true); }
      }));
    };
    render($('lista-categorias-despesa'), 'despesa');
    render($('lista-categorias-receita'), 'receita');
  }

  function openCategoryModal(cat) {
    const fields = `
      <div class="form-field"><label>Nome</label>
        <input type="text" name="name" required maxlength="80" placeholder="Nome da categoria"></div>
      <div class="form-field"><label>Tipo</label>
        <select name="type">
          <option value="despesa">Despesa</option>
          <option value="receita">Receita</option>
        </select></div>
      <div class="form-field"><label>Cor</label>
        <input type="color" name="color" value="#477B65"></div>`;
    openModal('Editar categoria', fields, async (form) => {
      const fd = new FormData(form);
      await api('/api/categories/' + cat.id, {
        method: 'PUT',
        body: JSON.stringify({ name: fd.get('name'), type: fd.get('type'), color: fd.get('color') }),
      });
      showToast('Categoria atualizada.');
      loadCategorias();
    });
    const form = $('modal-form');
    form.querySelector('[name=name]').value = cat.name;
    form.querySelector('[name=type]').value = cat.type;
    form.querySelector('[name=color]').value = cat.color;
  }

  // ============================================================
  // Relatórios + exportação CSV
  // ============================================================
  async function loadRelatorios() {
    const summary = await api('/api/reports/summary?month=' + state.month);

    $('rel-receitas').textContent = fmtMoney(summary.totalReceitas);
    $('rel-despesas').textContent = fmtMoney(summary.totalDespesas);
    const saldo = $('rel-saldo');
    saldo.textContent = fmtMoney(summary.saldo);
    saldo.className = 'card-value ' + (Number(summary.saldo) < 0 ? 'expense' : 'income');

    const linhas = [
      ...(summary.despesasPorCategoria || []).map((c) => ({ ...c, tipo: 'despesa' })),
      ...(summary.receitasPorCategoria || []).map((c) => ({ ...c, tipo: 'receita' })),
    ];
    const tbody = $('table-relatorio').querySelector('tbody');
    $('relatorio-empty').hidden = linhas.length > 0;
    tbody.innerHTML = linhas.map((c) => `<tr>
      <td><div class="cell-category"><span class="color-dot" style="background:${esc(c.color)}"></span>${esc(c.name)}</div></td>
      <td><span class="badge badge-${c.tipo}">${c.tipo === 'receita' ? 'Receita' : 'Despesa'}</span></td>
      <td class="col-right money-${c.tipo}">${fmtMoney(c.total)}</td>
    </tr>`).join('');
  }

  function exportCsv(url) {
    showToast('Gerando CSV…');
    window.location.href = url;
  }

  // ============================================================
  // Modal genérico
  // ============================================================
  let modalSubmit = null;

  function openModal(title, fieldsHTML, onSubmit) {
    $('modal-title').textContent = title;
    const form = $('modal-form');
    form.innerHTML = fieldsHTML;
    $('modal-overlay').hidden = false;
    modalSubmit = onSubmit;
    const first = form.querySelector('input, select');
    if (first) first.focus();
  }

  function closeModal() {
    $('modal-overlay').hidden = true;
    modalSubmit = null;
  }

  // ============================================================
  // Inicialização
  // ============================================================
  async function init() {
    try {
      const me = await api('/api/auth/me');
      $('user-name').textContent = me.name;
      $('user-email').textContent = me.email;
      $('user-initial').textContent = (me.name || '?').trim().charAt(0).toUpperCase();
    } catch (err) {
      return; // 401 já redireciona
    }

    document.querySelectorAll('.nav-item').forEach((b) =>
      b.addEventListener('click', () => showView(b.dataset.view)));
    document.querySelectorAll('[data-goto]').forEach((b) =>
      b.addEventListener('click', () => showView(b.dataset.goto)));

    $('btn-prev-month').addEventListener('click', () => {
      state.month = shiftMonth(state.month, -1);
      $('month-label').textContent = monthLabel(state.month);
      loadView(state.view);
    });
    $('btn-next-month').addEventListener('click', () => {
      state.month = shiftMonth(state.month, 1);
      $('month-label').textContent = monthLabel(state.month);
      loadView(state.view);
    });

    $('btn-logout').addEventListener('click', async () => {
      try { await api('/api/auth/logout', { method: 'POST' }); } catch (_) { /* ignora */ }
      location.href = '/';
    });

    $('btn-nova-transacao').addEventListener('click', () => openTransactionModal());
    $('btn-nova-conta').addEventListener('click', () => openBillModal());
    $('filtro-tipo').addEventListener('change', () => loadView('transacoes'));
    $('filtro-categoria').addEventListener('change', () => loadView('transacoes'));
    $('filtro-status').addEventListener('change', () => loadView('contas'));

    $('form-categoria').addEventListener('submit', async (e) => {
      e.preventDefault();
      try {
        await api('/api/categories', {
          method: 'POST',
          body: JSON.stringify({
            name: $('categoria-nome').value,
            type: $('categoria-tipo').value,
            color: $('categoria-cor').value,
          }),
        });
        $('categoria-nome').value = '';
        showToast('Categoria criada.');
        loadCategorias();
      } catch (err) { showToast(err.message, true); }
    });

    $('btn-csv-transacoes').addEventListener('click', () =>
      exportCsv('/api/export/csv?kind=transactions&month=' + state.month));
    $('btn-csv-contas').addEventListener('click', () =>
      exportCsv('/api/export/csv?kind=bills&month=' + state.month));
    $('btn-csv-ano').addEventListener('click', () =>
      exportCsv('/api/export/csv?kind=transactions&year=' + state.month.slice(0, 4)));

    // modal
    $('modal-close').addEventListener('click', closeModal);
    $('modal-cancel').addEventListener('click', closeModal);
    $('modal-overlay').addEventListener('click', (e) => {
      if (e.target === $('modal-overlay')) closeModal();
    });
    $('modal-form').addEventListener('submit', async (e) => {
      e.preventDefault();
      if (!modalSubmit) return;
      const saveBtn = $('modal-save');
      saveBtn.disabled = true;
      try {
        await modalSubmit(e.target);
        closeModal();
      } catch (err) {
        showToast(err.message, true);
      } finally {
        saveBtn.disabled = false;
      }
    });

    showView('resumo');
  }

  init();
})();
