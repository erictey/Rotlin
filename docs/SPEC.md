# Rotlin Language Spec (v1)

File extension: `.rot`. One statement per line. Blocks: `bet` … `periodt`.
Comments: `//` line, `/* */` nesting block. Strings: `"..."` with `$name`
and `${expr}` interpolation. UTF-8 (BOM tolerated).

## Keywords → Kotlin

| Rotlin | Kotlin | | Rotlin | Kotlin |
|---|---|---|---|---|
| `rizz` | `val` | | `sus` / `bruh` | `if` / `else` |
| `gyatt` | `var` | | `grind` | `while` |
| `skibidi` | `fun` | | `mog … inside` | `for … in` |
| `yeet` | `return` | | `vibecheck` | `when` |
| `spits T` | `: T` (return type) | | `dip` / `skip` | `break` / `continue` |
| `sigma` | `open class` | | `finna` | `try` |
| `npc` | `object` | | `caught in 4k (e)` | `catch (e: Exception)` |
| `vibe` | `interface` | | `crashout x` | `throw SkillIssue(lore(x))` |
| `is a` / `is an` | `: Super()` | | `summon` | `import` |
| `vibes with` | `: Interface` | | `hood` | `package` |
| `remix` | `override` | | `based` / `cringe` | `true` / `false` |
| `gatekeep` | `private` | | `ghosted` | `null` |
| `me` | `this` | | `bet` / `periodt` | `{` / `}` |

`bruh` and `caught in 4k` may close the preceding block directly (no
`periodt` needed before them).

## Types

| Rotlin | Kotlin | | Rotlin | Kotlin |
|---|---|---|---|---|
| `aura` | `Int` | | `fact` | `Boolean` |
| `ratio` | `Double` | | `squad<T>` | `MutableList<T>` |
| `lore` | `String` | | `stash<K,V>` | `MutableMap<K,V>` |
| `maybe T` | `T?` | | (omitted `spits`) | `Unit` |

Locals infer from initializers; parameters and `spits` are explicit.
Unknown type names pass through for JVM interop.

## Operators

Word operators (symbol forms are compile errors with a fix hint):

| Rotlin | Kotlin | | Rotlin | Kotlin |
|---|---|---|---|---|
| `twins` | `==` | | `aint` | `!=` |
| `clears` | `>` | | `flops` | `<` |
| `atleast` | `>=` | | `atmost` | `<=` |
| `and` / `or` / `not` | `&&` / `\|\|` / `!` | | `x gains y` / `x loses y` | `+=` / `-=` |
| `a through b` | `a..b` | | `x otherwise y` | `x ?: y` |
| `x deadass` | `x!!` (runtime: "caught in 4k") | | `?.` | `?.` (kept) |

Arithmetic stays symbolic: `+ - * / % = ( ) [ ]`. Precedence mirrors
Kotlin exactly. `aura` and `ratio` never mix implicitly — convert with
`ratio(x)` / `aura(x)` / `lore(x)`.

## Null safety

- `ghosted` fits only `maybe T` slots.
- Member access on a `maybe` needs `?.`, a `sus (x aint ghosted)` check
  (smart cast; `rizz`/params only — `gyatt` can flip, so it never narrows),
  or `deadass`.
- `stash[key]` returns `maybe V`.

## Program shape

Script-style: declarations (functions, variables, classes) before the
first statement become top-level; everything after is wrapped into a
synthesized `main`. Or write `skibidi main() bet … periodt` yourself.
`hood` (line 1) then `summon` lines come first.

## Web DSL (runtime library, not keywords)

```rotlin
drop site on 3000 bet
    page("/") bet
        bigyap("h1")  yap("p")  pic(url)  link("label", "/there")
        smash("button label") does bet … periodt
    periodt
periodt
```

`smash` handlers run server-side; clicks are POST-redirect-GET. Under
`rotlin drop`, saving the file recompiles and reloads the browser (SSE);
compile errors render as an in-page overlay while the old site stays up.

## Diagnostics

Every error: roast + plain-English translation + concrete fix + source
caret. Aura score = 1000 − 100·errors − 25·warnings, floor 0. Clean
compile: `+1000 aura. W code, no cap.`

## Compilation model

`.rot` → lex → parse → typecheck → line-aligned Kotlin
(`rot line = kt line − 2`) → `kotlin-compiler-embeddable` (pinned 2.4.0)
→ JVM classes → run in-process. kotlinc is the backstop for anything the
Rotlin checker types as Unknown (all JVM interop).
