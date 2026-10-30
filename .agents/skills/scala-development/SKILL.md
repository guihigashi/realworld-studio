---
name: scala-development
description: "Guidelines and commands for developing, compiling, and running Scala 3 applications with sbt in realworld-ai-seeder/server. Use when writing Scala code, configuring build.sbt, or running sbt commands."
---

# Scala 3 Development Skill

This skill guides development in the `server/` Scala 3 sbt project.

## Project Environment

- **Scala Version:** 3.10.x
- **Build Tool:** sbt 2.x
- **Base Directory:** `server/`

## Key Commands

Always run these commands inside the `server/` directory:

| Action | Command | Notes |
| :--- | :--- | :--- |
| Compile | `sbt compile` | Incremental compile of `src/main/` |
| Test | `sbt test` | Compiles and executes all MUnit tests |
| Run App | `sbt run` | Executes `@main` entrypoint (e.g. `hello()` in `Main.scala`) |
| Clean Build | `sbt clean compile` | Clears target artifacts and recompiles from scratch |
| Interactive REPL | `sbt console` | Starts Scala 3 REPL with project classpath loaded |

## Scala 3 Syntax & Idioms

### Significant Indentation
Prefer clean, 2-space indented Scala 3 syntax:

```scala
@main def run(): Unit =
  val items = List("engine", "seeder", "pipeline")
  for item <- items do
    println(s"Processing $item")
```

### Algebraic Data Types (`enum`)
Use Scala 3 `enum` for typed state and domain errors:

```scala
enum SeedStatus:
  case Pending
  case InProgress(progress: Double)
  case Completed(count: Int)
  case Failed(reason: String)
```

### Context Parameters & Given Instances
Use `given` and `using` instead of Scala 2 `implicit`:

```scala
trait Formatter[T]:
  def format(value: T): String

given Formatter[String] with
  def format(value: String): String = value.trim

def output[T](item: T)(using f: Formatter[T]): Unit =
  println(f.format(item))
```

### Resource Management
Always wrap auto-closeable I/O resources using `scala.util.Using`:

```scala
import scala.util.Using
import java.io.BufferedReader
import java.io.FileReader

def readFirstLine(path: String): scala.util.Try[String] =
  Using(BufferedReader(FileReader(path))) { reader =>
    reader.readLine()
  }
```
