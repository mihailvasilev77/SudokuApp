# SudokuApp — Staff-Level Code Review

**Reviewer perspective:** Staff Android Engineer & Technical Mentor
**Date:** April 2026
**Scope:** Full codebase review across architecture, bugs, refactoring, and V2 roadmap

---

## Part 1: Code Review & Best Practices

### 1.1 Architecture & State Management

Your app follows a rough MVVM shape — `GameViewModel` holds state, Fragments observe it, `SudokuGridView` renders it. That's the right instinct. But there are several places where the boundaries leak.

**Problem A: The ViewModel is a mutable grab-bag, not a state holder.**

Your `GameViewModel` exposes every field as a plain `var` or mutable array. Any class can reach in and mutate anything at any time, which makes it impossible to reason about "what changed and why." In professional Android, the ViewModel owns state and exposes it as an observable, immutable stream.

```kotlin
// BEFORE (your code) — GameViewModel.kt lines 9–23
val board = Array(9) { IntArray(9) }
val solution = Array(9) { IntArray(9) }
val isPrefilled = Array(9) { BooleanArray(9) }
val drafts = Array(9) { Array(9) { mutableSetOf<Int>() } }
var selectedRow = -1
var selectedCol = -1
var secondsElapsed = 0
var difficulty = "medium"
var isGameGenerated = false
var isDraftMode = false
```

```kotlin
// AFTER — encapsulated state with StateFlow
data class GameState(
    val board: List<List<Int>>,
    val solution: List<List<Int>>,
    val isPrefilled: List<List<Boolean>>,
    val drafts: List<List<Set<Int>>>,
    val selectedRow: Int = -1,
    val selectedCol: Int = -1,
    val secondsElapsed: Int = 0,
    val difficulty: Difficulty = Difficulty.MEDIUM,
    val isGameGenerated: Boolean = false,
    val isDraftMode: Boolean = false
)

sealed class Difficulty(val cellsToRemove: Int) {
    object Easy : Difficulty(35)
    object Medium : Difficulty(45)
    object Hard : Difficulty(55)
}

class GameViewModel : ViewModel() {
    private val _state = MutableStateFlow(GameState(...))
    val state: StateFlow<GameState> = _state.asStateFlow()

    fun selectCell(row: Int, col: Int) {
        _state.update { it.copy(selectedRow = row, selectedCol = col) }
    }
    // ... other actions as functions that call _state.update { ... }
}
```

**Why this matters:** With `StateFlow` + immutable `data class`, your Fragment collects a single stream and the UI always reflects the latest truth. There's no ambiguity about which field changed when. This is the foundation of Unidirectional Data Flow (UDF), which is the standard in modern Android.

---

**Problem B: The Fragment directly mutates ViewModel state.**

In `GameFragment.placeNumber()` (line 117), you write `viewModel.board[r][c] = 0` directly. The Fragment should never reach into the ViewModel's data structures and mutate them. Instead, the Fragment should call an action method on the ViewModel, and the ViewModel decides how to update its own state.

```kotlin
// BEFORE — GameFragment.kt line 117
viewModel.board[r][c] = 0

// AFTER — Fragment calls an action, ViewModel owns the mutation
// In Fragment:
viewModel.clearCell(r, c)

// In ViewModel:
fun clearCell(row: Int, col: Int) {
    _state.update { state ->
        val newBoard = state.board.toMutableList().map { it.toMutableList() }
        newBoard[row][col] = 0
        state.copy(board = newBoard)
    }
}
```

This same pattern applies everywhere: `placeNumber`, `applyHint`, `isDraftMode` toggle — all of these should be ViewModel functions, not Fragment-side mutations.

---

**Problem C: `GamePreferences` directly reaches into the ViewModel.**

`GamePreferences.saveGame(context, viewModel)` takes the entire ViewModel as a parameter and reads/writes its fields. This tightly couples your persistence layer to your presentation layer. A professional approach uses a Repository pattern:

