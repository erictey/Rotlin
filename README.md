# Rotlin

A statically typed, null-safe, object-oriented programming language for
teaching web dev — with brainrot syntax. Compiles to Kotlin → real JVM
bytecode, so every Java/Kotlin library works via `summon`.

```rotlin
gyatt score = 0

drop site on 3000 bet
    page("/") bet
        bigyap("AURA CLICKER")
        yap("your aura: $score")
        smash("+1 aura") does bet
            score gains 1
        periodt
    periodt
periodt
```

Save the file → browser updates itself. Break the code → the compiler
roasts you *inside the page* and tells you exactly how to fix it.

## Why

Target audience: low-attention-span kids who want to make things. The
pitch: instant dopamine (a webpage you can show people), real concepts
(static types, null safety, OOP, inheritance) hiding under meme words,
and error messages that teach instead of terrify.

This is **not** a token swapper. Rotlin has its own block syntax
(`bet` / `periodt`), word operators (`twins`, `clears`, `gains`), its own
type checker with flow-sensitive smart casts, and a compiler that maps
every error — including JVM runtime crashes — back to your `.rot` line.

## Quick start

Needs a JDK (21+). No Gradle or Kotlin install required — the wrapper
handles everything.

```
gradlew.bat :cli:run --args="cook examples/hello.rot"     # run a console program
gradlew.bat :cli:run --args="drop examples/clicker.rot"   # serve a site + hot reload
gradlew.bat :cli:run --args="aura examples/quiz.rot"      # type-check, get your aura score
```

Or build a standalone CLI once:

```
gradlew.bat :cli:installDist
cli\build\install\rotlin\bin\rotlin.bat cook examples\hello.rot
```

Learn the language: **[docs/TOUR.md](docs/TOUR.md)** (the kid-facing tour)
and **[docs/SPEC.md](docs/SPEC.md)** (the keyword tables).

## How it works

| Module | Job |
|---|---|
| `:compiler` | Lexer → parser → type checker → line-aligned Kotlin emitter. Pure functions, millisecond tests. |
| `:runtime` | `yap`, `squad`, `SkillIssue`, the web DSL (`site`/`page`/`smash`), dev host with SSE hot reload. Sits on the compiled program's classpath. |
| `:cli` | `cook` / `drop` / `aura` commands. Bundles `kotlin-compiler-embeddable` (pinned 2.4.0) so kids need zero installs. The only module touching `org.jetbrains.kotlin.*`. |

The emitter keeps generated Kotlin line-aligned with the source
(`rot line = kt line − 2`), so kotlinc diagnostics **and** runtime stack
traces map back to `.rot` lines by subtraction.

Diagnostics are the product: every error is a roast + a plain-English
explanation + a concrete fix, and a clean compile is worth
`+1000 aura. W code, no cap.`

## Tests

```
gradlew.bat test
```

Unit tests per compiler stage, golden emissions, and end-to-end tests
that compile-and-run real `.rot` programs — including a full
serve → click → hot-reload → roast-overlay loop over HTTP.
