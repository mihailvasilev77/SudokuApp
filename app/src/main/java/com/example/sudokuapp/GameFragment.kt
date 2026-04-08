package com.example.sudokuapp

import android.annotation.SuppressLint
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
import com.example.sudokuapp.data.StatsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Thin UI layer for the game screen.
 *
 * Contains zero game logic. Responsibilities:
 *   1. Wire click listeners -> ViewModel action methods
 *   2. Map [PlaceNumberResult] -> visual feedback
 *   3. Collect timer + mistakes StateFlows -> update displays
 *   4. Manage undo/redo button state
 *   5. Mediate between [GameViewModel] and [GameRepository] at lifecycle boundaries
 */
class GameFragment : Fragment() {

    private lateinit var sudokuGridView: SudokuGridView
    private lateinit var tvTimer: TextView
    private lateinit var tvMistakes: TextView
    private lateinit var btnUndo: Button
    private lateinit var btnRedo: Button

    private val viewModel: GameViewModel by activityViewModels()

    private val repository: GameRepository by lazy {
        SharedPreferencesGameRepository(requireContext())
    }

    private val statsRepository: StatsRepository by lazy {
        StatsRepository(requireContext())
    }

    private var hasWon = false
    private var isGameOver = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? =
        inflater.inflate(R.layout.fragment_game, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        sudokuGridView = view.findViewById(R.id.sudokuGridView)
        tvTimer = view.findViewById(R.id.tvTimer)
        tvMistakes = view.findViewById(R.id.tvMistakes)
        btnUndo = view.findViewById(R.id.btnUndo)
        btnRedo = view.findViewById(R.id.btnRedo)

        // Restore saved game or generate a new one
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
        updateUndoRedoButtons()
        observeTimer()
        observeMistakes()
        viewModel.startTimer()
    }

    // =====================================================================
    // REACTIVE OBSERVERS
    // =====================================================================

