# Portfolio Implementation & Git Commit Roadmap

A phased, commit-by-commit roadmap structured into modular vertical slices:
1. **Subdomain 1 (Personas & Tags):** Domain $\rightarrow$ Persistence $\rightarrow$ LLM Seeder $\rightarrow$ Endpoints $\rightarrow$ Integration Tests.
2. **Subdomain 2 (Articles & Background Queue):** State Machine $\rightarrow$ Database & LISTEN/NOTIFY $\rightarrow$ Worker & Publisher $\rightarrow$ SSE Endpoints.
3. **Frontend UI:** App Shell & Routes $\rightarrow$ Data Fetching & SSE $\rightarrow$ Component Styling & Split-Pane Editor.
4. **Production Packaging:** Standalone Fat-JAR & Portfolio Polish.

---

## 🏛️ Track 1: Backend Vertical Slices

### Milestone 0: Clean Baseline `[COMPLETED]`
* **Commit 1: `chore: initialize repository scaffolding, architecture design, and build definitions`**
  - [x] Localized `.gitignore` files for root, `server/`, and `web/` workspaces.
  - [x] Baseline configs (`server/build.sbt`, `web/package.json`).
  - [x] System architecture and design documentation in `.agents/`.

---

### Milestone 1: Tag & Persona Subdomain (Vertical Slice) `[COMPLETED]`
* **Commit 2: `feat(domain): define tag and persona aggregates with ai generator ports`**
  - [x] `Tag` value object with lowercase and length invariants.
  - [x] `WritingTone` enum (Academic, Conversational, Provocative, TechnicalDeepDive, Storyteller, Humorous).
  - [x] `User` aggregate root with authentication credentials (`password`), bio, tone, and tag associations.
  - [x] Domain ports: `TagRepository`, `UserRepository`, `AiUserGenerator`.
* **Commit 3: `feat(db): implement postgres skunk persistence with flyway migrations`**
  - [x] Flyway DDL migration `V1__create_personas_and_tags.sql` with modern `INT GENERATED ALWAYS AS IDENTITY`.
  - [x] Non-blocking Skunk session pool managed as a `ZLayer` (`SkunkPool.live`).
  - [x] `PostgresTagRepository` and `PostgresUserRepository` with flat, co-located SQL queries.
* **Commit 4: `feat(llm): implement llama.cpp client with gbnf json schema clamping`**
  - [x] `LlamaCppClient` targeting local `llama.cpp` server (`http://127.0.0.1:9931`).
  - [x] Dynamic OpenAPI `JsonSchema` derivation from `zio.schema.Schema[T]` for logit clamping.
  - [x] `AppConfig` (`app.mode`) printing derived schemas to console in `dev` mode.
  - [x] Deterministic fallback synthesis in `LlmAiUserGenerator` for offline resilience.
* **Commit 5: `feat(api): expose declarative endpoints and auto-wired server bootstrap`**
  - [x] Case class route handlers (`TagRoutes`, `UserRoutes`) eliminating service lookup overhead (`Routes[Any, Response]`).
  - [x] Consolidated `ApiRoutes.layer` assembling tag/user routes + Swagger UI at `/docs/openapi`.
  - [x] Declarative ZIO 2 auto-wiring in `Main.scala` with zero manual plumbing combinators.
  - [x] Live PostgreSQL & LLM integration test suites running in < 2 seconds (`MainEndpointSpec`, `LlmClientSpec`).

---

### Milestone 2: Article Generation & Publishing Subdomain (Vertical Slice) `[COMPLETED]`
* **Commit 6: `feat(domain): define article aggregate, commands, and typed state machine`**
  - [x] `ArticleStatus` typed state machine: `QueuedGeneration` $\rightarrow$ `Generating` $\rightarrow$ `PendingApproval` $\rightarrow$ `Approved` $\rightarrow$ `Publishing` $\rightarrow$ `Published` / `Failed`.
  - [x] `Article` aggregate root (title, slug, body, description, tagList, authorId, status, conduitUrl).
  - [x] `ArticleRepository` domain port.
