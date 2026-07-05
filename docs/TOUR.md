# THE ROTLIN TOUR

Rotlin is a real programming language that compiles to the JVM. Some of
its keywords are meme slang; the rest are regular Kotlin words. This
tour walks through the syntax. Your own variables, strings, and values
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

## 2. variables - alpha and beta

```rotlin
alpha name = "Alice"      // alpha = immutable, can never be reassigned
beta score = 0            // beta = mutable, can change
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
| `fact` | true/false | `true` / `false` |
| `squad<aura>` | a list | `squad(1, 2, 3)` |
| `stash<lore, aura>` | key-value pairs | `stash()` |

## 3. conditionals - if and else

Blocks open with `{` and close with `}`.

```rotlin
if (score atleast 100) {
    yap("high")
} else if (score > 0) {
    yap("medium")
} else {
    yap("low")
}
```

Equality is a word here; `==` and `!=` are banned (try `==` and see
what happens):

| rotlin | means | | rotlin | means |
|---|---|---|---|---|
| `is` | equal | | `aint` | not equal |
| `>` | greater | | `<` | less |
| `atleast` | >= | | `atmost` | <= |

Logic uses `and`, `or`, `not`.

## 4. loops - grind and mog

```rotlin
beta hp = 3
grind (hp > 0) {                 // while loop
    yap("hp: $hp")
    hp loses 1
}

mog (i inside 1 through 5) {     // for loop over a range
    yap("step $i")
}

alpha names = squad("Alice", "Bob", "Carol")
mog (name inside names) {        // for loop over a squad
    yap("Hi, $name")
}
```

`dip` breaks out of a loop. `skip` jumps to the next round.

## 5. functions - tung

```rotlin
tung rank(name: lore, level: aura) spits lore {
    if (level atleast 9) {
        yeet name + " is an expert"
    }
    yeet name + " is a beginner"
}

yap(rank("Alice", 10))
```

`tung` defines a function, `spits` declares the return type, `yeet`
returns a value. Parameters need explicit types.

## 6. null safety

Some values might not exist. Rotlin makes you handle that (null safety).

```rotlin
alpha answer: maybe lore = listen()     // user input, might be missing

// answer.length            <- Rotlin stops you: it might be null

if (answer aint null) {
    yap(answer.length)                  // safe here: Rotlin knows it exists
}

alpha backup = answer otherwise "nothing"  // fallback if null
yap(answer?.length)                        // safe access (returns maybe)
yap(answer deadahh)                        // assert non-null; crashes if it was null
```

If `deadahh` was wrong, you get: *"deadahh failed: the value was
null."*

## 7. classes

```rotlin
vibe Fetchable {                         // an interface
    tung fetch() spits lore
}

class Animal(alpha name: lore) {
    tung speak() spits lore {
        yeet "..."
    }
}

class Dog(alpha dogName: lore) is a Animal(dogName) vibes with Fetchable {
    private beta barks = 0               // private = hidden from outside

    override tung speak() spits lore {   // override the parent's version
        barks gains 1
        yeet this.dogName + " says woof"
    }

    override tung fetch() spits lore {
        yeet "brings the stick back"
    }
}

alpha dog = Dog("Rex")
yap(dog.speak())
```

`this` is this object. `npc Config { ... }` makes a singleton
(exactly one instance in the whole program).

## 8. when - the switch

```rotlin
when (score) {
    0 -> yap("zero")
    1, 2 -> yap("one or two")
    else -> yap("anything else")
}
```

## 9. error handling - try / catch

```rotlin
try {
    crashout "invalid operation"
} catch (error) {
    yap("handled it: $error")
}
```

`crashout` throws a SkillIssue. `try ... catch` handles it.

## 10. the web part

```rotlin
beta count = 0

drop site on 3000 {
    page("/") {
        bigyap("Counter")                // a big heading
        yap("Count: $count")             // a paragraph (same yap; context decides)
        pic("https://cataas.com/cat")    // an image
        smash("Increment") does {        // a button
            count gains 1
        }
    }
}
```

Run `rotlin drop clicker.rot`, open the link, and click the button. Now
change the text and save; the browser updates itself. Break the code and
save; the error shows up in the page. Fix it and repeat.

## 11. use the whole JVM - summon

Rotlin programs are real JVM programs, so every Java/Kotlin library is
available:

```rotlin
summon kotlin.math.PI
summon java.util.Random

yap("pi is $PI")
alpha rng = Random()
yap("dice: " + (rng.nextInt(6) + 1))
```

## cheat sheet

| rotlin | Kotlin equivalent |
|---|---|
| `alpha` / `beta` | val / var |
| `tung` / `yeet` / `spits` | fun / return / return type |
| `if` / `else` | if / else |
| `grind` / `mog ... inside` | while / for-in |
| `{` / `}` | { / } |
| `true` / `false` / `null` | true / false / null |
| `class` / `npc` / `vibe` | class / object / interface |
| `is a` / `vibes with` | extends / implements |
| `override` / `private` / `this` | override / private / this |
| `maybe T` / `otherwise` / `deadahh` | T? / ?: / !! |
| `summon` / `package` | import / package |
| `try` / `catch` / `crashout` | try / catch / throw |
| `dip` / `skip` | break / continue |
| `is` / `aint` | == / != |
