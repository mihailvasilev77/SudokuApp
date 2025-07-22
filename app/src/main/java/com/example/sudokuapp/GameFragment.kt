package com.example.sudokuapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit
import kotlinx.coroutines.*

class GameFragment : Fragment() {

    private lateinit var sudokuGridView: SudokuGridView
    private lateinit var tvTimer: TextView

    private val viewModel: GameViewModel by activityViewModels()
    private var hasWon = false

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_game, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        sudokuGridView = view.findViewById(R.id.sudokuGridView)
        tvTimer = view.findViewById(R.id.tvTimer)

        // Load saved game or generate new one
        GamePreferences.loadGame(requireContext(), viewModel)

        if (!viewModel.isGameGenerated) {
            viewModel.generateSudoku()
            viewModel.secondsElapsed = 0
        }

        // ✅ Pass board & solution to custom view
        sudokuGridView.board = viewModel.board
        sudokuGridView.solution = viewModel.solution

        // ✅ Cell selection callback
        sudokuGridView.onCellSelected = { r, c ->
            viewModel.selectedRow = r
            viewModel.selectedCol = c
        }

        startTimer()
        setupKeyboard(view)
        updateKeyboardButtons()
        updateKeyboardButtonSizes(view)
    }

    private fun startTimer() {
        viewModel.timerJob?.cancel()
        viewModel.timerJob = scope.launch {
            while (isActive) {
                val minutes = viewModel.secondsElapsed / 60
                val seconds = viewModel.secondsElapsed % 60
                tvTimer.text = String.format("%02d:%02d", minutes, seconds)
                delay(1000)
                viewModel.secondsElapsed++
            }
        }
    }

    private fun setupKeyboard(view: View) {
        val buttonIds = listOf(
            R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4, R.id.btn5,
            R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9, R.id.btnClear
        )

        for ((index, id) in buttonIds.withIndex()) {
            view.findViewById<Button>(id).setOnClickListener {
                val number = if (index == buttonIds.lastIndex) 0 else index + 1
                placeNumber(number)
            }
        }
    }

    private fun placeNumber(num: Int) {
        if (hasWon) return

        val r = viewModel.selectedRow
        val c = viewModel.selectedCol
        if (r == -1 || c == -1) return

        if (num == 0) {
            // Clear wrong guess and board value
            viewModel.board[r][c] = 0
            sudokuGridView.showWrongGuess(0)
            sudokuGridView.invalidate()
            return
        }

        if (viewModel.solution[r][c] != num) {
            // WRONG number: show red only while selected
            sudokuGridView.showWrongGuess(num)
        } else {
            // CORRECT number: persist it in board, turns white when deselected
            viewModel.board[r][c] = num
            sudokuGridView.placeCorrectNumber(num)
            updateKeyboardButtons()

            if (checkWin()) {
                showWinAnimation()
            }
        }
    }

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

    private fun updateKeyboardButtons() {
        val buttonsMap = mapOf(
            1 to view?.findViewById<Button>(R.id.btn1),
            2 to view?.findViewById<Button>(R.id.btn2),
            3 to view?.findViewById<Button>(R.id.btn3),
            4 to view?.findViewById<Button>(R.id.btn4),
            5 to view?.findViewById<Button>(R.id.btn5),
            6 to view?.findViewById<Button>(R.id.btn6),
            7 to view?.findViewById<Button>(R.id.btn7),
            8 to view?.findViewById<Button>(R.id.btn8),
            9 to view?.findViewById<Button>(R.id.btn9),
        )

        for ((num, button) in buttonsMap) {
            if (button == null) continue
            button.visibility = if (countCorrectNumber(num) >= 9) View.GONE else View.VISIBLE
        }
    }

    private fun updateKeyboardButtonSizes(view: View) {
        val screenWidth = resources.displayMetrics.widthPixels
        val buttonMargin = (4 * resources.displayMetrics.density).toInt()

        val buttonWidth = if (screenWidth < 600) {
            (screenWidth / 8) - buttonMargin * 2
        } else {
            (screenWidth / 10) - buttonMargin * 2
        }

        val buttonIds = listOf(
            R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4, R.id.btn5,
            R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9, R.id.btnClear
        )

        for (id in buttonIds) {
            val btn = view.findViewById<Button>(id)
            btn?.let {
                val params = it.layoutParams
                params.width = buttonWidth
                it.layoutParams = params
            }
        }
    }

    private fun checkWin(): Boolean {
        for (r in 0..8) {
            for (c in 0..8) {
                if (viewModel.board[r][c] == 0 || viewModel.board[r][c] != viewModel.solution[r][c]) {
                    return false
                }
            }
        }
        return true
    }

    private fun showWinDialog() {
        hasWon = true

        GamePreferences.clearGame(requireContext())
        viewModel.isGameGenerated = false

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("You won! 🎉")
            .setMessage("Congratulations! What would you like to do next?")
            .setPositiveButton("New Game") { _, _ ->
                viewModel.isGameGenerated = false
                viewModel.secondsElapsed = 0
                viewModel.selectedRow = -1
                viewModel.selectedCol = -1
                parentFragmentManager.commit {
                    replace(R.id.fragmentContainer, GameFragment())
                }
            }
            .setNegativeButton("Main Menu") { _, _ ->
                parentFragmentManager.commit {
                    replace(R.id.fragmentContainer, MenuFragment())
                }
            }
            .setCancelable(false)
            .show()
    }

    private fun showWinAnimation() {
        val container = view?.findViewById<ViewGroup>(R.id.gameContainer) ?: return

        val starCount = 20
        val random = java.util.Random()
        var starsCompleted = 0

        for (i in 0 until starCount) {
            val star = android.widget.ImageView(requireContext())
            star.setImageResource(R.drawable.star)
            val size = (20 + random.nextInt(30))
            val params = android.widget.FrameLayout.LayoutParams(size, size)
            params.leftMargin = random.nextInt(container.width)
            params.topMargin = random.nextInt(container.height)
            star.layoutParams = params
            container.addView(star)

            star.animate()
                .scaleX(2f)
                .scaleY(2f)
                .translationX((random.nextInt(200) - 100).toFloat())
                .translationY((random.nextInt(200) - 100).toFloat())
                .alpha(0f)
                .setDuration(1000)
                .withEndAction {
                    container.removeView(star)
                    starsCompleted++
                    if (starsCompleted == starCount) {
                        showWinDialog()
                    }
                }
                .start()
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.timerJob?.cancel()
        GamePreferences.saveGame(requireContext(), viewModel)
    }

    override fun onResume() {
        super.onResume()
        startTimer()
    }
}
