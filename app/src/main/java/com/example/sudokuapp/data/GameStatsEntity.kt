package com.example.sudokuapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing one completed game (win or loss).
 *
 * Every time a game ends — whether the player wins or hits the mistake
 * limit — a row is inserted here. The StatsFragment queries this table
 * to build the statistics dashboard.
 */
@Entity(tableName = "game_stats")
data class GameStatsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val difficulty: String,         // "Easy", "Medium", "Hard"
    val isWin: Boolean,
    val timeSeconds: Int,           // total elapsed seconds
    val mistakes: Int,
    val hintsUsed: Int,
    val completedAt: Long = System.currentTimeMillis()
)
