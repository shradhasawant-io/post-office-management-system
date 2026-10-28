# Post Office Agent Management System

A web application for managing **post office savings agents**, their **customers**, the **recurring-deposit accounts** those customers hold, and the **daily/monthly collections** agents make.

It has two parts:

- **Backend** – Spring Boot REST API with role-based security (Admin / Agent) on MySQL.
- **Frontend** – a plain HTML, CSS and JavaScript single-page UI, served by the backend itself (no Node, no build step).

---

## What the system is for

Post office agents collect small daily or monthly savings from customers (recurring deposit schemes). This system replaces the paper ledger:

- **Admins** (post office staff) onboard agents, supervise everything, and view collection reports.
- **Agents** register their own customers, open accounts for them, and record each deposit.

Every agent only sees and touches **their own** customers, accounts and transactions. Admins see everything.

---

## Features

### Admin
| Area | Capabilities |
| --- | --- |
| Dashboard | Total customers, total accounts, today's deposits, this month's deposits |
| Agents | Create, edit, deactivate; drill into an agent's customers, accounts and transactions |
| Customers | View all customers (or filter by agent) |
| Accounts | View all accounts (or filter by agent) |
| Transactions | View all transactions (or filter by agent) |
| Reports | Daily and monthly collection reports with totals and transaction list |
| Admins | Create, edit, deactivate other admins |

### Agent
| Area | Capabilities |
| --- | --- |
| Customers | Search, create, edit own customers |
| Accounts | Open an account for a customer, look up by account ID, edit |
| Transactions | Record a deposit, view the history of an account |

### Business rules enforced by the backend
- Mobile numbers must be 10 digits starting with 7, 8 or 9.
- `DAILY_SAVING` accounts must use `DAILY` frequency.
- Deposits must be greater than zero and only allowed on `ACTIVE` accounts.
- An account cannot be reassigned to another customer.
- Only `ACTIVE` agents/admins can log in; inactive agents cannot be edited.
- Passwords are stored as BCrypt hashes. On update, a blank password keeps the existing one.

---

## Tech stack

| Layer | Technology |
| --- | --- |
| Language | Java 21 |
| Framework | Spring Boot 4.1.1 (Web MVC, Data JPA, Security, Validation) |
| Database | MySQL |
| Build | Maven (wrapper included) |
| Frontend | HTML5, CSS3, vanilla JavaScript (ES2020) |

---

## Project structure

```
post-office-agent-management-system/
├── pom.xml
├── mvnw / mvnw.cmd
└── src/main/
    ├── java/com/postoffice/agent/
    │   ├── config/         Password encoder bean
    │   ├── controller/     REST controllers (Account, Admin, Agent, Auth, Customer, Dashboard, Report, Transaction)
    │   ├── dto/            Request / response objects
    │   ├── entity/         JPA entities
    │   ├── enums/          Status, type and mode enums
    │   ├── exception/      Custom exceptions + global handler
    │   ├── mapper/         Entity <-> DTO mapping
    │   ├── repository/     Spring Data repositories
    │   ├── security/       Basic-auth config, user details, JSON error handlers
    │   └── service/        Interfaces and serviceImpl/ implementations
    └── resources/
        ├── application.properties
        └── static/                 <-- frontend
            ├── index.html
            ├── css/style.css
            └── js/app.js
```

---

## Prerequisites

- **JDK 21**
- **MySQL 8+** running locally on port `3306`
- Maven is **not** required; use the included `mvnw` wrapper

---

## Setup and run

### 1. Create the database

```sql
CREATE DATABASE post_office_management__db;
```

Tables are created automatically on first start (`spring.jpa.hibernate.ddl-auto=update`).

### 2. Set database credentials

The app reads them from environment variables.

**Linux / macOS**
```bash
export DB_USERNAME=root
export DB_PASSWORD=your_password
```

**Windows (PowerShell)**
```powershell
$env:DB_USERNAME="root"
$env:DB_PASSWORD="your_password"
```

### 3. Allow the frontend to load without login

Static files are served by Spring, but the security config requires authentication for every request by default. In
`security/SecurityConfig.java`, add this line **before** the `/api/admins` matcher:
.requestMatchers(HttpMethod.GET, "/", "/index.html", "/css/**", "/js/**", "/favicon.ico").permitAll()

### 4. Start the app

```bash
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
```

### 5. Open it

```
http://localhost:8080
```

> Open the app through Spring at this URL. Opening `index.html` directly from disk, or from a separate dev server, will fail because the backend has no CORS configuration.

---

## First-time use

1. On the login page, click **Create first admin** and fill in a name, username and password. (`POST /api/admins` is intentionally public so the system can be bootstrapped.)
2. Log in with **Role = Admin**.
3. Go to **Agents → + New agent** and create an agent.
4. Log out, then log in as that agent with **Role = Agent**.
5. **Customers → + New customer**, then **New account** on that customer's row.
6. **Accounts →** enter the account ID → **Deposit**.
7. Log back in as admin to see the dashboard and reports update.

