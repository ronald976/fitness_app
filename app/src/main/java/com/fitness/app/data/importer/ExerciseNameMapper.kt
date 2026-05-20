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
    private val DB_RE = Regex("""\bdb\b""")
    private val BB_RE = Regex("""\bbb\b""")
    private val HS_RE = Regex("""\bhs\b""")

    private enum class EquipmentHint {
        BARBELL,
        SMITH,
        DUMBBELL,
        MACHINE,
        CABLE,
        FREE_WEIGHT
    }

    private enum class Movement {
        BENCH_PRESS,
        CHEST_PRESS,
        INCLINE_PRESS,
        FLY,
        ROW,
        LAT_PULLDOWN,
        UNILATERAL_LAT_PULLDOWN,
        PULL_UP,
        CHIN_UP,
        ASSISTED_PULL_UP,
        ASSISTED_CHIN_UP,
        BACK_SQUAT,
        FRONT_SQUAT,
        HACK_SQUAT,
        LEG_PRESS,
        CALF_RAISE,
        SEATED_CALF_RAISE,
        HIP_THRUST,
        OVERHEAD_PRESS,
        SHOULDER_PRESS,
        REAR_DELT_FLY,
        SHRUG,
        BICEP_CURL,
        EZ_CURL,
        HAMMER_CURL,
        OVERHEAD_TRICEP_EXTENSION
    }

    private data class ParsedName(
        val movement: Movement,
        val equipment: EquipmentHint? = null
    )

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
        // "abs" is no longer filler — it maps to its own Core exercise.
        if (n.matches(Regex("""(cable|cables|dumbbell|dumbbells)"""))) return true
        if (n.matches(Regex("""(cable|cables|dumbbell|dumbbells)\s*x\d+"""))) return true
        if (n.isBlank()) return true
        return false
    }

    private fun lookup(n: String): String? {
        parseName(n)?.let { parsed ->
            resolve(parsed)?.let { return it }
        }

        // Exact / substring rules, most specific first.
        // Chest / press
        if (n.contains("machine chest press") || n == "chest press" ||
            n == "chest press machine" || n == "machine press") return "machine_chest_press"
        if (n.contains("incline bench smith") || n.contains("incline smith") ||
            n.contains("smith incline")) return "incline_smith_press"
        if (n.contains("incline bench") || n == "incline bench" ||
            n == "incline" || n.startsWith("incline bench")) return "incline_bench_press"
        if (n == "incline db smith") return "incline_smith_press"
        if (n == "incline db" || n == "incline dumbbell" || n.contains("incline db press") ||
            n.contains("incline dumbbell press") || n == "dumbbell incline press") return "incline_db_press"
        if (n == "bench smith" || n == "smith bench" || n == "bench (smith)" ||
            n == "smith bench press") return "smith_bench_press"
        if (n == "bench" || n == "bench press" || n == "flat bench") return "bench_press"
        if (n.contains("machine fly") || n.contains("machine flys") || n == "pec machine" ||
            n.contains("pec deck") || n == "pecs machine") return "pec_deck"
        if (n.contains("dumbbell fly") || n == "db fly" || n == "db flys") return "dumbbell_fly"
        // "Cable chest lift" is a cable curl-style chest movement — distinct from cable flys
        // despite the shared "chest" / "lift" wording. Match it first so the broader fly check
        // below doesn't claim it.
        if (n.contains("cable chest lift") || n.contains("cables chest lift") ||
            n.contains("cable upper chest lift")) return "cable_chest_lift"
        if (n.contains("cable cross fly") || n.contains("cable fly") || n == "cable chest" ||
            n.contains("cable cross flys") || n.contains("cable lat twist") ||
            n.contains("cable lateral twist")) return "cable_fly"
        if (n == "push up" || n == "push-up" || n == "pushups" || n == "push ups") return "push_up"
        if (n == "dip" || n == "dips") return "dip"

        // Back / pull
        if (n.contains("unilateral lat pulldown") || n.contains("unilat lat pulldown") ||
            n.contains("unilat pulldown") || n.contains("unilat down pull") ||
            n == "unilateral pulldown") return "unilateral_lat_pulldown"
        if (n.contains("lat pulldown") || n == "lat pulldowns" ||
            n.contains("machine lat pulldown") || n.contains("hs lat pulldown") ||
            n.contains("lat pulldowns hammer strength") ||
            n.contains("cable pulldown")) return "lat_pulldown"
        if (n == "machine rows" || n == "machine row" || n.contains("chest supported row") ||
            n == "hs rows" || n.contains("hammer strength row") ||
            n.startsWith("machine rows")) return "chest_supported_row"
        if (n == "barbell row" || n == "barbell rows" || n == "bb row" || n == "bb rows") return "barbell_row"
        if (n.contains("smith row") || n == "smith rows") return "smith_row"
        if (n == "pendlay row" || n == "pendlay rows") return "pendlay_row"
        if (n.contains("seated cable row") || n == "cable rows" ||
            n.startsWith("cable rows") || n.contains("unilat cable row") ||
            n.contains("close grip cable row")) return "seated_cable_row"
        if (n.contains("dumbbell row") || n == "db row" || n == "db rows") return "dumbbell_row"
        if (n == "chin up" || n == "chin ups" || n == "chinup" || n == "chinups" ||
            n.contains("assisted pull up") || n.contains("ass pull up") ||
            n.contains("ass pull ups") || n.contains("ass pull") ||
            n == "pull up" || n == "pull ups" || n == "pullup" || n == "pullups" ||
            n.contains("machine chin up") || n.contains("chin ups bw") ||
            n.contains("pull ups bw") || n.contains("ass chin") ||
            n.contains("chin ups machine") || n == "chip ups" ||
            n.startsWith("chin ups") || n.startsWith("pull ups") ||
            n.contains("ass pull-up")) return "pull_up"
        if (n.contains("face pull")) return "face_pull"

        // Hinge / deadlift variants — "DL" means conventional; romanian/sumo recognised separately.
        if (n.contains("romanian dl") || n.contains("dl romanian") || n == "dlromanian" ||
            n.contains("romanian deadlift") || n == "rdl" ||
            n.contains("stiff leg dl")) return "romanian_deadlift"
        if (n == "dl" || n == "dls" || n == "deadlift" || n == "deadlifts" ||
            n == "dlsumo" || n.contains("sumo dl") || n.contains("dl sumo") ||
            n.contains("sumo deadlift") || n == "dl sumo") return "deadlift"

        // Legs
        if ((n.contains("smith") && n.contains("squat")) ||
            n.contains("low bar smith") || n.contains("squat smith")) return "smith_squat"
        if (n.contains("low bar squat") || n.contains("high bar squat") ||
            n == "back squat" || n == "squat" || n == "squats" ||
            n == "barbell squat" || n == "barbell squats") return "back_squat"
        if (n.contains("front squat")) return "front_squat"
        if (n.contains("bulgarian split")) return "bulgarian_split_squat"
        if (n.contains("walking lunge") || n == "lunge" || n == "lunges") return "walking_lunge"
        if (n.contains("hack squat")) return "hack_squat"
        if (n.contains("leg press") && n.contains("machine")) return "leg_press"
        if (n == "leg press" || n == "leg press free" ||
            n == "leg press free weight" || n == "leg press (free weight)" ||
            n.contains("leg press")) return "leg_press_free_weight"
        if (n.contains("leg extension")) return "leg_extension"
        if (n.contains("leg curl") || n.contains("hamstring curl")) return "leg_curl"
        if (n.contains("seated calf")) return "seated_calf_raise"
        if ((n.contains("calf raise") || n.contains("calf raises") || n.contains("calves")) &&
            n.contains("machine")) return "calf_raise"
        if (n.contains("calf raise") || n.contains("calf raises") || n.contains("calves") ||
            n.contains("barbell calf") || n.contains("claf raise")) return "calf_raise_free_weight"
        if (n.contains("hip thrust") && n.contains("smith")) return "smith_hip_thrust"
        if (n.contains("hip thrust")) return "hip_thrust"
        if (n.contains("hip adductor")) return "hip_adductor"

        // Shoulders
        if (n.contains("machine shoulder press") || n == "shoulder press" ||
            n == "shoulder press x3" || n.contains("shoulder press catch") ||
            n == "shoulder press machine" || n.startsWith("shoulder press")) return "machine_shoulder_press"
        if (n == "smith press" || n == "press smith") return "smith_overhead_press"
        if (n.contains("overhead press") || n == "press" || n == "ohp") return "overhead_press"
        if (n == "seated db press" || n.contains("seated dumbbell press") ||
            n == "db shoulder press") return "seated_db_press"
        if (n.contains("cable lat raise") || n.contains("cable lateral raise")) return "cable_lateral_raise"
        if (n.contains("lateral raise") || n.contains("dumbbell lat raise") ||
            n.contains("dumbbell lateral raise") || n.contains("db lateral") ||
            n == "lat raises") return "lateral_raise"
        if (n.contains("rear delt") || n.contains("reverse fly")) return "rear_delt_fly"
        if (n.contains("shrug") || n.contains("shrugs")) {
            return resolve(ParsedName(Movement.SHRUG, equipmentHint(n) ?: EquipmentHint.BARBELL))
        }

        // Arms
        if (n.contains("cable bicep curl") || n.contains("cable curl") ||
            n.contains("cable biceps curl") || n == "cable bicep" ||
            n == "cable biceps") return "cable_bicep_curl"
        if (n.contains("preacher curl") || n.contains("bb preacher") ||
            n.contains("barbell preacher") || n.contains("dumbbell preacher") ||
            n == "biceps curls") return "barbell_curl"
        if (n.contains("hammer curl")) return "hammer_curl"
        if (n == "ez bar curl" || n.contains("ez curl")) return "ez_bar_curl"
        if (n.contains("dumbbell curl") || n == "db curl" ||
            n.contains("dumbbell alternating") || n.contains("dumbbell biceps")) return "dumbbell_curl"
        if (n == "barbell curl" || n == "bb curl" || n == "curl" || n == "curls") return "barbell_curl"
        if (n.contains("skullcrusher") || n.contains("skull crusher")) return "skullcrusher"
        if (n.contains("overhead extension") || n.contains("overhead triceps") ||
            n.contains("overhead tricep") || n.contains("cable overhead triceps") ||
            n.contains("cable overhead tricep") || n == "overhead extensions" ||
            n.contains("cable overhead tri") || n == "dumbbell overhead" ||
            n.contains("cable triceps extension") || n.contains("dumbbell triceps extension")) {
            return resolve(ParsedName(
                Movement.OVERHEAD_TRICEP_EXTENSION,
                equipmentHint(n) ?: EquipmentHint.DUMBBELL
            ))
        }
        if (n.contains("pushdown") || n.contains("press down") || n.contains("pressdown") ||
            n.contains("tricep pushdown") || n.contains("cable pressdown")) return "tricep_pushdown"

        // Core
        if (n == "plank" || n == "planks") return "plank"
        if (n.contains("hanging leg raise")) return "hanging_leg_raise"
        if (n == "abs" || n == "ab" || n == "core") return "abs"

        return null
    }

    private fun parseName(n: String): ParsedName? {
        val equipment = equipmentHint(n)

        // Presses / chest
        if (n.contains("machine chest press") || n == "chest press" ||
            n == "chest press machine" || n == "machine press") {
            return ParsedName(Movement.CHEST_PRESS, EquipmentHint.MACHINE)
        }
        if (n.contains("incline") && (n.contains("bench") || n.contains("press") || DB_RE.containsMatchIn(n))) {
            return ParsedName(Movement.INCLINE_PRESS, equipment ?: EquipmentHint.BARBELL)
        }
        if (n.contains("bench")) {
            return ParsedName(Movement.BENCH_PRESS, equipment ?: EquipmentHint.BARBELL)
        }
        if (n.contains("machine fly") || n.contains("machine flys") || n == "pec machine" ||
            n.contains("pec deck") || n == "pecs machine") {
            return ParsedName(Movement.FLY, EquipmentHint.MACHINE)
        }
        if (n.contains("rear delt") || n.contains("reverse cable fly") ||
            n.contains("reverse fly")) {
            return ParsedName(Movement.REAR_DELT_FLY, equipment)
        }
        if (n.contains("cable cross fly") || n.contains("cable fly") || n == "cable chest" ||
            n.contains("cable lat twist") || n.contains("cable lateral twist")) {
            return ParsedName(Movement.FLY, EquipmentHint.CABLE)
        }
        // Note: "cable chest lift" intentionally NOT mapped here — it's a distinct movement
        // (cable curl-style) handled directly by the simple resolver as cable_chest_lift.
        if (n.contains("dumbbell fly") || n == "db fly" || n == "db flys") {
            return ParsedName(Movement.FLY, EquipmentHint.DUMBBELL)
        }

        // Back / pull
        if (n.contains("unilateral lat pulldown") || n.contains("unilat lat pulldown") ||
            n.contains("unilat pulldown") || n.contains("unilat down pull") ||
            n.contains("hs unilat pulldown") || n.contains("hs lat pulldowns unilateral") ||
            n == "unilateral pulldown") {
            return ParsedName(Movement.UNILATERAL_LAT_PULLDOWN, EquipmentHint.MACHINE)
        }
        if (n.contains("lat pulldown") || n == "lat pulldowns" ||
            n.contains("machine lat pulldown") || n.contains("hs lat pulldown") ||
            n.contains("cable pulldown")) {
            return ParsedName(Movement.LAT_PULLDOWN, EquipmentHint.MACHINE)
        }
        if (n.contains("assisted chin") || n.contains("ass chin") ||
            n.contains("ass. chin") || n.contains("machine chin") ||
            (n.contains("chin") && equipment == EquipmentHint.MACHINE)) {
            return ParsedName(Movement.ASSISTED_CHIN_UP, EquipmentHint.MACHINE)
        }
        if (n.contains("assisted pull") || n.contains("ass pull") ||
            n.contains("ass. pull") || n.contains("ass pull-up") ||
            (n.contains("pull") && equipment == EquipmentHint.MACHINE)) {
            return ParsedName(Movement.ASSISTED_PULL_UP, EquipmentHint.MACHINE)
        }
        if (n == "chin up" || n == "chin ups" || n == "chinup" || n == "chinups" ||
            n.startsWith("chin ups") || n.contains("chin ups bw") ||
            n.contains("machine chin up")) {
            return ParsedName(Movement.CHIN_UP)
        }
        if (n == "pull up" || n == "pull ups" || n == "pullup" || n == "pullups" ||
            n.startsWith("pull ups") || n.contains("pull ups bw")) {
            return ParsedName(Movement.PULL_UP)
        }
        if (n.contains("row")) {
            return ParsedName(Movement.ROW, equipment)
        }

        // Legs
        if (n.contains("front squat")) {
            return ParsedName(Movement.FRONT_SQUAT, equipment ?: EquipmentHint.BARBELL)
        }
        if (n.contains("hack squat")) {
            return ParsedName(Movement.HACK_SQUAT, EquipmentHint.MACHINE)
        }
        if (n.contains("squat") || n.contains("low bar") || n.contains("high bar")) {
            return ParsedName(Movement.BACK_SQUAT, equipment ?: EquipmentHint.BARBELL)
        }
        if (n.contains("leg press")) {
            return ParsedName(Movement.LEG_PRESS, equipment ?: EquipmentHint.FREE_WEIGHT)
        }
        if (n.contains("seated calf")) {
            return ParsedName(Movement.SEATED_CALF_RAISE, EquipmentHint.MACHINE)
        }
        if (n.contains("calf raise") || n.contains("calf raises") || n.contains("calves") ||
            n.contains("barbell calf") || n.contains("claf raise")) {
            return ParsedName(Movement.CALF_RAISE, equipment ?: EquipmentHint.FREE_WEIGHT)
        }
        if (n.contains("hip thrust")) {
            return ParsedName(Movement.HIP_THRUST, equipment ?: EquipmentHint.BARBELL)
        }

        // Shoulders / arms
        if (n == "smith press" || n == "press smith" || n.contains("overhead press") ||
            n == "press" || n == "ohp") {
            return ParsedName(Movement.OVERHEAD_PRESS, equipment ?: EquipmentHint.BARBELL)
        }
        if (n.contains("machine shoulder press") || n == "shoulder press" ||
            n == "shoulder press x3" || n.contains("shoulder press catch") ||
            n == "shoulder press machine" || n.startsWith("shoulder press") ||
            n == "seated db press" || n.contains("seated dumbbell press") ||
            n == "db shoulder press") {
            return ParsedName(Movement.SHOULDER_PRESS, equipment ?: EquipmentHint.MACHINE)
        }
        if (n.contains("shrug") || n.contains("shrugs")) {
            return ParsedName(Movement.SHRUG, equipment ?: EquipmentHint.BARBELL)
        }
        if (n == "ez bar curl" || n.contains("ez curl")) {
            return ParsedName(Movement.EZ_CURL, EquipmentHint.BARBELL)
        }
        if (n.contains("hammer curl")) {
            return ParsedName(Movement.HAMMER_CURL, EquipmentHint.DUMBBELL)
        }
        if (n.contains("bicep curl") || n.contains("biceps curl") || n.contains("cable curl") ||
            n == "cable bicep" || n == "cable biceps" || n.contains("preacher curl") ||
            n.contains("bb preacher") || n.contains("barbell preacher") ||
            n.contains("dumbbell preacher") || n == "curl" || n == "curls") {
            return ParsedName(Movement.BICEP_CURL, equipment ?: EquipmentHint.BARBELL)
        }
        if (n.contains("overhead extension") || n.contains("overhead triceps") ||
            n.contains("overhead tricep") || n.contains("overhead tri") ||
            n == "overhead extensions" ||
            n.contains("dumbbell overhead") || n.contains("cable triceps extension") ||
            n.contains("dumbbell triceps extension")) {
            return ParsedName(Movement.OVERHEAD_TRICEP_EXTENSION, equipment ?: EquipmentHint.DUMBBELL)
        }

        return null
    }

    private fun resolve(parsed: ParsedName): String? = when (parsed.movement) {
        Movement.BENCH_PRESS -> when (parsed.equipment) {
            EquipmentHint.SMITH -> "smith_bench_press"
            EquipmentHint.DUMBBELL -> "dumbbell_bench_press"
            EquipmentHint.MACHINE -> "machine_chest_press"
            else -> "bench_press"
        }
        Movement.CHEST_PRESS -> "machine_chest_press"
        Movement.INCLINE_PRESS -> when (parsed.equipment) {
            EquipmentHint.SMITH -> "incline_smith_press"
            EquipmentHint.DUMBBELL -> "incline_db_press"
            EquipmentHint.MACHINE -> "machine_chest_press"
            else -> "incline_bench_press"
        }
        Movement.FLY -> when (parsed.equipment) {
            EquipmentHint.CABLE -> "cable_fly"
            EquipmentHint.DUMBBELL -> "dumbbell_fly"
            else -> "pec_deck"
        }
        Movement.ROW -> when (parsed.equipment) {
            EquipmentHint.SMITH -> "smith_row"
            EquipmentHint.DUMBBELL -> "dumbbell_row"
            EquipmentHint.CABLE -> "seated_cable_row"
            EquipmentHint.MACHINE -> "chest_supported_row"
            else -> "barbell_row"
        }
        Movement.LAT_PULLDOWN -> "lat_pulldown"
        Movement.UNILATERAL_LAT_PULLDOWN -> "unilateral_lat_pulldown"
        Movement.PULL_UP -> "pull_up"
        Movement.CHIN_UP -> "chin_up"
        Movement.ASSISTED_PULL_UP -> "assisted_pull_up"
        Movement.ASSISTED_CHIN_UP -> "assisted_chin_up"
        Movement.BACK_SQUAT -> when (parsed.equipment) {
            EquipmentHint.SMITH -> "smith_squat"
            else -> "back_squat"
        }
        Movement.FRONT_SQUAT -> "front_squat"
        Movement.HACK_SQUAT -> "hack_squat"
        Movement.LEG_PRESS -> when (parsed.equipment) {
            EquipmentHint.MACHINE -> "leg_press"
            else -> "leg_press_free_weight"
        }
        Movement.CALF_RAISE -> when (parsed.equipment) {
            EquipmentHint.MACHINE -> "calf_raise"
            else -> "calf_raise_free_weight"
        }
        Movement.SEATED_CALF_RAISE -> "seated_calf_raise"
        Movement.HIP_THRUST -> when (parsed.equipment) {
            EquipmentHint.SMITH -> "smith_hip_thrust"
            else -> "hip_thrust"
        }
        Movement.OVERHEAD_PRESS -> when (parsed.equipment) {
            EquipmentHint.SMITH -> "smith_overhead_press"
            EquipmentHint.DUMBBELL -> "seated_db_press"
            EquipmentHint.MACHINE -> "machine_shoulder_press"
            else -> "overhead_press"
        }
        Movement.SHOULDER_PRESS -> when (parsed.equipment) {
            EquipmentHint.SMITH -> "smith_overhead_press"
            EquipmentHint.DUMBBELL -> "seated_db_press"
            else -> "machine_shoulder_press"
        }
        Movement.REAR_DELT_FLY -> when (parsed.equipment) {
            EquipmentHint.CABLE -> "cable_rear_delt_fly"
            else -> "rear_delt_fly"
        }
        Movement.SHRUG -> when (parsed.equipment) {
            EquipmentHint.DUMBBELL -> "dumbbell_shrug"
            else -> "shrug"
        }
        Movement.BICEP_CURL -> when (parsed.equipment) {
            EquipmentHint.CABLE -> "cable_bicep_curl"
            EquipmentHint.DUMBBELL -> "dumbbell_curl"
            else -> "barbell_curl"
        }
        Movement.EZ_CURL -> "ez_bar_curl"
        Movement.HAMMER_CURL -> "hammer_curl"
        Movement.OVERHEAD_TRICEP_EXTENSION -> when (parsed.equipment) {
            EquipmentHint.CABLE -> "cable_overhead_tricep_ext"
            EquipmentHint.BARBELL -> "barbell_overhead_tricep_ext"
            else -> "overhead_tricep_ext"
        }
    }

    private fun equipmentHint(n: String): EquipmentHint? = when {
        n.contains("smith") -> EquipmentHint.SMITH
        n.contains("free weight") -> EquipmentHint.FREE_WEIGHT
        n.contains("dumbbell") || n.contains("dumbbells") || DB_RE.containsMatchIn(n) -> EquipmentHint.DUMBBELL
        n.contains("cable") || n.contains("cables") -> EquipmentHint.CABLE
        n.contains("machine") || n.contains("hammer strength") || HS_RE.containsMatchIn(n) -> EquipmentHint.MACHINE
        n.contains("barbell") || BB_RE.containsMatchIn(n) || n.endsWith(" bar") ||
            n.contains(" bar ") || n.contains("(bar)") -> EquipmentHint.BARBELL
        else -> null
    }
}
