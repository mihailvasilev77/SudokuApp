package com.example.sudokuapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Result of a [GameViewModel.placeNumber] or [GameViewModel.applyHint] call.
 */
enum class PlaceNumberResult {
    IGNORED,
    DRAFTS_UPDATED,
    CLEARED,
    WRONG_GUESS,
    CORRECT,
    WIN,
    GAME_OVER       // mistake limit reached
}

class GameViewModel : ViewModel() {

    companion object {
        const val MAX_MISTAKES = 3
    }

    private val engine = SudokuEngine()

    // --- Game data ---
    val board = Array(9) { IntArray(9) }
    val solution = Array(9) { IntArray(9) }
    val isPrefilled = Array(9) { BooleanArray(9) }
    val drafts = Array(9) { Array(9) { mutableSetOf<Int>() } }

    var selectedRow = -1
    var selectedCol = -1

    var difficulty = Difficulty.MEDIUM
    var isGameGenerated = false
    var isDraftMode = false
        private set

    // --- Timer ---
    private val _secondsElapsed = MutableStateFlow(0)
    val secondsElapsed: StateFlow<Int> = _secondsElapsed.asStateFlow()
    private var timerJob: Job? = null

    // --- Mistake counter (reactive) ---
    private val _mistakes = MutableStateFlow(0)
    val mistakes: StateFlow<Int> = _mistakes.asStateFlow()

    // --- Hints used (for statistics) ---
    var hintsUsed = 0
        private set

    // --- Undo / Redo stacks ---
    // Each entry is a snapshot of (board, drafts) taken BEFORE an action.
    // This means undo restores the snapshot, and redo re-applies it.
    private val undoStack = ArrayDeque<BoardSnapshot>()
    private val redoStack = ArrayDeque<BoardSnapshot>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    // =====================================================================
    // UNDO / REDO
    // =====================================================================

    /**
     * Captures the current board + drafts state before a mutating action.
     * Called internally by placeNumber/applyHint before they modify the board.
     */
    private fun pushUndoSnapshot() {
        undoStack.addLast(BoardSnapshot.capture(board, drafts))
        // Any new action invalidates the redo history
        redoStack.clear()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        // Save current state to redo stack before reverting
        redoStack.addLast(BoardSnapshot.capture(board, drafts))
        undoStack.removeLast().restoreInto(board, drafts)
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        // Save current state to undo stack before re-applying
        undoStack.addLast(BoardSnapshot.capture(board, drafts))
        redoStack.removeLast().restoreInto(board, drafts)
    }

    // =====================================================================
    // STATE SNAPSHOT — for the Repository layer
    // =====================================================================

    fun toSaveState(): GameState = GameState.fromArrays(
        board = board,
        solution = solution,
        isPrefilled = isPrefilled,
        drafts = drafts,
        secondsElapsed = _secondsElapsed.value,
        difficulty = difficulty,
        selectedRow = selectedRow,
        selectedCol = selectedCol,
        isGameGenerated = isGameGenerated,
        mistakes = _mistakes.value
    )

    fun restoreFrom(state: GameState) {
        state.copyIntoArrays(board, solution, isPrefilled, drafts)
        _secondsElapsed.value = state.secondsElapsed
        difficulty = state.difficulty
        selectedRow = state.selectedRow
        selectedCol = state.selectedCol
        isGameGenerated = state.isGameGenerated
        _mistakes.value = state.mistakes
        // Clear history on restore — we don't persist undo/redo stacks
        undoStack.clear()
        redoStack.clear()
    }

    // =====================================================================
    // PUBLIC ACTIONS
    // =====================================================================

    fun selectCell(row: Int, col: Int) {
        selectedRow = row
        selectedCol = col
    }

    fun toggleDraftMode() {
        isDraftMode = !isDraftMode
    }

    fun disableDraftMode() {
        isDraftMode = false
    }

