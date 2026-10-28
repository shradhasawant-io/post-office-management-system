/*
 * Post Office Agent Management – single-page frontend (no build step).
 *
 * Design notes
 * - The backend authenticates every request with HTTP Basic and exposes no
 *   login/token endpoint. We therefore keep the encoded credentials in
 *   sessionStorage (cleared when the tab closes) and attach them per request.
 * - /api/auth/verify-role is used at login so a user who picks the wrong role
 *   is rejected up front instead of hitting 403s on every later call.
 * - Views are declared per role in VIEWS; the router only knows how to mount
 *   one. Adding a screen means adding an entry, not touching navigation code.
 * - All server-supplied text goes through esc() before reaching innerHTML.
 */

const $ = (sel, root = document) => root.querySelector(sel);

const ENUMS = {
  gender: ['MALE', 'FEMALE'],
  accountType: ['DAILY_SAVING', 'MONTHLY_SAVING'],
  frequency: ['DAILY', 'MONTHLY'],
  paymentMode: ['CASH', 'UPI', 'BANK_TRANSFER'],
};

const state = { auth: null, role: null, username: null, view: null };

/* ------------------------------------------------------------------ utils */

const esc = (v) =>
  String(v ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

const money = (n) => (n == null ? '—' : '₹' + Number(n).toLocaleString('en-IN', { minimumFractionDigits: 2 }));
const date = (d) => d || '—';
const dateTime = (d) => (d ? d.replace('T', ' ').slice(0, 16) : '—');
const badge = (v) => `<span class="badge ${esc(v)}">${esc(v ?? '—')}</span>`;

// UTF-8 safe Basic token (plain btoa throws on non-Latin1 characters).
const basicToken = (u, p) => btoa(unescape(encodeURIComponent(`${u}:${p}`)));

let toastTimer;
function toast(msg) {
  const t = $('#toast');
  t.textContent = msg;
  t.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => (t.hidden = true), 2500);
}

/* -------------------------------------------------------------------- api */

class ApiError extends Error {}

async function api(method, path, body, authOverride) {
  const headers = { Authorization: 'Basic ' + (authOverride ?? state.auth) };
  if (body !== undefined) headers['Content-Type'] = 'application/json';

  const res = await fetch(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });

  if (res.status === 204 || res.headers.get('content-length') === '0') return null;
  const isJson = (res.headers.get('content-type') || '').includes('json');
  const data = isJson ? await res.json() : null;

  if (!res.ok) {
    // A 401 mid-session means credentials were revoked or the account was deactivated.
    if (res.status === 401 && authOverride === undefined) logout();
    let msg = data?.message || `Request failed (${res.status})`;
    // Bean-validation failures carry per-field detail; surface it.
    if (data?.details && typeof data.details === 'object') msg += ': ' + Object.values(data.details).join(', ');
    throw new ApiError(msg);
  }
  return data;
}

/* ------------------------------------------------------------------ modal */

/**
 * Generic form dialog. `fields` drive both rendering and payload shape so
 * every create/edit flow shares one code path.
 * field: { name, label, type?, options?, required?, full?, hint? }
 */
function openForm({ title, fields, values = {}, onSubmit }) {
  $('#modal-title').textContent = title;
  $('#modal-error').hidden = true;

  const form = $('#modal-form');
  form.innerHTML = fields
    .map((f) => {
      const v = values[f.name] ?? '';
      const cls = f.full ? 'full' : '';
      const req = f.required ? 'required' : '';
      let input;
      if (f.options) {
        const opts = (f.required ? '' : '<option value=""></option>') +
          f.options.map((o) => `<option ${o === v ? 'selected' : ''}>${esc(o)}</option>`).join('');
        input = `<select name="${f.name}" ${req}>${opts}</select>`;
      } else {
        input = `<input name="${f.name}" type="${f.type || 'text'}" value="${esc(v)}" ${req} ${f.step ? `step="${f.step}"` : ''} ${f.readonly ? 'readonly' : ''}>`;
      }
      return `<label class="${cls}">${esc(f.label)}${input}</label>`;
    })
    .join('');

  form.onsubmit = async (e) => {
    e.preventDefault();
    const payload = {};
    for (const f of fields) {
      const raw = form.elements[f.name].value.trim();
      if (raw === '') continue; // omit blanks so optional backend fields stay null
      payload[f.name] = f.type === 'number' ? Number(raw) : raw;
    }
    try {
      await onSubmit(payload);
      closeModal();
    } catch (err) {
      const box = $('#modal-error');
      box.textContent = err.message;
      box.hidden = false;
    }
  };
  $('#modal').hidden = false;
}