```
Fragment → ViewModel → Repository → DataSource (SharedPreferences / Room)
```

The ViewModel should call `repository.saveGame(state)` with a plain data object, never pass itself to another layer.

---

**Problem D: Difficulty is a raw String.**

Using `"easy"`, `"medium"`, `"hard"` as strings is fragile — a typo silently breaks everything. Use a sealed class or enum (shown above). This also eliminates the `when/else` branch that currently defaults to medium silently.

---

### 1.2 Bad Practices

**Memory Leak: Unscoped CoroutineScope in Fragment.**

This is the most critical issue in your codebase.

```kotlin
// GameFragment.kt line 21
private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
```

This scope is **never cancelled**. When the Fragment is destroyed (e.g., you navigate away, or the Activity is recreated on rotation), the coroutine keeps running, holding a reference to the dead Fragment. This is a textbook memory leak.

```kotlin
// AFTER — use the built-in lifecycle-aware scope
// Delete the `scope` property entirely. Use viewLifecycleOwner.lifecycleScope:

private fun startTimer() {
    viewModel.timerJob?.cancel()
    viewModel.timerJob = viewLifecycleOwner.lifecycleScope.launch {
        while (isActive) {
            val minutes = viewModel.secondsElapsed / 60
            val seconds = viewModel.secondsElapsed % 60
            tvTimer.text = String.format("%02d:%02d", minutes, seconds)
            delay(1000)
            viewModel.secondsElapsed++
        }
    }
}
```

`viewLifecycleOwner.lifecycleScope` is automatically cancelled when the Fragment's view is destroyed. This is the correct scope for UI work in Fragments.

**Even better:** The timer should live entirely in the ViewModel using `viewModelScope`, and the Fragment should just observe the elapsed time:

```kotlin
// In ViewModel:
fun startTimer() {
    timerJob?.cancel()
    timerJob = viewModelScope.launch {
        while (isActive) {
            delay(1000)
            _state.update { it.copy(secondsElapsed = it.secondsElapsed + 1) }
        }
    }
}

// In Fragment — just observe:
viewLifecycleOwner.lifecycleScope.launch {
    viewModel.state.collect { state ->
        val m = state.secondsElapsed / 60
        val s = state.secondsElapsed % 60
        tvTimer.text = String.format("%02d:%02d", m, s)
    }
}
```

---

**Lifecycle mishandling: Timer drifts across pause/resume.**

Your timer increments `secondsElapsed` every ~1000ms of coroutine delay, but coroutine `delay` is not a wall-clock guarantee. After backgrounding and resuming, the timer will be slightly off. A more robust approach stores the `System.currentTimeMillis()` when the game started and computes elapsed time from that.

---

**`performClick()` not called in `onTouchEvent`.**

Your `SudokuGridView.onTouchEvent` returns `true` but never calls `performClick()`. This breaks accessibility (TalkBack) and triggers a lint warning. Add `performClick()` inside the `ACTION_DOWN` block.

---

### 1.3 Kotlin Idioms

**Java-in-Kotlin smell #1: Manual loops everywhere.**

```kotlin
// BEFORE — GameFragment.kt lines 137–146
private fun countCorrectNumber(num: Int): Int {
    var count = 0
    for (r in 0..8) {
        for (c in 0..8) {
            if (viewModel.board[r][c] == num && viewModel.solution[r][c] == num) {
                count++
            }
        }
    }
    return count
}

// AFTER — idiomatic Kotlin
private fun countCorrectNumber(num: Int): Int =
    (0..8).sumOf { r ->
        (0..8).count { c ->
            viewModel.board[r][c] == num && viewModel.solution[r][c] == num
        }
    }
```

**Java-in-Kotlin smell #2: Semicolons and Java-style iteration.**

