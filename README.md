# OrderCraft

OrderCraft is a full-stack order management application for customer records, products, bills of materials, sales orders, procurement, invoices, and payments.

## Technology

- **Frontend:** Angular 22.2.0, TypeScript 6.0, RxJS, Angular HttpClient
- **Backend:** Java 25, Spring Boot 4.1.1, Spring MVC, Spring Security, Spring Data JPA / Hibernate
- **Authentication:** JWT
- **Database:** MySQL 8.4 (`ordercraft`)
- **Automated tests:** Angular/Vitest and Maven/JUnit; backend tests use isolated H2 configuration

## Architecture

The Angular single-page application calls the Spring REST API. The backend is organized into controllers, services, Spring Data repositories, JPA entities, and DTOs. A JWT interceptor attaches the saved token to API requests, while the Angular route guard and Spring Security protect application routes and API endpoints.

```text
Angular pages and services
          │ HTTP + JWT
          ▼
Spring REST controllers → business services → JPA repositories → MySQL
```

## Features

- Customer and product management
- Product BOM management and material requirement calculation
- Sales orders and line items, with totals recalculated from item quantity and unit price
- Purchase orders associated with sales-order demand and BOM materials
- Invoices associated with sales orders; the service allows one invoice per sales order and checks the invoice amount against the order total
- Payments associated with invoices; the service prevents overpayment and recalculates invoice payment status
- Dashboard summaries and cross-module global search
- Registration, login, JWT-based API access, and protected frontend routes

Related records and financial rules are validated by backend services. The project does not implement separate supplier, tax, or inventory modules.

## Prerequisites

- Java 25
- Node.js compatible with Angular CLI 22.2: `^22.22.3`, `^24.15.0`, or `>=26.0.0`
- npm (the project declares npm 11.16.0)
- MySQL 8.4, with the existing local database/schema named `ordercraft` available to the configured database account

The frontend currently targets `http://localhost:8081` in `frontend/src/app/api.ts`. The backend listens on port `8081`; the Angular development server uses port `4200`.

## Local setup

Run commands from the repository root unless noted. The backend reads database settings and the JWT signing key from environment variables. Keep real values in your local environment or secret manager; do not put them in source files or Git.

In a PowerShell window, configure the backend environment and start Spring Boot:

```powershell
$env:DB_URL = "jdbc:mysql://localhost:3306/ordercraft"
$env:DB_USERNAME = Read-Host "MySQL username"
$dbPassword = Read-Host "MySQL password" -AsSecureString
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new("", $dbPassword).Password
$jwtSecret = Read-Host "JWT signing key (at least 32 UTF-8 bytes)" -AsSecureString
$env:JWT_SECRET = [System.Net.NetworkCredential]::new("", $jwtSecret).Password

Set-Location .\Backend
.\mvnw.cmd spring-boot:run
```

Keep a stable local `JWT_SECRET` across backend restarts if existing tokens need to remain valid. These variables are scoped to that PowerShell session. The database schema must already exist; the application does not provision a new MySQL server or database.

In a second PowerShell window, install frontend dependencies and start Angular:

```powershell
Set-Location .\frontend
npm ci
npm start -- --host localhost --port 4200
```

Open [http://localhost:4200](http://localhost:4200).

The current backend configuration uses Hibernate `ddl-auto=update`. Treat this as a local-development convenience, not a production migration strategy. Production deployment needs reviewed, versioned schema migrations and deployment-specific secret/configuration management.

## Automated verification

Run backend tests from `Backend`:

```powershell
Set-Location .\Backend
.\mvnw.cmd test
```

Run frontend tests and the production build from `frontend`:

```powershell
Set-Location .\frontend
npm test -- --watch=false
npm run build
```

The backend test configuration uses an isolated in-memory H2 database. Passing automated tests does not by itself verify data in the live MySQL database or browser behavior.

Latest local run for this repository state:

- Backend: **33 tests passed**, 0 failures/errors
- Frontend: **41 tests passed** across 11 test files
- Angular production build: **passed**
- `git diff --check`: **passed**

## Manual validation

Separate from the automated suites, the project owner reports manual testing of Customer, Product, BOM, Sales Order and Sales Order Item, Purchase Order, Invoice, and Payment flows. Reported scenarios include partial and full payments, overpayment rejection, duplicate-invoice prevention, Purchase Order editing, and payment editing. These are manual checks, not browser automation or CI results.

## Screenshots

No project screenshots are currently included. For a portfolio presentation, capture genuine screenshots of the dashboard, a sales order with its items and recalculated total, BOM material requirements, and invoice/payment balance states after running the application with suitable demo data.