function closeModal() { $('#modal').hidden = true; }
$('#modal-cancel').onclick = closeModal;

/* ---------------------------------------------------------------- table */

/**
 * Renders rows into a table. `cols` is [header, (row)=>html]. `actions`
 * returns button markup; buttons use data-act/data-i and are wired through
 * one delegated handler so we never leak per-row listeners.
 */
function table(cols, rows, actions) {
  if (!rows || !rows.length) return '<div class="card empty">No records found.</div>';
  const head = cols.map(([h]) => `<th>${esc(h)}</th>`).join('') + (actions ? '<th></th>' : '');
  const body = rows
    .map((r, i) =>
      '<tr>' + cols.map(([, fn]) => `<td>${fn(r)}</td>`).join('') +
      (actions ? `<td>${actions(r, i)}</td>` : '') + '</tr>')
    .join('');
  return `<div class="table-wrap"><table><thead><tr>${head}</tr></thead><tbody>${body}</tbody></table></div>`;
}

const act = (name, i, label, cls = '') => `<button class="btn sm ${cls}" data-act="${name}" data-i="${i}">${label}</button>`;

/** Wires delegated clicks on the view body to handlers keyed by data-act. */
function bindActions(rows, handlers) {
  $('#view-body').onclick = (e) => {
    const b = e.target.closest('[data-act]');
    if (!b) return;
    handlers[b.dataset.act]?.(rows[Number(b.dataset.i)]);
  };
}

const setActions = (html) => { $('#view-actions').innerHTML = html; };
const body = (html) => { $('#view-body').innerHTML = html; };
const loading = () => body('<div class="empty">Loading…</div>');
const failed = (err) => body(`<div class="card error">${esc(err.message)}</div>`);

/* --------------------------------------------------------- shared forms */

const customerFields = [
  { name: 'name', label: 'Name', required: true },
  { name: 'fatherName', label: "Father's name", required: true },
  { name: 'mobile', label: 'Mobile (10 digits, starts 7/8/9)', required: true },
  { name: 'email', label: 'Email', type: 'email' },
  { name: 'dob', label: 'Date of birth', type: 'date' },
  { name: 'gender', label: 'Gender', options: ENUMS.gender },
  { name: 'address', label: 'Address', full: true },
  { name: 'city', label: 'City' },
  { name: 'state', label: 'State' },
  { name: 'pinCode', label: 'PIN code' },
  { name: 'aadhaar', label: 'Aadhaar' },
  { name: 'pan', label: 'PAN' },
  { name: 'customerCode', label: 'Customer code' },
];

function customerForm(existing, onDone) {
  openForm({
    title: existing ? 'Edit customer' : 'New customer',
    fields: customerFields,
    values: existing || {},
    onSubmit: async (p) => {
      await api(existing ? 'PUT' : 'POST', existing ? `/api/customers/${existing.id}` : '/api/customers', p);
      toast('Customer saved');
      onDone();
    },
  });
}

function accountForm(existing, customerId, onDone) {
  openForm({
    title: existing ? 'Edit account' : 'New account',
    fields: [
      { name: 'customerId', label: 'Customer ID', type: 'number', required: true, readonly: !!(existing || customerId) },
      { name: 'accountNumber', label: 'Account number', required: !existing, readonly: !!existing },
      { name: 'accountType', label: 'Account type', options: ENUMS.accountType, required: true },
      { name: 'frequency', label: 'Frequency', options: ENUMS.frequency, required: true },
      { name: 'depositAmount', label: 'Deposit amount', type: 'number', step: '0.01', required: true },
      { name: 'startDate', label: 'Start date', type: 'date', required: true },
    ],
    values: existing || { customerId },
    onSubmit: async (p) => {
      await api(existing ? 'PUT' : 'POST', existing ? `/api/accounts/${existing.accountId}` : '/api/accounts', p);
      toast('Account saved');
      onDone(existing?.accountId);
    },
  });
}

