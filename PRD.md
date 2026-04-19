# Fitness App — PRD, Framework & Structure

## Context
Greenfield Android app for structured strength training. The user wants to pick a plan (e.g. PPL), open today's session (e.g. "Push Day"), log sets quickly, swap in pre-defined alternative exercises when equipment is unavailable, and get weight/rep suggestions for the next session based on history. This plan establishes the PRD, tech framework, and scaffolded project structure so implementation can start from a clear blueprint. The existing `silly-stroustrup` folder is explicitly out of scope.

Confirmed decisions: **Kotlin + Jetpack Compose**, **offline-only with Room**, **curated templates + user-editable plans**, **progressive-overload suggestions**.

---

## 1. Product Requirements (PRD)

### 1.1 Goal
Let a lifter execute a structured program on their phone with minimum taps per set, easy exercise substitution, and automatic progression hints — all offline.

### 1.2 Primary user stories (MVP)
1. **Pick a plan** — browse curated plans (PPL, Upper/Lower, Full-Body 3x), view details, set as active.
2. **Start today's session** — from home screen, one tap opens the next scheduled day.
3. **Log a set** — big weight + reps input; tap "Log set"; rest timer auto-starts.
4. **Swap an exercise** — tap "Swap" on an exercise; pick from its pre-defined alternatives list; swap applies to today's session only (default) or to the plan.
5. **See suggestion** — each exercise shows "Last time: 60kg × 5,5,5 → Today: try 60kg × 6,6,5" based on the progression rule.
6. **Review history** — see recent sessions and per-exercise progress (best set, estimated 1RM trend).
7. **Edit plans** — clone a curated plan and edit (add/remove/reorder exercises, change rep ranges, edit alternatives).

### 1.3 Secondary / post-MVP
- Custom rest timer per exercise, body-weight / warm-up sets, notes per set, plate calculator, deload detection, CSV export, charts, backup/restore (JSON file), dark/light themes, widgets.

### 1.4 Non-goals (v1)
Cloud sync, accounts, social features, video demos, cardio/HIIT tracking, nutrition, wearables integration, iOS.

### 1.5 Key UX principles
- **Two-tap logging**: input fields pre-filled with the suggestion; `✓` logs it.
- **Thumb-reachable controls**: big buttons bottom-of-screen during an active workout.
- **No network spinners**: everything is local and instant.

---

## 2. Framework & Tech Stack

| Layer | Choice | Why |
|---|---|---|
| Language | Kotlin | Android-native standard |
| UI | Jetpack Compose + Material 3 | Declarative, fast iteration |
| Architecture | MVVM + unidirectional state (StateFlow) | Standard, testable |
| Navigation | Navigation-Compose (type-safe routes) | Official Compose nav |
| Persistence | Room (SQLite) | Type-safe, coroutine-friendly |
| Preferences | DataStore (Proto or Preferences) | Modern SharedPreferences replacement |
| DI | Hilt | First-party, Compose-friendly |
| Async | Kotlin Coroutines + Flow | Native to Room/Compose |
| Background | WorkManager (post-MVP, for backup) | Reliable scheduling |
| Testing | JUnit4, Turbine, Compose UI test, Robolectric | Standard Android stack |
| Build | Gradle (Kotlin DSL), Version Catalogs | Modern default |
| Min SDK / Target | minSdk 26, targetSdk latest stable | ~98% device coverage, modern APIs |

---

## 3. Data Model (Room entities)

```
PlanEntity            (id, name, description, isTemplate, createdAt)
PlanDayEntity         (id, planId, dayIndex, name)              // e.g. "Push A"
ExerciseEntity        (id, name, primaryMuscle, equipment, notes, isCustom)
ExerciseAlternativeEntity (exerciseId, alternativeExerciseId, orderIdx)
PlannedExerciseEntity (id, planDayId, exerciseId, orderIdx, targetSets, repLow, repHigh, restSec, weightIncrementKg)
SessionEntity         (id, planDayId, startedAt, completedAt, notes)
SessionExerciseEntity (id, sessionId, plannedExerciseId, actualExerciseId, orderIdx)   // actualExerciseId lets us record swaps
SetLogEntity          (id, sessionExerciseId, setIndex, weightKg, reps, rpe?, isWarmup, completedAt)
UserPrefsEntity       (id=0, activePlanId, unit=KG|LB, defaultRestSec, progressionStrategy)
```

Relationships: one Plan → many Days → many PlannedExercises (each references an Exercise). Running a day creates a Session with SessionExercises (copying planned ones, allowing per-session swaps). SetLogs hang off SessionExercise.

---

## 4. Progressive Overload Rule (v1)

**Double-progression** per PlannedExercise:
- Look at the most recent completed Session for this plannedExerciseId.
- If **every working set** hit ≥ `repHigh` at weight `W`: suggest `W + weightIncrementKg` at `repLow` reps.
- Else if top set hit < `repLow`: suggest same `W`, same reps (stall — user can manually deload after 2 stalls; v2 auto-deloads).
- Else: suggest same `W`, try `lastReps + 1` on each set up to `repHigh`.
- No history yet → show empty inputs with range hint `"repLow–repHigh reps"`.

Encapsulated in `domain/suggestion/ProgressionStrategy.kt` so alt strategies (RPE, linear) can plug in later.

---

## 5. App Structure & Screens

