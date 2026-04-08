package com.example.sudokuapp

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray

/**
 * [GameRepository] implementation backed by SharedPreferences + JSON.
 */
class SharedPreferencesGameRepository(context: Context) : GameRepository {

    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    override fun saveGame(state: GameState) {
        prefs.edit {
            putString(KEY_BOARD, intListToJson(state.board))
            putString(KEY_SOLUTION, intListToJson(state.solution))
            putString(KEY_PREFILLED, boolListToJson(state.isPrefilled))
            putString(KEY_DRAFTS, draftsToJson(state.drafts))
            putInt(KEY_SECONDS, state.secondsElapsed)
            putString(KEY_DIFFICULTY, state.difficulty.label)
            putInt(KEY_SELECTED_ROW, state.selectedRow)
            putInt(KEY_SELECTED_COL, state.selectedCol)
            putBoolean(KEY_IS_GAME, state.isGameGenerated)
            putInt(KEY_MISTAKES, state.mistakes)
        }
    }

    override fun loadGame(): GameState? {
        if (!prefs.getBoolean(KEY_IS_GAME, false)) return null

        return GameState(
            board = jsonToIntList(prefs.getString(KEY_BOARD, null)) ?: return null,
            solution = jsonToIntList(prefs.getString(KEY_SOLUTION, null)) ?: return null,
            isPrefilled = jsonToBoolList(prefs.getString(KEY_PREFILLED, null))
                ?: List(9) { List(9) { true } },
            drafts = jsonToDrafts(prefs.getString(KEY_DRAFTS, null))
                ?: List(9) { List(9) { emptySet() } },
            secondsElapsed = prefs.getInt(KEY_SECONDS, 0),
            difficulty = Difficulty.fromLabel(
                prefs.getString(KEY_DIFFICULTY, "Medium") ?: "Medium"
            ),
            selectedRow = prefs.getInt(KEY_SELECTED_ROW, -1),
            selectedCol = prefs.getInt(KEY_SELECTED_COL, -1),
            isGameGenerated = prefs.getBoolean(KEY_IS_GAME, false),
            mistakes = prefs.getInt(KEY_MISTAKES, 0)
        )
    }

    override fun hasSavedGame(): Boolean =
        prefs.getBoolean(KEY_IS_GAME, false)

    override fun clearGame() {
        prefs.edit { clear() }
    }

    // =====================================================================
    // JSON serialization
    // =====================================================================

    private fun intListToJson(grid: List<List<Int>>): String {
        val json = JSONArray()
        for (row in grid) {
            val jsonRow = JSONArray()
            for (num in row) jsonRow.put(num)
            json.put(jsonRow)
        }
        return json.toString()
    }

    private fun jsonToIntList(jsonString: String?): List<List<Int>>? {
        if (jsonString == null) return null
        val json = JSONArray(jsonString)
        return (0 until json.length()).map { r ->
            val row = json.getJSONArray(r)
            (0 until row.length()).map { c -> row.getInt(c) }
        }
    }

    private fun boolListToJson(grid: List<List<Boolean>>): String {
        val json = JSONArray()
        for (row in grid) {
            val jsonRow = JSONArray()
            for (value in row) jsonRow.put(value)
            json.put(jsonRow)
        }
        return json.toString()
    }

    private fun jsonToBoolList(jsonString: String?): List<List<Boolean>>? {
        if (jsonString == null) return null
        val json = JSONArray(jsonString)
        return (0 until json.length()).map { r ->
            val row = json.getJSONArray(r)
            (0 until row.length()).map { c -> row.getBoolean(c) }
        }
    }

    private fun draftsToJson(drafts: List<List<Set<Int>>>): String {
        val outer = JSONArray()
        for (row in drafts) {
            val jsonRow = JSONArray()
            for (cell in row) {
                val jsonCell = JSONArray()
                cell.forEach { jsonCell.put(it) }
                jsonRow.put(jsonCell)
            }
            outer.put(jsonRow)
        }
        return outer.toString()
    }

    private fun jsonToDrafts(jsonString: String?): List<List<Set<Int>>>? {
        if (jsonString == null) return null
        val outer = JSONArray(jsonString)
        return (0 until outer.length()).map { r ->
            val row = outer.getJSONArray(r)
            (0 until row.length()).map { c ->
                val cell = row.getJSONArray(c)
                (0 until cell.length()).map { cell.getInt(it) }.toSet()
            }
        }
    }

    companion object {
        private const val PREF_NAME = "sudoku_prefs"
        private const val KEY_BOARD = "board"
        private const val KEY_SOLUTION = "solution"
        private const val KEY_PREFILLED = "is_prefilled"
        private const val KEY_SECONDS = "seconds"
        private const val KEY_DIFFICULTY = "difficulty"
        private const val KEY_IS_GAME = "is_game"
        private const val KEY_SELECTED_ROW = "selected_row"
        private const val KEY_SELECTED_COL = "selected_col"
        private const val KEY_DRAFTS = "drafts"
        private const val KEY_MISTAKES = "mistakes"
    }
}