function depositForm(accountId, onDone) {
  openForm({
    title: 'New deposit',
    fields: [
      { name: 'transactionAccountId', label: 'Account ID', type: 'number', required: true, readonly: !!accountId },
      { name: 'transactionAmount', label: 'Amount', type: 'number', step: '0.01', required: true },
      { name: 'paymentMode', label: 'Payment mode', options: ENUMS.paymentMode, required: true },
      { name: 'remarks', label: 'Remarks', full: true },
    ],
    values: { transactionAccountId: accountId },
    onSubmit: async (p) => {
      await api('POST', '/api/transactions/deposit', p);
      toast('Deposit recorded');
      onDone(p.transactionAccountId);
    },
  });
}

/* ----------------------------------------------------- reusable columns */

const customerCols = [
  ['ID', (c) => c.id], ['Code', (c) => esc(c.customerCode)], ['Name', (c) => esc(c.name)],
  ['Father', (c) => esc(c.fatherName)], ['Mobile', (c) => esc(c.mobile)], ['City', (c) => esc(c.city)],
  ['Status', (c) => badge(c.status)], ['Agent', (c) => esc(c.agentName)],
];
const accountCols = [
  ['ID', (a) => a.accountId], ['Number', (a) => esc(a.accountNumber)], ['Customer', (a) => a.customerId],
  ['Type', (a) => esc(a.accountType)], ['Frequency', (a) => esc(a.frequency)],
  ['Installment', (a) => money(a.depositAmount)], ['Balance', (a) => money(a.balance)],
  ['Start', (a) => date(a.startDate)], ['Status', (a) => badge(a.accountStatus)],
];
const txCols = [
  ['ID', (t) => t.transactionId], ['Account', (t) => t.transactionAccountId], ['Date', (t) => date(t.transactionDate)],
  ['Type', (t) => badge(t.transactionType)], ['Amount', (t) => money(t.transactionAmount)],
  ['Mode', (t) => esc(t.paymentMode)], ['Balance after', (t) => money(t.balance)], ['Remarks', (t) => esc(t.remarks)],
];

/* ============================================================== ADMIN == */

async function adminDashboard() {
  setActions('');
  loading();
  try {
    const d = await api('GET', '/api/dashboard');
    const tile = (l, v) => `<div class="card tile"><div class="label">${l}</div><div class="value">${v}</div></div>`;
    body(`<div class="tiles">
      ${tile('Total customers', d.totalCustomers)}
      ${tile('Total accounts', d.totalAccounts)}
      ${tile("Today's deposits", money(d.todayDeposits))}
      ${tile('Monthly deposits', money(d.monthlyDeposits))}
    </div>`);
  } catch (e) { failed(e); }
}

const agentFields = (editing) => [
  { name: 'agentName', label: 'Full name', required: true },
  { name: 'agentUsername', label: 'Username', required: true },
  { name: 'password', label: editing ? 'New password (optional)' : 'Password', type: 'password', required: !editing, full: true },
];

async function adminAgents() {
  setActions('<button class="btn primary" id="add">+ New agent</button>');
  $('#add').onclick = () =>
    openForm({ title: 'New agent', fields: agentFields(false), onSubmit: async (p) => { await api('POST', '/api/agents', p); toast('Agent created'); adminAgents(); } });
  loading();
  try {
    const rows = await api('GET', '/api/agents');
    body(table(
      [['ID', (a) => a.agentId], ['Name', (a) => esc(a.agentName)], ['Username', (a) => esc(a.agentUsername)],
       ['Status', (a) => badge(a.agentStatus)], ['Created', (a) => dateTime(a.createdAt)]],
      rows,
      (a, i) => act('edit', i, 'Edit') + (a.agentStatus === 'ACTIVE' ? act('deactivate', i, 'Deactivate', 'danger') : '')
        + act('customers', i, 'Customers') + act('accounts', i, 'Accounts') + act('tx', i, 'Transactions'),
    ));
    bindActions(rows, {
      edit: (a) => openForm({ title: 'Edit agent', fields: agentFields(true), values: a,
        onSubmit: async (p) => { await api('PUT', `/api/agents/${a.agentId}`, p); toast('Agent updated'); adminAgents(); } }),
      deactivate: async (a) => {
        if (!confirm(`Deactivate ${a.agentName}?`)) return;
        try { await api('PATCH', `/api/agents/${a.agentId}/deactivate`); toast('Agent deactivated'); adminAgents(); } catch (e) { toast(e.message); }
      },
      // Drill-downs reuse the list views, filtered by the chosen agent.
      customers: (a) => showView('customers', { agent: a }),
      accounts: (a) => showView('accounts', { agent: a }),
      tx: (a) => showView('transactions', { agent: a }),
    });
  } catch (e) { failed(e); }
}