### 5.1 Screens (Compose destinations)
1. **Home** — active plan card, "Start today's workout" CTA, recent sessions strip.
2. **Plans** — list of templates + user plans; "Clone", "Set active", "New plan".
3. **PlanDetail** — days list with exercise previews; edit/clone entry points.
4. **PlanEditor** — reorder days, add/remove exercises, edit sets/reps/alternatives.
5. **ExerciseLibrary** — searchable list, filter by muscle/equipment; create custom.
6. **ExerciseDetail** — history chart, alternatives list, notes.
7. **ActiveWorkout** — today's exercises as a vertical pager or list; per-exercise card with:
   - Suggestion line, set rows (weight/reps inputs), `Log set` button, `Swap` button, rest timer overlay.
8. **SessionSummary** — after finish: totals, PRs achieved, notes field.
9. **History** — sessions list; tap → read-only SessionSummary.
10. **Settings** — units, default rest, backup/restore (post-MVP), theme.

### 5.2 Project layout
```
app/src/main/java/com/<ns>/fitness/
├── FitnessApp.kt                 // @HiltAndroidApp
├── MainActivity.kt               // single-activity, Compose host
├── di/                           // Hilt modules
├── data/
│   ├── db/
│   │   ├── FitnessDatabase.kt
│   │   ├── entities/
│   │   └── dao/
│   ├── repository/               // PlanRepository, SessionRepository, ExerciseRepository
│   └── seed/                     // curated templates (JSON in assets/ + seeder)
├── domain/
│   ├── model/                    // plain Kotlin domain types (separate from entities)
│   ├── suggestion/               // ProgressionStrategy + impls
│   └── usecase/                  // StartSession, LogSet, SwapExercise, FinishSession
├── ui/
│   ├── theme/                    // Material 3 theme, typography, colors
│   ├── navigation/               // NavGraph, typed routes
│   ├── components/               // NumberStepper, RestTimer, SetRow, ExerciseCard
│   └── screens/
│       ├── home/
│       ├── plans/
│       ├── planeditor/
│       ├── exercises/
│       ├── workout/              // ActiveWorkoutScreen + ViewModel
│       ├── history/
│       └── settings/
└── util/                         // formatters, date helpers
```

### 5.3 Seed data (assets/seed/)
`exercises.json` (~40 common lifts with muscle/equipment/alternatives), `plans.json` (3 curated: PPL 6-day, Upper/Lower 4-day, Full-body 3-day). Seeded on first launch via a `DatabaseSeeder` invoked from a `RoomDatabase.Callback`.

### 5.4 Key flows

**Start workout:** Home → tap CTA → `StartSessionUseCase(activePlanDayId)` creates `Session` + `SessionExercise` rows from planned → navigate ActiveWorkout.

**Log set:** inputs → `LogSetUseCase` inserts `SetLogEntity`, marks the set complete, kicks off rest timer (in-memory countdown in ViewModel, notification via foreground service post-MVP).

**Swap exercise:** ExerciseCard "Swap" → bottom sheet shows `ExerciseAlternative`s for this exercise → choose → update `SessionExercise.actualExerciseId`. Option toggle: "also update plan" (writes to `PlannedExerciseEntity`).

**Suggestion:** when ActiveWorkout loads an exercise, ViewModel calls `ProgressionStrategy.suggest(plannedExerciseId, history)`; pre-fills set inputs.

---

## 6. Critical files to create (implementation checklist)

Setup:
- `build.gradle.kts` (root + app), `gradle/libs.versions.toml` — dependencies & plugins
- `FitnessApp.kt`, `MainActivity.kt`, `di/DatabaseModule.kt`, `di/RepositoryModule.kt`

Data:
- `data/db/FitnessDatabase.kt` + entities + DAOs listed in §3
- `data/db/DatabaseSeeder.kt`, `assets/seed/exercises.json`, `assets/seed/plans.json`
- `data/repository/{Plan,Exercise,Session}Repository.kt`

Domain:
- `domain/suggestion/ProgressionStrategy.kt` + `DoubleProgressionStrategy.kt`
- `domain/usecase/{StartSession,LogSet,SwapExercise,FinishSession}UseCase.kt`

UI (minimum for MVP walkthrough):
- `ui/navigation/NavGraph.kt`
- `ui/screens/home/HomeScreen.kt` + ViewModel
- `ui/screens/plans/PlansScreen.kt` + `PlanDetailScreen.kt`
- `ui/screens/workout/ActiveWorkoutScreen.kt` + ViewModel (core of the app)
- `ui/screens/workout/SwapExerciseSheet.kt`
- `ui/screens/history/HistoryScreen.kt`
- `ui/components/{SetRow,NumberStepper,RestTimer,ExerciseCard}.kt`

---

## 7. Verification

End-to-end smoke test after scaffolding:
1. `./gradlew assembleDebug` builds clean.
2. Install on a device/emulator (API 26+). First launch seeds DB (verify via `Database Inspector`: 3 plans, ~40 exercises).
3. Home → "Plans" → set PPL as active → back to Home.
4. Start today's workout → log 3 sets on first exercise → rest timer appears → `Swap` → pick alternative → log a set on the swapped exercise.
5. Finish session → SessionSummary shows counts.
6. Re-open same day → suggestion line shows last session's numbers and a progression hint.
7. History screen lists the session; tapping opens read-only summary.

Automated:
- **Unit:** `DoubleProgressionStrategyTest` — cases for no history, hit top of range, stalled, partial progress.
- **Repository:** Room in-memory DB tests for Session + SetLog flows (including swap).
- **UI:** Compose test for `ActiveWorkoutScreen` — logging a set updates state and pre-fills next set.

---

## 8. Open questions deferred to implementation
- Unit toggle (kg/lb) — MVP: app-wide setting, stored as kg internally.
- Bodyweight exercises — v1 treats weight field as "added weight", reps only is fine.
- Timezone/date boundaries for "today" — use device local date; store timestamps as UTC millis.
- Backup/restore format — JSON export to Downloads; defer to v1.1.
