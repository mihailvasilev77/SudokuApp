package com.example.sudokuapp

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.*
import kotlin.random.Random

class GameViewModel : ViewModel() {

    val board = Array(9) { IntArray(9) }
    val solution = Array(9) { IntArray(9) }
    val isPrefilled = Array(9) { BooleanArray(9)  }
    val drafts = Array(9) { Array(9) { mutableSetOf<Int>() } }

    var selectedRow = -1
    var selectedCol = -1

    var secondsElapsed = 0
    var timerJob: Job? = null

    var difficulty = "medium"
    var isGameGenerated = false
    var isDraftMode = false


    fun generateSudoku() {
        board.forEach { it.fill(0) }
        fillDiagonalBoxes()
        solveBoard(0, 0)
        for (r in 0..8) for (c in 0..8) {
            solution[r][c] = board[r][c];
            isPrefilled[r][c] = true;
            drafts[r][c].clear();
        }


        val cellsToRemove = when (difficulty) {
            "easy" -> 1
            "medium" -> 45
            "hard" -> 55
            else -> 45
        }

        repeat(cellsToRemove) {
            var r: Int
            var c: Int
            do {
                r = Random.nextInt(9)
                c = Random.nextInt(9)
            } while (board[r][c] == 0)
            board[r][c] = 0
            isPrefilled[r][c] = false
        }

        isGameGenerated = true
    }

    private fun fillDiagonalBoxes() {
        for (i in 0..8 step 3) {
            fillBox(i, i)
        }
    }

    private fun fillBox(row: Int, col: Int) {
        val nums = (1..9).shuffled().toMutableList()
        for (r in 0..2) {
            for (c in 0..2) {
                board[row + r][col + c] = nums.removeAt(0)
            }
        }
    }

    private fun solveBoard(row: Int, col: Int): Boolean {
        var r = row
        var c = col
        if (r == 9) return true
        if (c == 9) return solveBoard(r + 1, 0)
        if (board[r][c] != 0) return solveBoard(r, c + 1)

        for (num in 1..9) {
            if (isSafe(r, c, num)) {
                board[r][c] = num
                if (solveBoard(r, c + 1)) return true
                board[r][c] = 0
            }
        }
        return false
    }

    private fun isSafe(row: Int, col: Int, num: Int): Boolean {
        for (i in 0..8) {
            if (board[row][i] == num || board[i][col] == num) return false
        }
        val startRow = row - row % 3
        val startCol = col - col % 3
        for (r in 0..2) {
            for (c in 0..2) {
                if (board[startRow + r][startCol + c] == num) return false
            }
        }
        return true
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}
