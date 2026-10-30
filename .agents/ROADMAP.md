# Portfolio Implementation & Git Commit Roadmap

A phased, commit-by-commit roadmap structured into two primary tracks:
1. **Backend & Endpoints:** Domain $\rightarrow$ Persistence & Queue $\rightarrow$ LLM & Publisher $\rightarrow$ Endpoint API & OpenAPI $\rightarrow$ Full Endpoint Testing.
2. **Frontend UI:** App Shell & Routes $\rightarrow$ Data Fetching & SSE Integration $\rightarrow$ Component Styling & UX Polish.

---

## 🏛️ Track 1: Domain $\rightarrow$ Backend $\rightarrow$ Test Endpoints

### Milestone 0: Clean Baseline
* **Commit 1: `chore: initialize repository scaffolding, architecture design, and build definitions`**
  - Localized `.gitignore` files for root, `server/`, and `web/` workspaces.
  - Baseline configs (`server/build.sbt`, `web/package.json`).
  - System architecture and design documentation in `.agents/`.

### Milestone 1: Domain Modeling
* **Commit 2: `feat(domain): define core scala 3 domain models and algebraic data types`**
  - Scala 3 ADTs & enums with `zio.schema.Schema`: `WritingTone`, `ArticleStatus` (typed state machine).
  - Domain case classes: `Persona`, `Tag`, `ArticleJob`, `CreatePersonaRequest`, `QueueArticleRequest`.
  - MUnit tests validating domain transitions and validation invariants.

### Milestone 2: Functional Database & LISTEN/NOTIFY Queue
* **Commit 3: `feat(db): configure skunk postgres client and zio-cats interop session pool`**
  - Add `skunk-core` and `zio-interop-cats` to `server/build.sbt`.
  - SQL DDL migrations: tables (`personas`, `tags`, `persona_tags`, `articles`), status enum, and `pg_notify` trigger.
  - Skunk session pool managed as a `ZLayer`.
* **Commit 4: `feat(db): implement persona and article repositories with bidirectional codecs`**
  - Skunk codecs and queries: insert/select personas, tag relationships, insert articles.
  - Atomic job claiming queries using `FOR UPDATE SKIP LOCKED`.
  - Repository integration tests.
* **Commit 5: `feat(queue): implement postgres listen/notify event dispatcher using skunk channels`**
  - Background fiber listening to `article_events` via `session.channel(id"article_events").listen(...)`.
  - Event fan-out to `ZIO.Hub` (for SSE) and worker wakeup triggers with 0% idle CPU.

### Milestone 3: LLM Engine & Conduit Publisher
* **Commit 6: `feat(llm): implement llama.cpp client with grammar-constrained json schema generation`**
  - ZIO HTTP client for `llama-server` (`POST /completion`).
  - Structured JSON Schema generation (GBNF grammar clamping).
  - Throttling with `ZIO.Semaphore(permits = maxGpuSlots)` to safeguard VRAM.
* **Commit 7: `feat(worker): implement generation worker to persist pending_approval drafts`**
  - Background fiber worker: claimed `queued_generation` job $\rightarrow$ invokes LLM $\rightarrow$ updates status to `pending_approval`.
* **Commit 8: `feat(publisher): implement conduit api client and publishing worker`**
  - ZIO HTTP client for RealWorld Conduit API: login/register persona $\rightarrow$ `POST /api/articles` with Bearer token.
  - Publishing worker fiber claiming `approved` articles, saving external slug, and setting `published`.

### Milestone 4: ZIO HTTP Endpoint API, OpenAPI & Real-Time SSE
* **Commit 9: `feat(api): define declarative endpoint api with zio-schema and swagger ui openapi docs`**
  - Declarative `Endpoint` definitions for `/api/personas`, `/api/articles`, and `/api/articles/:id/approve`.
  - Automatic OpenAPI 3.0 specification derivation with `OpenAPIGen.fromEndpoints`.
  - Interactive Swagger UI served directly at `/docs/openapi` via `SwaggerUI.routes`.
* **Commit 10: `feat(api): implement endpoint handlers and expose sse status stream`**
  - Implement handlers binding `Endpoint` definitions to domain repositories.
  - `GET /api/events` streaming Server-Sent Events from `ZIO.Hub`.
* **Commit 11: `test(api): add comprehensive integration test suite for all rest and sse endpoints`**
  - Automated tests verifying persona creation, article queuing, approval flow, OpenAPI spec availability, and SSE broadcast.

---

## 🎨 Track 2: UI $\rightarrow$ Routes $\rightarrow$ Get Data $\rightarrow$ Work on Style

### Milestone 5: App Shell & Routing
* **Commit 12: `feat(ui): scaffold application shell, navigation, and routing`**
  - React Router / views: Personas Hub, Article Studio, Approval Board, Publishing Audit, Swagger Link.
  - Base layout structure with navigation bar and status indicators.

### Milestone 6: Data Fetching & Real-Time Sync (Get Data)
* **Commit 13: `feat(ui): implement api client and real-time sse integration`**
  - Typed API service fetching personas, submitting article generation jobs, and sending approvals.
  - React hook subscribing to `/api/events` (EventSource) for live board updates without page refreshes.

### Milestone 7: Interactive Workflows & UI Polish (Work on Style)
* **Commit 14: `feat(ui): build styled persona manager with domain tag chips and creation modal`**
  - Persona cards with avatar, writing tone badges, and tag chips.
  - Manual creation and "Generate with AI" modal.
* **Commit 15: `feat(ui): build styled article generation studio with author tag filtering`**
  - Interactive author selector automatically scoping selectable tags to that author's expertise.
  - Topic/hint input and dispatch animation.
* **Commit 16: `feat(ui): build split-pane approval board with live markdown preview and editor`**
  - Side-by-side markdown editor and live GitHub-flavored preview.
  - "Approve & Publish", "Reject", and "Regenerate" actions with live feedback.
* **Commit 17: `feat(ui): build publishing audit log and external conduit link monitor`**
  - Live feed of published articles with direct links to the Conduit blog instance.

---

## 🚀 Track 3: Production Packaging & Portfolio Polish
* **Commit 18: `feat(build): configure embedded spa classloading and sbt-assembly fat-jar`**
  - Vite outputs directly to `server/src/main/resources/dist`.
  - Single-binary JAR packaging: `java -jar server-assembly.jar` serves API + UI.
* **Commit 19: `docs: create comprehensive portfolio readme with architecture diagrams, benchmarks, and quickstart`**
  - Portfolio presentation: architectural diagrams, clean code highlights, quickstart demo script.