    fun placeNumber(num: Int): PlaceNumberResult {
        val r = selectedRow
        val c = selectedCol
        if (r !in 0..8 || c !in 0..8) return PlaceNumberResult.IGNORED
        if (isPrefilled[r][c]) return PlaceNumberResult.IGNORED

        if (isDraftMode) {
            if (num == 0) {
                if (drafts[r][c].isEmpty()) return PlaceNumberResult.IGNORED
                pushUndoSnapshot()
                drafts[r][c].clear()
            } else {
                pushUndoSnapshot()
                val cellDrafts = drafts[r][c]
                if (num in cellDrafts) cellDrafts.remove(num) else cellDrafts.add(num)
            }
            return PlaceNumberResult.DRAFTS_UPDATED
        }

        if (num == 0) {
            if (board[r][c] != 0 && board[r][c] == solution[r][c]) {
                return PlaceNumberResult.IGNORED
            }
            if (board[r][c] == 0) return PlaceNumberResult.IGNORED
            pushUndoSnapshot()
            board[r][c] = 0
            return PlaceNumberResult.CLEARED
        }

        if (solution[r][c] != num) {
            _mistakes.value++
            return if (_mistakes.value >= MAX_MISTAKES) {
                PlaceNumberResult.GAME_OVER
            } else {
                PlaceNumberResult.WRONG_GUESS
            }
        }

        // Correct number
        pushUndoSnapshot()
        board[r][c] = num
        drafts[r][c].clear()
        engine.clearRelatedDrafts(drafts, r, c, num)

        return if (engine.isBoardComplete(board, solution))
            PlaceNumberResult.WIN else PlaceNumberResult.CORRECT
    }

    fun applyHint(): PlaceNumberResult {
        val emptyCells = buildList {
            for (r in 0..8) for (c in 0..8) {
                if (!isPrefilled[r][c] && board[r][c] == 0) add(r to c)
            }
        }

        if (emptyCells.isEmpty()) return PlaceNumberResult.IGNORED

        pushUndoSnapshot()
        hintsUsed++
        val (r, c) = emptyCells.random()
        val num = solution[r][c]
        board[r][c] = num
        drafts[r][c].clear()
        engine.clearRelatedDrafts(drafts, r, c, num)

        return if (engine.isBoardComplete(board, solution))
            PlaceNumberResult.WIN else PlaceNumberResult.CORRECT
    }

    fun countCorrectNumber(num: Int): Int =
        (0..8).sumOf { r ->
            (0..8).count { c -> board[r][c] == num && solution[r][c] == num }
        }

    // =====================================================================
    // TIMER
    // =====================================================================

    fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                _secondsElapsed.value++
            }
        }
    }

    fun pauseTimer() {
        timerJob?.cancel()
    }

    fun resetTimer() {
        timerJob?.cancel()
        _secondsElapsed.value = 0
    }

    // =====================================================================
    // BOARD RESET (same puzzle, fresh attempt)
    // =====================================================================

    /**
     * Resets the board to its initial prefilled state without generating
     * a new puzzle. Clears all user-placed numbers, drafts, mistakes,
     * and undo/redo history. The solution and prefilled cells stay the same.
     */
    fun resetBoard() {
        for (r in 0..8) for (c in 0..8) {
            if (!isPrefilled[r][c]) {
                board[r][c] = 0
            }
            drafts[r][c].clear()
        }
        _mistakes.value = 0
        hintsUsed = 0
        selectedRow = -1
        selectedCol = -1
        isDraftMode = false
        undoStack.clear()
        redoStack.clear()
    }

    // =====================================================================
    // PUZZLE GENERATION
    // =====================================================================

    fun generateSudoku() {
        val puzzle = engine.generatePuzzle(difficulty)

        for (r in 0..8) for (c in 0..8) {
            board[r][c] = puzzle.board[r][c]
            solution[r][c] = puzzle.solution[r][c]
            isPrefilled[r][c] = puzzle.isPrefilled[r][c]
            drafts[r][c].clear()
        }

        _mistakes.value = 0
        hintsUsed = 0
        undoStack.clear()
        redoStack.clear()
        isGameGenerated = true
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}

/**
 * Lightweight snapshot of the board and drafts for undo/redo.
 *
 * We only snapshot the data that changes on user actions — not
 * the solution, isPrefilled, timer, etc. which are immutable
 * during gameplay. This keeps memory usage low.
 */
data class BoardSnapshot(
    val board: List<List<Int>>,
    val drafts: List<List<Set<Int>>>
) {
    companion object {
        fun capture(
            board: Array<IntArray>,
            drafts: Array<Array<MutableSet<Int>>>
        ): BoardSnapshot = BoardSnapshot(
            board = board.map { it.toList() },
            drafts = drafts.map { row -> row.map { it.toSet() } }
        )
    }

    fun restoreInto(
        board: Array<IntArray>,
        drafts: Array<Array<MutableSet<Int>>>
    ) {
        for (r in 0..8) for (c in 0..8) {
            board[r][c] = this.board[r][c]
            drafts[r][c].clear()
            drafts[r][c].addAll(this.drafts[r][c])
        }
    }
}
