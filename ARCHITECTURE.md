# BuddiLive Architecture Document

BuddiLive is a web-based personal finance application for budgeting, account tracking, and financial reporting. It is a multi-tenant system where each user's data is isolated by `user_id`.

## Technology Stack

| Layer | Technology |
|-------|-----------|
| Frontend | ExtJS 6.2.0 (Classic Gray theme, loaded from CDN) |
| Backend Framework | Restlet 2.4.3 (Java REST framework) |
| Templating | FreeMarker 2.3.32 |
| ORM | MyBatis 3.5.4 (XML-based mappers) |
| Database | PostgreSQL (via postgresql-42.6.0 driver) |
| Connection Pool | C3P0 0.9.5.5 |
| DB Migrations | Liquibase 4.20.0 |
| Auth | Cookie-based (SHA-512 password hashing, AES-256 encryption) |
| 2FA | TOTP with backup codes (ZXing for QR generation) |
| Build | Maven |
| Deployment | Docker + Nginx + Ansible |
| Java Target | 1.8 |

### Custom Libraries (moss framework)

- **moss-common** - Common utilities
- **moss-crypto** - Cryptographic utilities (AES-256, password hashing)
- **moss-restlet** - Restlet extensions (CookieAuthenticator, AuthenticationRouter)

---

## High-Level Architecture

```
                    ┌──────────────┐
                    │    Nginx     │  (SSL/TLS termination, reverse proxy)
                    │   :80/:443  │
                    └──────┬───────┘
                           │
                    ┌──────┴───────┐
                    │  BuddiLive   │  (Embedded Jetty :8686 or WAR in servlet container)
                    │  Application │
                    ├──────────────┤
                    │   Restlet    │  Router + Resources (REST API)
                    │   Encoder    │  (gzip compression)
                    │   Auth       │  CookieAuthenticator + CookieVerifier
                    ├──────────────┤
                    │   MyBatis    │  SQL mapping layer
                    │   C3P0 Pool  │
                    ├──────────────┤
                    │  Liquibase   │  Schema migrations (run on startup)
                    └──────┬───────┘
                           │
                    ┌──────┴───────┐
                    │  PostgreSQL  │
                    │    :5432     │
                    └──────────────┘
```

---

## Backend Architecture

### Package Structure

