# ShelfSync — Implementation Plan

**ShelfSync** is a personal inventory and billing service: a Java/Spring Boot backend that tracks
inventory in real time and bills sales automatically.

- **DynamoDB** holds the *live state* of every item (current stock, price, reorder level). Stock
  changes use conditional, atomic updates so concurrent sales can never oversell.
- **RDS MySQL** holds the *history*: every stock movement, every order and every invoice, as an
  append-only ledger that can be reported on with SQL.
- **AWS Lambda** generates invoices (PDF) off the request path.
- **S3** stores generated invoice PDFs; the API hands out short-lived presigned download URLs.
- Everything is runnable locally with Docker Compose (MySQL + LocalStack).

## Key decisions

| Decision | Choice | Why |
|---|---|---|
| Language / framework | Java 17, Spring Boot 3.3, Maven multi-module | LTS Java, standard Spring stack |
| DynamoDB access | AWS SDK v2 Enhanced Client + low-level `UpdateItem` for atomic counters | Conditional expressions (`stock >= :qty`) make decrements race-free |
| MySQL access | Spring Data JPA + Flyway migrations | Versioned schema, simple repositories |
| Cross-store consistency | Saga-style: reserve stock in DynamoDB, write ledger in MySQL, compensate (re-increment) on failure | No distributed transactions across DynamoDB and MySQL |
| Invoice rendering | Shared `invoice-core` module (OpenPDF) used by the Lambda and by an in-process fallback | Same output in AWS and in local dev without deploying Lambda |
| Invoice delivery | API invokes Lambda asynchronously-ish (synchronous invoke from a background executor); scheduled job retries failed invoices | Checkout latency is unaffected by PDF generation |
| Auth | Single-user API key header (`X-Api-Key`) | It is a *personal* service; keep it simple |

## Modules

```
ShelfSync/
├── pom.xml                  # parent POM
├── invoice-core/            # invoice model + PDF renderer (plain Java)
├── invoice-lambda/          # AWS Lambda handler: render PDF -> S3
└── shelfsync-api/           # Spring Boot REST service
```

---

## Phase 1 — Project scaffold & local infrastructure

Deliverables
- Maven parent POM with three modules, dependency management (Spring Boot BOM, AWS SDK BOM).
- Spring Boot application class, `application.yml` with env-driven config, `local` profile.
- AWS client configuration (DynamoDB, S3, Lambda) with optional endpoint override for LocalStack.
- `docker-compose.yml` with MySQL 8 and LocalStack; LocalStack init script creating the DynamoDB
  table and S3 bucket. `.env.example`.
- API-key filter and global error handler skeleton.

Files: `pom.xml`, `*/pom.xml`, `shelfsync-api/src/main/java/com/shelfsync/{ShelfSyncApplication,config/*,web/ApiKeyFilter,web/GlobalExceptionHandler}.java`,
`shelfsync-api/src/main/resources/application*.yml`, `docker-compose.yml`, `infra/localstack/init.sh`, `.env.example`.

## Phase 2 — Live inventory on DynamoDB

Deliverables
- `InventoryItem` DynamoDB bean (sku, name, category, unitPrice, stock, reorderLevel, version, updatedAt).
- `InventoryRepository`: CRUD via Enhanced Client with optimistic locking; atomic
  `adjustStock(sku, delta)` with condition `stock + delta >= 0`.
- `InventoryService` + `InventoryController`: create/update/list/get/delete items, restock,
  manual adjustment, low-stock listing.
- Request/response DTOs with Bean Validation; domain exceptions mapped to HTTP codes.

Files: `inventory/*` under `shelfsync-api/src/main/java/com/shelfsync/`.

## Phase 3 — Transaction ledger & billing on MySQL

Deliverables
- Flyway migration `V1__init.sql`: `stock_movements`, `orders`, `order_lines`, `invoices`.
- JPA entities + repositories.
- Every inventory change (restock, adjustment, sale, compensation) writes a `StockMovement`.
- `BillingService.checkout(...)`: validates items, atomically reserves stock in DynamoDB line by
  line, compensates already-reserved lines on any failure, computes subtotal/tax/total, persists
  the order and a `PENDING` invoice, and publishes an `OrderPlacedEvent`.
- `OrderController` (checkout, get, list) and `LedgerController` (movement history per SKU / range).

Files: `ledger/*`, `billing/*`, `resources/db/migration/V1__init.sql`.

## Phase 4 — Invoice generation with Lambda + S3

Deliverables
- `invoice-core`: `InvoiceRequest` model and `InvoicePdfRenderer` (OpenPDF).
- `invoice-lambda`: `InvoiceHandler` (`RequestHandler<InvoiceRequest, InvoiceResult>`) that renders
  the PDF and uploads it to S3 under `invoices/{yyyy}/{MM}/{invoiceNumber}.pdf`.
- API side: `InvoiceGenerator` interface with `LambdaInvoiceGenerator` (invokes the function) and
  `LocalInvoiceGenerator` (renders in-process and uploads to S3) selected by config.
- `InvoiceService`: listens for `OrderPlacedEvent` after commit, generates asynchronously, marks
  invoice `GENERATED`/`FAILED`; scheduled retry for failed/pending invoices; presigned URL endpoint.

Files: `invoice-core/src/**`, `invoice-lambda/src/**`, `shelfsync-api/.../invoice/*`.

## Phase 5 — Tests, packaging & docs

Deliverables
- Unit tests: renderer, billing checkout/compensation, inventory service, controller (MockMvc).
- `Dockerfile` for the API (multi-stage), Lambda shaded jar, `infra/deploy-lambda.sh`.
- `.gitignore`, `README.md` with architecture diagram, run and test instructions.

Files: `**/src/test/**`, `shelfsync-api/Dockerfile`, `infra/deploy-lambda.sh`, `.gitignore`, `README.md`.
