package com.example.sudokuapp

/**
 * Immutable snapshot of the entire game state, used for persistence.
 *
 * This is a plain data class with no Android dependencies, which means:
 *   - It can cross the ViewModel ↔ Repository boundary cleanly
 *   - It's easy to serialize (to SharedPreferences now, Room later)
 *   - It's easy to test
 *
 * The ViewModel converts its mutable working arrays into this snapshot
 * when saving, and restores from it when loading.
 */
data class GameState(
    val board: List<List<Int>>,
    val solution: List<List<Int>>,
    val isPrefilled: List<List<Boolean>>,
    val drafts: List<List<Set<Int>>>,
    val secondsElapsed: Int,
    val difficulty: Difficulty,
    val selectedRow: Int,
    val selectedCol: Int,
    val isGameGenerated: Boolean
) {
    companion object {
        /** Convenience builder from the mutable arrays used at runtime. */
        fun fromArrays(
            board: Array<IntArray>,
            solution: Array<IntArray>,
            isPrefilled: Array<BooleanArray>,
            drafts: Array<Array<MutableSet<Int>>>,
            secondsElapsed: Int,
            difficulty: Difficulty,
            selectedRow: Int,
            selectedCol: Int,
            isGameGenerated: Boolean
        ): GameState = GameState(
            board = board.map { it.toList() },
            solution = solution.map { it.toList() },
            isPrefilled = isPrefilled.map { it.toList() },
            drafts = drafts.map { row -> row.map { it.toSet() } },
            secondsElapsed = secondsElapsed,
            difficulty = difficulty,
            selectedRow = selectedRow,
            selectedCol = selectedCol,
            isGameGenerated = isGameGenerated
        )
    }

    /** Write board data back into mutable arrays (used by ViewModel on restore). */
    fun copyIntoArrays(
        board: Array<IntArray>,
        solution: Array<IntArray>,
        isPrefilled: Array<BooleanArray>,
        drafts: Array<Array<MutableSet<Int>>>
    ) {
        for (r in 0..8) for (c in 0..8) {
            board[r][c] = this.board[r][c]
            solution[r][c] = this.solution[r][c]
            isPrefilled[r][c] = this.isPrefilled[r][c]
            drafts[r][c].clear()
            drafts[r][c].addAll(this.drafts[r][c])
        }
    }
}