```
ca.digitalcave.buddi.live
├── BuddiApplication.java          # Main Restlet Application (routes, init)
├── BuddiLiveStandalone.java       # Embedded Jetty runner (port 8686)
├── db/                            # Data access layer
│   ├── Users.java/.xml            # User CRUD + auth queries
│   ├── Sources.java/.xml          # Accounts & categories
│   ├── Transactions.java/.xml     # Transactions & splits
│   ├── ScheduledTransactions.java/.xml
│   ├── Entries.java/.xml          # Budget entries
│   ├── BuddiSystem.java/.xml      # System settings (cookie key)
│   ├── handler/                   # MyBatis type handlers
│   │   ├── BooleanHandler.java    #   Y/N → boolean
│   │   ├── CurrencyHandler.java   #   code → Currency
│   │   └── LocaleHandler.java     #   string → Locale
│   ├── liquibase/
│   │   └── Migration.java         # Runs Liquibase on startup
│   └── util/
│       ├── ConstraintsChecker.java # Business rule validation
│       ├── DataUpdater.java        # Batch update operations
│       └── DatabaseException.java
├── model/                         # Domain objects
│   ├── User.java                  # User account (extends AuthUser)
│   ├── Account.java               # Debit/credit account (extends Source)
│   ├── Category.java              # Income/expense category (extends Source)
│   ├── Source.java                 # Base class for Account/Category
│   ├── AccountType.java           # Account type grouping
│   ├── CategoryPeriod.java        # Budget period enum (WEEK/MONTH/QUARTER/YEAR)
│   ├── Transaction.java           # Financial transaction
│   ├── Split.java                 # Transaction line item (from/to/amount)
│   ├── Entry.java                 # Budget entry
│   ├── ScheduledTransaction.java  # Recurring transaction template
│   └── report/
│       └── Interval.java          # Report time interval
├── resource/                      # REST API endpoints (Restlet Resources)
│   ├── buddilive/                 # Core API resources
│   │   ├── AccountsResource.java
│   │   ├── CategoriesResource.java
│   │   ├── TransactionsResource.java
│   │   ├── ScheduledTransactionsResource.java
│   │   ├── ScheduledTransactionsRunnerResource.java
│   │   ├── SourcesResource.java
│   │   ├── PeriodsResource.java
│   │   ├── ParentsResource.java
│   │   ├── DescriptionsResource.java
│   │   ├── preferences/
│   │   │   ├── UserPreferencesResource.java
│   │   │   ├── ChangePasswordResource.java
│   │   │   ├── CurrenciesResource.java
│   │   │   └── LocalesResource.java
│   │   └── report/               # 6 report types
│   │       ├── PieTotalsByCategoryResource.java
│   │       ├── IncomeAndExpensesByCategoryResource.java
│   │       ├── AverageIncomeAndExpensesByCategoryResource.java
│   │       ├── InflowAndOutflowByAccountResource.java
│   │       ├── InflowAndOutflowByPayeeResource.java
│   │       └── BalancesOverTimeResource.java
│   └── data/
│       ├── BackupResource.java
│       ├── ExportResource.java
│       └── RestoreResource.java
├── security/
│   ├── BuddiVerifier.java                # Cookie session verifier
│   └── BuddiLiveAuthenticationHelper.java # Login, register, password reset, 2FA
├── service/
│   └── BuddiStatusService.java    # Error response handler (JSON/HTML/text)
└── util/
    ├── CryptoUtil.java            # Encrypt/decrypt user data fields
    ├── FormatUtil.java            # Locale-aware date/currency formatting
    └── LocaleUtil.java            # Locale management
```

### REST API Routes

All data routes are under `/data/` and require authentication (cookie-based).

| Method | Path | Resource | Description |
|--------|------|----------|-------------|
| GET/POST | `/data/accounts` | AccountsResource | Account CRUD |
| GET/POST | `/data/categories` | CategoriesResource | Category CRUD |
| GET/POST | `/data/categories/periods` | PeriodsResource | Budget periods |
| GET/POST | `/data/categories/parents` | ParentsResource | Parent categories |
| GET/POST | `/data/transactions` | TransactionsResource | Transaction CRUD |
| GET/POST | `/data/transactions/descriptions` | DescriptionsResource | Autocomplete |
| GET/POST | `/data/scheduledtransactions` | ScheduledTransactionsResource | Scheduled tx CRUD |
| GET/POST | `/data/scheduledtransactions/execute` | ScheduledTransactionsRunnerResource | Execute due txns |
| GET/POST | `/data/sources/from` | SourcesResource | Source selector (from) |
| GET/POST | `/data/sources/to` | SourcesResource | Source selector (to) |
| GET/POST | `/data/changepassword` | ChangePasswordResource | Password change |
| GET/POST | `/data/userpreferences` | UserPreferencesResource | User settings |
| GET/POST | `/data/backup` | BackupResource | JSON backup |
| GET/POST | `/data/export` | ExportResource | CSV export |
| GET/POST | `/data/restore` | RestoreResource | Restore from backup |
| GET | `/data/report/*` | Report resources | 6 report types |
| GET | `/stores/currencies` | CurrenciesResource | Currency list |
| GET | `/stores/locales` | LocalesResource | Locale list |

**Public routes** (no auth required):
- `GET /` → Redirect to `/index`
- `GET /index` → IndexResource (FreeMarker-templated login or app)
- `POST /authentication` → Login/register/password reset
- Static files served by DefaultResource

### Request Flow

```
HTTP Request
  → Restlet Encoder (gzip)
  → CookieAuthenticator (validates session cookie)
  → Router (dispatches to Resource by URL pattern)
  → Resource (gets User from request context)
  → SqlSession (MyBatis, from BuddiApplication's SqlSessionFactory)
  → MyBatis Mapper (XML-defined SQL)
  → CryptoUtil.decryptWrapper() (if user has encryption enabled)
  → JSON response
```

