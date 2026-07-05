package rotlin.compiler

/**
 * Rotlin's type universe. `display()` speaks Rotlin, not Kotlin — kids see
 * `maybe lore`, never `String?`. UnknownT is the cascade suppressor: anything
 * unresolvable types as Unknown, errors once, and stays silent downstream
 * (kotlinc remains the backstop).
 */
sealed interface RType {
    fun display(): String

    object AuraT : RType {
        override fun display() = "aura"
    }

    object RatioT : RType {
        override fun display() = "ratio"
    }

    object LoreT : RType {
        override fun display() = "lore"
    }

    object FactT : RType {
        override fun display() = "fact"
    }

    object UnitT : RType {
        override fun display() = "nothing"
    }

    /** The type of the `ghosted` literal itself. */
    object GhostT : RType {
        override fun display() = "ghosted"
    }

    data class MaybeT(val inner: RType) : RType {
        override fun display() = "maybe ${inner.display()}"
    }

    data class SquadT(val elem: RType) : RType {
        override fun display() = "squad<${elem.display()}>"
    }

    data class StashT(val key: RType, val value: RType) : RType {
        override fun display() = "stash<${key.display()}, ${value.display()}>"
    }

    object UnknownT : RType {
        override fun display() = "???"
    }

    /** A `1 through 10` range. */
    object RangeT : RType {
        override fun display() = "range"
    }

    /** An instance of a user sigma/npc. Members type as Unknown; kotlinc backstops. */
    data class ClassT(val name: String) : RType {
        override fun display() = name
    }
}

/** True when a value of [from] can be used where [to] is expected. */
fun assignable(from: RType, to: RType): Boolean = when {
    from is RType.UnknownT || to is RType.UnknownT -> true
    to is RType.MaybeT -> from is RType.GhostT || assignable(from, to.inner) ||
        (from is RType.MaybeT && assignable(from.inner, to.inner))
    from is RType.GhostT -> false // ghost only fits into maybe
    from is RType.MaybeT -> false // maybe doesn't fit a definite slot
    from is RType.SquadT && to is RType.SquadT -> assignable(from.elem, to.elem)
    from is RType.StashT && to is RType.StashT ->
        assignable(from.key, to.key) && assignable(from.value, to.value)
    // class hierarchies live in kotlinc's head, not ours - stay permissive
    from is RType.ClassT && to is RType.ClassT -> true
    else -> from == to
}
