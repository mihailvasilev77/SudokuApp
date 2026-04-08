package com.example.sudokuapp

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [SudokuEngine].
 *
 * These run on the JVM (no emulator needed) because SudokuEngine
 * has zero Android dependencies. That's the whole point of extracting
 * it from the ViewModel.
 */
class SudokuEngineTest {

    private lateinit var engine: SudokuEngine

    @Before
    fun setUp() {
        engine = SudokuEngine()
    }

    // =====================================================================
    // PUZZLE GENERATION
    // =====================================================================

    @Test
    fun `generatePuzzle produces a valid complete solution`() {
        val puzzle = engine.generatePuzzle(Difficulty.MEDIUM)
        assertTrue(
            "Solution should be a valid Sudoku",
            engine.isValidSolution(puzzle.solution)
        )
    }

    @Test
    fun `generatePuzzle removes correct number of cells for each difficulty`() {
        for (difficulty in Difficulty.entries) {
            val puzzle = engine.generatePuzzle(difficulty)
            val emptyCells = puzzle.board.sumOf { row -> row.count { it == 0 } }
            assertEquals(
                "Difficulty ${difficulty.label} should remove ${difficulty.cellsToRemove} cells",
                difficulty.cellsToRemove,
                emptyCells
            )
        }
    }

    @Test
    fun `generatePuzzle marks empty cells as not prefilled`() {
        val puzzle = engine.generatePuzzle(Difficulty.HARD)
        for (r in 0..8) {
            for (c in 0..8) {
                if (puzzle.board[r][c] == 0) {
                    assertFalse(
                        "Empty cell [$r][$c] should not be prefilled",
                        puzzle.isPrefilled[r][c]
                    )
                } else {
                    assertTrue(
                        "Filled cell [$r][$c] should be prefilled",
                        puzzle.isPrefilled[r][c]
                    )
                }
            }
        }
    }

    @Test
    fun `generatePuzzle board matches solution for filled cells`() {
        val puzzle = engine.generatePuzzle(Difficulty.EASY)
        for (r in 0..8) {
            for (c in 0..8) {
                if (puzzle.board[r][c] != 0) {
                    assertEquals(
                        "Filled cell [$r][$c] should match the solution",
                        puzzle.solution[r][c],
                        puzzle.board[r][c]
                    )
                }
            }
        }
    }

    @Test
    fun `multiple generations produce different puzzles`() {
        val puzzle1 = engine.generatePuzzle(Difficulty.MEDIUM)
        val puzzle2 = engine.generatePuzzle(Difficulty.MEDIUM)
        // It's theoretically possible for two puzzles to be identical,
        // but astronomically unlikely. Check solutions differ.
        val different = (0..8).any { r ->
            (0..8).any { c -> puzzle1.solution[r][c] != puzzle2.solution[r][c] }
        }
        assertTrue("Two generated puzzles should (almost certainly) differ", different)
    }

    // =====================================================================
    // isSafe
    // =====================================================================

    @Test
    fun `isSafe rejects duplicate in same row`() {
        val board = Array(9) { IntArray(9) }
        board[0][0] = 5
        assertFalse("5 already in row 0", engine.isSafe(board, 0, 8, 5))
    }

    @Test
    fun `isSafe rejects duplicate in same column`() {
        val board = Array(9) { IntArray(9) }
        board[0][3] = 7
        assertFalse("7 already in column 3", engine.isSafe(board, 8, 3, 7))
    }

    @Test
    fun `isSafe rejects duplicate in same 3x3 box`() {
        val board = Array(9) { IntArray(9) }
        board[1][1] = 3
        // (0,0) is in the same box as (1,1)
        assertFalse("3 already in box", engine.isSafe(board, 0, 0, 3))
    }

    @Test
    fun `isSafe accepts valid placement`() {
        val board = Array(9) { IntArray(9) }
        board[0][0] = 5
        // 3 in (0,8) doesn't conflict with 5 in (0,0) — different number
        assertTrue("3 should be safe at (0,8)", engine.isSafe(board, 0, 8, 3))
    }

    @Test
    fun `isSafe works on empty board`() {
        val board = Array(9) { IntArray(9) }
        for (num in 1..9) {
            assertTrue("Any number should be safe on empty board", engine.isSafe(board, 4, 4, num))
        }
    }

    // =====================================================================
    // BOARD COMPLETION
    // =====================================================================

    @Test
    fun `isBoardComplete returns false for board with empty cells`() {
        val puzzle = engine.generatePuzzle(Difficulty.EASY)
        assertFalse(
            "Puzzle with empty cells is not complete",
            engine.isBoardComplete(puzzle.board, puzzle.solution)
        )
    }