```kotlin
// BEFORE — GameViewModel.kt lines 29–33
for (r in 0..8) for (c in 0..8) {
    solution[r][c] = board[r][c];  // <-- semicolons
    isPrefilled[r][c] = true;
    drafts[r][c].clear();
}

// AFTER
for (r in 0..8) for (c in 0..8) {
    solution[r][c] = board[r][c]
    isPrefilled[r][c] = true
    drafts[r][c].clear()
}
```

**Java-in-Kotlin smell #3: Redundant conditionals in `onDraw`.**

```kotlin
// BEFORE — SudokuGridView.kt lines 124–134
// This condition is checked 3 times redundantly:
paintText.color = if (solution[r][c] == board[r][c]) {
    if (solution[r][c] != 0 && solution[r][c] == board[r][c] && board[r][c] != 0 &&
        solution[r][c] == board[r][c] && !isPrefilled[r][c]
    ) {
        ContextCompat.getColor(context, R.color.sudoku_solved)
    } else {
        ContextCompat.getColor(context, R.color.black)
    }
} else {
    ContextCompat.getColor(context, R.color.black)
}

// AFTER — The outer if already guarantees solution == board.
// board[r][c] != 0 is guaranteed by the enclosing if. Simplify:
paintText.color = when {
    !isPrefilled[r][c] && board[r][c] == solution[r][c] ->
        ContextCompat.getColor(context, R.color.sudoku_solved)
    else ->
        ContextCompat.getColor(context, R.color.black)
}
```

**Java-in-Kotlin smell #4: Using `java.util.Random` instead of `kotlin.random.Random`.**

```kotlin
// BEFORE — GameFragment.kt line 237
val random = java.util.Random()

// AFTER — already imported in GameViewModel, use it consistently
import kotlin.random.Random
// then: Random.nextInt(30), Random.nextInt(container.width), etc.
```

---

## Part 2: Bug Hunt & Inconsistencies

### Bug 1 (Critical): Easy mode removes only 1 cell

```kotlin
// GameViewModel.kt line 37
val cellsToRemove = when (difficulty) {
    "easy" -> 1     // <-- This is clearly a typo/testing leftover
    "medium" -> 45
    "hard" -> 55
    else -> 45
}
```

Easy mode gives you a completed board with one single empty cell. This was probably `1` for testing and you forgot to change it. A reasonable value is 30–35 for easy.

### Bug 2 (Critical): Drafts accept 0 as a valid candidate

```kotlin
// GameFragment.kt line 111
if (viewModel.isDraftMode) {
    viewModel.drafts[r][c].add(num)  // num can be 0 (from Clear button)
```

When the user presses "Clear" in draft mode, `num` is `0`, and you add `0` to the draft set. Your draft rendering then tries to draw "0" in the cell. The fix: check `if (num == 0)` first and clear the drafts instead.

```kotlin
// FIX
if (viewModel.isDraftMode) {
    if (num == 0) {
        viewModel.drafts[r][c].clear()
    } else {
        viewModel.drafts[r][c].add(num)
    }
    sudokuGridView.invalidate()
    return
}
```

### Bug 3 (Medium): Drafts are not cleared when a correct number is placed

When you place a correct number in a cell, the drafts for that cell are not cleared. They're visually hidden (because `board[r][c] != 0` takes priority in rendering), but the data stays dirty. More importantly, **other cells' drafts that contain the placed number are not updated** — in a polished Sudoku app, placing a 5 should remove 5 from the drafts of all cells in the same row, column, and box.

### Bug 4 (Medium): `isPrefilled` array is never restored from SharedPreferences

`GamePreferences` saves/loads `board` and `solution`, but `isPrefilled` is never saved or loaded. After process death + restore, every non-zero cell will render as "prefilled" (black text) instead of distinguishing user-placed numbers (blue text). You need to save and restore the `isPrefilled` array alongside the board.

### Bug 5 (Low): Cell removal can re-hit already-empty cells