### Authentication

- **Password storage**: SHA-512 with 20,000 iterations and 96-byte salt (auto-upgrades legacy SHA-256)
- **Sessions**: Encrypted cookies via moss-restlet CookieAuthenticator
- **Cookie encryption key**: Stored in `buddi_system` table; nullifying it invalidates all sessions
- **2FA**: TOTP-based with one-time backup codes stored in `user_totp_backups`
- **Registration**: Email activation key workflow
- **User identifier**: Hashed email (not stored in plaintext for privacy)

---

## Frontend Architecture

### Framework: ExtJS 6.2.0

Classic MVC pattern with Controllers, Views, and Stores. No build tooling - files are served directly and loaded dynamically by `Ext.Loader`.

### File Organization

```
src/main/webapp/
├── index.html                    # FreeMarker template (entry point)
├── buddilive/                    # Application code (71 JS files)
│   ├── Application.js            # Ext.application config
│   ├── controller/               # 15 controllers (event handlers)
│   │   ├── Viewport.js           # Main layout controller
│   │   ├── Reports.js            # Report dispatch
│   │   ├── account/Tree.js       # Account tree interactions
│   │   ├── budget/Tree.js        # Budget tree interactions
│   │   ├── transaction/          # Transaction + split logic
│   │   ├── scheduled/            # Scheduled transaction logic
│   │   ├── preferences/          # Settings controllers
│   │   └── restore/              # Import/restore logic
│   ├── store/                    # 10 data stores (AJAX proxies)
│   │   ├── account/TreeStore.js
│   │   ├── budget/TreeStore.js
│   │   ├── transaction/ListStore.js
│   │   ├── scheduled/ListStore.js
│   │   └── preferences/*.js      # Combobox stores
│   └── view/                     # 46 view components
│       ├── Viewport.js           # Border layout (tabs: Accounts, Budget)
│       ├── account/              # Account tree + editor
│       ├── budget/               # Budget panel + tree + editor
│       ├── transaction/          # Transaction grid + editor + splits
│       ├── scheduled/            # Scheduled tx list + editor + 10 freq panels
│       ├── report/               # 7 report views + date picker
│       ├── preferences/          # Settings + password + restore forms
│       ├── component/            # 9 reusable components
│       │   ├── CurrencyField.js  # Locale-aware currency input
│       │   ├── DescriptionCombobox.js  # Autocomplete
│       │   ├── LazyCombo*.js     # On-demand loading combos
│       │   └── PasswordField.js
│       └── ads/top.html
├── css/
│   ├── buddilive.css             # Minimal app styles
│   └── login.css                 # Login page styles
├── img/                          # Icons (40+ PNGs + Fugue icon set)
└── doc/                          # Static HTML documentation (11 pages)
```

### Key UI Screens

1. **My Accounts** (tab) - Tree view of accounts with balances; transaction grid below
2. **My Budget** (tab) - Hierarchical category tree with budget entry editing
3. **Transaction Editor** - Docked panel for recording transactions with multi-split support
4. **Reports** - Pie charts, line charts, and data tables (6 report types)
5. **Scheduled Transactions** - Grid with complex frequency editor (10 schedule patterns)
6. **Preferences** - User settings, password change, backup/restore

### API Communication

- All data via `Ext.data.proxy.Ajax` with JSON reader/writer
- Endpoints: `data/*.json` (e.g., `data/transactions.json`, `data/accounts.json`)
- Buffered rendering for transaction grid (250 items per page)
- `Ext.util.TaskManager` runs hourly check for due scheduled transactions
- UI state persisted via `Ext.state.LocalStorageProvider`

---

## Database Schema

10 tables managed by Liquibase migrations (`src/main/webapp/WEB-INF/liquibase/master.xml`):

