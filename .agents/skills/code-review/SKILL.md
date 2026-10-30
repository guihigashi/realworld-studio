---
name: code-review
description: "Guidelines and checklist for reviewing Scala 3 code changes, verifying functional purity, type safety, test coverage, and performance. Use when reviewing pull requests or reviewing newly implemented code."
---

# Scala 3 Code Review Skill

Use this checklist and methodology when performing code reviews in `realworld-ai-seeder`.

## Review Checklist

### 1. Correctness & Logic
- [ ] Does the implementation fulfill all business requirements?
- [ ] Are all edge cases addressed (empty collections, boundary limits, missing options, network timeouts)?
- [ ] Are nulls completely absent? (Zero usage of `null` — always use `Option`).

### 2. Scala 3 Idiomatic Style
- [ ] Uses modern Scala 3 significant indentation syntax cleanly and consistently.
- [ ] Appropriate use of `enum` for ADTs instead of sealed trait hierarchies where simpler.
- [ ] Clean pattern matching: exhaustive matches without suppressing warnings (`@unchecked` only with explicit justification).
- [ ] Public methods and definitions declare explicit return types.

### 3. Functional Purity & State
- [ ] Functions are referentially transparent wherever possible.
- [ ] Zero mutable global state. No shared `var` across threads.
- [ ] Side effects (I/O, database access, seeding triggers) are isolated and clearly encapsulated.
- [ ] Resources (files, network connections, streams) are deterministically released (`Using(...)`).

### 4. Testing & Validation
- [ ] New functionality has corresponding MUnit tests in `server/src/test/scala/`.
- [ ] Error conditions and edge cases are tested (`intercept[...]`).
- [ ] All tests run and pass cleanly via `cd server && sbt test`.

### 5. Build & Performance
- [ ] No unnecessary heavy dependencies added to `server/build.sbt`.
- [ ] Algorithmic complexity is optimal for expected dataset sizes in the seeder.
- [ ] Avoids unnecessary collections copying or premature materialization of large streams/iterators.
