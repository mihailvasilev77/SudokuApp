package com.example.sudokuapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database for the Sudoku app.
 *
 * Currently stores only game statistics. When the active game save
 * migrates from SharedPreferences to Room, a second entity can be
 * added here with a database migration.
 */
@Database(entities = [GameStatsEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun gameStatsDao(): GameStatsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Thread-safe singleton accessor.
         * The database is created once and reused for the app's lifetime.
         */
        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sudoku_database"
                ).build().also { INSTANCE = it }
            }
    }
}