---

## Authentication

- The API uses **HTTP Basic** authentication; there are no tokens or sessions.
- The frontend encodes `username:password` once at login, keeps it in `sessionStorage` (cleared when the tab closes), and sends it as the `Authorization` header on every request.
- At login it calls `POST /api/auth/verify-role` so a user who selects the wrong role is rejected immediately.
- Method-level security (`@PreAuthorize`) enforces `ADMIN` / `AGENT` access on each endpoint.

---

## API reference

All endpoints are under `/api` and need Basic auth unless marked public.

### Auth
| Method | Path | Role | Description |
| --- | --- | --- | --- |
| POST | `/auth/verify-role` | any | Confirms the credentials match the selected role. Body: `{"selectedRole":"ADMIN"}` |

### Admins
| Method | Path | Role | Description |
| --- | --- | --- | --- |
| POST | `/admins` | **public** | Create an admin |
| GET | `/admins` | ADMIN | List admins |
| GET | `/admins/{id}` | ADMIN | Get one admin |
| PUT | `/admins/{id}` | ADMIN | Update admin |
| PATCH | `/admins/{id}/deactivate` | ADMIN | Deactivate admin |

### Agents
| Method | Path | Role | Description |
| --- | --- | --- | --- |
| POST | `/agents` | ADMIN | Create agent |
| GET | `/agents` | ADMIN | List agents |
| GET | `/agents/{id}` | ADMIN | Get one agent |
| PUT | `/agents/{id}` | ADMIN | Update agent |
| PATCH | `/agents/{id}/deactivate` | ADMIN | Deactivate agent |

### Customers
| Method | Path | Role | Description |
| --- | --- | --- | --- |
| POST | `/customers` | AGENT | Create a customer (assigned to the logged-in agent) |
| GET | `/customers` | ADMIN | List all customers |
| GET | `/customers/{id}` | AGENT | Get own customer |
| PUT | `/customers/{id}` | AGENT | Update own customer |
| GET | `/customers/search?keyword=` | AGENT | Search own customers by name, email, mobile or account number |
| GET | `/customers/agents/{agentId}` | ADMIN | Customers of one agent |

### Accounts
| Method | Path | Role | Description |
| --- | --- | --- | --- |
| POST | `/accounts` | AGENT | Open an account |
| GET | `/accounts/{id}` | AGENT | Get own account |
| PUT | `/accounts/{id}` | AGENT | Update own account |
| GET | `/accounts` | ADMIN | List all accounts |
| GET | `/accounts/agents/{agentId}` | ADMIN | Accounts of one agent |

### Transactions
| Method | Path | Role | Description |
| --- | --- | --- | --- |
| POST | `/transactions/deposit` | AGENT | Record a deposit |
| GET | `/transactions/{id}` | AGENT | Get one transaction |
| GET | `/transactions/account/{accountId}` | AGENT | History of an account |
| GET | `/transactions` | ADMIN | List all transactions |
| GET | `/transactions/agents/{agentId}` | ADMIN | Transactions of one agent |

### Dashboard and reports
| Method | Path | Role | Description |
| --- | --- | --- | --- |
| GET | `/dashboard` | ADMIN | Summary counts and deposit totals |
| GET | `/reports/daily?date=YYYY-MM-DD` | ADMIN | Collection report for a day |
| GET | `/reports/monthly?year=2026&month=10` | ADMIN | Collection report for a month |

### Error format
```json
{
  "timestamp": "2026-10-26T12:00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Transaction amount must be greater than zero",
  "path": "/api/transactions/deposit"
}
```

---

## Data model (summary)

```
Admin
Agent ──< Customer ──< Account ──< Transaction
```

- **Agent** owns many **Customers**.
- **Customer** has many **Accounts** (`DAILY_SAVING` or `MONTHLY_SAVING`).
- **Account** has many **Transactions** (`DEPOSIT`), each storing the running balance.

Enums: `AccountType`, `AccountStatus`, `Frequency`, `PaymentMode` (`CASH`, `UPI`, `BANK_TRANSFER`), `TransactionType`, `Gender`, and status enums for admins, agents and customers.

---

## Frontend notes

- No framework and no build step; the browser loads `index.html`, `css/style.css` and `js/app.js`.
- Screens are declared per role in the `VIEWS` object in `app.js`. To add a screen, add an entry there and write its render function.
- All server text is HTML-escaped before display.
- Layout is responsive; the sidebar collapses on narrow screens.

---

## Known limitations

- No CORS configuration, so the frontend must be served from the same origin as the API.
- No withdrawal endpoint; only deposits are supported.
- Agents have no "list all accounts" endpoint, so the agent Accounts screen works by account-ID lookup.
- Opening an account sets its balance to the deposit amount without creating a transaction record.
- HTTP Basic sends credentials with every request; use HTTPS in any real deployment.
- `spring.jpa.show-sql=true` is on for development; turn it off in production.

---

## License

Add a license of your choice before publishing.