async function adminCustomers(ctx = {}) {
  setActions(ctx.agent ? `<span class="muted">Agent: ${esc(ctx.agent.agentName)}</span><button class="btn" id="all">Show all</button>` : '');
  if (ctx.agent) $('#all').onclick = () => showView('customers');
  loading();
  try {
    const rows = await api('GET', ctx.agent ? `/api/customers/agents/${ctx.agent.agentId}` : '/api/customers');
    body(table(customerCols, rows));
  } catch (e) { failed(e); }
}

async function adminAccounts(ctx = {}) {
  setActions(ctx.agent ? `<span class="muted">Agent: ${esc(ctx.agent.agentName)}</span><button class="btn" id="all">Show all</button>` : '');
  if (ctx.agent) $('#all').onclick = () => showView('accounts');
  loading();
  try {
    const rows = await api('GET', ctx.agent ? `/api/accounts/agents/${ctx.agent.agentId}` : '/api/accounts');
    body(table(accountCols, rows));
  } catch (e) { failed(e); }
}

async function adminTransactions(ctx = {}) {
  setActions(ctx.agent ? `<span class="muted">Agent: ${esc(ctx.agent.agentName)}</span><button class="btn" id="all">Show all</button>` : '');
  if (ctx.agent) $('#all').onclick = () => showView('transactions');
  loading();
  try {
    const rows = await api('GET', ctx.agent ? `/api/transactions/agents/${ctx.agent.agentId}` : '/api/transactions');
    body(table(txCols, rows));
  } catch (e) { failed(e); }
}

function adminReports() {
  setActions('');
  const today = new Date();
  body(`
    <div class="card toolbar">
      <label>Report type
        <select id="r-type"><option value="daily">Daily</option><option value="monthly">Monthly</option></select>
      </label>
      <label id="r-date-wrap">Date <input id="r-date" type="date" value="${today.toISOString().slice(0, 10)}"></label>
      <label id="r-month-wrap" hidden>Month <input id="r-month" type="month" value="${today.toISOString().slice(0, 7)}"></label>
      <button class="btn primary" id="r-run">Generate</button>
    </div>
    <div id="r-out"></div>`);

  $('#r-type').onchange = (e) => {
    $('#r-date-wrap').hidden = e.target.value !== 'daily';
    $('#r-month-wrap').hidden = e.target.value !== 'monthly';
  };

  $('#r-run').onclick = async () => {
    const out = $('#r-out');
    out.innerHTML = '<div class="empty">Loading…</div>';
    try {
      let url;
      if ($('#r-type').value === 'daily') {
        url = `/api/reports/daily?date=${$('#r-date').value}`;
      } else {
        const [y, m] = $('#r-month').value.split('-');
        url = `/api/reports/monthly?year=${y}&month=${Number(m)}`;
      }
      const r = await api('GET', url);
      out.innerHTML = `
        <div class="summary card">
          <div><div class="muted">Period</div><b>${date(r.startDate)} → ${date(r.endDate)}</b></div>
          <div><div class="muted">Transactions</div><b>${r.totalTransactions}</b></div>
          <div><div class="muted">Total collection</div><b>${money(r.totalCollection)}</b></div>
        </div>` +
        table(
          [['Txn', (t) => t.transactionId], ['Account', (t) => t.accountId], ['Date', (t) => date(t.transactionDate)],
           ['Amount', (t) => money(t.amount)], ['Mode', (t) => esc(t.paymentMode)], ['Remarks', (t) => esc(t.remarks)]],
          r.transactions);
    } catch (e) { out.innerHTML = `<div class="card error">${esc(e.message)}</div>`; }
  };
}

