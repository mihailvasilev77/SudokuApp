package com.example.sudokuapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Thin UI layer for the game screen.
 *
 * Contains zero game logic. Responsibilities:
 *   1. Wire click listeners → ViewModel action methods
 *   2. Map [PlaceNumberResult] → visual feedback
 *   3. Collect timer [StateFlow] → update clock display
 *   4. Mediate between [GameViewModel] and [GameRepository] at lifecycle boundaries
 */
class GameFragment : Fragment() {

    private lateinit var sudokuGridView: SudokuGridView
    private lateinit var tvTimer: TextView

    private val viewModel: GameViewModel by activityViewModels()

    // Repository created once per fragment — uses the same SharedPreferences key.
    // When Hilt is added, this becomes an @Inject constructor parameter instead.
    private val repository: GameRepository by lazy {
        SharedPreferencesGameRepository(requireContext())
    }

    private var hasWon = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? =
        inflater.inflate(R.layout.fragment_game, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        sudokuGridView = view.findViewById(R.id.sudokuGridView)
        tvTimer = view.findViewById(R.id.tvTimer)

        // Restore saved game via the repository, or generate a new one
        repository.loadGame()?.let { viewModel.restoreFrom(it) }

        if (!viewModel.isGameGenerated) {
            viewModel.generateSudoku()
            viewModel.resetTimer()
        }

        // Share data references with the custom view
        sudokuGridView.board = viewModel.board
        sudokuGridView.solution = viewModel.solution
        sudokuGridView.isPrefilled = viewModel.isPrefilled
        sudokuGridView.drafts = viewModel.drafts

        sudokuGridView.onCellSelected = { r, c ->
            viewModel.selectCell(r, c)
            sudokuGridView.invalidate()
        }

        setupKeyboard(view)
        updateKeyboardButtons()
        updateKeyboardButtonSizes(view)
        observeTimer()
        viewModel.startTimer()
    }

    // =====================================================================
    // TIMER
    // =====================================================================

    private fun observeTimer() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.secondsElapsed.collect { seconds ->
                    tvTimer.text = String.format("%02d:%02d", seconds / 60, seconds % 60)
                }
            }
        }
    }

    // =====================================================================
    // KEYBOARD
    // =====================================================================

    private fun setupKeyboard(view: View) {
        val numberButtonIds = listOf(
            R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4, R.id.btn5,
            R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9, R.id.btnClear
        )

        numberButtonIds.forEachIndexed { index, id ->
            view.findViewById<Button>(id).setOnClickListener {
                if (hasWon) return@setOnClickListener
                val number = if (index == numberButtonIds.lastIndex) 0 else index + 1
                handlePlaceResult(viewModel.placeNumber(number), number)
            }
        }

        view.findViewById<Button>(R.id.btnDraft).setOnClickListener {
            viewModel.toggleDraftMode()
            it.alpha = if (viewModel.isDraftMode) 0.5f else 1f
        }

        view.findViewById<Button>(R.id.btnHint).setOnClickListener {
            if (hasWon) return@setOnClickListener
            if (viewModel.isDraftMode) {
                viewModel.disableDraftMode()
                view.findViewById<Button>(R.id.btnDraft).alpha = 1f
            }
            handlePlaceResult(viewModel.applyHint(), 0)
        }
    }

    // =====================================================================
    // UI FEEDBACK
    // =====================================================================

    private fun handlePlaceResult(result: PlaceNumberResult, num: Int) {
        when (result) {
            PlaceNumberResult.IGNORED -> Unit

            PlaceNumberResult.DRAFTS_UPDATED -> sudokuGridView.invalidate()

            PlaceNumberResult.CLEARED -> sudokuGridView.showWrongGuess(0)

            PlaceNumberResult.WRONG_GUESS -> sudokuGridView.showWrongGuess(num)

            PlaceNumberResult.CORRECT -> {
                sudokuGridView.placeCorrectNumber(num)
                sudokuGridView.invalidate()
                updateKeyboardButtons()
            }

            PlaceNumberResult.WIN -> {
                sudokuGridView.placeCorrectNumber(num)
                sudokuGridView.invalidate()
                updateKeyboardButtons()
                showWinAnimation()
            }
        }
    }

    // =====================================================================
    // KEYBOARD VISIBILITY
    // =====================================================================

    private fun updateKeyboardButtons() {
        val buttonIds = listOf(
            R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4,
            R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9
        )

        buttonIds.forEachIndexed { index, id ->
            val num = index + 1
            view?.findViewById<Button>(id)?.visibility =
                if (viewModel.countCorrectNumber(num) >= 9) View.GONE else View.VISIBLE
        }
    }

    private fun updateKeyboardButtonSizes(view: View) {
        val screenWidth = resources.displayMetrics.widthPixels
        val buttonMargin = (4 * resources.displayMetrics.density).toInt()
        val buttonWidth = (screenWidth / if (screenWidth < 600) 8 else 10) - buttonMargin * 2

        val allButtonIds = listOf(
            R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4, R.id.btn5,
            R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9, R.id.btnClear,
            R.id.btnDraft, R.id.btnHint
        )

        for (id in allButtonIds) {
            view.findViewById<Button>(id)?.apply {
                layoutParams = layoutParams.also { it.width = buttonWidth }
            }
        }
    }

    // =====================================================================
    // WIN FLOW
    // =====================================================================

    private fun showWinAnimation() {
        val container = view?.findViewById<ViewGroup>(R.id.gameContainer) ?: return
        val starCount = 20
        var starsCompleted = 0

        repeat(starCount) {
            val star = android.widget.ImageView(requireContext()).apply {
                setImageResource(R.drawable.star)
                val size = 20 + Random.nextInt(30)
                layoutParams = android.widget.FrameLayout.LayoutParams(size, size).apply {
                    leftMargin = Random.nextInt(container.width)
                    topMargin = Random.nextInt(container.height)
                }
            }
            container.addView(star)

            star.animate()
                .scaleX(2f).scaleY(2f)
                .translationX((Random.nextInt(200) - 100).toFloat())
                .translationY((Random.nextInt(200) - 100).toFloat())
                .alpha(0f)
                .setDuration(1000)
                .withEndAction {
                    container.removeView(star)
                    if (++starsCompleted == starCount) showWinDialog()
                }
                .start()
        }
    }

    private fun showWinDialog() {
        hasWon = true
        viewModel.pauseTimer()
        repository.clearGame()
        viewModel.isGameGenerated = false

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("You won! 🎉")
            .setMessage("Congratulations! What would you like to do next?")
            .setPositiveButton("New Game") { _, _ ->
                viewModel.isGameGenerated = false
                viewModel.resetTimer()
                viewModel.selectCell(-1, -1)
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

    // =====================================================================
    // LIFECYCLE
    // =====================================================================

    override fun onPause() {
        super.onPause()
        viewModel.pauseTimer()
        repository.saveGame(viewModel.toSaveState())
    }

    override fun onResume() {
        super.onResume()
        if (!hasWon) viewModel.startTimer()
    }
}
