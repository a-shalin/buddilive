# BuddiLive Architecture Document

BuddiLive is a web-based personal finance application for budgeting, account tracking, and financial reporting. It is a multi-tenant system where each user's data is isolated by `user_id`.

## Technology Stack

| Layer | Technology |
|-------|-----------|
| Frontend | ExtJS 6.2.0 (Classic Gray theme, served from `static/`) |
| Backend Framework | Spring Boot 4.1.0 (Spring MVC, embedded Tomcat 11) |
| Templating | FreeMarker 2.3.34 |
| ORM | MyBatis 3.5.19 via mybatis-spring-boot-starter (XML-based mappers) |
| Database | PostgreSQL (production) / Derby (standalone/test) |
| Connection Pool | HikariCP (Spring Boot default) |
| DB Migrations | Liquibase 4.29.2 (Spring Boot auto-configuration) |
| Auth | Spring Security + custom cookie filter (SHA-512 hashing, AES-256 encryption) |
| 2FA | TOTP with backup codes (ZXing for QR generation) |
| Build | Maven (Java 21 target, Spring Boot Maven plugin) |
| Deployment | Docker + Nginx + Ansible |
| Java Target | 21 |

### Custom Libraries (moss framework)

- **moss-common** - Common utilities
- **moss-crypto** - Cryptographic utilities (AES-256, password hashing)
- **moss-auth** - Authentication UI resources (login panel, FreeMarker templates, ExtJS components)

These moss modules are vendored in-repo under `src/main/java/ca/digitalcave/moss`.

---

## High-Level Architecture

```
                    ┌──────────────┐
                    │    Nginx     │  (SSL/TLS termination, reverse proxy)
                    │   :80/:443  │
                    └──────┬───────┘
                           │
                    ┌──────┴───────┐
                    │  BuddiLive   │  (Embedded Tomcat :8080)
                    │  Application │
                    ├──────────────┤
                    │  Spring MVC  │  Controllers (REST API)
                    │  Security    │  CookieAuthenticationFilter
                    │  Compression │  (server.compression.enabled)
                    ├──────────────┤
                    │   MyBatis    │  SQL mapping layer
                    │  HikariCP   │
                    ├──────────────┤
                    │  Liquibase   │  Schema migrations (auto-run on startup)
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
├── BuddiSpringApplication.java       # @SpringBootApplication entry point
├── config/
│   ├── AppConfig.java                # Shared beans (Crypto, JsonFactory, etc.)
│   ├── SecurityConfig.java           # Spring Security filter chain
│   └── WebConfig.java                # Static resources, view controllers, filters
├── controller/                       # Spring MVC controllers
│   ├── AccountsController.java
│   ├── AuthenticationController.java # Login, register, password reset, 2FA
│   ├── CategoriesController.java
│   ├── ChangePasswordController.java
│   ├── DataManagementController.java # Backup, export, restore
│   ├── DescriptionsController.java
│   ├── DonationController.java
│   ├── GlobalExceptionHandler.java   # @RestControllerAdvice error handler
│   ├── IndexController.java          # FreeMarker-templated entry point
│   ├── ParentsController.java
│   ├── PeriodsController.java
│   ├── ReportController.java         # 6 report types
│   ├── ScheduledTransactionsController.java
│   ├── SourcesController.java
│   ├── StoreController.java          # Currencies, locales
│   ├── TransactionsController.java
│   └── UserPreferencesController.java
├── db/                               # Data access layer
│   ├── Users.java/.xml               # User CRUD + auth queries
│   ├── Sources.java/.xml             # Accounts & categories
│   ├── Transactions.java/.xml        # Transactions & splits
│   ├── ScheduledTransactions.java/.xml
│   ├── Entries.java/.xml             # Budget entries
│   ├── BuddiSystem.java/.xml         # System settings (cookie key)
│   ├── handler/                      # MyBatis type handlers
│   │   ├── BooleanHandler.java       #   Y/N → boolean
│   │   ├── CurrencyHandler.java      #   code → Currency
│   │   └── LocaleHandler.java        #   string → Locale
│   └── util/
│       ├── ConstraintsChecker.java    # Business rule validation
│       ├── DataUpdater.java           # Batch update operations
│       └── DatabaseException.java
├── model/                            # Domain objects
│   ├── User.java                     # User account (extends AuthUser)
│   ├── Account.java                  # Debit/credit account (extends Source)
│   ├── Category.java                 # Income/expense category (extends Source)
│   ├── Source.java                    # Base class for Account/Category
│   ├── AccountType.java              # Account type grouping
│   ├── CategoryPeriod.java           # Budget period enum (WEEK/MONTH/QUARTER/YEAR)
│   ├── Transaction.java              # Financial transaction
│   ├── Split.java                    # Transaction line item (from/to/amount)
│   ├── Entry.java                    # Budget entry
│   ├── ScheduledTransaction.java     # Recurring transaction template
│   └── report/
│       └── Interval.java             # Report time interval
├── security/
│   ├── BuddiLiveAuthenticationHelper.java # Login, register, password reset, 2FA
│   ├── CookieAuthenticationFilter.java    # Reads/validates encrypted session cookie
│   ├── CookieAuthenticationToken.java     # Spring Security token
│   └── CookieUtil.java                    # Cookie encrypt/decrypt/serialize
└── util/
    ├── CryptoUtil.java               # Encrypt/decrypt user data fields
    ├── FormatUtil.java               # Locale-aware date/currency formatting
    └── LocaleUtil.java               # Locale management
```