```
┌──────────────────┐     ┌──────────────────┐
│      users       │     │   buddi_system   │
│──────────────────│     │──────────────────│
│ id, uuid         │     │ id (=1)          │
│ identifier (hash)│     │ cookie_encrypt_  │
│ credentials      │     │   ion_key        │
│ encryption_key   │     └──────────────────┘
│ locale, currency │
│ totp_secret      │     ┌──────────────────┐
│ premium (Y/N)    │     │user_totp_backups │
└────────┬─────────┘     │──────────────────│
         │               │ user_id (FK)     │
         │               │ totp_backup      │
    ┌────┴────┐          │ used             │
    │         │          └──────────────────┘
    │         │
    │    ┌────┴───────────┐     ┌──────────────────────────┐
    │    │user_activations│     │       sources            │
    │    │────────────────│     │──────────────────────────│
    │    │ user_id (FK)   │     │ id, uuid, user_id (FK)   │
    │    │ activation_key │     │ name, type (D/C/I/E)     │
    │    └────────────────┘     │ account_type, balance    │
    │                           │ period_type, parent (FK) │
    │                           └──────┬───────────────────┘
    │                                  │
    │    ┌─────────────────────┐       │
    │    │      entries        │       │
    │    │─────────────────────│       │
    │    │ category (FK)───────┼───────┘
    │    │ user_id (FK)        │
    │    │ amount, entry_date  │
    │    └─────────────────────┘
    │
    │    ┌─────────────────────┐    ┌──────────────────────┐
    │    │    transactions     │    │       splits          │
    │    │─────────────────────│    │──────────────────────│
    │    │ id, uuid            │    │ transaction_id (FK)  │
    │    │ user_id (FK)        │    │ from_source (FK)─────┼→ sources
    │    │ description, date   │←───┤ to_source (FK)───────┼→ sources
    │    │ number              │    │ amount, memo         │
    │    └─────────────────────┘    │ from_balance,        │
    │                               │   to_balance         │
    │    ┌─────────────────────┐    └──────────────────────┘
    │    │scheduledtransactions│    ┌──────────────────────┐
    │    │─────────────────────│    │   scheduledsplits    │
    │    │ id, uuid            │    │──────────────────────│
    │    │ user_id (FK)        │←───┤ scheduledtxn_id (FK) │
    │    │ description, number │    │ from_source (FK)     │
    │    │ schedule_*          │    │ to_source (FK)       │
    │    │ frequency_type      │    │ amount, memo         │
    │    │ start/end/last_date │    └──────────────────────┘
    │    └─────────────────────┘
```

**Source types** (`type` column):
- `D` = Debit account, `C` = Credit account
- `I` = Income category, `E` = Expense category

**Multi-tenant isolation**: All tables include `user_id` foreign key; all queries filter by user.

---

## Build & Deployment

### Build

```bash
mvn clean package              # → target/buddilive.war
mvn clean package -Ptest       # → target/buddilive-test.war
mvn exec:java -Pstandalone     # standalone Jetty on port 8686
```

### Configuration

| File | Purpose |
|------|---------|
| `conf/server/config.properties` | Production DB + mail config |
| `conf/test/config.properties` | Test environment config |
| `src/main/webapp/WEB-INF/web.xml` | Servlet mapping (Restlet → `/*`) |
| `src/main/webapp/WEB-INF/liquibase/master.xml` | DB schema changelog |
| `conf/logging.properties` | Log levels and file handler |
| `.env` | Docker secrets (DB/mail passwords) |

### Internationalization

15 locales supported via `src/main/resources/i18n*.properties`:
EN_US, DE, ES, ES_MX, FR, IT, NL, NO, PT, PT_BR, RU, EL, HE, SR, SV

---

## Key Design Patterns

- **Multi-tenant by user_id**: Every database query filters by the authenticated user's ID
- **Double-entry bookkeeping**: Transactions have splits with `from_source` and `to_source`, each tracking running balances
- **Encrypted user data**: Optional AES-256 encryption of sensitive fields, decrypted at read time via `CryptoUtil`
- **Convention-based routing**: URL paths map directly to Restlet Resource classes
- **Server-side rendering for auth**: FreeMarker template checks `<#if user??>` to serve login vs. app
- **Hourly scheduled transaction execution**: Frontend `TaskManager` triggers backend to create transactions from due schedules