    @SuppressLint("DefaultLocale")
    private fun observeTimer() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.secondsElapsed.collect { seconds ->
                    tvTimer.text = String.format("%02d:%02d", seconds / 60, seconds % 60)
                }
            }
        }
    }

    private fun observeMistakes() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.mistakes.collect { count ->
                    tvMistakes.text = getString(
                        R.string.mistakes_format, count, GameViewModel.MAX_MISTAKES
                    )
                    // Fade the text from black to red as mistakes increase
                    tvMistakes.alpha = if (count == 0) 0.5f else 1f
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
                if (hasWon || isGameOver) return@setOnClickListener
                val number = if (index == numberButtonIds.lastIndex) 0 else index + 1
                handlePlaceResult(viewModel.placeNumber(number), number)
            }
        }

        view.findViewById<Button>(R.id.btnDraft).setOnClickListener {
            viewModel.toggleDraftMode()
            it.alpha = if (viewModel.isDraftMode) 0.5f else 1f
        }

        view.findViewById<Button>(R.id.btnHint).setOnClickListener {
            if (hasWon || isGameOver) return@setOnClickListener
            if (viewModel.isDraftMode) {
                viewModel.disableDraftMode()
                view.findViewById<Button>(R.id.btnDraft).alpha = 1f
            }
            handlePlaceResult(viewModel.applyHint(), 0)
        }

        btnUndo.setOnClickListener {
            if (hasWon || isGameOver) return@setOnClickListener
            viewModel.undo()
            sudokuGridView.invalidate()
            updateKeyboardButtons()
            updateUndoRedoButtons()
        }

        btnRedo.setOnClickListener {
            if (hasWon || isGameOver) return@setOnClickListener
            viewModel.redo()
            sudokuGridView.invalidate()
            updateKeyboardButtons()
            updateUndoRedoButtons()
        }
    }

    // =====================================================================
    // UI FEEDBACK
    // =====================================================================

    private fun handlePlaceResult(result: PlaceNumberResult, num: Int) {
        when (result) {
            PlaceNumberResult.IGNORED -> Unit

            PlaceNumberResult.DRAFTS_UPDATED -> {
                sudokuGridView.invalidate()
                updateUndoRedoButtons()
            }

            PlaceNumberResult.CLEARED -> {
                sudokuGridView.showWrongGuess(0)
                updateUndoRedoButtons()
            }

            PlaceNumberResult.WRONG_GUESS -> {
                sudokuGridView.showWrongGuess(num)
            }

            PlaceNumberResult.CORRECT -> {
                sudokuGridView.placeCorrectNumber(num)
                sudokuGridView.invalidate()
                updateKeyboardButtons()
                updateUndoRedoButtons()
            }

            PlaceNumberResult.WIN -> {
                sudokuGridView.placeCorrectNumber(num)
                sudokuGridView.invalidate()
                updateKeyboardButtons()
                showWinAnimation()
            }

            PlaceNumberResult.GAME_OVER -> {
                sudokuGridView.showWrongGuess(num)
                showGameOverDialog()
            }
        }
    }

    // =====================================================================
    // BUTTON STATE
    // =====================================================================

    private fun updateUndoRedoButtons() {
        btnUndo.isEnabled = viewModel.canUndo
        btnUndo.alpha = if (viewModel.canUndo) 1f else 0.4f
        btnRedo.isEnabled = viewModel.canRedo
        btnRedo.alpha = if (viewModel.canRedo) 1f else 0.4f
    }

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
            R.id.btnDraft, R.id.btnHint, R.id.btnUndo, R.id.btnRedo
        )

        for (id in allButtonIds) {
            view.findViewById<Button>(id)?.apply {
                layoutParams = layoutParams.also { it.width = buttonWidth }
            }
        }
    }

    // =====================================================================
    // WIN / GAME OVER
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

        // Record win statistics on a background thread
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            statsRepository.recordGame(
                difficulty = viewModel.difficulty,
                isWin = true,
                timeSeconds = viewModel.secondsElapsed.value,
                mistakes = viewModel.mistakes.value,
                hintsUsed = viewModel.hintsUsed
            )
        }
        viewModel.isGameGenerated = false

        val minutes = viewModel.secondsElapsed.value / 60
        val seconds = viewModel.secondsElapsed.value % 60
        val timeStr = String.format("%02d:%02d", minutes, seconds)
        val mistakeCount = viewModel.mistakes.value

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("You won! 🎉")
            .setMessage("Time: $timeStr | Mistakes: $mistakeCount / ${GameViewModel.MAX_MISTAKES}")
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

    private fun showGameOverDialog() {
        isGameOver = true
        viewModel.pauseTimer()

        // Record loss statistics on a background thread
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            statsRepository.recordGame(
                difficulty = viewModel.difficulty,
                isWin = false,
                timeSeconds = viewModel.secondsElapsed.value,
                mistakes = viewModel.mistakes.value,
                hintsUsed = viewModel.hintsUsed
            )
        }

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Game Over")
            .setMessage("You made ${GameViewModel.MAX_MISTAKES} mistakes. Better luck next time!")
            .setPositiveButton("Try Again") { _, _ ->
                // Reset the SAME puzzle — clears user input but keeps the board.
                // The player gets another shot at the same challenge.
                viewModel.resetBoard()
                viewModel.resetTimer()
                // Re-create the fragment to get a fresh UI state
                parentFragmentManager.commit {
                    replace(R.id.fragmentContainer, GameFragment())
                }
            }
            .setNeutralButton("New Game") { _, _ ->
                // Generate a completely new puzzle
                repository.clearGame()
                viewModel.isGameGenerated = false
                viewModel.resetTimer()
                viewModel.selectCell(-1, -1)
                parentFragmentManager.commit {
                    replace(R.id.fragmentContainer, GameFragment())
                }
            }
            .setNegativeButton("Main Menu") { _, _ ->
                repository.clearGame()
                viewModel.isGameGenerated = false
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
        if (!hasWon && !isGameOver) {
            repository.saveGame(viewModel.toSaveState())
        }
    }

    override fun onResume() {
        super.onResume()
        if (!hasWon && !isGameOver) viewModel.startTimer()
    }
}
