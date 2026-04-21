package com.fitness.app.data.importer

/**
 * Maps free-form exercise names from the historical .txt logs onto stable keys that match
 * slugs in seed/exercises.json. A null return means "skip — no useful mapping".
 *
 * Preserves bench-diagonality variants `(2)` / `(3)` as part of the returned name by
 * returning a [Match] carrying both the canonical slug and an optional display suffix
 * used to split e.g. "Incline bench (2)" from "Incline bench (3)" into separate exercises.
 */
object ExerciseNameMapper {

    data class Match(val slug: String, val variantSuffix: String? = null)

    /** Extracts a bench-diagonality marker like "(2)" or "(3)" if present at end of name. */
    private val VARIANT_RE = Regex("""\((\d)\)\s*$""")

    fun map(rawName: String): Match? {
        val trimmed = rawName.trim()
        if (trimmed.isEmpty()) return null

        // Pull off a trailing "(N)" variant marker (bench diagonality).
        val variantMatch = VARIANT_RE.find(trimmed)
        val variant = variantMatch?.groupValues?.getOrNull(1)?.let { "($it)" }
        val base = (variantMatch?.let { trimmed.removeRange(it.range) } ?: trimmed).trim()
        val normalized = normalize(base)

        // Skip noise: "cables x6", generic superset markers, warmup/ambiguous groups.
        if (isGenericFiller(normalized)) return null

        val slug = lookup(normalized) ?: return null
        return Match(slug = slug, variantSuffix = variant)
    }

