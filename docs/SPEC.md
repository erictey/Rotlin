# Rotlin Language Spec (v1)

File extension: `.rot`. One statement per line. Blocks: `{` … `}`.
Comments: `//` line, `/* */` nesting block. Strings: `"..."` with `$name`
and `${expr}` interpolation. UTF-8 (BOM tolerated).

## Keywords to Kotlin

| Rotlin | Kotlin | | Rotlin | Kotlin |
|---|---|---|---|---|
| `alpha` | `val` | | `if` / `else` | `if` / `else` |
| `beta` | `var` | | `grind` | `while` |
| `tung` | `fun` | | `mog … inside` | `for … in` |
| `yeet` | `return` | | `when` | `when` |
| `spits T` | `: T` (return type) | | `dip` / `skip` | `break` / `continue` |
| `class` | `open class` | | `try` | `try` |
| `npc` | `object` | | `catch (e)` | `catch (e: Exception)` |
| `vibe` | `interface` | | `crashout x` | `throw SkillIssue(lore(x))` |
| `is a` / `is an` | `: Super()` | | `summon` | `import` |
| `vibes with` | `: Interface` | | `package` | `package` |
| `override` | `override` | | `true` / `false` | `true` / `false` |
| `private` | `private` | | `null` | `null` |
| `this` | `this` | | `{` / `}` | `{` / `}` |

`else` and `catch` may close the preceding block directly (no `}`
needed before them), though the `} else {` / `} catch {` style works too.

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

Equality and null-assert are words; the symbol forms are compile errors
with a fix hint:

| Rotlin | Kotlin | | Rotlin | Kotlin |
|---|---|---|---|---|
| `is` | `==` | | `aint` | `!=` |
| `>` | `>` | | `<` | `<` |
| `atleast` | `>=` | | `atmost` | `<=` |
| `and` / `or` / `not` | `&&` / `\|\|` / `!` | | `x gains y` / `x loses y` | `+=` / `-=` |
| `a through b` | `a..b` | | `x otherwise y` | `x ?: y` |
| `x deadahh` | `x!!` (runtime error if null) | | `?.` | `?.` (kept) |

Arithmetic stays symbolic: `+ - * / % = ( ) [ ]`. Precedence mirrors
Kotlin exactly. `aura` and `ratio` never mix implicitly - convert with
`ratio(x)` / `aura(x)` / `lore(x)`.

## Null safety

- `null` fits only `maybe T` slots.
- Member access on a `maybe` needs `?.`, an `if (x aint null)` check
  (smart cast; `alpha`/params only - `beta` can flip, so it never narrows),
  or `deadahh`.
- `stash[key]` returns `maybe V`.

## Program shape

Script-style: declarations (functions, variables, classes) before the
first statement become top-level; everything after is wrapped into a
synthesized `main`. Or write `tung main() { … }` yourself.
`package` (line 1) then `summon` lines come first.

## Web DSL (runtime library, not keywords)

```rotlin
drop site on 3000 {
    page("/") {
        bigyap("h1")  yap("p")  pic(url)  link("label", "/there")
        smash("button label") does { … }
    }
}
```

`smash` handlers run server-side; clicks are POST-redirect-GET. Under
`rotlin drop`, saving the file recompiles and reloads the browser (SSE);
compile errors render as an in-page overlay while the old site stays up.

## Diagnostics

Every error: a plain-Kotlin-style message + a concrete fix + source
caret. Aura score = 1000 − 100·errors − 25·warnings, floor 0. Clean
compile: `+1000 aura. Compiled with no errors and no warnings.`

## Compilation model

`.rot` is lexed, parsed, typechecked, and emitted as line-aligned Kotlin
(`rot line = kt line − 2`), then compiled by `kotlin-compiler-embeddable`
(pinned 2.4.0) to JVM classes and run in-process. kotlinc is the backstop
for anything the Rotlin checker types as Unknown (all JVM interop).
