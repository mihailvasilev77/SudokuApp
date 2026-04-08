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
 *
 * The Fragment maps this to visual feedback (grid invalidation, wrong-guess
 * color, win animation) without containing any game logic.
 */
enum class PlaceNumberResult {
    IGNORED,
    DRAFTS_UPDATED,
    CLEARED,
    WRONG_GUESS,
    CORRECT,
    WIN
}

class GameViewModel : ViewModel() {

    private val engine = SudokuEngine()

    // --- Game data (mutable arrays shared with SudokuGridView for rendering) ---
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

    private val _secondsElapsed = MutableStateFlow(0)
    val secondsElapsed: StateFlow<Int> = _secondsElapsed.asStateFlow()

    private var timerJob: Job? = null

    // =====================================================================
    // STATE SNAPSHOT — for the Repository layer
    // =====================================================================

    /** Creates an immutable snapshot of all game state for persistence. */
    fun toSaveState(): GameState = GameState.fromArrays(
        board = board,
        solution = solution,
        isPrefilled = isPrefilled,
        drafts = drafts,
        secondsElapsed = _secondsElapsed.value,
        difficulty = difficulty,
        selectedRow = selectedRow,
        selectedCol = selectedCol,
        isGameGenerated = isGameGenerated
    )

    /** Restores game state from a persisted snapshot. */
    fun restoreFrom(state: GameState) {
        state.copyIntoArrays(board, solution, isPrefilled, drafts)
        _secondsElapsed.value = state.secondsElapsed
        difficulty = state.difficulty
        selectedRow = state.selectedRow
        selectedCol = state.selectedCol
        isGameGenerated = state.isGameGenerated
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
                drafts[r][c].clear()
            } else {
                val cellDrafts = drafts[r][c]
                if (num in cellDrafts) cellDrafts.remove(num) else cellDrafts.add(num)
            }
            return PlaceNumberResult.DRAFTS_UPDATED
        }

        if (num == 0) {
            if (board[r][c] != 0 && board[r][c] == solution[r][c]) {
                return PlaceNumberResult.IGNORED
            }
            board[r][c] = 0
            return PlaceNumberResult.CLEARED
        }

        if (solution[r][c] != num) return PlaceNumberResult.WRONG_GUESS

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

        isGameGenerated = true
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}
