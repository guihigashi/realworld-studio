# RealWorld Conduit AI Studio 🖋️⚡

[![Scala 3](https://img.shields.io/badge/scala-3.10.0-DC322F.svg?style=flat&logo=scala)](https://www.scala-lang.org/)
[![ZIO 2](https://img.shields.io/badge/zio-2.1.27-orange.svg?style=flat)](https://zio.dev/)
[![ZIO HTTP](https://img.shields.io/badge/zio--http-3.11.6-blue.svg?style=flat)](https://github.com/zio/zio-http)
[![Skunk](https://img.shields.io/badge/skunk-2.0.0--RC3-teal.svg?style=flat)](https://typelevel.org/skunk/)
[![PostgreSQL](https://img.shields.io/badge/postgresql-16+-336791.svg?style=flat&logo=postgresql)](https://www.postgresql.org/)
[![Redis](https://img.shields.io/badge/redis-7+-DC382D.svg?style=flat&logo=redis)](https://redis.io/)
[![React 19](https://img.shields.io/badge/react-19.3.1-61DAFB.svg?style=flat&logo=react)](https://react.dev/)
[![TypeScript](https://img.shields.io/badge/typescript-5.8+-3178C6.svg?style=flat&logo=typescript)](https://www.typescriptlang.org/)
[![Vite](https://img.shields.io/badge/vite-8.3.5-646CFF.svg?style=flat&logo=vite)](https://vitejs.dev/)
[![MUI](https://img.shields.io/badge/mui-9.5.0-007FFF.svg?style=flat&logo=mui)](https://mui.com/)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

A high-throughput, human-in-the-loop autonomous AI content generation and publishing engine designed for **RealWorld / Conduit** platforms. 

Built with **Scala 3**, **ZIO 2**, and **React 19**, the studio manages expert user personas with domain expertise and tailored writing tones, queues deterministic LLM article generation jobs via token-clamped local models (`llama.cpp` / OpenAI API), provides an interactive split-pane approval studio with live GitHub-Flavored Markdown preview, and autonomously publishes approved articles to Conduit with Redis-backed JWT session caching.

---

## 📑 Table of Contents

- [Key Features](#-key-features)
- [Architecture & System Flow](#-architecture--system-flow)
- [Article Lifecycle State Machine](#-article-lifecycle-state-machine)
- [Core Engineering Highlights](#-core-engineering-highlights)
- [Getting Started](#-getting-started)
  - [Prerequisites](#prerequisites)
  - [1. Launch Infrastructure Services](#1-launch-infrastructure-services)
  - [2. Apply Database Migrations](#2-apply-database-migrations)
  - [3. Start Local LLM Inference](#3-start-local-llm-inference)
  - [4. Run Backend Server](#4-run-backend-server)
  - [5. Run Frontend Development Server](#5-run-frontend-development-server)
- [Interactive UI Capabilities](#-interactive-ui-capabilities)
- [REST API & Swagger Documentation](#-rest-api--swagger-documentation)
- [Testing](#-testing)
- [License](#-license)

---

## ✨ Key Features

* **Deterministic Structured AI Generation:** Eliminates hallucinated payloads and formatting errors. Generates domain-compliant personas using local LLMs (`llama.cpp` / `llama-server`) with JSON Schema & GBNF logit clamping derived directly from native `zio-schema` Case Classes, and synthesizes publication-ready articles via frontmatter-structured Markdown with target word count pacing (up to 2,000 words).
* **100% Non-Blocking Reactive Database Tier:** Built on **Skunk** (`skunk-core`) over pure asynchronous sockets (bypassing blocking JDBC thread pools). Uses PostgreSQL triggers and `LISTEN / NOTIFY` channels to dispatch events with **0% idle polling overhead**.
* **Atomic Job Claiming:** Workers safely compete for jobs using `FOR UPDATE SKIP LOCKED` inside transactional boundaries, guaranteeing zero duplicate processing or deadlocks across concurrent fibers.
* **Real-Time UI Reactive Sync (SSE):** Database events fan out into a centralized `ZIO.Hub` and stream directly to client browsers over Server-Sent Events (`/api/events`), updating the UI instantaneously without client-side polling.
* **Autonomous Conduit Publisher & Redis JWT Cache:** Authenticates personas against the RealWorld Conduit REST API, caches signed JWTs in Redis with proactive expiration renewal, dispatches articles, and tracks live article slugs.
* **Human-in-the-Loop Studio & Split-Pane Editor:** Full review workflow with side-by-side editing, syntax-highlighted code blocks, live word count, persona generation, custom regeneration prompts, article updates, and republishing.
* **Type-Safe Declarative APIs & Swagger UI:** Built on the ZIO HTTP `Endpoint` API, automatically serving runtime OpenAPI 3.0 documentation and interactive Swagger UI at `/docs/openapi`.

---

## 🏗️ Architecture & System Flow

```mermaid
flowchart TD
    subgraph UI ["Frontend Studio (React 19 + TypeScript + Vite)"]
        PersonaUI["User Persona Hub"]
        QueueUI["Article Generation Studio"]
        ApprovalUI["Approval Board (Split-Pane Editor)"]
        AuditUI["Publishing & Audit Log"]
        SwaggerView["Swagger UI (/docs/openapi)"]
    end

    subgraph Backend ["ZIO 2 Backend Engine (server/)"]
        subgraph HTTPServer ["ZIO HTTP Web Server (:8080)"]
            Endpoints["Declarative Endpoint API\n(/api/users, /api/articles, /api/tags)"]
            SwaggerRoutes["SwaggerUI & OpenAPI Spec\n(/docs/openapi)"]
            SSEEndpoint["SSE Stream\n(/api/events)"]
        end

        SSEHub["ZIO Hub Broadcaster"]
        EventListener["Skunk LISTEN Dispatcher (article_events)"]

        subgraph Workers ["Lightweight ZIO Fiber Workers"]
            GenWorker["Generation Worker\n(Semaphore Controlled)"]
            LLMClient["OpenAI / llama.cpp Client\n(JSON Schema Clamping & Paced Frontmatter)"]
            PubWorker["Publishing Worker"]
            ConduitClient["Conduit HTTP Client"]
            TokenCache["Redis JWT Token Cache"]
        end

        DB[("PostgreSQL Database\n(Skunk + LISTEN/NOTIFY)")]
        RedisDB[("Redis Cache\n(:6380)")]
    end

    subgraph External ["External Infrastructure"]
        LLM["llama-server (:9931 / :8080)"]
        ConduitAPI["RealWorld Conduit API\n(rwa-api.guihigashi.dev)"]
    end

    %% UI Interactions
    PersonaUI -->|"Create / Generate Personas"| Endpoints
    QueueUI -->|"Enqueue Generation Request"| Endpoints
    ApprovalUI -->|"Edit / Approve / Regenerate"| Endpoints
    AuditUI -->|"Update & Republish Article"| Endpoints
    SwaggerView -.->|"Inspect API Spec"| SwaggerRoutes

    %% Persistence
    Endpoints -->|"Non-blocking SQL via Skunk"| DB

    %% Event Dispatching
    DB -.->|"PostgreSQL NOTIFY article_events"| EventListener
    EventListener -->|"Publish Status Changes"| SSEHub
    EventListener -->|"Wakeup Trigger"| GenWorker
    EventListener -->|"Wakeup Trigger"| PubWorker

    %% Real-Time SSE
    SSEHub -->|"Push Events"| SSEEndpoint
    SSEEndpoint -.->|"Server-Sent Events"| ApprovalUI
    SSEEndpoint -.->|"Server-Sent Events"| AuditUI

    %% Generation Pipeline
    GenWorker -->|"Claim via FOR UPDATE SKIP LOCKED"| DB
    GenWorker -->|"Prompt with Target Word Count"| LLMClient
    LLMClient <-->|"POST /v1/chat/completions"| LLM
    GenWorker -->|"Store Draft (PendingApproval)"| DB

    %% Publishing Pipeline
    PubWorker -->|"Claim Approved Articles"| DB
    PubWorker -->|"Fetch / Cache Bearer Token"| TokenCache
    TokenCache <-->|"Get / Set JWT with TTL"| RedisDB
    PubWorker -->|"POST /api/articles"| ConduitClient
    ConduitClient <-->|"Conduit REST Protocol"| ConduitAPI
    PubWorker -->|"Mark Published & Store Slug"| DB
```

---

## 🔄 Article Lifecycle State Machine

```mermaid
flowchart TD
    Start(["● Enqueue Request"]) --> QueuedGeneration["QueuedGeneration"]
    
    QueuedGeneration -->|"Worker Claim (SKIP LOCKED)"| Generating["Generating"]
    
    Generating -->|"LLM Synthesis Success"| PendingApproval["PendingApproval"]
    Generating -->|"Inference Failure / Timeout"| Failed["Failed"]
    
    PendingApproval -->|"Reviewer Approved"| Approved["Approved"]
    PendingApproval -->|"Regenerate (Reviewer Feedback)"| QueuedGeneration
    PendingApproval -->|"Reviewer Deleted"| Deleted(["● Deleted / Purged"])
    
    Approved -->|"Claimed by Publisher"| Publishing["Publishing"]
    
    Publishing -->|"Conduit API 201 Created"| Published["Published"]
    Publishing -->|"Conduit API Error"| Failed
    
    Published -->|"Republish / Edit Sync"| Approved
    Failed -->|"Retry / Regenerate"| QueuedGeneration
    Failed -->|"Manual Review & Approve"| Approved
    
    Published --> End(["● Live on Conduit"])
```

---

## 💡 Core Engineering Highlights

### 1. Hybrid LLM Synthesis: Schema Clamping & Frontmatter Pacing
Generating structured domain content requires distinct engineering strategies for short profiles vs. long-form articles:
* **Persona Profiles (Logit Clamping):** Native Scala 3 case classes derived with `zio-schema` are mapped to OpenAPI `JsonSchema` definitions and passed directly to the inference engine (`response_format: { type: "json_schema" }`). This mathematically guarantees valid JSON with zero parsing failures.
* **Long-Form Articles (Frontmatter & Word Count Pacing):** To prevent premature token truncation and JSON escaping issues when nesting rich code blocks inside JSON strings, article generation uses structured YAML frontmatter. Strict pacing guides, dynamic `maxTokens` scaling, target word count ranges (up to 2,000 words), and automated trailing meta-commentary stripping ensure production-ready GitHub-Flavored Markdown.

### 2. Zero-Polling Event Pipeline (`LISTEN / NOTIFY` + Skunk)
Traditional polling loops saturate database CPU and connection pools. This system uses PostgreSQL triggers on the `articles` table to emit `pg_notify('article_events', payload)` on every status update. A dedicated Skunk socket session subscribes via `session.channel(id"article_events").listen(...)`.
* Worker fibers remain suspended with **0% CPU utilization** until notified.
* Multiple parallel workers safely race for jobs using `FOR UPDATE SKIP LOCKED`.
* A one-time boot sweep handles in-flight recovery on application startup.

### 3. ZIO Concurrency & Semaphore Rate Limiting
Local LLM inference requires heavy GPU VRAM and context allocation. The backend orchestrates tasks over lightweight **ZIO Fibers**, bounded by a `ZIO.Semaphore` permit pool. If multiple requests are enqueued, jobs queue cleanly in PostgreSQL without causing CUDA out-of-memory errors or JVM thread exhaustion.

### 4. Resilient JWT Authentication & Redis Caching
Publishing to RealWorld Conduit requires persona authentication. The `ConduitTokenCache` inspects JWT expiration (`exp` claim via `jwt-scala`) and caches active bearer tokens in **Redis** with proactive expiration buffers, avoiding redundant login requests on every publication.

---

## 🚀 Getting Started

### Prerequisites

Ensure the following tools are installed on your machine:
* **Java Development Kit (JDK) 21+**
* **sbt** (Scala Build Tool)
* **Node.js 20+** and **Yarn 4**
* **Docker & Docker Compose**
* **A running LLM inference server** (e.g. `llama.cpp` / `llama-server` or an OpenAI-compatible endpoint)

---

### 1. Launch Infrastructure Services

Start the local PostgreSQL and Redis containers via Docker Compose:

```bash
docker compose up -d
```

* **PostgreSQL:** `localhost:5433` (User: `postgres`, Password: `password`, Database: `postgres`)
* **Redis:** `localhost:6380`

---

### 2. Apply Database Migrations

Run database migrations to initialize tables, enums, and notification triggers:

```bash
# Using Flyway CLI:
flyway -url="jdbc:postgresql://localhost:5433/postgres" -user="postgres" -password="password" migrate

# Or directly execute SQL files via psql/docker:
docker exec -i $(docker compose ps -q postgres) psql -U postgres -d postgres < server/src/main/resources/db/migration/V1__create_personas_and_tags.sql
docker exec -i $(docker compose ps -q postgres) psql -U postgres -d postgres < server/src/main/resources/db/migration/V2__create_articles_and_events.sql
```

---

### 3. Start Local LLM Inference

The studio targets an OpenAI-compatible completion endpoint (default: `http://127.0.0.1:9931/v1` or `http://127.0.0.1:8080/v1`). You can spin up `llama-server` pointing to any instruction-tuned GGUF model:

```bash
./llama-server \
  -m models/Meta-Llama-3.1-8B-Instruct-Q4_K_M.gguf \
  --port 9931 \
  --ctx-size 8192 \
  --n-gpu-layers 99
```

> [!NOTE]
> If your inference server runs on another port or host, update `openai.baseUrl` in `server/src/main/resources/application.conf`.

---

### 4. Run Backend Server

Navigate to the `server/` directory and start the ZIO application:

```bash
cd server
sbt run
```

The server boots up on **`http://localhost:8080`**:
* REST API endpoints: `/api/*`
* Real-time SSE Stream: `/api/events`
* Swagger UI & OpenAPI Specification: `http://localhost:8080/docs/openapi`

---

### 5. Run Frontend Development Server

In a new terminal window, navigate to `web/` and launch Vite:

```bash
cd web
yarn install
yarn dev
```

Open your browser at **`http://localhost:5174`**. Vite automatically proxies `/api` and `/docs` requests to the Scala backend.

---

## 🖥️ Interactive UI Capabilities

| View | Route | Description |
| :--- | :--- | :--- |
| **Persona Hub** | `/` | Manage expert personas, configure writing tones (Academic, Technical Deep Dive, Conversational, etc.), set domain tags, or use **"Generate Persona with AI"** for instant synthetic user profiles. |
| **Generation Studio** | `/studio` | Select an author persona, pick relevant topic tags, enter guidance or topic keywords, adjust target word count (up to 2,000 words), and enqueue generation jobs to background fibers. |
| **Approval Board** | `/approval` | Real-time queue of generated articles awaiting review. Includes a **split-pane live editor** with GitHub-Flavored Markdown preview, syntax-highlighted code blocks, live word counters, and instant actions: **Approve & Publish**, **Regenerate with Feedback**, or **Delete**. |
| **Publishing Audit** | `/audit` | Live monitor of all published articles with links directly to the deployed Conduit blog post, external slug tracking, and in-place editing with republish capabilities. |

---

## 📡 REST API & Swagger Documentation

Interactive OpenAPI 3.0 documentation is auto-generated by ZIO HTTP and available at:
👉 **`http://localhost:8080/docs/openapi`**

### Summary of Key Endpoints

#### User Personas & Tags
* `GET /api/tags` — Retrieve all available domain tags.
* `POST /api/tags/seed` — Batch seed domain tags.
* `GET /api/users` — List all expert personas with tags and writing tones.
* `POST /api/users` — Create a persona manually.
* `POST /api/users/generate` — Generate a synthetic persona profile using the LLM.
* `PUT /api/users/:id` — Update persona credentials, tone, or expertise.
* `DELETE /api/users/:id` — Remove persona.

#### Articles & Queue Management
* `POST /api/articles` — Enqueue a new article generation job (`QueuedGeneration`).
* `GET /api/articles?status=&authorId=` — Query articles by status and author filter.
* `GET /api/articles/:id` — Fetch article details.
* `PUT /api/articles/:id` — Edit article title, description, or markdown body.
* `DELETE /api/articles/:id` — Delete article draft.
* `POST /api/articles/:id/approve` — Approve draft and enqueue for publishing (`Approved`).
* `POST /api/articles/:id/regenerate` — Request LLM re-generation with custom reviewer instructions.
* `POST /api/articles/:id/republish` — Re-sync modified published article to Conduit API.

#### Real-Time Event Stream
* `GET /api/events` — Server-Sent Events (SSE) stream broadcasting real-time status transitions (`article_events`).

---

## 🧪 Testing

### Backend Unit & Integration Tests
The backend features test suites covering non-blocking Skunk repositories, workers, LLM client generation, and SSE streams using **ZIO Test**:

```bash
cd server
sbt test
```

### Frontend Type Checking & Linting
The frontend is strictly verified with TypeScript and Oxlint:

```bash
cd web
yarn lint
yarn build
```

---

## 📄 License

This project is licensed under the Apache 2.0 License - see the [LICENSE](LICENSE) file for details.