### REST API Routes

All data routes are under `/data/` and require authentication (cookie-based).

| Method | Path | Controller | Description |
|--------|------|------------|-------------|
| GET/POST | `/data/accounts` | AccountsController | Account CRUD |
| GET/POST | `/data/categories` | CategoriesController | Category CRUD |
| GET/POST | `/data/categories/periods` | PeriodsController | Budget periods |
| GET/POST | `/data/categories/parents` | ParentsController | Parent categories |
| GET/POST | `/data/transactions` | TransactionsController | Transaction CRUD |
| GET/POST | `/data/transactions/descriptions` | DescriptionsController | Autocomplete |
| GET/POST | `/data/scheduledtransactions` | ScheduledTransactionsController | Scheduled tx CRUD |
| GET/POST | `/data/scheduledtransactions/execute` | ScheduledTransactionsController | Execute due txns |
| GET/POST | `/data/sources/from` | SourcesController | Source selector (from) |
| GET/POST | `/data/sources/to` | SourcesController | Source selector (to) |
| GET/POST | `/data/changepassword` | ChangePasswordController | Password change |
| GET/POST | `/data/userpreferences` | UserPreferencesController | User settings |
| GET/POST | `/data/backup` | DataManagementController | JSON backup |
| GET/POST | `/data/export` | DataManagementController | CSV export |
| GET/POST | `/data/restore` | DataManagementController | Restore from backup |
| GET | `/data/report/*` | ReportController | 6 report types |
| GET | `/stores/currencies` | StoreController | Currency list |
| GET | `/stores/locales` | StoreController | Locale list |

**Public routes** (no auth required):
- `GET /` → Redirect to `/index`
- `GET /index` → IndexController (FreeMarker-templated login or app)
- `/authentication/**` → AuthenticationController (login/register/password reset/2FA)
- `GET /donation-completed` → DonationController
- Static files served from `classpath:/static/` by Spring Boot

### Request Flow

```
HTTP Request
  → Spring Security filter chain
  → CookieAuthenticationFilter (validates encrypted session cookie)
  → DispatcherServlet (routes to @Controller by @RequestMapping)
  → Controller method (@Autowired MyBatis mappers)
  → MyBatis Mapper (XML-defined SQL, Spring-managed SqlSession)
  → CryptoUtil.decryptWrapper() (if user has encryption enabled)
  → JSON response (ResponseEntity or StreamingResponseBody)
```

### Authentication

- **Password storage**: SHA-512 with 20,000 iterations and 96-byte salt (auto-upgrades legacy SHA-256)
- **Sessions**: Encrypted cookies via CookieAuthenticationFilter + CookieUtil
- **Session lifecycle**: Backend exposes cookie timeout metadata; ExtJS schedules pre-expiry logout and redirects on `401`
- **Cookie encryption key**: Stored in `buddi_system` table; nullifying it invalidates all sessions
- **2FA**: TOTP-based with one-time backup codes stored in `user_totp_backups`
- **Registration**: Email activation key workflow (server mode) or direct registration (standalone mode)
- **User identifier**: Hashed login identifier for lookup; optional recoverable email is stored separately
- **Spring Security**: Custom `SecurityFilterChain` with CSRF disabled, `CookieAuthenticationFilter` before `UsernamePasswordAuthenticationFilter`

---

## Frontend Architecture

### Framework: ExtJS 6.2.0

Classic MVC pattern with Controllers, Views, and Stores. App classes are loaded dynamically by `Ext.Loader`.

### File Organization

