package com.example.sudokuapp

/**
 * Pure Sudoku logic with zero Android dependencies.
 *
 * This class handles puzzle generation, solving, and validation.
 * Because it has no Android imports, it can be tested with plain JUnit
 * on the JVM — no emulator or instrumented test needed.
 */
class SudokuEngine {

    // =====================================================================
    // PUZZLE GENERATION
    // =====================================================================

    /**
     * Generates a complete, valid Sudoku puzzle.
     *
     * @param difficulty controls how many cells are removed from the solution
     * @return a [GeneratedPuzzle] containing the player board, full solution,
     *         and which cells are prefilled (given clues)
     */
    fun generatePuzzle(difficulty: Difficulty): GeneratedPuzzle {
        val board = Array(9) { IntArray(9) }

        // Step 1: Fill the three diagonal 3x3 boxes independently.
        // These boxes don't share any row or column, so they can be
        // filled with random permutations without constraint checking.

        for (i in 0..8 step 3) {
            fillBox(board, i, i)
        }

        // Step 2: Solve the rest using backtracking.
        solveBoard(board, 0, 0)

        // Step 3: Copy the solved board as the solution.
        val solution = Array(9) { r -> board[r].copyOf() }
        val isPrefilled = Array(9) { BooleanArray(9) { true } }

        // Step 4: Remove cells to create the puzzle.
        val allCells = (0..8).flatMap { r -> (0..8).map { c -> r to c } }.shuffled()
        allCells.take(difficulty.cellsToRemove).forEach { (r, c) ->
            board[r][c] = 0
            isPrefilled[r][c] = false
        }

        return GeneratedPuzzle(board, solution, isPrefilled)
    }

    // =====================================================================
    // SOLVING
    // =====================================================================

    /**
     * Solves the given board in-place using recursive backtracking.
     *
     * @return true if a solution was found, false if the board is unsolvable
     */
    fun solveBoard(board: Array<IntArray>, row: Int, col: Int): Boolean {
        if (row == 9) return true
        if (col == 9) return solveBoard(board, row + 1, 0)
        if (board[row][col] != 0) return solveBoard(board, row, col + 1)

        for (num in (1..9).shuffled()) {
            if (isSafe(board, row, col, num)) {
                board[row][col] = num
                if (solveBoard(board, row, col + 1)) return true
                board[row][col] = 0
            }
        }
        return false
    }

    // =====================================================================
    // VALIDATION
    // =====================================================================

    /**
     * Checks whether placing [num] at [row],[col] violates Sudoku rules.
     * Checks the row, column, and 3x3 box.
     */
    fun isSafe(board: Array<IntArray>, row: Int, col: Int, num: Int): Boolean {
        // Check row and column
        for (i in 0..8) {
            if (board[row][i] == num || board[i][col] == num) return false
        }
        // Check 3x3 box
        val boxRow = row - row % 3
        val boxCol = col - col % 3
        for (r in 0..2) {
            for (c in 0..2) {
                if (board[boxRow + r][boxCol + c] == num) return false
            }
        }
        return true
    }

    /**
     * Returns true if every cell in [board] matches the corresponding
     * cell in [solution] and no cell is empty.
     */
    fun isBoardComplete(board: Array<IntArray>, solution: Array<IntArray>): Boolean {
        for (r in 0..8) {
            for (c in 0..8) {
                if (board[r][c] == 0 || board[r][c] != solution[r][c]) return false
            }
        }
        return true
    }

    /**
     * Checks that a fully filled board is a valid Sudoku solution:
     * every row, column, and 3x3 box contains exactly the digits 1–9.
     */
    fun isValidSolution(board: Array<IntArray>): Boolean {
        // Check rows
        for (r in 0..8) {
            if (board[r].toSet() != (1..9).toSet()) return false
        }
        // Check columns
        for (c in 0..8) {
            val col = (0..8).map { r -> board[r][c] }.toSet()
            if (col != (1..9).toSet()) return false
        }
        // Check 3x3 boxes
        for (boxR in 0..2) {
            for (boxC in 0..2) {
                val box = mutableSetOf<Int>()
                for (r in 0..2) {
                    for (c in 0..2) {
                        box.add(board[boxR * 3 + r][boxC * 3 + c])
                    }
                }
                if (box != (1..9).toSet()) return false
            }
        }
        return true
    }

    // =====================================================================
    // DRAFT MANAGEMENT
    // =====================================================================

    /**
     * Removes [num] from the draft sets of all cells in the same
     * row, column, and 3x3 box as [row],[col].
     */
    fun clearRelatedDrafts(
        drafts: Array<Array<MutableSet<Int>>>,
        row: Int,
        col: Int,
        num: Int
    ) {
        for (i in 0..8) {
            drafts[row][i].remove(num)
            drafts[i][col].remove(num)
        }
        val boxRow = row - row % 3
        val boxCol = col - col % 3
        for (dr in 0..2) {
            for (dc in 0..2) {
                drafts[boxRow + dr][boxCol + dc].remove(num)
            }
        }
    }

    // =====================================================================
    // PRIVATE HELPERS
    // =====================================================================

    private fun fillBox(board: Array<IntArray>, row: Int, col: Int) {
        val nums = (1..9).shuffled().toMutableList()
        for (r in 0..2) {
            for (c in 0..2) {
                board[row + r][col + c] = nums.removeAt(0)
            }
        }
    }
}

/**
 * Data class returned by [SudokuEngine.generatePuzzle].
 * Contains everything needed to start a new game.
 */
data class GeneratedPuzzle(
    val board: Array<IntArray>,
    val solution: Array<IntArray>,
    val isPrefilled: Array<BooleanArray>
)
