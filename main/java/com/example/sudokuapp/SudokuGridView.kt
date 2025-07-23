package com.example.sudokuapp

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat

class SudokuGridView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var board: Array<IntArray> = Array(9) { IntArray(9) }
    var solution: Array<IntArray> = Array(9) { IntArray(9) }

    var selectedRow = -1
    var selectedCol = -1

    var onCellSelected: ((Int, Int) -> Unit)? = null

    private var wrongGuess: Int? = null

    private val paintGridThick = Paint().apply {
        color = ContextCompat.getColor(context, R.color.black)
        strokeWidth = 6f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val paintGridThin = Paint().apply {
        color = ContextCompat.getColor(context, R.color.gray)
        strokeWidth = 2f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val paintText = Paint().apply {
        color = ContextCompat.getColor(context, R.color.black)
        textSize = 50f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
    }

    private val paintHighlightRowCol = Paint().apply {
        color = ContextCompat.getColor(context, R.color.light_gray)
    }

    private val paintHighlightSameNumber = Paint().apply {
        color = ContextCompat.getColor(context, R.color.sudoku_selected)
    }

    private val paintSelected = Paint().apply {
        color = ContextCompat.getColor(context, R.color.sudoku_correct_blue)
    }

    private val paintPrefilledBg = Paint().apply {
        color = ContextCompat.getColor(context, R.color.white)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = width.coerceAtMost(height).toFloat()
        val cellSize = size / 9f

        // 1) HIGHLIGHT ROW & COLUMN
        if (selectedRow != -1 && selectedCol != -1) {
            for (i in 0..8) {
                canvas.drawRect(
                    i * cellSize, selectedRow * cellSize,
                    (i + 1) * cellSize, (selectedRow + 1) * cellSize,
                    paintHighlightRowCol
                )
                canvas.drawRect(
                    selectedCol * cellSize, i * cellSize,
                    (selectedCol + 1) * cellSize, (i + 1) * cellSize,
                    paintHighlightRowCol
                )
            }
        }

        // 2) HIGHLIGHT SAME NUMBERS
        if (selectedRow != -1 && selectedCol != -1) {
            val selectedNum = board[selectedRow][selectedCol]
            if (selectedNum != 0) {
                for (r in 0..8) {
                    for (c in 0..8) {
                        if (board[r][c] == selectedNum) {
                            canvas.drawRect(
                                c * cellSize, r * cellSize,
                                (c + 1) * cellSize, (r + 1) * cellSize,
                                paintHighlightSameNumber
                            )
                        }
                    }
                }
            }
        }

        // 3) SELECTED CELL HIGHLIGHT
        if (selectedRow != -1 && selectedCol != -1) {
            canvas.drawRect(
                selectedCol * cellSize, selectedRow * cellSize,
                (selectedCol + 1) * cellSize, (selectedRow + 1) * cellSize,
                paintSelected
            )
        }

        // 4) DRAW NUMBERS
        val textOffset = (paintText.descent() + paintText.ascent()) / 2
        for (r in 0..8) {
            for (c in 0..8) {
                val x = c * cellSize + cellSize / 2
                val y = r * cellSize + cellSize / 2 - textOffset

                if (board[r][c] != 0) {
                    paintText.color = if (solution[r][c] == board[r][c]) {
                        if (solution[r][c] != 0 && solution[r][c] == board[r][c] && board[r][c] != 0 &&
                            solution[r][c] == board[r][c] && !isPrefilled(r, c)
                        ) {
                            ContextCompat.getColor(context, R.color.sudoku_solved) // Correctly guessed numbers
                        } else {
                            ContextCompat.getColor(context, R.color.black)
                        }
                    } else {
                        ContextCompat.getColor(context, R.color.black)
                    }
                    canvas.drawText(board[r][c].toString(), x, y, paintText)
                }
            }
        }

        // 5) DRAW WRONG GUESS TEMPORARILY
        if (selectedRow != -1 && selectedCol != -1 && wrongGuess != null) {
            paintText.color = ContextCompat.getColor(context, R.color.red)
            canvas.drawText(
                wrongGuess.toString(),
                selectedCol * cellSize + cellSize / 2,
                selectedRow * cellSize + cellSize / 2 - textOffset,
                paintText
            )
        }

        // 6) DRAW GRID
        for (i in 0..9) {
            val paint = if (i % 3 == 0) paintGridThick else paintGridThin
            canvas.drawLine(i * cellSize, 0f, i * cellSize, size, paint)
            canvas.drawLine(0f, i * cellSize, size, i * cellSize, paint)
        }
    }

    private fun isPrefilled(r: Int, c: Int): Boolean {
        return board[r][c] != 0 && board[r][c] == solution[r][c]
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val size = width.coerceAtMost(height).toFloat()
            val cellSize = size / 9f
            val c = (event.x / cellSize).toInt()
            val r = (event.y / cellSize).toInt()

            if (r in 0..8 && c in 0..8) {
                selectedRow = r
                selectedCol = c
                wrongGuess = null // clear wrong guess when selecting new cell
                onCellSelected?.invoke(r, c)
                invalidate()
            }
        }
        return true
    }

    fun showWrongGuess(num: Int) {
        wrongGuess = if (num == 0) null else num
        invalidate()
    }

    fun placeCorrectNumber(num: Int) {
        wrongGuess = null
        board[selectedRow][selectedCol] = num
        invalidate()
    }
}