async function adminAdmins() {
  const fields = (editing) => [
    { name: 'adminName', label: 'Full name', required: true },
    { name: 'username', label: 'Username', required: true },
    { name: 'password', label: editing ? 'New password (optional)' : 'Password', type: 'password', required: !editing, full: true },
  ];
  setActions('<button class="btn primary" id="add">+ New admin</button>');
  $('#add').onclick = () =>
    openForm({ title: 'New admin', fields: fields(false), onSubmit: async (p) => { await api('POST', '/api/admins', p); toast('Admin created'); adminAdmins(); } });
  loading();
  try {
    const rows = await api('GET', '/api/admins');
    body(table(
      [['ID', (a) => a.adminId], ['Name', (a) => esc(a.adminName)], ['Username', (a) => esc(a.username)],
       ['Status', (a) => badge(a.adminStatus)], ['Created', (a) => dateTime(a.createdAt)]],
      rows,
      (a, i) => act('edit', i, 'Edit') + (a.adminStatus === 'ACTIVE' ? act('deactivate', i, 'Deactivate', 'danger') : ''),
    ));
    bindActions(rows, {
      edit: (a) => openForm({ title: 'Edit admin', fields: fields(true), values: a,
        onSubmit: async (p) => { await api('PUT', `/api/admins/${a.adminId}`, p); toast('Admin updated'); adminAdmins(); } }),
      deactivate: async (a) => {
        if (!confirm(`Deactivate ${a.adminName}?`)) return;
        try { await api('PATCH', `/api/admins/${a.adminId}/deactivate`); toast('Admin deactivated'); adminAdmins(); } catch (e) { toast(e.message); }
      },
    });
  } catch (e) { failed(e); }
}

/* ============================================================== AGENT == */

/*
 * The agent API has no "list customers" endpoint, but the search endpoint
 * matches on substring, so an empty keyword returns the agent's full roster.
 */
async function agentCustomers() {
  setActions('<input id="q" placeholder="Search name / mobile / email / account"><button class="btn primary" id="add">+ New customer</button>');
  const run = async () => {
    loading();
    try {
      const rows = await api('GET', `/api/customers/search?keyword=${encodeURIComponent($('#q').value.trim())}`);
      body(table(customerCols.slice(0, 7), rows,
        (c, i) => act('edit', i, 'Edit') + act('account', i, 'New account')));
      bindActions(rows, {
        edit: (c) => customerForm(c, run),
        account: (c) => accountForm(null, c.id, () => showView('accounts')),
      });
    } catch (e) { failed(e); }
  };
  let debounce;
  $('#q').oninput = () => { clearTimeout(debounce); debounce = setTimeout(run, 300); };
  $('#add').onclick = () => customerForm(null, run);
  run();
}

/* The backend only exposes account lookup by ID for agents, so this view is lookup-driven. */
function agentAccounts() {
  setActions('<button class="btn primary" id="add">+ New account</button>');
  $('#add').onclick = () => accountForm(null, null, (id) => id && lookup(id));
  body(`
    <div class="card toolbar">
      <label>Account ID <input id="acc-id" type="number" min="1"></label>
      <button class="btn primary" id="acc-go">Find</button>
    </div>
    <div id="acc-out"></div>`);

  async function lookup(id) {
    const out = $('#acc-out');
    out.innerHTML = '<div class="empty">Loading…</div>';
    try {
      const a = await api('GET', `/api/accounts/${id}`);
      out.innerHTML = table(accountCols, [a],
        () => act('edit', 0, 'Edit') + act('deposit', 0, 'Deposit') + act('history', 0, 'History'));
      out.onclick = (e) => {
        const b = e.target.closest('[data-act]');
        if (!b) return;
        if (b.dataset.act === 'edit') accountForm(a, null, () => lookup(id));
        if (b.dataset.act === 'deposit') depositForm(a.accountId, () => lookup(id));
        if (b.dataset.act === 'history') showView('transactions', { accountId: a.accountId });
      };
    } catch (e) { out.innerHTML = `<div class="card error">${esc(e.message)}</div>`; }
  }
  $('#acc-go').onclick = () => $('#acc-id').value && lookup($('#acc-id').value);
}

