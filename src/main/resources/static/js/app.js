/*
 * Post Office Agent Management – single-page frontend (no build step).
 *
 * Design notes
 * - The backend authenticates every request with HTTP Basic and exposes no
 *   login/token endpoint. We therefore keep the encoded credentials in
 *   sessionStorage (cleared when the tab closes) and attach them per request.
 * - /api/auth/verify-role is used at login to authenticate the HTTP Basic
 *   credentials and return the authenticated user's actual role.
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

const state = {
  auth: null,
  role: null,
  username: null,
  view: null
};


/* ------------------------------------------------------------------ utils */

const esc = (v) =>
  String(v ?? '').replace(
    /[&<>"']/g,
    (c) => ({
      '&': '&amp;',
      '<': '&lt;',
      '>': '&gt;',
      '"': '&quot;',
      "'": '&#39;'
    }[c])
  );

const money = (n) =>
  n == null
    ? '—'
    : '₹' +
      Number(n).toLocaleString('en-IN', {
        minimumFractionDigits: 2
      });

const date = (d) => d || '—';

const dateTime = (d) =>
  d
    ? d.replace('T', ' ').slice(0, 16)
    : '—';

const badge = (v) =>
  `<span class="badge ${esc(v)}">${esc(v ?? '—')}</span>`;


/*
 * UTF-8 safe Basic token.
 * Plain btoa() throws on non-Latin1 characters.
 */
const basicToken = (u, p) =>
  btoa(
    unescape(
      encodeURIComponent(`${u}:${p}`)
    )
  );


let toastTimer;

function toast(msg) {

  const t = $('#toast');

  t.textContent = msg;
  t.hidden = false;

  clearTimeout(toastTimer);

  toastTimer = setTimeout(
    () => (t.hidden = true),
    2500
  );
}


/* -------------------------------------------------------------------- api */

class ApiError extends Error {}


async function api(
  method,
  path,
  body,
  authOverride
) {

  const headers = {
    Authorization:
      'Basic ' +
      (authOverride ?? state.auth)
  };

  if (body !== undefined) {
    headers['Content-Type'] =
      'application/json';
  }

  const res = await fetch(
    path,
    {
      method,
      headers,
      body:
        body === undefined
          ? undefined
          : JSON.stringify(body)
    }
  );

  if (
    res.status === 204 ||
    res.headers.get('content-length') === '0'
  ) {
    return null;
  }

  const isJson =
    (res.headers.get('content-type') || '')
      .includes('json');

  const data =
    isJson
      ? await res.json()
      : null;

  if (!res.ok) {

    /*
     * A 401 during an active session means
     * credentials were rejected/revoked or
     * the account was deactivated.
     */
    if (
      res.status === 401 &&
      authOverride === undefined
    ) {
      logout();
    }

    let msg =
      data?.message ||
      `Request failed (${res.status})`;

    /*
     * Bean-validation failures can contain
     * per-field details.
     */
    if (
      data?.details &&
      typeof data.details === 'object'
    ) {
      msg +=
        ': ' +
        Object.values(data.details).join(', ');
    }

    throw new ApiError(msg);
  }

  return data;
}


/* ------------------------------------------------------------------ modal */

/**
 * Generic form dialog.
 *
 * fields:
 * {
 *   name,
 *   label,
 *   type?,
 *   options?,
 *   required?,
 *   full?,
 *   hint?
 * }
 */
function openForm({
  title,
  fields,
  values = {},
  onSubmit
}) {

  $('#modal-title').textContent =
    title;

  $('#modal-error').hidden =
    true;

  const form =
    $('#modal-form');

  form.innerHTML =
    fields
      .map((f) => {

        const v =
          values[f.name] ?? '';

        const cls =
          f.full
            ? 'full'
            : '';

        const req =
          f.required
            ? 'required'
            : '';

        let input;

        if (f.options) {

          const opts =
            (f.required
              ? ''
              : '<option value=""></option>') +

            f.options
              .map(
                (o) =>
                  `<option ${
                    o === v
                      ? 'selected'
                      : ''
                  }>${esc(o)}</option>`
              )
              .join('');

          input =
            `<select
              name="${f.name}"
              ${req}>
              ${opts}
            </select>`;

        } else {

          input =
            `<input
              name="${f.name}"
              type="${f.type || 'text'}"
              value="${esc(v)}"
              ${req}
              ${
                f.step
                  ? `step="${f.step}"`
                  : ''
              }
              ${
                f.readonly
                  ? 'readonly'
                  : ''
              }>`;
        }

        return `
          <label class="${cls}">
            ${esc(f.label)}
            ${input}
          </label>
        `;
      })
      .join('');

  form.onsubmit =
    async (e) => {

      e.preventDefault();

      const payload = {};

      for (const f of fields) {

        const raw =
          form.elements[f.name]
            .value
            .trim();

        if (raw === '') {
          continue;
        }

        payload[f.name] =
          f.type === 'number'
            ? Number(raw)
            : raw;
      }

      try {

        await onSubmit(payload);

        closeModal();

      } catch (err) {

        const box =
          $('#modal-error');

        box.textContent =
          err.message;

        box.hidden =
          false;
      }
    };

  $('#modal').hidden =
    false;
}


function closeModal() {
  $('#modal').hidden = true;
}

$('#modal-cancel').onclick =
  closeModal;


/* ---------------------------------------------------------------- table */

/**
 * Renders rows into a table.
 *
 * cols:
 * [
 *   ['Header', row => html]
 * ]
 */
function table(
  cols,
  rows,
  actions
) {

  if (
    !rows ||
    !rows.length
  ) {
    return `
      <div class="card empty">
        No records found.
      </div>
    `;
  }

  const head =
    cols
      .map(
        ([h]) =>
          `<th>${esc(h)}</th>`
      )
      .join('') +
    (
      actions
        ? '<th></th>'
        : ''
    );

  const body =
    rows
      .map(
        (r, i) =>
          '<tr>' +

          cols
            .map(
              ([, fn]) =>
                `<td>${fn(r)}</td>`
            )
            .join('') +

          (
            actions
              ? `<td>${actions(r, i)}</td>`
              : ''
          ) +

          '</tr>'
      )
      .join('');

  return `
    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            ${head}
          </tr>
        </thead>

        <tbody>
          ${body}
        </tbody>
      </table>
    </div>
  `;
}


const act = (
  name,
  i,
  label,
  cls = ''
) =>
  `<button
    class="btn sm ${cls}"
    data-act="${name}"
    data-i="${i}">
    ${label}
  </button>`;


/**
 * Wires delegated clicks on the view body
 * to handlers keyed by data-act.
 */
function bindActions(
  rows,
  handlers
) {

  $('#view-body').onclick =
    (e) => {

      const b =
        e.target.closest(
          '[data-act]'
        );

      if (!b) {
        return;
      }

      handlers[
        b.dataset.act
      ]?.(
        rows[
          Number(b.dataset.i)
        ]
      );
    };
}


const setActions =
  (html) => {
    $('#view-actions').innerHTML =
      html;
  };


const body =
  (html) => {
    $('#view-body').innerHTML =
      html;
  };


const loading =
  () =>
    body(
      '<div class="empty">Loading…</div>'
    );


const failed =
  (err) =>
    body(
      `<div class="card error">
        ${esc(err.message)}
      </div>`
    );


/* --------------------------------------------------------- shared forms */

const customerFields = [

  {
    name: 'name',
    label: 'Name',
    required: true
  },

  {
    name: 'fatherName',
    label: "Father's name",
    required: true
  },

  {
    name: 'mobile',
    label:
      'Mobile (10 digits, starts 7/8/9)',
    required: true
  },

  {
    name: 'email',
    label: 'Email',
    type: 'email'
  },

  {
    name: 'dob',
    label: 'Date of birth',
    type: 'date'
  },

  {
    name: 'gender',
    label: 'Gender',
    options: ENUMS.gender
  },

  {
    name: 'address',
    label: 'Address',
    full: true
  },

  {
    name: 'city',
    label: 'City'
  },

  {
    name: 'state',
    label: 'State'
  },

  {
    name: 'pinCode',
    label: 'PIN code'
  },

  {
    name: 'aadhaar',
    label: 'Aadhaar'
  },

  {
    name: 'pan',
    label: 'PAN'
  },

  {
    name: 'customerCode',
    label: 'Customer code'
  }
];


function customerForm(
  existing,
  onDone
) {

  openForm({

    title:
      existing
        ? 'Edit customer'
        : 'New customer',

    fields:
      customerFields,

    values:
      existing || {},

    onSubmit:
      async (p) => {

        await api(
          existing
            ? 'PUT'
            : 'POST',

          existing
            ? `/api/customers/${existing.id}`
            : '/api/customers',

          p
        );

        toast(
          'Customer saved'
        );

        onDone();
      }
  });
}


function accountForm(
  existing,
  customerId,
  onDone
) {

  openForm({

    title:
      existing
        ? 'Edit account'
        : 'New account',

    fields: [

      {
        name: 'customerId',
        label: 'Customer ID',
        type: 'number',
        required: true,
        readonly:
          !!(
            existing ||
            customerId
          )
      },

      {
        name: 'accountNumber',
        label: 'Account number',
        required:
          !existing,
        readonly:
          !!existing
      },

      {
        name: 'accountType',
        label: 'Account type',
        options:
          ENUMS.accountType,
        required: true
      },

      {
        name: 'frequency',
        label: 'Frequency',
        options:
          ENUMS.frequency,
        required: true
      },

      {
        name: 'depositAmount',
        label: 'Deposit amount',
        type: 'number',
        step: '0.01',
        required: true
      },

      {
        name: 'startDate',
        label: 'Start date',
        type: 'date',
        required: true
      }
    ],

    values:
      existing || {
        customerId
      },

    onSubmit:
      async (p) => {

        await api(

          existing
            ? 'PUT'
            : 'POST',

          existing
            ? `/api/accounts/${existing.accountId}`
            : '/api/accounts',

          p
        );

        toast(
          'Account saved'
        );

        onDone(
          existing?.accountId
        );
      }
  });
}


function depositForm(
  accountId,
  onDone
) {

  openForm({

    title:
      'New deposit',

    fields: [

      {
        name:
          'transactionAccountId',

        label:
          'Account ID',

        type:
          'number',

        required:
          true,

        readonly:
          !!accountId
      },

      {
        name:
          'transactionAmount',

        label:
          'Amount',

        type:
          'number',

        step:
          '0.01',

        required:
          true
      },

      {
        name:
          'paymentMode',

        label:
          'Payment mode',

        options:
          ENUMS.paymentMode,

        required:
          true
      },

      {
        name:
          'remarks',

        label:
          'Remarks',

        full:
          true
      }
    ],

    values: {
      transactionAccountId:
        accountId
    },

    onSubmit:
      async (p) => {

        await api(
          'POST',
          '/api/transactions/deposit',
          p
        );

        toast(
          'Deposit recorded'
        );

        onDone(
          p.transactionAccountId
        );
      }
  });
}


/* ----------------------------------------------------- reusable columns */

const customerCols = [

  [
    'ID',
    (c) => c.id
  ],

  [
    'Code',
    (c) =>
      esc(c.customerCode)
  ],

  [
    'Name',
    (c) =>
      esc(c.name)
  ],

  [
    'Father',
    (c) =>
      esc(c.fatherName)
  ],

  [
    'Mobile',
    (c) =>
      esc(c.mobile)
  ],

  [
    'City',
    (c) =>
      esc(c.city)
  ],

  [
    'Status',
    (c) =>
      badge(c.status)
  ],

  [
    'Agent',
    (c) =>
      esc(c.agentName)
  ]
];


const accountCols = [

  [
    'ID',
    (a) =>
      a.accountId
  ],

  [
    'Number',
    (a) =>
      esc(a.accountNumber)
  ],

  [
    'Customer',
    (a) =>
      a.customerId
  ],

  [
    'Type',
    (a) =>
      esc(a.accountType)
  ],

  [
    'Frequency',
    (a) =>
      esc(a.frequency)
  ],

  [
    'Installment',
    (a) =>
      money(a.depositAmount)
  ],

  [
    'Balance',
    (a) =>
      money(a.balance)
  ],

  [
    'Start',
    (a) =>
      date(a.startDate)
  ],

  [
    'Status',
    (a) =>
      badge(a.accountStatus)
  ]
];


const txCols = [

  [
    'ID',
    (t) =>
      t.transactionId
  ],

  [
    'Account',
    (t) =>
      t.transactionAccountId
  ],

  [
    'Date',
    (t) =>
      date(t.transactionDate)
  ],

  [
    'Type',
    (t) =>
      badge(t.transactionType)
  ],

  [
    'Amount',
    (t) =>
      money(t.transactionAmount)
  ],

  [
    'Mode',
    (t) =>
      esc(t.paymentMode)
  ],

  [
    'Balance after',
    (t) =>
      money(t.balance)
  ],

  [
    'Remarks',
    (t) =>
      esc(t.remarks)
  ]
];


/* ============================================================== ADMIN == */

async function adminDashboard() {

  setActions('');

  loading();

  try {

    const d =
      await api(
        'GET',
        '/api/dashboard'
      );

    const tile =
      (l, v) =>
        `
        <div class="card tile">
          <div class="label">
            ${l}
          </div>

          <div class="value">
            ${v}
          </div>
        </div>
        `;

    body(`
      <div class="tiles">

        ${tile(
          'Total customers',
          d.totalCustomers
        )}

        ${tile(
          'Total accounts',
          d.totalAccounts
        )}

        ${tile(
          "Today's deposits",
          money(d.todayDeposits)
        )}

        ${tile(
          'Monthly deposits',
          money(d.monthlyDeposits)
        )}

      </div>
    `);

  } catch (e) {

    failed(e);

  }
}


const agentFields =
  (editing) => [

    {
      name:
        'agentName',

      label:
        'Full name',

      required:
        true
    },

    {
      name:
        'agentUsername',

      label:
        'Username',

      required:
        true
    },

    {
      name:
        'password',

      label:
        editing
          ? 'New password (optional)'
          : 'Password',

      type:
        'password',

      required:
        !editing,

      full:
        true
    }
  ];


async function adminAgents() {

  setActions(
    '<button class="btn primary" id="add">' +
    '+ New agent' +
    '</button>'
  );

  $('#add').onclick =
    () =>
      openForm({

        title:
          'New agent',

        fields:
          agentFields(false),

        onSubmit:
          async (p) => {

            await api(
              'POST',
              '/api/agents',
              p
            );

            toast(
              'Agent created'
            );

            adminAgents();
          }
      });

  loading();

  try {

    const rows =
      await api(
        'GET',
        '/api/agents'
      );

    body(
      table(

        [
          [
            'ID',
            (a) =>
              a.agentId
          ],

          [
            'Name',
            (a) =>
              esc(a.agentName)
          ],

          [
            'Username',
            (a) =>
              esc(a.agentUsername)
          ],

          [
            'Status',
            (a) =>
              badge(a.agentStatus)
          ],

          [
            'Created',
            (a) =>
              dateTime(a.createdAt)
          ]
        ],

        rows,

        (a, i) =>
          act(
            'edit',
            i,
            'Edit'
          ) +

          (
            a.agentStatus === 'ACTIVE'
              ? act(
                  'deactivate',
                  i,
                  'Deactivate',
                  'danger'
                )
              : ''
          ) +

          act(
            'customers',
            i,
            'Customers'
          ) +

          act(
            'accounts',
            i,
            'Accounts'
          ) +

          act(
            'tx',
            i,
            'Transactions'
          )
      )
    );

    bindActions(
      rows,
      {

        edit:
          (a) =>
            openForm({

              title:
                'Edit agent',

              fields:
                agentFields(true),

              values:
                a,

              onSubmit:
                async (p) => {

                  await api(
                    'PUT',
                    `/api/agents/${a.agentId}`,
                    p
                  );

                  toast(
                    'Agent updated'
                  );

                  adminAgents();
                }
            }),

        deactivate:
          async (a) => {

            if (
              !confirm(
                `Deactivate ${a.agentName}?`
              )
            ) {
              return;
            }

            try {

              await api(
                'PATCH',
                `/api/agents/${a.agentId}/deactivate`
              );

              toast(
                'Agent deactivated'
              );

              adminAgents();

            } catch (e) {

              toast(
                e.message
              );
            }
          },

        customers:
          (a) =>
            showView(
              'customers',
              {
                agent: a
              }
            ),

        accounts:
          (a) =>
            showView(
              'accounts',
              {
                agent: a
              }
            ),

        tx:
          (a) =>
            showView(
              'transactions',
              {
                agent: a
              }
            )
      }
    );

  } catch (e) {

    failed(e);

  }
}


async function adminCustomers(
  ctx = {}
) {

  setActions(
    ctx.agent
      ? `
        <span class="muted">
          Agent:
          ${esc(ctx.agent.agentName)}
        </span>

        <button
          class="btn"
          id="all">
          Show all
        </button>
      `
      : ''
  );

  if (ctx.agent) {

    $('#all').onclick =
      () =>
        showView(
          'customers'
        );
  }

  loading();

  try {

    const rows =
      await api(
        'GET',

        ctx.agent
          ? `/api/customers/agents/${ctx.agent.agentId}`
          : '/api/customers'
      );

    body(
      table(
        customerCols,
        rows
      )
    );

  } catch (e) {

    failed(e);

  }
}


async function adminAccounts(
  ctx = {}
) {

  setActions(
    ctx.agent
      ? `
        <span class="muted">
          Agent:
          ${esc(ctx.agent.agentName)}
        </span>

        <button
          class="btn"
          id="all">
          Show all
        </button>
      `
      : ''
  );

  if (ctx.agent) {

    $('#all').onclick =
      () =>
        showView(
          'accounts'
        );
  }

  loading();

  try {

    const rows =
      await api(
        'GET',

        ctx.agent
          ? `/api/accounts/agents/${ctx.agent.agentId}`
          : '/api/accounts'
      );

    body(
      table(
        accountCols,
        rows
      )
    );

  } catch (e) {

    failed(e);

  }
}


async function adminTransactions(
  ctx = {}
) {

  setActions(
    ctx.agent
      ? `
        <span class="muted">
          Agent:
          ${esc(ctx.agent.agentName)}
        </span>

        <button
          class="btn"
          id="all">
          Show all
        </button>
      `
      : ''
  );

  if (ctx.agent) {

    $('#all').onclick =
      () =>
        showView(
          'transactions'
        );
  }

  loading();

  try {

    const rows =
      await api(
        'GET',

        ctx.agent
          ? `/api/transactions/agents/${ctx.agent.agentId}`
          : '/api/transactions'
      );

    body(
      table(
        txCols,
        rows
      )
    );

  } catch (e) {

    failed(e);

  }
}


/* ========================================================== ADMIN ADMINS */

async function adminAdmins() {

  const fields = (editing) => [
    {
      name: 'adminName',
      label: 'Full name',
      required: true
    },
    {
      name: 'username',
      label: 'Username',
      required: true
    },
    {
      name: 'password',
      label:
        editing
          ? 'New password (optional)'
          : 'Password',
      type: 'password',
      required: !editing,
      full: true
    }
  ];

  setActions(
    '<button class="btn primary" id="add">' +
    '+ New admin</button>'
  );

  $('#add').onclick = () =>
    openForm({
      title: 'New admin',
      fields: fields(false),

      onSubmit: async (p) => {

        await api(
          'POST',
          '/api/admins',
          p
        );

        toast('Admin created');

        adminAdmins();
      }
    });

  loading();

  try {

    const rows =
      await api(
        'GET',
        '/api/admins'
      );

    body(
      table(
        [
          ['ID', (a) => a.adminId],
          [
            'Name',
            (a) => esc(a.adminName)
          ],
          [
            'Username',
            (a) => esc(a.username)
          ],
          [
            'Status',
            (a) => badge(a.adminStatus)
          ],
          [
            'Created',
            (a) => dateTime(a.createdAt)
          ]
        ],
        rows,

        (a, i) =>
          act('edit', i, 'Edit') +
          (
            a.adminStatus === 'ACTIVE'
              ? act(
                  'deactivate',
                  i,
                  'Deactivate',
                  'danger'
                )
              : ''
          )
      )
    );

    bindActions(rows, {

      edit: (a) =>
        openForm({
          title: 'Edit admin',
          fields: fields(true),
          values: a,

          onSubmit: async (p) => {

            await api(
              'PUT',
              `/api/admins/${a.adminId}`,
              p
            );

            toast('Admin updated');

            adminAdmins();
          }
        }),

      deactivate: async (a) => {

        if (
          !confirm(
            `Deactivate ${a.adminName}?`
          )
        ) {
          return;
        }

        try {

          await api(
            'PATCH',
            `/api/admins/${a.adminId}/deactivate`
          );

          toast('Admin deactivated');

          adminAdmins();

        } catch (e) {

          toast(e.message);
        }
      }
    });

  } catch (e) {

    failed(e);
  }
}

/*
 * NOTE:
 *
 * Admin Reports has intentionally been removed from VIEWS below.
 *
 * Your required authorization model is:
 *
 * ADMIN -> no Reports
 * AGENT -> Reports
 *
 * The backend must remain the final authorization authority.
 */


/* ============================================================ AGENT == */


/*
 * The agent API has no "list customers" endpoint, but the search endpoint
 * matches on substring, so an empty keyword returns the agent's full roster.
 */
async function agentCustomers() {

  setActions(
    `
      <input
        id="q"
        placeholder="Search name / mobile / email / account">

      <button
        class="btn primary"
        id="add">
        + New customer
      </button>
    `
  );

  const run =
    async () => {

      loading();

      try {

        const rows =
          await api(
            'GET',
            `/api/customers/search?keyword=${encodeURIComponent(
              $('#q').value.trim()
            )}`
          );

        body(
          table(

            customerCols.slice(
              0,
              7
            ),

            rows,

            (c, i) =>
              act(
                'edit',
                i,
                'Edit'
              ) +

              act(
                'account',
                i,
                'New account'
              )
          )
        );

        bindActions(
          rows,
          {

            edit:
              (c) =>
                customerForm(
                  c,
                  run
                ),

            account:
              (c) =>
                accountForm(
                  null,
                  c.id,
                  () =>
                    showView(
                      'accounts'
                    )
                )
          }
        );

      } catch (e) {

        failed(e);

      }
    };


  let debounce;

  $('#q').oninput =
    () => {

      clearTimeout(
        debounce
      );

      debounce =
        setTimeout(
          run,
          300
        );
    };


  $('#add').onclick =
    () =>
      customerForm(
        null,
        run
      );

  run();
}


/*
 * The backend only exposes account lookup by ID
 * for agents, so this view is lookup-driven.
 */
function agentAccounts() {

  setActions(
    `
      <button
        class="btn primary"
        id="add">
        + New account
      </button>
    `
  );

  $('#add').onclick =
    () =>
      accountForm(
        null,
        null,
        () => {
          const selectedFilter = $('#account-filter')?.value;
          const currentValue = $('#account-filter-value')?.value?.trim();

          if (selectedFilter && currentValue) {
            runSearch();
          } else {
            $('#account-out').innerHTML = `
              <div class="card empty">
                Account saved. Select a filter and value, then click Find to view matching accounts.
              </div>
            `;
          }
        }
      );

  body(
    `
      <div class="card toolbar">

        <label>
          Filter

          <select id="account-filter">
            <option value="">Select filter</option>
            <option value="ACCOUNT_NUMBER">Account Number</option>
            <option value="AMOUNT">Amount</option>
            <option value="DATE">Date</option>
            <option value="PAYMENT_MODE">Payment Mode</option>
            <option value="ACCOUNT_STATUS">Account Status</option>
            <option value="ACCOUNT_TYPE">Account Type</option>
            <option value="BALANCE">Balance</option>
          </select>
        </label>

        <div id="account-filter-value-wrap">
          <label>
            Value
            <input
              id="account-filter-value"
              type="text"
              placeholder="Select a filter first"
              disabled>
          </label>
        </div>

        <button class="btn primary" id="account-find">
          Find
        </button>

        <button class="btn" id="account-clear">
          Clear
        </button>

      </div>

      <div id="account-filter-help" class="muted">
        Select one filter, enter or select its value, then click Find.
      </div>

      <div id="account-out"></div>
    `
  );

  const filter = $('#account-filter');
  const valueWrap = $('#account-filter-value-wrap');

  function renderFilterValue(filterType) {
    if (!filterType) {
      valueWrap.innerHTML = `
        <label>
          Value
          <input
            id="account-filter-value"
            type="text"
            placeholder="Select a filter first"
            disabled>
        </label>
      `;
      return;
    }

    if (filterType === 'PAYMENT_MODE') {
      valueWrap.innerHTML = `
        <label>
          Payment Mode
          <select id="account-filter-value">
            <option value="">Select payment mode</option>
            <option value="CASH">CASH</option>
            <option value="UPI">UPI</option>
            <option value="BANK_TRANSFER">BANK TRANSFER</option>
          </select>
        </label>
      `;
      return;
    }

    if (filterType === 'ACCOUNT_STATUS') {
      valueWrap.innerHTML = `
        <label>
          Account Status
          <select id="account-filter-value">
            <option value="">Select account status</option>
            <option value="ACTIVE">ACTIVE</option>
            <option value="INACTIVE">INACTIVE</option>
          </select>
        </label>
      `;
      return;
    }

    if (filterType === 'ACCOUNT_TYPE') {
      valueWrap.innerHTML = `
        <label>
          Account Type
          <select id="account-filter-value">
            <option value="">Select account type</option>
            <option value="DAILY_SAVING">Daily Saving</option>
            <option value="MONTHLY_SAVING">Monthly Saving</option>
          </select>
        </label>
      `;
      return;
    }

    let type = 'text';
    let step = '';
    let min = '';
    let placeholder = 'Enter value';

    if (filterType === 'AMOUNT') {
      type = 'number';
      step = '0.01';
      min = '0';
      placeholder = 'Enter amount';
    } else if (filterType === 'BALANCE') {
      type = 'number';
      step = '0.01';
      min = '0';
      placeholder = 'Enter balance';
    } else if (filterType === 'DATE') {
      type = 'date';
      placeholder = '';
    } else if (filterType === 'ACCOUNT_NUMBER') {
      placeholder = 'Enter account number';
    }

    valueWrap.innerHTML = `
      <label>
        Value
        <input
          id="account-filter-value"
          type="${type}"
          ${step ? `step="${step}"` : ''}
          ${min ? `min="${min}"` : ''}
          placeholder="${placeholder}">
      </label>
    `;

    $('#account-filter-value')?.focus();
  }

  filter.onchange = () => {
    renderFilterValue(filter.value);
    $('#account-filter-help').textContent = filter.value
      ? 'Enter or select a value for the chosen filter, then click Find.'
      : 'Select one filter, enter or select its value, then click Find.';
    $('#account-out').innerHTML = '';
  };

  $('#account-find').onclick = () => runSearch();

  $('#account-clear').onclick = () => {
    filter.value = '';
    renderFilterValue('');
    $('#account-out').innerHTML = '';
    $('#account-filter-help').textContent =
      'Select one filter, enter or select its value, then click Find.';
  };

  async function runSearch() {
    const selectedFilter = filter.value;
    const valueInput = $('#account-filter-value');
    const currentValue = valueInput?.value?.trim();

    if (!selectedFilter) {
      toast('Please select a filter.');
      return;
    }

    if (!currentValue) {
      toast('Please enter or select a filter value.');
      valueInput?.focus();
      return;
    }

    if (
      (selectedFilter === 'AMOUNT' || selectedFilter === 'BALANCE') &&
      (!Number.isFinite(Number(currentValue)) || Number(currentValue) < 0)
    ) {
      toast('Please enter a valid non-negative amount.');
      valueInput?.focus();
      return;
    }

    const out = $('#account-out');
    out.innerHTML = '<div class="empty">Loading…</div>';

    try {
      const params = new URLSearchParams();
      params.set('filter', selectedFilter);
      params.set('value', currentValue);

      const rows = await api(
        'GET',
        `/api/accounts/search?${params.toString()}`
      );

      if (!Array.isArray(rows)) {
        throw new Error('Unexpected response from the accounts search API.');
      }

      out.innerHTML = table(
        accountCols,
        rows,
        (a, i) =>
          act('edit', i, 'Edit') +
          act('deposit', i, 'Deposit') +
          act('history', i, 'History')
      );

      bindActions(rows, {
        edit: (a) =>
          accountForm(
            a,
            null,
            () => runSearch()
          ),

        deposit: (a) =>
          depositForm(
            a.accountId,
            () => runSearch()
          ),

        history: (a) =>
          showView(
            'transactions',
            { accountId: a.accountId }
          )
      });
    } catch (e) {
      out.innerHTML = `
        <div class="card error">
          ${esc(e.message)}
        </div>
      `;
    }
  }
}

/*
 * Agent transaction history.
 */
function agentTransactions(
  ctx = {}
) {

  setActions(
    `
      <button
        class="btn primary"
        id="add">
        + New deposit
      </button>
    `
  );

  body(
    `
      <div class="card toolbar">

        <label>
          Account ID

          <input
            id="tx-acc"
            type="number"
            min="1"
            value="${esc(
              ctx.accountId ?? ''
            )}">
        </label>

        <button
          class="btn primary"
          id="tx-go">
          Load history
        </button>

      </div>

      <div id="tx-out"></div>
    `
  );


  async function load() {

    const id =
      $('#tx-acc').value;

    if (!id) {
      return;
    }

    $('#tx-out').innerHTML =
      '<div class="empty">Loading…</div>';

    try {

      $('#tx-out').innerHTML =
        table(
          txCols,

          await api(
            'GET',
            `/api/transactions/account/${id}`
          )
        );

    } catch (e) {

      $('#tx-out').innerHTML =
        `
          <div class="card error">
            ${esc(e.message)}
          </div>
        `;
    }
  }


  $('#add').onclick =
    () =>
      depositForm(
        $('#tx-acc').value || null,

        (id) => {

          $('#tx-acc').value =
            id;

          load();
        }
      );


  $('#tx-go').onclick =
    load;


  if (ctx.accountId) {
    load();
  }
}


/* ============================================================ REPORTS == */


/*
 * Agent Reports
 *
 * IMPORTANT SECURITY RULE:
 *
 * The frontend NEVER sends agentId.
 *
 * The backend determines the authenticated agent from
 * Spring Security / AuthenticatedAgentService and performs
 * the ownership restriction.
 *
 * Supported filters:
 *
 * 1. Account Number
 * 2. Amount
 * 3. Date / Date Range
 * 4. Payment Mode
 *
 * Only ONE filter is active at a time.
 */
function agentReports() {

  setActions('');

  body(
    `
      <div class="card">

        <div class="page-section-header">

          <div>

            <h3>
              Reports
            </h3>

            <p class="muted">
              View reports for your customers.
            </p>

          </div>

        </div>


        <form id="report-filter-form">

          <div class="toolbar report-toolbar">


            <!-- Filter dropdown -->

            <div
              class="report-filter-wrapper">

              <button
                type="button"
                class="btn"
                id="report-filter-btn"
                aria-haspopup="true"
                aria-expanded="false">

                Filter

                <span
                  aria-hidden="true">
                  ▼
                </span>

              </button>


              <div
                id="report-filter-menu"
                class="report-filter-menu"
                hidden>


                <button
                  type="button"
                  class="report-filter-option"
                  data-filter="accountNumber">

                  Account Number

                </button>


                <button
                  type="button"
                  class="report-filter-option"
                  data-filter="amount">

                  Amount

                </button>


                <button
                  type="button"
                  class="report-filter-option"
                  data-filter="date">

                  Date

                </button>


                <button
                  type="button"
                  class="report-filter-option"
                  data-filter="paymentMode">

                  Payment Mode

                </button>


              </div>

            </div>


            <!-- Selected filter -->

            <span
              id="selected-filter-label"
              class="report-selected-filter"
              hidden>
            </span>


            <!-- Dynamic input -->

            <div
              id="report-filter-input"
              class="report-filter-input">
            </div>


            <!-- Generate -->

            <button
              type="submit"
              class="btn primary"
              id="report-generate-btn">

              Generate

            </button>


            <!-- Clear -->

            <button
              type="button"
              class="btn"
              id="report-clear-btn">

              Clear

            </button>


          </div>

        </form>

      </div>


      <!-- Error -->

      <div
        id="report-error"
        class="card error"
        hidden>
      </div>


      <!-- Results -->

      <div id="report-results">

        <div class="card empty">

          Select a filter and generate
          the report.

        </div>

      </div>
    `
  );


  const form =
    $('#report-filter-form');

  const filterButton =
    $('#report-filter-btn');

  const filterMenu =
    $('#report-filter-menu');

  const selectedFilterLabel =
    $('#selected-filter-label');

  const filterInput =
    $('#report-filter-input');

  const generateButton =
    $('#report-generate-btn');

  const clearButton =
    $('#report-clear-btn');

  const results =
    $('#report-results');

  const errorBox =
    $('#report-error');


  let selectedFilter =
    null;


  /* ------------------------------------------------------------
     ERROR
     ------------------------------------------------------------ */

  function clearError() {

    errorBox.hidden =
      true;

    errorBox.textContent =
      '';
  }


  /* ------------------------------------------------------------
     LOADING
     ------------------------------------------------------------ */

  function setLoading(
    isLoading
  ) {

    generateButton.disabled =
      isLoading;

    clearButton.disabled =
      isLoading;

    filterButton.disabled =
      isLoading;

    generateButton.textContent =
      isLoading
        ? 'Generating…'
        : 'Generate';
  }


  /* ------------------------------------------------------------
     FILTER MENU
     ------------------------------------------------------------ */

  filterButton.onclick =
    () => {

      const isHidden =
        filterMenu.hidden;

      filterMenu.hidden =
        !isHidden;

      filterButton.setAttribute(
        'aria-expanded',
        String(isHidden)
      );
    };


  /*
   * Close dropdown when clicking outside.
   */
  document.addEventListener(
    'click',
    (event) => {

      if (
        !event.target.closest(
          '.report-filter-wrapper'
        )
      ) {

        filterMenu.hidden =
          true;

        filterButton.setAttribute(
          'aria-expanded',
          'false'
        );
      }
    }
  );


  /* ------------------------------------------------------------
     FILTER SELECTION
     ------------------------------------------------------------ */

  filterMenu
    .querySelectorAll(
      '.report-filter-option'
    )
    .forEach(
      (button) => {

        button.onclick =
          () => {

            selectedFilter =
              button.dataset.filter;

            const filterName =
              button.textContent.trim();

            selectedFilterLabel.textContent =
              `Filter: ${filterName}`;

            selectedFilterLabel.hidden =
              false;

            filterMenu.hidden =
              true;

            filterButton.setAttribute(
              'aria-expanded',
              'false'
            );

            renderFilterInput();
          };
      }
    );


  /* ------------------------------------------------------------
     DYNAMIC FILTER INPUT
     ------------------------------------------------------------ */

  function renderFilterInput() {

    filterInput.innerHTML =
      '';


    /*
     * ACCOUNT NUMBER
     */

    if (
      selectedFilter ===
      'accountNumber'
    ) {

      filterInput.innerHTML =
        `
          <label
            class="report-input-group">

            <span>
              Account Number
            </span>

            <input
              id="report-account-number"
              type="text"
              placeholder="Enter account number"
              autocomplete="off">

          </label>
        `;

      $('#report-account-number')
        ?.focus();

      return;
    }


    /*
     * AMOUNT
     */

    if (
      selectedFilter ===
      'amount'
    ) {

      filterInput.innerHTML =
        `
          <label
            class="report-input-group">

            <span>
              Amount
            </span>

            <input
              id="report-amount"
              type="number"
              min="0"
              step="0.01"
              placeholder="Enter amount">

          </label>
        `;

      $('#report-amount')
        ?.focus();

      return;
    }


    /*
     * DATE / DATE RANGE
     */

    if (
      selectedFilter ===
      'date'
    ) {

      filterInput.innerHTML =
        `
          <div
            class="report-date-group">

            <label
              class="report-input-group">

              <span>
                From Date
              </span>

              <input
                id="report-start-date"
                type="date">

            </label>


            <label
              class="report-input-group">

              <span>
                To Date
              </span>

              <input
                id="report-end-date"
                type="date">

            </label>

          </div>
        `;

      return;
    }


    /*
     * PAYMENT MODE
     */

    if (
      selectedFilter ===
      'paymentMode'
    ) {

      filterInput.innerHTML =
        `
          <label
            class="report-input-group">

            <span>
              Payment Mode
            </span>

            <select
              id="report-payment-mode">

              <option value="">
                Select payment mode
              </option>

              <option value="CASH">
                CASH
              </option>

              <option value="UPI">
                UPI
              </option>

              <option value="BANK_TRANSFER">
                BANK TRANSFER
              </option>

            </select>

          </label>
        `;

      $('#report-payment-mode')
        ?.focus();
    }
  }


  /* ------------------------------------------------------------
     BUILD BACKEND URL
     ------------------------------------------------------------ */

  function buildReportUrl() {

    if (!selectedFilter) {

      throw new Error(
        'Please select a filter first.'
      );
    }


    const params =
      new URLSearchParams();


    /*
     * ACCOUNT NUMBER
     */

    if (
      selectedFilter ===
      'accountNumber'
    ) {

      const accountNumber =
        $('#report-account-number')
          ?.value
          .trim();

      if (!accountNumber) {

        throw new Error(
          'Please enter an account number.'
        );
      }

      params.append(
        'accountNumber',
        accountNumber
      );
    }


    /*
     * AMOUNT
     */

    if (
      selectedFilter ===
      'amount'
    ) {

      const amount =
        $('#report-amount')
          ?.value
          .trim();

      if (!amount) {

        throw new Error(
          'Please enter an amount.'
        );
      }

      const numericAmount =
        Number(amount);

      if (
        !Number.isFinite(
          numericAmount
        ) ||
        numericAmount < 0
      ) {

        throw new Error(
          'Please enter a valid amount.'
        );
      }

      params.append(
        'amount',
        amount
      );
    }


    /*
     * DATE / DATE RANGE
     */

    if (
      selectedFilter ===
      'date'
    ) {

      const startDate =
        $('#report-start-date')
          ?.value;

      const endDate =
        $('#report-end-date')
          ?.value;

      if (
        !startDate &&
        !endDate
      ) {

        throw new Error(
          'Please select a date or date range.'
        );
      }

      if (
        startDate &&
        endDate &&
        startDate > endDate
      ) {

        throw new Error(
          'End date cannot be before start date.'
        );
      }

      if (startDate) {

        params.append(
          'startDate',
          startDate
        );
      }

      if (endDate) {

        params.append(
          'endDate',
          endDate
        );
      }
    }


    /*
     * PAYMENT MODE
     */

    if (
      selectedFilter ===
      'paymentMode'
    ) {

      const paymentMode =
        $('#report-payment-mode')
          ?.value;

      if (!paymentMode) {

        throw new Error(
          'Please select a payment mode.'
        );
      }

      params.append(
        'paymentMode',
        paymentMode
      );
    }


    /*
     * IMPORTANT:
     *
     * Do NOT add agentId.
     *
     * The backend obtains the authenticated
     * agent ID from Spring Security.
     */

    return (
      '/api/reports/search?' +
      params.toString()
    );
  }


  /* ------------------------------------------------------------
     GENERATE REPORT
     ------------------------------------------------------------ */

  async function generateReport() {

    clearError();

    setLoading(
      true
    );

    results.innerHTML =
      `
        <div class="card empty">
          Loading reports…
        </div>
      `;

    try {

      const url =
        buildReportUrl();

      /*
       * This uses the existing api()
       * function, so the current HTTP Basic
       * credentials are automatically attached.
       */
      const response =
        await api(
          'GET',
          url
        );


      /*
       * Support both:
       *
       * 1. direct array
       * 2. report response containing transactions
       */
      const rows =
        Array.isArray(response)
          ? response
          : response?.transactions ||
            [];


      /*
       * EMPTY RESULT
       */

      if (!rows.length) {

        results.innerHTML =
          `
            <div class="card empty">

              No reports found for
              the selected filter.

            </div>
          `;

        return;
      }


      /*
       * SUMMARY
       */

      let summary =
        '';


      if (
        !Array.isArray(response) &&
        response
      ) {

        summary =
          `
            <div class="summary card">


              ${
                response.startDate != null
                  ? `
                    <div>

                      <div class="muted">
                        From
                      </div>

                      <b>
                        ${date(
                          response.startDate
                        )}
                      </b>

                    </div>
                  `
                  : ''
              }


              ${
                response.endDate != null
                  ? `
                    <div>

                      <div class="muted">
                        To
                      </div>

                      <b>
                        ${date(
                          response.endDate
                        )}
                      </b>

                    </div>
                  `
                  : ''
              }


              ${
                response.totalTransactions != null
                  ? `
                    <div>

                      <div class="muted">
                        Transactions
                      </div>

                      <b>
                        ${esc(
                          response.totalTransactions
                        )}
                      </b>

                    </div>
                  `
                  : ''
              }


              ${
                response.totalCollection != null
                  ? `
                    <div>

                      <div class="muted">
                        Total Collection
                      </div>

                      <b>
                        ${money(
                          response.totalCollection
                        )}
                      </b>

                    </div>
                  `
                  : ''
              }


            </div>
          `;
      }


      /*
       * RESULT TABLE
       */

      results.innerHTML =
        summary +

        table(

          [

            [
              'Transaction',

              (t) =>
                t.transactionId ??
                t.id ??
                '—'
            ],


            [
              'Account',

              (t) =>
                esc(
                  t.accountNumber ??
                  t.accountId ??
                  t.transactionAccountId ??
                  '—'
                )
            ],


            [
              'Date',

              (t) =>
                date(
                  t.transactionDate ??
                  t.date
                )
            ],


            [
              'Amount',

              (t) =>
                money(
                  t.amount ??
                  t.transactionAmount
                )
            ],


            [
              'Payment Mode',

              (t) =>
                esc(
                  t.paymentMode ??
                  '—'
                )
            ],


            [
              'Transaction Type',

              (t) =>
                badge(
                  t.transactionType
                )
            ],


            [
              'Remarks',

              (t) =>
                esc(
                  t.remarks ??
                  '—'
                )
            ]

          ],

          rows
        );

    } catch (e) {

      errorBox.textContent =
        e.message ||
        'Unable to generate report.';

      errorBox.hidden =
        false;

      results.innerHTML =
        '';

    } finally {

      setLoading(
        false
      );
    }
  }


  /* ------------------------------------------------------------
     FORM SUBMIT
     ------------------------------------------------------------ */

  form.onsubmit =
    async (event) => {

      event.preventDefault();

      await generateReport();
    };


  /* ------------------------------------------------------------
     CLEAR
     ------------------------------------------------------------ */

  clearButton.onclick =
    () => {

      selectedFilter =
        null;

      selectedFilterLabel.hidden =
        true;

      selectedFilterLabel.textContent =
        '';

      filterInput.innerHTML =
        '';

      filterMenu.hidden =
        true;

      filterButton.setAttribute(
        'aria-expanded',
        'false'
      );

      clearError();

      results.innerHTML =
        `
          <div class="card empty">

            Select a filter and generate
            the report.

          </div>
        `;
    };
}


/* ============================================================ ROUTING == */


/*
 * ADMIN
 *
 * Reports intentionally NOT included.
 *
 * Required:
 * Admin -> no Reports access.
 */
const VIEWS = {

  ADMIN: [

    {
      key:
        'dashboard',

      label:
        '📊 Dashboard',

      render:
        adminDashboard
    },

    {
      key:
        'agents',

      label:
        '🧑‍💼 Agents',

      render:
        adminAgents
    },

    {
      key:
        'customers',

      label:
        '👥 Customers',

      render:
        adminCustomers
    },

    {
      key:
        'accounts',

      label:
        '🏦 Accounts',

      render:
        adminAccounts
    },

    {
      key:
        'transactions',

      label:
        '💸 Transactions',

      render:
        adminTransactions
    },

    {
      key:
        'admins',

      label:
        '🔐 Admins',

      render:
        adminAdmins
    }

  ],


  /*
   * AGENT
   *
   * Reports added here.
   */
  AGENT: [

    {
      key:
        'customers',

      label:
        '👥 Customers',

      render:
        agentCustomers
    },

    {
      key:
        'accounts',

      label:
        '🏦 Accounts',

      render:
        agentAccounts
    },

    {
      key:
        'transactions',

      label:
        '💸 Transactions',

      render:
        agentTransactions
    },

    {
      key:
        'reports',

      label:
        '📑 Reports',

      render:
        agentReports
    }

  ]

};


function showView(
  key,
  ctx
) {

  const view =
    VIEWS[state.role]
      ?.find(
        (v) =>
          v.key === key
      );

  if (!view) {
    return;
  }

  state.view =
    key;

  $('#view-title')
    .textContent =
    view.label.replace(
      /^\S+\s/,
      ''
    );

  document
    .querySelectorAll(
      '#nav button'
    )
    .forEach(
      (b) =>
        b.classList.toggle(
          'active',
          b.dataset.key === key
        )
    );

  /*
   * Drop handlers from
   * previous view.
   */
  $('#view-body').onclick =
    null;

  view.render(ctx);
}


function mountApp() {

  $('#login-view').hidden =
    true;

  $('#app-view').hidden =
    false;

  $('#who').textContent =
    `${state.username} (${state.role})`;

  $('#nav').innerHTML =
    VIEWS[state.role]
      .map(
        (v) =>
          `<button
            data-key="${v.key}">
            ${v.label}
          </button>`
      )
      .join('');

  $('#nav').onclick =
    (e) => {

      const b =
        e.target.closest(
          'button'
        );

      if (b) {
        showView(
          b.dataset.key
        );
      }
    };

  showView(
    VIEWS[state.role][0].key
  );
}


/* ============================================================== AUTH == */


/*
 * Converts:
 *
 * ROLE_ADMIN -> ADMIN
 * ROLE_AGENT -> AGENT
 * ADMIN      -> ADMIN
 * AGENT      -> AGENT
 */
function normalizeRole(
  value
) {

  if (
    typeof value !==
    'string'
  ) {
    return null;
  }

  const role =
    value
      .replace(
        /^ROLE_/i,
        ''
      )
      .trim()
      .toUpperCase();

  return (
    role === 'ADMIN' ||
    role === 'AGENT'
  )
    ? role
    : null;
}


/*
 * Authenticate using HTTP Basic.
 *
 * The backend determines the actual role
 * from Authentication#getAuthorities().
 *
 * Expected backend response:
 *
 * {
 *   "role": "ADMIN"
 * }
 *
 * OR:
 *
 * {
 *   "role": "AGENT"
 * }
 */
async function authenticateUser(
  username,
  password
) {

  const token =
    basicToken(
      username,
      password
    );

  const response =
    await api(
      'POST',
      '/api/auth/verify-role',
      undefined,
      token
    );


  const role =
    normalizeRole(

      response?.role ??

      response?.data?.role ??

      (
        typeof response ===
        'string'
          ? response
          : null
      )

    );


  if (!role) {

    throw new ApiError(
      'Login succeeded, but the server did not return a valid user role.'
    );
  }

  return {
    token,
    role
  };
}


function logout() {

  sessionStorage.removeItem(
    'session'
  );

  Object.assign(
    state,
    {
      auth:
        null,

      role:
        null,

      username:
        null,

      view:
        null
    }
  );

  $('#app-view').hidden =
    true;

  $('#login-view').hidden =
    false;

  closeModal();
}


$('#logout-btn').onclick =
  logout;


/* ------------------------------------------------------------
   LOGIN
   ------------------------------------------------------------ */

$('#login-form').onsubmit =
  async (e) => {

    e.preventDefault();

    const err =
      $('#login-error');

    err.hidden =
      true;

    const username =
      $('#login-user')
        .value
        .trim();

    const password =
      $('#login-pass')
        .value;


    if (
      !username ||
      !password
    ) {

      err.textContent =
        'Username and password are required.';

      err.hidden =
        false;

      return;
    }


    const loginButton =
      $(
        '#login-form button[type="submit"]'
      );

    loginButton.disabled =
      true;


    try {

      /*
       * Backend authenticates the
       * Basic credentials and determines
       * the actual role.
       */
      const user =
        await authenticateUser(
          username,
          password
        );


      Object.assign(
        state,
        {
          auth:
            user.token,

          role:
            user.role,

          username
        }
      );


      sessionStorage.setItem(
        'session',

        JSON.stringify(
          {
            auth:
              user.token,

            role:
              user.role,

            username
          }
        )
      );


      $('#login-pass').value =
        '';

      mountApp();

    } catch (ex) {

      /*
       * Do not keep credentials
       * after failed login.
       */
      sessionStorage.removeItem(
        'session'
      );

      Object.assign(
        state,
        {
          auth:
            null,

          role:
            null,

          username:
            null
        }
      );


      err.textContent =
        ex.message ||
        'Login failed';

      err.hidden =
        false;

    } finally {

      loginButton.disabled =
        false;
    }
  };


/* ------------------------------------------------------------
   FIRST ADMIN CREATION
   ------------------------------------------------------------ */

/*
 * POST /api/admins is intentionally public
 * on the backend to bootstrap the first admin.
 */
$('#first-admin-btn').onclick =
  () =>
    openForm({

      title:
        'Create first admin',

      fields: [

        {
          name:
            'adminName',

          label:
            'Full name',

          required:
            true
        },

        {
          name:
            'username',

          label:
            'Username',

          required:
            true
        },

        {
          name:
            'password',

          label:
            'Password',

          type:
            'password',

          required:
            true,

          full:
            true
        }

      ],

      onSubmit:
        async (p) => {

          const res =
            await fetch(
              '/api/admins',
              {
                method:
                  'POST',

                headers: {
                  'Content-Type':
                    'application/json'
                },

                body:
                  JSON.stringify(p)
              }
            );


          if (!res.ok) {

            throw new Error(

              (
                await res
                  .json()
                  .catch(
                    () => ({})
                  )
              ).message ||

              'Could not create admin'
            );
          }


          toast(
            'Admin created – you can log in now'
          );
        }
    });


/* ------------------------------------------------------------
   RESTORE SESSION
   ------------------------------------------------------------ */

(() => {

  try {

    const s =
      JSON.parse(
        sessionStorage.getItem(
          'session'
        ) ||
        'null'
      );


    if (
      s?.auth &&
      (
        s.role === 'ADMIN' ||
        s.role === 'AGENT'
      )
    ) {

      Object.assign(
        state,
        s
      );

      mountApp();
    }

  } catch {

    /*
     * Corrupt session data:
     * simply fall back to login.
     */
  }

})();