```kotlin
// GameViewModel.kt lines 43–52
repeat(cellsToRemove) {
    var r: Int; var c: Int
    do {
        r = Random.nextInt(9)
        c = Random.nextInt(9)
    } while (board[r][c] == 0)
    board[r][c] = 0
    isPrefilled[r][c] = false
}
```

This loop retries randomly until it finds a filled cell. For 55 removals (hard mode), this gets increasingly inefficient as the board empties — you'll see many wasted iterations. A better approach: shuffle a list of all 81 cell coordinates and take the first N.

```kotlin
// AFTER
val allCells = (0..8).flatMap { r -> (0..8).map { c -> r to c } }.shuffled()
allCells.take(cellsToRemove).forEach { (r, c) ->
    board[r][c] = 0
    isPrefilled[r][c] = false
}
```

### Bug 6 (Low): Device rotation creates duplicate timers

If the device rotates, the Fragment is recreated but the `viewModel.timerJob` from the old Fragment might still be running (depending on exact cancellation timing in `onPause`). When `onViewCreated` fires again, `startTimer()` cancels the old job and creates a new one — but there's a brief window where the timer could tick twice. Moving the timer entirely into the ViewModel eliminates this class of bugs.

### Bug 7 (Low): No bounds checking on `SudokuGridView.placeCorrectNumber`

```kotlin
fun placeCorrectNumber(num: Int) {
    wrongGuess = null
    board[selectedRow][selectedCol] = num  // selectedRow/Col could be -1
    invalidate()
}
```

If this is ever called before a cell is selected, you get an `ArrayIndexOutOfBoundsException`. Add a guard.

---

## Part 3: Refactoring Action Plan

Ordered from most critical to least, each step builds on the previous one.

### Step 1: Fix the coroutine memory leak
**Effort:** 15 minutes | **Impact:** Eliminates memory leak
Delete the `scope` property in `GameFragment`. Replace all uses with `viewLifecycleOwner.lifecycleScope`. This is a single find-and-replace that immediately fixes the most serious runtime issue.

### Step 2: Fix the bugs from Part 2
**Effort:** 30 minutes | **Impact:** Correct gameplay
Fix easy difficulty (change 1 → 35), draft-mode-clear bug, add `isPrefilled` to persistence, add bounds check to `placeCorrectNumber`, add `performClick()` to `onTouchEvent`.

### Step 3: Introduce the `Difficulty` enum/sealed class
**Effort:** 20 minutes | **Impact:** Type safety, no more magic strings
Replace `var difficulty = "medium"` with `var difficulty: Difficulty = Difficulty.Medium`. Update the spinner mapping in `MenuFragment` and the persistence layer.

### Step 4: Move game logic into the ViewModel
**Effort:** 1–2 hours | **Impact:** Clean separation of concerns
Move `placeNumber`, `applyHint`, `checkWin`, `startTimer` logic into `GameViewModel`. The Fragment should only call `viewModel.placeNumber(num)` and observe the resulting state. This is the single biggest architectural improvement you can make.

### Step 5: Introduce `StateFlow` + immutable state
**Effort:** 2–3 hours | **Impact:** True UDF, testable state
Wrap all ViewModel state into a `GameState` data class. Expose it via `StateFlow`. Have the Fragment collect it and rebind the UI. This makes your entire game state inspectable, loggable, and unit-testable.

### Step 6: Extract a Repository layer
**Effort:** 1–2 hours | **Impact:** Decoupled persistence
Create a `GameRepository` interface with `save(state)` / `load(): GameState?` / `clear()`. Implement it with `SharedPreferencesGameRepository`. Inject it into the ViewModel. This prepares you for the Room migration later.

### Step 7: Write unit tests for the pure logic
**Effort:** 2–3 hours | **Impact:** Confidence in refactoring
Your `isSafe`, `solveBoard`, `generateSudoku`, and `checkWin` functions are pure logic — no Android dependencies. Extract them into a `SudokuEngine` class and write JUnit tests. Aim for edge cases: what happens with a fully solved board? An empty board? An unsolvable configuration?

