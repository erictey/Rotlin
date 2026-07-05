# THE ROTLIN TOUR

so you wanna code. bet. this is Rotlin — a real programming language that
compiles to the JVM (the thing that runs Minecraft servers, no cap) but
speaks your language.

## 0. run stuff

```
rotlin cook app.rot     compiles + runs your program
rotlin drop app.rot     puts your WEBSITE on localhost, auto-reloads on save
rotlin aura app.rot     checks your code and rates it out of 1000 aura
```

## 1. yap at the world

```rotlin
yap("wsg chat")
```

save as `first.rot`, then `rotlin cook first.rot`. that's a whole program.
no ceremony, no `public static void` ancient scrolls.

## 2. variables — rizz and gyatt

```rotlin
rizz name = "eric"        // rizz = locked in. can NEVER change. no takebacks.
gyatt score = 0           // gyatt = can change whenever
score gains 100           // score += 100
score loses 25            // score -= 25
yap("yo $name, you got $score aura")
```

rotlin FIGURES OUT the types (that's called type inference and it's free).
`name` is a `lore` (text), `score` is an `aura` (whole number). the types:

| type | what it is | example |
|---|---|---|
| `aura` | whole number | `42` |
| `ratio` | decimal number | `3.14` |
| `lore` | text | `"skibidi"` |
| `fact` | true/false | `based` / `cringe` |
| `squad<aura>` | a list | `squad(1, 2, 3)` |
| `stash<lore, aura>` | key-value pairs | `stash()` |

## 3. vibe checks — sus and bruh

blocks open with `bet` and close with `periodt`. end of discussion.

```rotlin
sus (score atleast 100) bet
    yap("W")
bruh sus (score clears 0) bet
    yap("mid")
bruh bet
    yap("L + ratio")
periodt
```

comparisons are WORDS here. symbols are banned (try `==`, see what happens):

| rotlin | means | | rotlin | means |
|---|---|---|---|---|
| `twins` | equal | | `aint` | not equal |
| `clears` | greater | | `flops` | less |
| `atleast` | >= | | `atmost` | <= |

logic is `and`, `or`, `not`. like english. because it is english.

## 4. loops — grind and mog

```rotlin
gyatt hp = 3
grind (hp clears 0) bet          // while loop: stay on the grind
    yap("hp: $hp")
    hp loses 1
periodt

mog (i inside 1 through 5) bet   // for loop over a range
    yap("wave $i")
periodt

rizz homies = squad("dre", "jayden", "kai")
mog (homie inside homies) bet    // for loop over a squad
    yap("sup $homie")
periodt
```

`dip` breaks out of a loop. `skip` jumps to the next round.

## 5. functions — let him cook

```rotlin
skibidi roast(name: lore, level: aura) spits lore bet
    sus (level atleast 9) bet
        yeet name + " is down catastrophic"
    periodt
    yeet name + " is kinda mid"
periodt

yap(roast("caseoh", 10))
```

`skibidi` defines it, `spits` says what comes back, `yeet` sends it back.
parameters need types — that's the deal, no cap.

## 6. null safety — ghosted

some values might not exist. rotlin makes you HANDLE it, that's the
"null safety" thing grown devs cry about.

```rotlin
rizz answer: maybe lore = listen()      // user input... might be ghosted

// answer.length            <- rotlin STOPS you. it might be a ghost.

sus (answer aint ghosted) bet
    yap(answer.length)                  // safe in here - rotlin KNOWS
periodt

rizz backup = answer otherwise "nothing"   // fallback if ghosted
yap(answer?.length)                        // safe peek (gives maybe)
yap(answer deadass)                        // "TRUST me it's real" - crashes if you lied
```

if `deadass` was cap, you get: *"you said deadass but it was ghosted.
caught in 4k."* — that's on you.

## 7. classes — sigma grindset

```rotlin
vibe Fetchable bet                       // an interface: a vibe to match
    skibidi fetch() spits lore
periodt

sigma Animal(rizz name: lore) bet
    skibidi speak() spits lore bet
        yeet "..."
    periodt
periodt

sigma Dog(rizz dogName: lore) is a Animal(dogName) vibes with Fetchable bet
    gatekeep gyatt barks = 0             // gatekeep = private, nobody touches it

    remix skibidi speak() spits lore bet // remix = override the parent's version
        barks gains 1
        yeet me.dogName + " says WOOF"
    periodt

    remix skibidi fetch() spits lore bet
        yeet "brings the stick back"
    periodt
periodt

rizz rex = Dog("rex")
yap(rex.speak())
```

`me` is this object. `npc Config bet ... periodt` makes a singleton
(exactly one of it in the whole program — an npc).

## 8. vibecheck — the switch-up

```rotlin
vibecheck (score) bet
    0 -> yap("NPC behavior")
    1, 2 -> yap("lowkey mid")
    bruh -> yap("certified W")
periodt
```

## 9. when it crashes out — finna / caught in 4k

```rotlin
finna bet
    crashout "lil bro tried something illegal"
caught in 4k (oops) bet
    yap("handled it: $oops")
periodt
```

`crashout` throws a SkillIssue. `finna ... caught in 4k` handles it.

## 10. THE WEB PART (the reason you're here)

```rotlin
gyatt score = 0

drop site on 3000 bet
    page("/") bet
        bigyap("AURA CLICKER")           // <- big heading
        yap("your aura: $score")         // <- paragraph (yes, same yap. context matters.)
        pic("https://cataas.com/cat")    // <- image
        smash("+1 aura") does bet        // <- a BUTTON. smash it.
            score gains 1
        periodt
    periodt
periodt
```

run `rotlin drop clicker.rot`, open the link, click the button, feel the
dopamine. now change the text and SAVE — the browser updates itself.
break the code and save — the roast shows up IN the page. fix it. repeat.

## 11. use the whole JVM — summon

rotlin programs are real JVM programs. every Java/Kotlin library ever
written is yours:

```rotlin
summon kotlin.math.PI
summon java.util.Random

yap("pi is $PI")
rizz rng = Random()
yap("dice: " + (rng.nextInt(6) + 1))
```

## cheat sheet

| rotlin | normal-people language |
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

now go cook.
