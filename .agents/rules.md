# Agent Rules & Operational Instructions

Project policies, workflows, and standards for all AI coding agents working on `realworld-ai-seeder`.

---

## 1. Standard Operational Workflows

### Feature Implementation & Modification

1. **Locate & Inspect:**
    - Review relevant sources in `server/src/main/scala/` and tests in `server/src/test/scala/`.
    - Inspect `server/build.sbt` if new library dependencies or compiler plugins are needed.
    - For frontend changes, inspect `web/src/` and `web/package.json`.
2. **Implement Iteratively:**
    - Adhere to the Scala 3 idioms and formatting conventions detailed below.
    - Write or update unit tests in `server/src/test/scala/` alongside new functionality.
3. **Verify via Compiler:**
    - Execute the compilation check:
      ```bash
      cd server && sbt compile
      ```
    - Resolve any syntax errors, type mismatches, or deprecation warnings.
4. **Run Test Suites:**
    - Execute all test suites:
      ```bash
      cd server && sbt test
      ```
    - Or target a specific test suite during development:
      ```bash
      cd server && sbt "testOnly <SuiteName>"
      ```
5. **Sanity Check Main Application:**
    - Verify that the application entrypoint runs as expected:
      ```bash
      cd server && sbt run
      ```

### Debugging & Troubleshooting

- **Thin Client / Server Connection Locks:** If sbt reports connection issues or port locks, run `sbt --client shutdown`
  or kill orphaned background sbt processes before restarting.
- **Incremental Compilation Artifacts:** If strange classloader or incremental compilation anomalies appear, run:
  ```bash
  cd server && sbt clean compile
  ```
- **Targeted Line Execution in IntelliJ / AIA:** When using debugger tools via IDE integration (`execute_tool`), refer
  to the `ij-debugger` skill for non-suspending logpoint-first debugging.

---

## 2. Code Style & Idioms (Scala 3)

- **Indentation Syntax:** Use Scala 3 significant indentation syntax (2 spaces). Avoid unnecessary braces `{}` for
  control structures, classes, and defs unless required for disambiguation.
- **Immutability First:**
    - Default to `val`, `case class`, `enum`, and standard immutable collections (`List`, `Vector`, `Map`, `Set`).
    - Avoid `var` and mutable collections unless strictly necessary for local, encapsulated performance optimizations.
- **Type Safety & Inference:**
    - Provide explicit return types for all public methods, definitions, and exports.
    - Rely on type inference for private/local intermediate variables when readability is enhanced.
- **Functional Error Handling:**
    - Do not use exceptions for expected control flow or domain errors.
    - Prefer `Option`, `Either`, or typed domain error ADTs (`enum`). Use `scala.util.Try` or `scala.util.Using` when
      wrapping legacy JVM I/O operations.
- **Top-Level Definitions:**
    - Leverage Scala 3 top-level functions, types, and values in package scopes where appropriate rather than creating
      artificial `object` utility namespaces.

---

## 3. Build & Dependency Management (sbt)

- **sbt Configuration:** The backend build definition is located at `server/build.sbt`.
- **Dependency Hygiene:**
    - Keep test-only dependencies marked with `% Test`.
    - Prefer stable versions of libraries compatible with Scala 3.
    - Never downgrade `scalaVersion` without explicit user instruction.
- **Clean Builds:** Ensure code compiles cleanly without introducing compiler warnings (`-Werror` / `-feature` ready).

---

## 4. Testing Guardrails

- **Mandatory Tests:** Any non-trivial business logic, transformation, or parser must have automated tests written with
  MUnit (`munit.FunSuite`).
- **Isolation:** Tests must be idempotent and self-contained. Do not rely on external networks or unmanaged filesystem
  state unless specifically writing integration tests.
- **Test Before Completion:** Never report a code modification task as done without running `sbt test` in `server/` and
  verifying that all tests pass.

---

## 5. Safety & Environment Guardrails

- **Scope:** Respect repository boundaries. Do not modify files outside the repository.
- **Secrets:** Never write credentials, API keys, or private tokens into source files, git commits, or logs.
- **Working Directories:**
    - Backend sbt commands must be executed within `server/`.
    - Frontend yarn commands must be executed within `web/`.

## 6. Vite & TypeScript

- Use ambient types for Vite and TypeScript.
- @mui/material imports should be destructured.
- @mui/icons-material imports should be destructured, without alias if possible.