```
src/main/resources/static/
├── buddilive/                    # Application code (74 JS files)
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

src/main/resources/ca/digitalcave/moss/auth/resource/ui/extjs/
└── app/Application.js            # Authentication/login ExtJS app entry point
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
- Endpoints: `data/*` (e.g., `data/transactions`, `data/accounts`)
- Buffered rendering for transaction grid (250 items per page)
- `Ext.util.TaskManager` runs hourly check for due scheduled transactions
- UI state persisted via `Ext.state.LocalStorageProvider`
- `IndexController` injects per-user runtime config (`__buddiConfig`) including formatting preferences and session timeout values

---

## Database Schema

10 tables managed by Liquibase migrations (`src/main/resources/db/changelog/master.xml`):

```
┌──────────────────┐     ┌──────────────────┐
│      users       │     │   buddi_system   │
│──────────────────│     │──────────────────│
│ id, uuid         │     │ id (=1)          │
│ identifier (hash)│     │ cookie_encrypt_  │
│ email            │     │   ion_key        │
│ credentials      │     └──────────────────┘
│ encryption_key   │
│ encryption_ver   │     ┌──────────────────┐
│ premium (Y/N)    │     │user_totp_backups │
│ locale, currency │     │──────────────────│
│ use_two_factor   │     │ user_id (FK)     │
│ totp_secret      │     │ totp_backup      │
│ override_*       │     │ used             │
│ show_currency_*  │     └──────────────────┘
│ show_deleted     │
│ last_login       │
└──────────────────┘

┌────────────────┐         ┌──────────────────────────┐
│user_activations│         │       sources            │
│────────────────│         │──────────────────────────│
│ user_id (FK)   │         │ id, uuid, user_id (FK)   │
│ activation_key │         │ name, type (D/C/I/E)     │
└────────────────┘         │ account_type, balance    │
                           │ period_type, parent (FK) │
                           └──────┬───────────────────┘
                                  │
┌─────────────────────┐           │
│      entries        │           │
│─────────────────────│           │
│ category (FK)───────┼───────────┘
│ user_id (FK)        │
│ amount, entry_date  │
└─────────────────────┘

┌─────────────────────┐    ┌──────────────────────┐
│    transactions     │    │       splits         │
│─────────────────────│    │──────────────────────│
│ id, uuid            │    │ transaction_id (FK)  │
│ user_id (FK)        │    │ from_source (FK)─────┼→ sources
│ description, date   │←───┤ to_source (FK)───────┼→ sources
│ number              │    │ amount, memo         │
└─────────────────────┘    │ from_balance,        │
                           │   to_balance         │
                           └──────────────────────┘

┌─────────────────────┐    ┌──────────────────────┐
│scheduledtransactions│    │   scheduledsplits    │
│─────────────────────│    │──────────────────────│
│ id, uuid            │    │ scheduledtxn_id (FK) │
│ user_id (FK)        │←───┤ from_source (FK)     │
│ description, number │    │ to_source (FK)       │
│ schedule_*          │    │ amount, memo         │
│ frequency_type      │    └──────────────────────┘
│ start/end/last_date │
└─────────────────────┘
```

**Source types** (`type` column):
- `D` = Debit account, `C` = Credit account
- `I` = Income category, `E` = Expense category

**Users table highlights**: includes identifier + optional recoverable email, encryption metadata (`encryption_key`, `encryption_version`), 2FA fields (`use_two_factor`, `totp_secret`), and formatting preferences (`override_*`, `show_currency_symbol`, `currency_spacing`, `show_deleted`).

**Multi-tenant isolation**: All tables include `user_id` foreign key; all queries filter by user.

---

## Build & Deployment

### Build

```bash
mvn clean package                     # → target/buddilive.jar (server profile)
mvn clean package -Pstandalone        # → target/buddilive.jar (standalone profile)
mvn verify -Pe2etest                  # E2E tests (embedded Tomcat + Derby)
```

### Spring Boot Profiles

| Profile | Config file | Database | Registration |
|---------|-------------|----------|--------------|
| `server` | `application-server.properties` | PostgreSQL (env vars) | Email activation |
| `standalone` | `application-standalone.properties` | Embedded Derby | Direct (no email) |
| `e2etest` | `application-e2etest.properties` | Embedded Derby | Email activation |

### Configuration

| File | Purpose |
|------|---------|
| `application.properties` | Common config (port, compression, MyBatis, Liquibase) |
| `application-server.properties` | Production DB + mail config (env var placeholders) |
| `application-standalone.properties` | Derby DB, direct registration, no mail |
| `application-e2etest.properties` | Derby DB for E2E tests |
| `src/main/resources/db/changelog/master.xml` | Liquibase schema changelog |
| `.env` | Docker secrets (DB/mail passwords) |

### Docker Deployment

```bash
mvn clean package -DskipTests
cp target/buddilive.jar docker/
docker compose up --build -d
```

The Docker stack includes:
- **app** — Spring Boot JAR with embedded Tomcat (port 8080)
- **postgres** — PostgreSQL 16
- **nginx** — Reverse proxy with SSL termination (Let's Encrypt)
- **certbot** — Automatic certificate renewal

Ansible playbook (`ansible/playbooks/deploy.yml`) automates the full deployment.

### Internationalization

15 locales supported via `src/main/resources/i18n*.properties`:
EN_US, DE, ES, ES_MX, FR, IT, NL, NO, PT, PT_BR, RU, EL, HE, SR, SV

---

## Key Design Patterns

- **Multi-tenant by user_id**: Every database query filters by the authenticated user's ID
- **Double-entry bookkeeping**: Transactions have splits with `from_source` and `to_source`, each tracking running balances
- **Encrypted user data**: Optional AES-256 encryption of sensitive fields, decrypted at read time via `CryptoUtil`
- **Spring MVC controllers**: `@RestController` classes with `@Autowired` MyBatis mappers and `@Transactional` write operations
- **Server-side rendering for auth**: FreeMarker template checks `<#if user??>` to serve login vs. app
- **Proactive session expiry handling**: frontend schedules logout before cookie expiry and immediately logs out on unauthorized responses
- **Premium mode behavior**: runtime user config currently emits `premium=true`, effectively enabling premium-gated UI features for all users
- **Hourly scheduled transaction execution**: Frontend `TaskManager` triggers backend to create transactions from due schedules
