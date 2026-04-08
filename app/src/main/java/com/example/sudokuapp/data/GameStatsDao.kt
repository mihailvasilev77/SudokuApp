package com.example.sudokuapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/**
 * Data Access Object for game statistics.
 *
 * All queries are synchronous (no suspend/Flow) for simplicity.
 * The StatsFragment calls these on a background thread via the repository.
 */
@Dao
interface GameStatsDao {

    @Insert
    fun insert(stats: GameStatsEntity)

    @Query("SELECT COUNT(*) FROM game_stats")
    fun getTotalGames(): Int

    @Query("SELECT COUNT(*) FROM game_stats WHERE isWin = 1")
    fun getTotalWins(): Int

    @Query("SELECT COUNT(*) FROM game_stats WHERE isWin = 1 AND difficulty = :difficulty")
    fun getWinsByDifficulty(difficulty: String): Int

    @Query("SELECT COUNT(*) FROM game_stats WHERE difficulty = :difficulty")
    fun getGamesByDifficulty(difficulty: String): Int

    @Query("SELECT MIN(timeSeconds) FROM game_stats WHERE isWin = 1 AND difficulty = :difficulty")
    fun getBestTime(difficulty: String): Int?

    @Query("SELECT AVG(timeSeconds) FROM game_stats WHERE isWin = 1 AND difficulty = :difficulty")
    fun getAverageTime(difficulty: String): Double?

    /**
     * Returns all win/loss results ordered most-recent-first.
     * The repository iterates this to count the current win streak.
     */
    @Query("SELECT isWin FROM game_stats ORDER BY completedAt DESC")
    fun getAllResultsDescending(): List<Boolean>

    @Query("SELECT * FROM game_stats ORDER BY completedAt DESC LIMIT :limit")
    fun getRecentGames(limit: Int = 10): List<GameStatsEntity>

    @Query("DELETE FROM game_stats")
    fun clearAll()
}