    private fun normalize(s: String): String =
        s.lowercase()
            .replace(Regex("""[.,;:!?]"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun isGenericFiller(n: String): Boolean {
        // e.g. "cables x6", "cables", "dumbbells x6", just-numbers, etc.
        if (n.matches(Regex("""(cable|cables|dumbbell|dumbbells|abs)"""))) return true
        if (n.matches(Regex("""(cable|cables|dumbbell|dumbbells)\s*x\d+"""))) return true
        if (n.isBlank()) return true
        return false
    }

    private fun lookup(n: String): String? {
        // Exact / substring rules, most specific first.
        // Chest / press
        if (n.contains("machine chest press") || n == "chest press") return "machine_chest_press"
        if (n.contains("incline bench") || n == "incline bench smith" || n == "incline bench" ||
            n == "incline" || n.startsWith("incline bench")) return "incline_bench_press"
        if (n == "incline db" || n == "incline dumbbell" || n.contains("incline db press") ||
            n.contains("incline dumbbell press")) return "incline_db_press"
        if (n == "bench" || n == "bench press" || n == "flat bench") return "bench_press"
        if (n.contains("machine fly") || n.contains("machine flys") || n == "pec machine" ||
            n.contains("pec deck")) return "pec_deck"
        if (n.contains("dumbbell fly") || n == "db fly" || n == "db flys") return "dumbbell_fly"
        if (n.contains("cable chest lift") || n.contains("cable cross fly") ||
            n.contains("cable fly") || n == "cable chest" ||
            n.contains("cable cross flys")) return "cable_fly"
        if (n == "push up" || n == "push-up" || n == "pushups" || n == "push ups") return "push_up"
        if (n == "dip" || n == "dips") return "dip"

        // Back / pull
        if (n.contains("lat pulldown") || n == "lat pulldowns" ||
            n.contains("machine lat pulldown") || n.contains("hs lat pulldown") ||
            n.contains("lat pulldowns hammer strength")) return "lat_pulldown"
        if (n == "machine rows" || n == "machine row" || n.contains("chest supported row") ||
            n == "hs rows" || n.contains("hammer strength row")) return "chest_supported_row"
        if (n == "barbell row" || n == "barbell rows" || n == "bb row" || n == "bb rows") return "barbell_row"
        if (n == "pendlay row" || n == "pendlay rows") return "pendlay_row"
        if (n.contains("seated cable row") || n == "cable rows") return "seated_cable_row"
        if (n.contains("dumbbell row") || n == "db row" || n == "db rows") return "dumbbell_row"
        if (n == "chin up" || n == "chin ups" || n == "chinup" || n == "chinups" ||
            n.contains("assisted pull up") || n.contains("ass pull up") ||
            n.contains("ass pull ups") || n.contains("ass. pull") ||
            n == "pull up" || n == "pull ups" || n == "pullup" || n == "pullups" ||
            n.contains("machine chin up")) return "pull_up"
        if (n.contains("face pull")) return "face_pull"

        // Hinge / deadlift variants — "DL" means conventional; romanian/sumo recognised separately.
        if (n.contains("romanian dl") || n.contains("dl romanian") || n == "dlromanian" ||
            n.contains("romanian deadlift") || n == "rdl") return "romanian_deadlift"
        if (n == "dl" || n == "dls" || n == "deadlift" || n == "deadlifts" ||
            n == "dlsumo" || n.contains("sumo dl") || n.contains("dl sumo") ||
            n.contains("sumo deadlift") || n == "dl sumo") return "deadlift"

        // Legs
        if (n.contains("low bar squat") || n.contains("high bar squat") ||
            n == "back squat" || n == "squat" || n == "squats" ||
            n.contains("smith squat") || n.contains("low bar smith") ||
            n.contains("squat smith")) return "back_squat"
        if (n.contains("front squat")) return "front_squat"
        if (n.contains("bulgarian split")) return "bulgarian_split_squat"
        if (n.contains("walking lunge") || n == "lunge" || n == "lunges") return "walking_lunge"
        if (n.contains("hack squat")) return "hack_squat"
        if (n == "leg press" || n.contains("leg press machine") ||
            n.contains("leg press")) return "leg_press"
        if (n.contains("leg extension")) return "leg_extension"
        if (n.contains("leg curl") || n.contains("hamstring curl")) return "leg_curl"
        if (n.contains("seated calf")) return "seated_calf_raise"
        if (n.contains("calf raise") || n.contains("calf raises") || n.contains("calves") ||
            n.contains("barbell calf") || n.contains("claf raise")) return "calf_raise"
        if (n.contains("hip thrust")) return "hip_thrust"
        if (n.contains("hip adductor")) return "hip_adductor"

        // Shoulders
        if (n.contains("machine shoulder press") || n == "shoulder press" ||
            n == "shoulder press x3" || n.contains("shoulder press catch")) return "machine_shoulder_press"
        if (n.contains("overhead press") || n == "press" || n == "ohp") return "overhead_press"
        if (n == "seated db press" || n.contains("seated dumbbell press") ||
            n == "db shoulder press") return "seated_db_press"
        if (n.contains("cable lat raise") || n.contains("cable lateral raise")) return "cable_lateral_raise"
        if (n.contains("lateral raise") || n.contains("dumbbell lat raise") ||
            n.contains("dumbbell lateral raise") || n.contains("db lateral") ||
            n == "lat raises") return "lateral_raise"
        if (n.contains("rear delt") || n.contains("reverse fly")) return "rear_delt_fly"
        if (n.contains("shrug") || n.contains("shrugs")) return "shrug"

        // Arms
        if (n.contains("preacher curl") || n.contains("bb preacher") ||
            n.contains("barbell preacher") || n.contains("dumbbell preacher") ||
            n.contains("cable bicep curl") || n.contains("cable curl")) return "barbell_curl"
        if (n.contains("hammer curl")) return "hammer_curl"
        if (n == "ez bar curl" || n.contains("ez curl")) return "ez_bar_curl"
        if (n.contains("dumbbell curl") || n == "db curl") return "dumbbell_curl"
        if (n == "barbell curl" || n == "bb curl" || n == "curl" || n == "curls") return "barbell_curl"
        if (n.contains("skullcrusher") || n.contains("skull crusher")) return "skullcrusher"
        if (n.contains("overhead extension") || n.contains("overhead triceps") ||
            n.contains("overhead tricep") || n.contains("cable overhead triceps") ||
            n.contains("cable overhead tricep") || n == "overhead extensions") return "overhead_tricep_ext"
        if (n.contains("pushdown") || n.contains("press down") || n.contains("pressdown") ||
            n.contains("tricep pushdown") || n.contains("cable pressdown")) return "tricep_pushdown"

        // Core — map loose "abs" entries to plank so they still record something.
        if (n == "plank" || n == "planks") return "plank"
        if (n.contains("hanging leg raise")) return "hanging_leg_raise"

        return null
    }
}
