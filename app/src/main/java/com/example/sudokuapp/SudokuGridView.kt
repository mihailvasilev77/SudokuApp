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
    var isPrefilled: Array<BooleanArray> = Array(9) { BooleanArray(9) }
    var drafts: Array<Array<MutableSet<Int>>> = Array(9) { Array(9) { mutableSetOf<Int>() } }

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

    // =====================================================================
    // GRID GEOMETRY
    // =====================================================================

    /**
     * Padding inset so thick border lines aren't clipped at the view edges.
     * Without this, lines drawn at x=0 or y=0 have half their stroke width
     * outside the view bounds, making outer borders appear thinner than
     * inner 3x3 box borders.
     */
    private val gridPadding get() = paintGridThick.strokeWidth / 2f

    /** Total drawable size (square, fits within width/height). */
    private val totalSize get() = width.coerceAtMost(height).toFloat()

    /** Size of the 9x9 grid area after inset. */
    private val gridSize get() = totalSize - gridPadding * 2f

    /** Size of one cell. */
    private val cellSize get() = gridSize / 9f

    /** Left/top coordinate of a cell. */
    private fun cellLeft(col: Int) = gridPadding + col * cellSize
    private fun cellTop(row: Int) = gridPadding + row * cellSize

    // =====================================================================
    // DRAWING
    // =====================================================================

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cs = cellSize  // cache for performance inside loops
        val pad = gridPadding

        // 1) HIGHLIGHT ROW & COLUMN of selected cell
        if (selectedRow != -1 && selectedCol != -1) {
            for (i in 0..8) {
                // Entire selected row
                canvas.drawRect(
                    cellLeft(i), cellTop(selectedRow),
                    cellLeft(i) + cs, cellTop(selectedRow) + cs,
                    paintHighlightRowCol
                )
                // Entire selected column
                canvas.drawRect(
                    cellLeft(selectedCol), cellTop(i),
                    cellLeft(selectedCol) + cs, cellTop(i) + cs,
                    paintHighlightRowCol
                )
            }
        }

        // 2) HIGHLIGHT cells containing the same number as the selected cell
        if (selectedRow != -1 && selectedCol != -1) {
            val selectedNum = board[selectedRow][selectedCol]
            if (selectedNum != 0) {
                for (r in 0..8) {
                    for (c in 0..8) {
                        if (board[r][c] == selectedNum) {
                            canvas.drawRect(
                                cellLeft(c), cellTop(r),
                                cellLeft(c) + cs, cellTop(r) + cs,
                                paintHighlightSameNumber
                            )
                        }
                    }
                }
            }
        }

        // 3) SELECTED CELL highlight (drawn on top of row/col highlights)
        if (selectedRow != -1 && selectedCol != -1) {
            canvas.drawRect(
                cellLeft(selectedCol), cellTop(selectedRow),
                cellLeft(selectedCol) + cs, cellTop(selectedRow) + cs,
                paintSelected
            )
        }

        // 4) DRAW NUMBERS AND DRAFTS
        val textOffset = (paintText.descent() + paintText.ascent()) / 2
        val originalTextSize = paintText.textSize

        for (r in 0..8) {
            for (c in 0..8) {
                val x = cellLeft(c) + cs / 2
                val y = cellTop(r) + cs / 2 - textOffset

                val isWrongGuessCell = (r == selectedRow && c == selectedCol && wrongGuess != null)

                if (board[r][c] != 0) {
                    paintText.color = when {
                        !isPrefilled[r][c] && board[r][c] == solution[r][c] ->
                            ContextCompat.getColor(context, R.color.sudoku_solved)
                        else ->
                            ContextCompat.getColor(context, R.color.black)
                    }
                    canvas.drawText(board[r][c].toString(), x, y, paintText)

                } else if (drafts[r][c].isNotEmpty() && !isWrongGuessCell) {
                    // FIX: Skip drawing drafts if a wrong guess is being shown
                    // in this cell. Otherwise the draft numbers show through
                    // behind the red wrong-guess number.
                    paintText.textSize = originalTextSize / 2.5f
                    paintText.color = ContextCompat.getColor(context, R.color.gray)

                    for (num in drafts[r][c]) {
                        val noteX = ((num - 1) % 3) * (cs / 3)
                        val noteY = ((num - 1) / 3) * (cs / 3)

                        val cx = cellLeft(c) + noteX + cs / 6
                        val cy = cellTop(r) + noteY + cs / 3
                        canvas.drawText(num.toString(), cx, cy, paintText)
                    }

                    paintText.textSize = originalTextSize
                }
            }
        }

        // 5) DRAW WRONG GUESS (red number, temporary)
        if (selectedRow != -1 && selectedCol != -1 && wrongGuess != null) {
            val textOff = (paintText.descent() + paintText.ascent()) / 2
            paintText.color = ContextCompat.getColor(context, R.color.red)
            canvas.drawText(
                wrongGuess.toString(),
                cellLeft(selectedCol) + cs / 2,
                cellTop(selectedRow) + cs / 2 - textOff,
                paintText
            )
        }

        // 6) DRAW GRID LINES
        // Inner cell lines (thin)
        for (i in 0..9) {
            if (i % 3 != 0) {
                val pos = pad + i * cs
                canvas.drawLine(pos, pad, pos, pad + gridSize, paintGridThin)
                canvas.drawLine(pad, pos, pad + gridSize, pos, paintGridThin)
            }
        }
        // 3x3 box borders and outer border (thick) — drawn last so they
        // paint over thin lines at shared positions (0, 3, 6, 9)
        for (i in 0..3) {
            val pos = pad + i * 3 * cs
            canvas.drawLine(pos, pad, pos, pad + gridSize, paintGridThick)
            canvas.drawLine(pad, pos, pad + gridSize, pos, paintGridThick)
        }
    }

    // =====================================================================
    // TOUCH HANDLING
    // =====================================================================

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            // Convert touch coordinates to grid row/col, accounting for padding
            val c = ((event.x - gridPadding) / cellSize).toInt()
            val r = ((event.y - gridPadding) / cellSize).toInt()

            if (r in 0..8 && c in 0..8) {
                selectedRow = r
                selectedCol = c
                wrongGuess = null
                onCellSelected?.invoke(r, c)
                invalidate()
            }

            performClick()
        }
        return true
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    // =====================================================================
    // PUBLIC API (called by Fragment)
    // =====================================================================

    fun showWrongGuess(num: Int) {
        wrongGuess = if (num == 0) null else num
        invalidate()
    }

    fun placeCorrectNumber(num: Int) {
        if (selectedRow !in 0..8 || selectedCol !in 0..8) return
        wrongGuess = null
        board[selectedRow][selectedCol] = num
        invalidate()
    }
}