* **Commit 7: `feat(db): add articles schema and skunk atomic queue repository`**
  - [x] Flyway DDL migration `V2__create_articles_and_events.sql` with `articles` table and `article_events` channel.
  - [x] `PostgresArticleRepository` with `FOR UPDATE SKIP LOCKED` for atomic job claiming.
  - [x] PostgreSQL `LISTEN/NOTIFY` dispatcher using Skunk channels fanning out to `ZIO.Hub`.
* **Commit 8: `feat(worker): implement llm article generator and background worker fibers`**
  - [x] Markdown article content synthesis via `LlamaCppClient` using persona domain tags and tone.
  - [x] Background generation worker fiber: claims `QueuedGeneration` $\rightarrow$ generates draft $\rightarrow$ transitions to `PendingApproval`.
  - [x] ZIO concurrency safeguard with `ZIO.Semaphore` limiting concurrent LLM inference slots.
* **Commit 9: `feat(publisher): implement realworld conduit api client and publishing worker`**
  - [x] ZIO HTTP client for Conduit API: authenticate persona $\rightarrow$ `POST /api/articles` with Bearer token.
  - [x] Publishing worker fiber: claims `Approved` articles $\rightarrow$ posts to Conduit $\rightarrow$ records external URL $\rightarrow$ marks `Published`.
* **Commit 10: `feat(api): expose article management endpoints and real-time sse stream`**
  - [x] Declarative endpoints: `POST /api/articles` (enqueue), `GET /api/articles` (filter by status/author), `POST /api/articles/:id/approve`, `POST /api/articles/:id/regenerate`.
  - [x] `GET /api/events` streaming Server-Sent Events (SSE) from `ZIO.Hub` for real-time frontend synchronization.
  - [x] Comprehensive integration test suite for the complete article lifecycle.

---

## 🎨 Track 2: UI (React 19 + TypeScript + Vite + MUI)

### Milestone 3: App Shell & Routing `[NEXT UP]`
* **Commit 11: `feat(ui): scaffold application shell, navigation, and routing`**
  - [ ] React Router configuration: Personas Hub, Article Studio, Approval Board, Publishing Audit.
  - [ ] Responsive navigation drawer and system status indicators.

### Milestone 4: Data Fetching & Real-Time Sync
* **Commit 12: `feat(ui): implement typed api client and real-time sse subscription`**
  - [ ] Typed API client fetching tags, personas, and article jobs.
  - [ ] Custom React hook subscribing to `/api/events` (EventSource) for live UI state updates without polling.

### Milestone 5: Interactive Workflows & UI Polish
* **Commit 13: `feat(ui): build persona manager with domain tag chips and ai generation modal`**
  - [ ] Persona cards with avatar, writing tone badges, and tag chips.
  - [ ] "Generate Persona with AI" modal calling `POST /api/users/generate`.
* **Commit 14: `feat(ui): build split-pane approval board with live markdown editor`**
  - [ ] Side-by-side split-pane editor with live GitHub-flavored markdown preview.
  - [ ] "Approve & Publish", "Reject", and "Regenerate" actions with live status feedback.
* **Commit 15: `feat(ui): build publishing audit log and external conduit link monitor`**
  - [ ] Audit log feed displaying published articles with direct hyperlinks to the live Conduit blog.

---

## 🚀 Track 3: Production Packaging & Portfolio Polish

### Milestone 6: Single-Binary Assembly & Showcase
* **Commit 16: `feat(build): configure embedded spa static serving and sbt assembly`**
  - [ ] Vite production build output copied into `server/src/main/resources/dist`.
  - [ ] ZIO HTTP static resource routing serving embedded SPA on `GET /`.
  - [ ] Standalone fat-JAR packaging: single executable `java -jar server.jar` serving API + UI.
* **Commit 17: `docs: create comprehensive portfolio readme with architecture diagrams and quickstart`**
  - [ ] Architecture diagrams (Mermaid flowcharts of event-driven queue and LLM logit clamping).
  - [ ] Quickstart demo script and performance telemetry highlights.