    @Test
    fun `isBoardComplete returns true when board matches solution`() {
        val puzzle = engine.generatePuzzle(Difficulty.EASY)
        // Fill in all empty cells with the correct solution
        for (r in 0..8) for (c in 0..8) {
            puzzle.board[r][c] = puzzle.solution[r][c]
        }
        assertTrue(
            "Fully filled correct board should be complete",
            engine.isBoardComplete(puzzle.board, puzzle.solution)
        )
    }

    @Test
    fun `isBoardComplete returns false when board has wrong number`() {
        val puzzle = engine.generatePuzzle(Difficulty.EASY)
        // Fill everything correctly first
        for (r in 0..8) for (c in 0..8) {
            puzzle.board[r][c] = puzzle.solution[r][c]
        }
        // Corrupt one cell
        val original = puzzle.board[4][4]
        puzzle.board[4][4] = if (original == 9) 1 else original + 1
        assertFalse(
            "Board with one wrong number should not be complete",
            engine.isBoardComplete(puzzle.board, puzzle.solution)
        )
    }

    // =====================================================================
    // SOLUTION VALIDATION
    // =====================================================================

    @Test
    fun `isValidSolution rejects board with duplicate in row`() {
        val puzzle = engine.generatePuzzle(Difficulty.EASY)
        val solution = puzzle.solution
        // Break row 0 by making two cells the same
        val saved = solution[0][8]
        solution[0][8] = solution[0][0]
        assertFalse("Duplicate in row should be invalid", engine.isValidSolution(solution))
        solution[0][8] = saved // restore
    }

    @Test
    fun `isValidSolution rejects empty board`() {
        val board = Array(9) { IntArray(9) }
        assertFalse("Empty board is not a valid solution", engine.isValidSolution(board))
    }

    // =====================================================================
    // SOLVER
    // =====================================================================

    @Test
    fun `solveBoard solves a puzzle with removed cells`() {
        val puzzle = engine.generatePuzzle(Difficulty.HARD)
        val boardCopy = Array(9) { r -> puzzle.board[r].copyOf() }
        val solved = engine.solveBoard(boardCopy, 0, 0)
        assertTrue("Solver should find a solution", solved)
        assertTrue("Solved board should be valid", engine.isValidSolution(boardCopy))
    }

    @Test
    fun `solveBoard returns false for contradictory board`() {
        val board = Array(9) { IntArray(9) }
        // Place two 1s in the same row — unsolvable
        board[0][0] = 1
        board[0][1] = 1
        assertFalse(
            "Board with contradictions should be unsolvable",
            engine.solveBoard(board, 0, 0)
        )
    }

    // =====================================================================
    // DRAFT MANAGEMENT
    // =====================================================================

    @Test
    fun `clearRelatedDrafts removes number from same row`() {
        val drafts = Array(9) { Array(9) { mutableSetOf<Int>() } }
        // Put 5 as a draft in every cell of row 3
        for (c in 0..8) drafts[3][c].add(5)

        engine.clearRelatedDrafts(drafts, 3, 4, 5)

        for (c in 0..8) {
            assertFalse(
                "Draft 5 should be removed from row 3, col $c",
                5 in drafts[3][c]
            )
        }
    }

    @Test
    fun `clearRelatedDrafts removes number from same column`() {
        val drafts = Array(9) { Array(9) { mutableSetOf<Int>() } }
        for (r in 0..8) drafts[r][2].add(7)

        engine.clearRelatedDrafts(drafts, 5, 2, 7)

        for (r in 0..8) {
            assertFalse(
                "Draft 7 should be removed from col 2, row $r",
                7 in drafts[r][2]
            )
        }
    }

    @Test
    fun `clearRelatedDrafts removes number from same 3x3 box`() {
        val drafts = Array(9) { Array(9) { mutableSetOf<Int>() } }
        // Fill box (3,3)-(5,5) with draft 9
        for (r in 3..5) for (c in 3..5) drafts[r][c].add(9)

        engine.clearRelatedDrafts(drafts, 4, 4, 9)

        for (r in 3..5) {
            for (c in 3..5) {
                assertFalse(
                    "Draft 9 should be removed from box cell [$r][$c]",
                    9 in drafts[r][c]
                )
            }
        }
    }

    @Test
    fun `clearRelatedDrafts does not affect unrelated cells`() {
        val drafts = Array(9) { Array(9) { mutableSetOf<Int>() } }
        // Put 4 in cell (8,8) — not in row 0, col 0, or box (0,0)
        drafts[8][8].add(4)

        engine.clearRelatedDrafts(drafts, 0, 0, 4)

        assertTrue(
            "Draft 4 in unrelated cell (8,8) should remain",
            4 in drafts[8][8]
        )
    }
}
