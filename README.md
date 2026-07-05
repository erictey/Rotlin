# Rotlin

A statically typed, null-safe, object-oriented programming language for
teaching web dev but with brainrot syntax. Compiles to Kotlin so every Java/Kotlin library works via `summon`.

```rotlin
beta score = 0

drop site on 3000 {
    page("/") {
        bigyap("AURA CLICKER")
        yap("your aura: $score")
        smash("+1 aura") does {
            score gains 1
        }
    }
}
```

Save the file and the browser updates itself. Break the code and the
compiler reports the error *inside the page* and tells you exactly how
to fix it.

## Why

Target audience: low-attention-span kids who want to make things. The
pitch: instant dopamine (a webpage you can show people), real concepts
(static types, null safety, OOP, inheritance) hiding under meme words,
and error messages that teach instead of terrify.

This is **not** a token swapper. Rotlin has its own line-oriented block
syntax, word operators (`is`, `aint`, `gains`), its own type checker
with flow-sensitive smart casts, and a compiler that maps every error -
including JVM runtime crashes - back to your `.rot` line.

## Install

### Easiest: download the release (no build)

1. Grab `rotlin.zip` from the [Releases page](../../releases) and unzip it.
2. Install a JDK 21+ (see below) if you don't have one.
3. Double-click the installer for your machine:
   - **Windows** - `installWin.bat`
   - **macOS** - `installMac.command`
   - **Linux** - run `bash installLinux.sh`
4. Open a **new** terminal:

   ```
   rotlin cook examples/hello.rot
   ```

The installer just adds Rotlin to your PATH - nothing to compile. The zip
already contains the built CLI. Only a JDK is required.

Rather build from source? Follow the two steps below.

### 1. Install a JDK (21 or newer)

Rotlin runs on the JVM, so you need a Java Development Kit. Version **21+**.

**Windows**

1. Download an installer from [Adoptium Temurin 21](https://adoptium.net/temurin/releases/?version=21).
   Pick the `.msi` for Windows x64.
2. Run it. On the setup screen, enable **"Set JAVA_HOME variable"** and
   **"Add to PATH"**.
3. Open a **new** terminal and confirm with `java -version` - you should
   see `openjdk version "21..."` or higher.

**macOS**

```
brew install temurin@21
java -version
```

**Linux (Debian/Ubuntu)**

```
sudo apt update
sudo apt install openjdk-21-jdk
java -version
```

If `java -version` prints 21 or higher, you're done with this step.

### 2. Build the Rotlin CLI (once)

From the project root (the folder with `gradlew.bat`), build the
standalone CLI. You run this **one time** - after that you never touch
Gradle again.

```
gradlew.bat :cli:installDist
```

macOS / Linux: use `./gradlew` instead of `gradlew.bat`.

The first run downloads Gradle + dependencies, so it's slow. When it
finishes, the CLI lives at:

```
cli\build\install\rotlin\bin\rotlin.bat      # Windows
cli/build/install/rotlin/bin/rotlin          # macOS / Linux
```

### 3. Add `rotlin` to PATH (optional)

Add the `bin` folder to your PATH so you can just type `rotlin`:

**Windows (PowerShell, current user):**

```
setx PATH "$env:PATH;$PWD\cli\build\install\rotlin\bin"
```

Reopen your terminal afterward.

**macOS / Linux (bash/zsh):**

```
echo "export PATH=\"$PWD/cli/build/install/rotlin/bin:\$PATH\"" >> ~/.bashrc
source ~/.bashrc
```

## Quick start

Run the built binary directly - no Gradle:

```
cli\build\install\rotlin\bin\rotlin.bat cook examples\hello.rot     # run a console program
cli\build\install\rotlin\bin\rotlin.bat drop examples\clicker.rot   # serve a site + hot reload
cli\build\install\rotlin\bin\rotlin.bat aura examples\quiz.rot      # type-check, get your aura score
```

(On macOS / Linux: `cli/build/install/rotlin/bin/rotlin cook examples/hello.rot`.
If you added `rotlin` to PATH, just type `rotlin cook examples/hello.rot`.)

| Command | Job |
|---|---|
| `cook <file>.rot` | Compile and run a console program |
| `drop <file>.rot` | Serve a web site with live hot reload |
| `aura <file>.rot` | Type-check only, report your aura score |

Learn the language: **[docs/TOUR.md](docs/TOUR.md)** (the kid-facing tour)
and **[docs/SPEC.md](docs/SPEC.md)** (the keyword tables).

**Trouble**

- **`java` not found** - reopen your terminal after install; PATH updates
  need a fresh shell.
- **Wrong Java version** - check `JAVA_HOME` points at your JDK 21 folder.
- **Gradle first run hangs** - it's downloading; give it a minute on
  first run.

## How it works

| Module | Job |
|---|---|
| `:compiler` | Lexer, parser, type checker, line-aligned Kotlin emitter. Pure functions, millisecond tests. |
| `:runtime` | `yap`, `squad`, `SkillIssue`, the web DSL (`site`/`page`/`smash`), dev host with SSE hot reload. Sits on the compiled program's classpath. |
| `:cli` | `cook` / `drop` / `aura` commands. Bundles `kotlin-compiler-embeddable` (pinned 2.4.0) so kids need zero installs. The only module touching `org.jetbrains.kotlin.*`. |

The emitter keeps generated Kotlin line-aligned with the source
(`rot line = kt line − 2`), so kotlinc diagnostics **and** runtime stack
traces map back to `.rot` lines by subtraction.

Diagnostics are the product: every error is a plain-Kotlin-style
message + a concrete fix, and a clean compile is worth
`+1000 aura. Compiled with no errors and no warnings.`

## Tests

```
gradlew.bat test
```

Unit tests per compiler stage, golden emissions, and end-to-end tests
that compile-and-run real `.rot` programs - including a full
serve, click, hot-reload, error-overlay loop over HTTP.

## Cutting a release (maintainers)

```
gradlew.bat :cli:distZip
```

Produces `cli/build/distributions/rotlin.zip` - the portable, prebuilt
bundle (CLI + all jars + `examples/` + `docs/` + the `installWin.bat` /
`installMac.command` / `installLinux.sh` one-click installers from
`packaging/`). Attach that zip to a GitHub release. Users need only a
JDK 21+ - no Gradle, no build.
