# THE ROTLIN TOUR

Rotlin is a real programming language that compiles to the JVM, but its
keywords are written in meme slang. This tour walks through the syntax.
Only the keywords are slang; your own variables, strings, and values
should read like normal code.

## 0. run stuff

```
rotlin cook app.rot     compiles + runs your program
rotlin drop app.rot     serves your website on localhost, auto-reloads on save
rotlin aura app.rot     checks your code and rates it out of 1000 aura
```

## 1. print output

```rotlin
yap("Hello, world")
```

Save as `first.rot`, then `rotlin cook first.rot`. That is a whole
program. No `public static void` boilerplate.

## 2. variables - rizz and gyatt

```rotlin
rizz name = "Alice"       // rizz = immutable, can never be reassigned
gyatt score = 0           // gyatt = mutable, can change
score gains 100           // score += 100
score loses 25            // score -= 25
yap("Hi $name, your score is $score")
```

Rotlin infers the types (type inference) for you. `name` is a `lore`
(text), `score` is an `aura` (whole number). The types:

| type | what it is | example |
|---|---|---|
| `aura` | whole number | `42` |
| `ratio` | decimal number | `3.14` |
| `lore` | text | `"hello"` |
| `fact` | true/false | `based` / `cringe` |
| `squad<aura>` | a list | `squad(1, 2, 3)` |
| `stash<lore, aura>` | key-value pairs | `stash()` |

## 3. conditionals - sus and bruh

Blocks open with `bet` and close with `periodt`.

```rotlin
sus (score atleast 100) bet
    yap("high")
bruh sus (score clears 0) bet
    yap("medium")
bruh bet
    yap("low")
periodt
```

Comparisons are words here; symbols are banned (try `==` and see what
happens):

| rotlin | means | | rotlin | means |
|---|---|---|---|---|
| `twins` | equal | | `aint` | not equal |
| `clears` | greater | | `flops` | less |
| `atleast` | >= | | `atmost` | <= |

Logic uses `and`, `or`, `not`.

## 4. loops - grind and mog

```rotlin
gyatt hp = 3
grind (hp clears 0) bet          // while loop
    yap("hp: $hp")
    hp loses 1
periodt

mog (i inside 1 through 5) bet   // for loop over a range
    yap("step $i")
periodt

rizz names = squad("Alice", "Bob", "Carol")
mog (name inside names) bet      // for loop over a squad
    yap("Hi, $name")
periodt
```

`dip` breaks out of a loop. `skip` jumps to the next round.

## 5. functions - skibidi

```rotlin
skibidi rank(name: lore, level: aura) spits lore bet
    sus (level atleast 9) bet
        yeet name + " is an expert"
    periodt
    yeet name + " is a beginner"
periodt

yap(rank("Alice", 10))
```

`skibidi` defines a function, `spits` declares the return type, `yeet`
returns a value. Parameters need explicit types.

## 6. null safety - ghosted

Some values might not exist. Rotlin makes you handle that (null safety).

```rotlin
rizz answer: maybe lore = listen()      // user input, might be missing

// answer.length            <- Rotlin stops you: it might be null

sus (answer aint ghosted) bet
    yap(answer.length)                  // safe here: Rotlin knows it exists
periodt

rizz backup = answer otherwise "nothing"   // fallback if null
yap(answer?.length)                        // safe access (returns maybe)
yap(answer deadass)                        // assert non-null; crashes if it was null
```

If `deadass` was wrong, you get: *"you said deadass but it was ghosted.
caught in 4k."*

## 7. classes - sigma

```rotlin
vibe Fetchable bet                       // an interface
    skibidi fetch() spits lore
periodt

sigma Animal(rizz name: lore) bet
    skibidi speak() spits lore bet
        yeet "..."
    periodt
periodt

sigma Dog(rizz dogName: lore) is a Animal(dogName) vibes with Fetchable bet
    gatekeep gyatt barks = 0             // gatekeep = private

    remix skibidi speak() spits lore bet // remix = override the parent's version
        barks gains 1
        yeet me.dogName + " says woof"
    periodt

    remix skibidi fetch() spits lore bet
        yeet "brings the stick back"
    periodt
periodt

rizz dog = Dog("Rex")
yap(dog.speak())
```

`me` is this object. `npc Config bet ... periodt` makes a singleton
(exactly one instance in the whole program).

## 8. vibecheck - the switch

```rotlin
vibecheck (score) bet
    0 -> yap("zero")
    1, 2 -> yap("one or two")
    bruh -> yap("anything else")
periodt
```

## 9. error handling - finna / caught in 4k

```rotlin
finna bet
    crashout "invalid operation"
caught in 4k (error) bet
    yap("handled it: $error")
periodt
```

`crashout` throws a SkillIssue. `finna ... caught in 4k` handles it.

## 10. the web part

```rotlin
gyatt count = 0

drop site on 3000 bet
    page("/") bet
        bigyap("Counter")                // a big heading
        yap("Count: $count")             // a paragraph (same yap; context decides)
        pic("https://cataas.com/cat")    // an image
        smash("Increment") does bet      // a button
            count gains 1
        periodt
    periodt
periodt
```

Run `rotlin drop clicker.rot`, open the link, and click the button. Now
change the text and save; the browser updates itself. Break the code and
save; the roast shows up in the page. Fix it and repeat.

## 11. use the whole JVM - summon

Rotlin programs are real JVM programs, so every Java/Kotlin library is
available:

```rotlin
summon kotlin.math.PI
summon java.util.Random

yap("pi is $PI")
rizz rng = Random()
yap("dice: " + (rng.nextInt(6) + 1))
```

## cheat sheet

| rotlin | Kotlin equivalent |
|---|---|
| `rizz` / `gyatt` | val / var |
| `skibidi` / `yeet` / `spits` | fun / return / return type |
| `sus` / `bruh` | if / else |
| `grind` / `mog ... inside` | while / for-in |
| `bet` / `periodt` | { / } |
| `based` / `cringe` / `ghosted` | true / false / null |
| `sigma` / `npc` / `vibe` | class / object / interface |
| `is a` / `vibes with` | extends / implements |
| `remix` / `gatekeep` / `me` | override / private / this |
| `maybe T` / `otherwise` / `deadass` | T? / ?: / !! |
| `summon` / `hood` | import / package |
| `finna` / `caught in 4k` / `crashout` | try / catch / throw |
| `dip` / `skip` | break / continue |
