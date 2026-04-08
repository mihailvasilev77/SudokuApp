package com.example.sudokuapp.data

import android.content.Context
import com.example.sudokuapp.Difficulty

/**
 * Repository for game statistics backed by Room.
 *
 * All public methods run Room queries on the calling thread.
 * Callers should invoke them from a coroutine or background thread.
 */
class StatsRepository(context: Context) {

    private val dao = AppDatabase.getInstance(context).gameStatsDao()

    fun recordGame(
        difficulty: Difficulty,
        isWin: Boolean,
        timeSeconds: Int,
        mistakes: Int,
        hintsUsed: Int
    ) {
        dao.insert(
            GameStatsEntity(
                difficulty = difficulty.label,
                isWin = isWin,
                timeSeconds = timeSeconds,
                mistakes = mistakes,
                hintsUsed = hintsUsed
            )
        )
    }

    fun getOverview(): StatsOverview {
        val totalGames = dao.getTotalGames()
        val totalWins = dao.getTotalWins()
        val winStreak = dao.getAllResultsDescending().takeWhile { it }.size

        val perDifficulty = Difficulty.entries.map { diff ->
            val games = dao.getGamesByDifficulty(diff.label)
            val wins = dao.getWinsByDifficulty(diff.label)
            val bestTime = dao.getBestTime(diff.label)
            val avgTime = dao.getAverageTime(diff.label)

            DifficultyStats(
                difficulty = diff,
                gamesPlayed = games,
                gamesWon = wins,
                bestTimeSeconds = bestTime,
                averageTimeSeconds = avgTime?.toInt()
            )
        }

        return StatsOverview(
            totalGames = totalGames,
            totalWins = totalWins,
            currentWinStreak = winStreak,
            perDifficulty = perDifficulty,
            recentGames = dao.getRecentGames(10)
        )
    }

    fun clearAll() {
        dao.clearAll()
    }
}

/**
 * Aggregated statistics for display in the stats dashboard.
 */
data class StatsOverview(
    val totalGames: Int,
    val totalWins: Int,
    val currentWinStreak: Int,
    val perDifficulty: List<DifficultyStats>,
    val recentGames: List<GameStatsEntity>
) {
    val winRate: Int
        get() = if (totalGames > 0) (totalWins * 100 / totalGames) else 0
}

data class DifficultyStats(
    val difficulty: Difficulty,
    val gamesPlayed: Int,
    val gamesWon: Int,
    val bestTimeSeconds: Int?,
    val averageTimeSeconds: Int?
) {
    val winRate: Int
        get() = if (gamesPlayed > 0) (gamesWon * 100 / gamesPlayed) else 0

    fun formatTime(seconds: Int?): String {
        if (seconds == null) return "--:--"
        return String.format("%02d:%02d", seconds / 60, seconds % 60)
    }
}
