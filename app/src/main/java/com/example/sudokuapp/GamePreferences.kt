package com.example.sudokuapp

import android.content.Context
import org.json.JSONArray
import androidx.core.content.edit

object GamePreferences {
    private const val PREF_NAME = "sudoku_prefs"
    private const val KEY_BOARD = "board"
    private const val KEY_SOLUTION = "solution"
    private const val KEY_SECONDS = "seconds"
    private const val KEY_DIFFICULTY = "difficulty"
    private const val KEY_IS_GAME = "is_game"
    private const val KEY_SELECTED_ROW = "selected_row"
    private const val KEY_SELECTED_COL = "selected_col"
    private const val KEY_DRAFTS = "drafts"
    private const val KEY_DRAFT_MODE = "draft_mode"

    fun saveGame(context: Context, viewModel: GameViewModel) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit {

            putString(KEY_BOARD, arrayToJson(viewModel.board))
            putString(KEY_SOLUTION, arrayToJson(viewModel.solution))
            putInt(KEY_SECONDS, viewModel.secondsElapsed)
            putString(KEY_DIFFICULTY, viewModel.difficulty)
            putBoolean(KEY_IS_GAME, viewModel.isGameGenerated)
            putInt(KEY_SELECTED_ROW, viewModel.selectedRow)
            putInt(KEY_SELECTED_COL, viewModel.selectedCol)
            putString(KEY_DRAFTS, draftsToJson(viewModel.drafts))
            putBoolean(KEY_DRAFT_MODE, viewModel.isDraftMode)

        }
    }

    fun loadGame(context: Context, viewModel: GameViewModel) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        if (!prefs.getBoolean(KEY_IS_GAME, false)) return

        jsonToArray(prefs.getString(KEY_BOARD, null), viewModel.board)
        jsonToArray(prefs.getString(KEY_SOLUTION, null), viewModel.solution)
        viewModel.secondsElapsed = prefs.getInt(KEY_SECONDS, 0)
        viewModel.difficulty = prefs.getString(KEY_DIFFICULTY, "medium") ?: "medium"
        viewModel.isGameGenerated = prefs.getBoolean(KEY_IS_GAME, false)
        viewModel.selectedRow = prefs.getInt(KEY_SELECTED_ROW, -1)
        viewModel.selectedCol = prefs.getInt(KEY_SELECTED_COL, -1)
        draftsFromJson(prefs.getString(KEY_DRAFTS, null), viewModel.drafts)
        viewModel.isDraftMode = prefs.getBoolean(KEY_DRAFT_MODE, false)

    }

    private fun arrayToJson(array: Array<IntArray>): String {
        val json = JSONArray()
        for (row in array) {
            val jsonRow = JSONArray()
            for (num in row) jsonRow.put(num)
            json.put(jsonRow)
        }
        return json.toString()
    }

    private fun jsonToArray(jsonString: String?, array: Array<IntArray>) {
        if (jsonString == null) return
        val json = JSONArray(jsonString)
        for (r in 0 until json.length()) {
            val row = json.getJSONArray(r)
            for (c in 0 until row.length()) {
                array[r][c] = row.getInt(c)
            }
        }
    }
    private fun draftsToJson(drafts: Array<Array<MutableSet<Int>>>): String {
        val outer = JSONArray()
        for (r in drafts.indices) {
            val row = JSONArray()
            for (c in drafts[r].indices) {
                val cell = JSONArray()
                drafts[r][c].forEach { cell.put(it) }
                row.put(cell)
            }
            outer.put(row)
        }
        return outer.toString()
    }


    private fun draftsFromJson(jsonString: String?, drafts: Array<Array<MutableSet<Int>>>) {
        if (jsonString == null) return
        val outer = JSONArray(jsonString)
        for (r in 0 until outer.length()) {
            val row = outer.getJSONArray(r)
            for (c in 0 until row.length()) {
                val cell = row.getJSONArray(c)
                drafts[r][c].clear()
                for (i in 0 until cell.length()) {
                    drafts[r][c].add(cell.getInt(i))
                }
            }
        }
    }

    fun clearGame(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit { clear() }
    }
}