### Step 8: Polish Kotlin idioms throughout
**Effort:** 1 hour | **Impact:** Readability
Apply the `when` expressions, `sumOf`/`count` replacements, remove semicolons, and use `kotlin.random.Random` consistently.

---

## Part 4: Version 2.0 Roadmap

### Tech Stack Upgrades

**1. Migrate to Jetpack Compose**
Your entire UI is XML + custom `View` + manual `invalidate()` calls. Compose would let you declare the grid as a composable function that automatically redraws when `StateFlow<GameState>` emits. You already have Compose dependencies in your build file — they're just unused. Start by converting `MenuFragment` (simpler), then tackle the Sudoku grid as a `Canvas` composable. This is the highest-impact modernization for your portfolio.

**2. Add Room for game persistence and statistics**
Replace `SharedPreferences` + manual JSON with a Room database. This lets you store not just the current game, but a history of completed games with times, difficulty, and win/loss records. Define entities like `GameEntity` and `GameStatsEntity`. Room gives you compile-time query verification and plays beautifully with `Flow` for reactive data.

**3. Add dependency injection with Hilt**
Right now, `GamePreferences` is a static `object` and dependencies are wired by hand. Hilt gives you `@HiltViewModel`, `@Inject constructor`, and proper scoping. This is expected knowledge for any professional Android role and makes your code testable by allowing you to swap real dependencies for fakes.

### New User-Facing Features

**1. Undo/Redo stack**
Every number placement, draft toggle, and hint pushes the previous state onto a stack. An Undo button pops it. This is a natural fit for the immutable `GameState` approach — each state snapshot is an entry in the history. Users expect this in any puzzle game, and implementing it demonstrates solid data structure thinking.

**2. Statistics & Personal Records dashboard**
Track fastest solve time per difficulty, average time, number of hints used, win streak, and total games played. Display them on a dedicated screen with simple charts. This gives you a reason to use Room, gives users a reason to keep playing, and demonstrates you can build a feature end-to-end (data layer → ViewModel → UI).

**3. Animated error feedback with mistake counter**
Instead of just showing a red number, shake the cell, flash the conflicting row/column/box cells, and increment a visible mistake counter. At 3 mistakes, the game ends. This adds tension and replayability, and gives you practice with Android animation APIs (or Compose `animateColorAsState`, `Animatable`, etc.).

---

## Summary: Priority Matrix

| Priority | Task | Why |
|----------|------|-----|
| P0 | Fix coroutine memory leak | Crashes/leaks in production |
| P0 | Fix easy difficulty (1 → 35) | Game is unplayable on Easy |
| P0 | Fix draft-mode clear bug | Incorrect gameplay |
| P1 | Save/restore `isPrefilled` | State corruption after process death |
| P1 | Move logic into ViewModel | Foundation for everything else |
| P1 | Introduce `StateFlow` + UDF | Industry-standard architecture |
| P2 | Extract `SudokuEngine` + unit tests | Safety net for refactoring |
| P2 | Repository layer | Clean persistence boundary |
| P3 | Compose migration | Modern UI toolkit |
| P3 | Room + statistics | Rich feature + modern persistence |
| P3 | Hilt DI | Professional-grade wiring |

The P0 items are things you should fix today. P1 items are your next sprint. P2 and P3 build toward a genuinely impressive V2 that any interviewer would respect.

---

*Good foundations here, Mihail. The fact that you built a working Sudoku generator with backtracking, custom view rendering, draft mode, and state persistence as a learning project puts you ahead of where most beginners start. The patterns you need to learn next — UDF, StateFlow, proper scoping — are the exact things that separate junior from mid-level Android engineers. Fix the leaks, establish the architecture, and then the feature work becomes the fun part.*
