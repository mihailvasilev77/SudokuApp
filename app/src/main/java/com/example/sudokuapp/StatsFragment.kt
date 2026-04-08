package com.example.sudokuapp

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import com.example.sudokuapp.data.DifficultyStats
import com.example.sudokuapp.data.GameStatsEntity
import com.example.sudokuapp.data.StatsOverview
import com.example.sudokuapp.data.StatsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Dashboard fragment displaying game statistics from the Room database.
 *
 * Shows overall stats (games played, win rate, win streak),
 * per-difficulty breakdowns, and a recent games list.
 */
class StatsFragment : Fragment() {

    private val statsRepository: StatsRepository by lazy {
        StatsRepository(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? =
        inflater.inflate(R.layout.fragment_stats, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<Button>(R.id.btnBack).setOnClickListener {
            parentFragmentManager.commit {
                replace(R.id.fragmentContainer, MenuFragment())
            }
        }

        view.findViewById<Button>(R.id.btnClearStats).setOnClickListener {
            showClearConfirmation()
        }

        loadStats()
    }

    private fun loadStats() {
        viewLifecycleOwner.lifecycleScope.launch {
            val overview = withContext(Dispatchers.IO) {
                statsRepository.getOverview()
            }
            displayOverview(overview)
        }
    }

    private fun displayOverview(overview: StatsOverview) {
        val view = view ?: return

        // Overall stats
        view.findViewById<TextView>(R.id.tvTotalGames).text =
            overview.totalGames.toString()
        view.findViewById<TextView>(R.id.tvWinRate).text =
            "${overview.winRate}%"
        view.findViewById<TextView>(R.id.tvWinStreak).text =
            overview.currentWinStreak.toString()

        // Per-difficulty breakdown
        val container = view.findViewById<LinearLayout>(R.id.difficultyStatsContainer)
        container.removeAllViews()

        for (stats in overview.perDifficulty) {
            container.addView(buildDifficultyCard(stats))
        }

        // Recent games
        val recentContainer = view.findViewById<LinearLayout>(R.id.recentGamesContainer)
        val tvNoGames = view.findViewById<TextView>(R.id.tvNoGamesYet)
        recentContainer.removeAllViews()

        if (overview.recentGames.isEmpty()) {
            tvNoGames.visibility = View.VISIBLE
        } else {
            tvNoGames.visibility = View.GONE
            for (game in overview.recentGames) {
                recentContainer.addView(buildRecentGameRow(game))
            }
        }
    }

    /**
     * Builds a card view for a single difficulty level's stats.
     */
    private fun buildDifficultyCard(stats: DifficultyStats): View {
        val ctx = requireContext()
        val dp = resources.displayMetrics.density

        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            elevation = 2 * dp
            setPadding((16 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (12 * dp).toInt()
            }
        }

        // Difficulty title
        card.addView(TextView(ctx).apply {
            text = stats.difficulty.label
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (8 * dp).toInt()
            }
        })

        // Stats row
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Games count
        row.addView(buildStatColumn(
            getString(R.string.games_count, stats.gamesPlayed),
            getString(R.string.stats_win_rate, stats.winRate)
        ))

        // Best time
        row.addView(buildStatColumn(
            getString(R.string.best_time, stats.formatTime(stats.bestTimeSeconds)),
            getString(R.string.avg_time, stats.formatTime(stats.averageTimeSeconds))
        ))

        card.addView(row)
        return card
    }

    /**
     * Builds a two-line stat column (value on top, label below).
     */
    private fun buildStatColumn(line1: String, line2: String): View {
        val ctx = requireContext()
        val dp = resources.displayMetrics.density

        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )

            addView(TextView(ctx).apply {
                text = line1
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
            })

            addView(TextView(ctx).apply {
                text = line2
                textSize = 12f
                alpha = 0.6f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = (2 * dp).toInt()
                }
            })
        }
    }

    /**
     * Builds a single row for a recent game entry.
     */
    private fun buildRecentGameRow(game: GameStatsEntity): View {
        val ctx = requireContext()
        val dp = resources.displayMetrics.density

        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (6 * dp).toInt(), 0, (6 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

            // Win/Loss badge
            addView(TextView(ctx).apply {
                text = if (game.isWin) getString(R.string.win_label)
                       else getString(R.string.loss_label)
                setTextColor(if (game.isWin) Color.parseColor("#4CAF50") else Color.parseColor("#F44336"))
                setTypeface(null, Typeface.BOLD)
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(
                    (48 * dp).toInt(),
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })

            // Difficulty
            addView(TextView(ctx).apply {
                text = game.difficulty
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            })

            // Time
            val minutes = game.timeSeconds / 60
            val seconds = game.timeSeconds % 60
            addView(TextView(ctx).apply {
                text = String.format("%02d:%02d", minutes, seconds)
                textSize = 13f
                gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams(
                    (60 * dp).toInt(),
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })

            // Mistakes
            addView(TextView(ctx).apply {
                text = "${game.mistakes}m"
                textSize = 12f
                alpha = 0.6f
                gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams(
                    (36 * dp).toInt(),
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })
        }
    }

    private fun showClearConfirmation() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.clear_stats_confirm_title)
            .setMessage(R.string.clear_stats_confirm_message)
            .setPositiveButton(R.string.clear_confirm) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        statsRepository.clearAll()
                    }
                    loadStats()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