function agentTransactions(ctx = {}) {
  setActions('<button class="btn primary" id="add">+ New deposit</button>');
  body(`
    <div class="card toolbar">
      <label>Account ID <input id="tx-acc" type="number" min="1" value="${esc(ctx.accountId ?? '')}"></label>
      <button class="btn primary" id="tx-go">Load history</button>
    </div>
    <div id="tx-out"></div>`);

  async function load() {
    const id = $('#tx-acc').value;
    if (!id) return;
    $('#tx-out').innerHTML = '<div class="empty">Loading…</div>';
    try {
      $('#tx-out').innerHTML = table(txCols, await api('GET', `/api/transactions/account/${id}`));
    } catch (e) { $('#tx-out').innerHTML = `<div class="card error">${esc(e.message)}</div>`; }
  }
  $('#add').onclick = () => depositForm($('#tx-acc').value || null, (id) => { $('#tx-acc').value = id; load(); });
  $('#tx-go').onclick = load;
  if (ctx.accountId) load();
}

/* ============================================================ ROUTING == */

const VIEWS = {
  ADMIN: [
    { key: 'dashboard', label: '📊 Dashboard', render: adminDashboard },
    { key: 'agents', label: '🧑‍💼 Agents', render: adminAgents },
    { key: 'customers', label: '👥 Customers', render: adminCustomers },
    { key: 'accounts', label: '🏦 Accounts', render: adminAccounts },
    { key: 'transactions', label: '💸 Transactions', render: adminTransactions },
    { key: 'reports', label: '📑 Reports', render: adminReports },
    { key: 'admins', label: '🔐 Admins', render: adminAdmins },
  ],
  AGENT: [
    { key: 'customers', label: '👥 Customers', render: agentCustomers },
    { key: 'accounts', label: '🏦 Accounts', render: agentAccounts },
    { key: 'transactions', label: '💸 Transactions', render: agentTransactions },
  ],
};

function showView(key, ctx) {
  const view = VIEWS[state.role].find((v) => v.key === key);
  if (!view) return;
  state.view = key;
  $('#view-title').textContent = view.label.replace(/^\S+\s/, '');
  document.querySelectorAll('#nav button').forEach((b) => b.classList.toggle('active', b.dataset.key === key));
  $('#view-body').onclick = null; // drop handlers from the previous view
  view.render(ctx);
}

function mountApp() {
  $('#login-view').hidden = true;
  $('#app-view').hidden = false;
  $('#who').textContent = `${state.username} (${state.role})`;
  $('#nav').innerHTML = VIEWS[state.role].map((v) => `<button data-key="${v.key}">${v.label}</button>`).join('');
  $('#nav').onclick = (e) => { const b = e.target.closest('button'); if (b) showView(b.dataset.key); };
  showView(VIEWS[state.role][0].key);
}

/* ============================================================== AUTH == */

function logout() {
  sessionStorage.removeItem('session');
  Object.assign(state, { auth: null, role: null, username: null });
  $('#app-view').hidden = true;
  $('#login-view').hidden = false;
  closeModal();
}
$('#logout-btn').onclick = logout;

$('#login-form').onsubmit = async (e) => {
  e.preventDefault();
  const err = $('#login-error');
  err.hidden = true;
  const username = $('#login-user').value.trim();
  const role = $('#login-role').value;
  const token = basicToken(username, $('#login-pass').value);
  try {
    await api('POST', '/api/auth/verify-role', { selectedRole: role }, token);
    Object.assign(state, { auth: token, role, username });
    sessionStorage.setItem('session', JSON.stringify({ auth: token, role, username }));
    $('#login-pass').value = '';
    mountApp();
  } catch (ex) {
    err.textContent = ex.message || 'Login failed';
    err.hidden = false;
  }
};

// POST /api/admins is intentionally public on the backend to bootstrap the first admin.
$('#first-admin-btn').onclick = () =>
  openForm({
    title: 'Create first admin',
    fields: [
      { name: 'adminName', label: 'Full name', required: true },
      { name: 'username', label: 'Username', required: true },
      { name: 'password', label: 'Password', type: 'password', required: true, full: true },
    ],
    onSubmit: async (p) => {
      const res = await fetch('/api/admins', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(p) });
      if (!res.ok) throw new Error((await res.json().catch(() => ({}))).message || 'Could not create admin');
      toast('Admin created – you can log in now');
    },
  });

// Restore an existing tab session after reload.
(() => {
  try {
    const s = JSON.parse(sessionStorage.getItem('session') || 'null');
    if (s?.auth) { Object.assign(state, s); mountApp(); }
  } catch { /* corrupt session data – fall back to login screen */ }
})();