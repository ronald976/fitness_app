# Project notes for Claude

Android Kotlin / Jetpack Compose / Hilt / Room. Single-module app under `app/`.

## Build

- `./gradlew compileDebugKotlin` to type-check, `./gradlew assembleDebug` to build.
- **JDK is not on PATH on the dev machine** — `./gradlew` fails with "JAVA_HOME is not set". Don't promise build verification; rely on careful editing and let the user run the build.
- Don't commit `.kt` line-ending warnings — Git auto-converts LF↔CRLF on Windows.

## Architecture map

```
app/src/main/java/com/fitness/app/
├── data/
│   ├── db/
│   │   ├── entities/         # Room @Entity classes
│   │   └── dao/              # @Dao interfaces (queries here)
│   ├── repository/           # Thin wrappers over DAOs (single source of truth for app code)
│   ├── importer/LogImporter.kt   # Parses assets/logs/*.txt into sessions
│   └── xlsx/                 # Excel import/export
├── domain/
│   ├── suggestion/           # ProgressionStrategy + DoubleProgressionStrategy
│   └── usecase/              # GetSuggestionUseCase, DetectPrUseCase, SwapExerciseUseCase, etc.
└── ui/
    ├── components/           # Shared composables (ExerciseCard, SetRow, RestTimer)
    ├── navigation/FitnessNavHost.kt
    └── screens/
        ├── workout/          # ActiveWorkoutScreen + ViewModel + sheets (Swap, Add, ExercisePicker)
        ├── history/          # HistoryScreen + ViewModel
        └── dashboard/        # DashboardScreen + ViewModel + DashboardCharts
```

## Data model gotchas

- **`SessionExerciseEntity` has two exercise IDs**: `actualExerciseId` (the real `ExerciseEntity` performed) and `plannedExerciseId` (optional link back to the plan). Imported history (`LogImporter`) sets `plannedExerciseId = null`. **For cross-session lookups (suggestions, PRs, history), always match on `actualExerciseId`** — matching on `plannedExerciseId` silently drops imported data.
- Imported sets that couldn't be parsed for reps are stored as `reps = 0`. Filter `reps > 0` (and often `weightKg > 0`) when computing PRs / suggestions to avoid garbage.
- CASCADE deletes flow `SessionEntity → SessionExerciseEntity → SetLogEntity`. Deleting a session cleans up children automatically.
- `ExerciseEntity` has no unique constraint on `name`. When creating customs from user input, dedupe via `findOrCreateCustom(name)` in `ExerciseRepository` (case-insensitive name match).

## UI conventions

- Compose Material3. Top-level surface uses `Scaffold` with `TopAppBar`.
- Per-row state in workout uses `WorkoutExerciseUi` / `SetRowState` (data classes in `ActiveWorkoutViewModel.kt`). Add a field there, populate in `load()`, surface via composables.
- Dashboard charts are hand-drawn with Compose `Canvas` (`DashboardCharts.kt`). Tap-to-inspect uses `pointerInput { awaitPointerEventScope }` + a `Popup` overlay (see Forecast/Progression tabs for the pattern).

## Workflow conventions (from user)

- **No `Co-Authored-By: Claude` trailer in commits.** Plain HEREDOC body, no trailer.
- No emojis in code or messages unless explicitly requested.
- Commit messages: short subject (≤70 chars), body explains why not what. Multiple thematic bullets are fine for bundled commits.
- The user runs the actual app on a physical device; treat their visual feedback as ground truth (e.g., "the grey blob" = the unlogged Log button).
- Don't claim build success — say "edited and looks consistent" instead.

## Frequent file references

- Suggestion logic (what gets pre-filled in next workout): `domain/suggestion/DoubleProgressionStrategy.kt` driven by `domain/usecase/GetSuggestionUseCase.kt`.
- PR detection (celebration after logging): `domain/usecase/DetectPrUseCase.kt`.
- Best-set lookup (display PR on card): `SessionDao.bestPriorSetFor` ranks by `weightKg * reps DESC`.
- All-sets dashboard query: `SessionDao.allSetsForDashboard` returns flat `DashboardSetRow`s — use this for any aggregate, not the full session graph.
- Persistent log files: `app/src/main/assets/logs/*.txt` (Gmail-style historical workout logs).

## Testing

- Unit tests in `app/src/test/`. `DoubleProgressionStrategyTest` covers no-history, top-of-range, partial, stall, cap.
- No Compose UI tests run automatically here; rely on user device testing.
