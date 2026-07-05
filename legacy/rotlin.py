#!/usr/bin/env python3
"""Rotlin -> Kotlin transpiler (token-swap MVP).

Swaps brainrot keywords for real Kotlin keywords, while leaving the
contents of STRING LITERALS and COMMENTS untouched. That "leave strings
and comments alone" rule is the entire reason this is a hand-written
scanner and not a one-line str.replace() / regex.
"""

import sys

# Rotlin keyword  ->  real Kotlin keyword.
# This dict IS the language's vocabulary. Edit freely.
KEYWORDS = {
    # declarations / functions
    "skibidi":   "fun",
    "rizz":      "val",
    "gyatt":     "var",
    "yeet":      "return",
    "sigma":     "class",
    "npc":       "object",
    # control flow
    "sus":       "if",
    "bruh":      "else",
    "grind":     "while",
    "mog":       "for",
    "vibecheck": "when",
    "inside":    "in",
    # values
    "based":     "true",
    "cringe":    "false",
    "ghosted":   "null",
    # module
    "summon":    "import",
    "hood":      "package",
    # not keywords, just function names -- the swap works on any word token,
    # so mapping these is free (but it does "reserve" the names; see notes).
    "yap":       "println",
    "whisper":   "print",
}


def transpile(src: str) -> str:
    out = []
    i, n = 0, len(src)

    while i < n:
        c = src[i]

        # raw string:  """ ... """   (no escapes, may contain a lone ")
        if src.startswith('"""', i):
            end = src.find('"""', i + 3)
            if end == -1:
                out.append(src[i:]); break
            out.append(src[i:end + 3]); i = end + 3; continue

        # regular string:  " ... "   with \ escapes
        if c == '"':
            j = i + 1
            while j < n:
                if src[j] == '\\':      j += 2; continue
                if src[j] == '"':       j += 1; break
                j += 1
            out.append(src[i:j]); i = j; continue

        # char literal:  'x'  or  '\n'
        if c == "'":
            j = i + 1
            while j < n:
                if src[j] == '\\':      j += 2; continue
                if src[j] == "'":       j += 1; break
                j += 1
            out.append(src[i:j]); i = j; continue

        # line comment:  // ... end-of-line
        if src.startswith('//', i):
            end = src.find('\n', i)
            if end == -1:
                out.append(src[i:]); break
            out.append(src[i:end]); i = end; continue   # leave the \n for the loop

        # block comment:  /* ... */   (Kotlin allows nesting)
        if src.startswith('/*', i):
            depth, j = 1, i + 2
            while j < n and depth > 0:
                if src.startswith('/*', j):   depth += 1; j += 2
                elif src.startswith('*/', j): depth -= 1; j += 2
                else:                         j += 1
            out.append(src[i:j]); i = j; continue

        # word token (identifier or keyword): the ONLY place we swap
        if c.isalpha() or c == '_':
            j = i + 1
            while j < n and (src[j].isalnum() or src[j] == '_'):
                j += 1
            word = src[i:j]
            out.append(KEYWORDS.get(word, word))   # swap if known, else pass through
            i = j; continue

        # anything else (punctuation, whitespace, digits): emit verbatim
        out.append(c); i += 1

    return ''.join(out)


def main():
    if len(sys.argv) < 2:
        sys.stdout.write(transpile(sys.stdin.read())); return
    inp = sys.argv[1]
    with open(inp, encoding='utf-8') as f:
        out = transpile(f.read())
    if len(sys.argv) >= 3:            outp = sys.argv[2]
    elif inp.endswith('.rot'):        outp = inp[:-4] + '.kt'
    else:                             outp = inp + '.kt'
    with open(outp, 'w', encoding='utf-8') as f:
        f.write(out)
    print(f"wrote {outp}")


if __name__ == '__main__':
    main()
