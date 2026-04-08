package com.example.sudokuapp

/**
 * Type-safe difficulty levels. Each level defines how many of the
 * 81 cells are removed from the completed board.
 *
 * Using an enum eliminates the old magic strings ("easy", "medium", "hard")
 * and the silent fallback to medium on typos.
 */
enum class Difficulty(val label: String, val cellsToRemove: Int) {
    EASY("Easy", 35),
    MEDIUM("Medium", 45),
    HARD("Hard", 55);

    companion object {
        /**
         * Parse a difficulty from its label string (case-insensitive).
         * Used when restoring from SharedPreferences.
         */
        fun fromLabel(label: String): Difficulty =
            entries.firstOrNull { it.label.equals(label, ignoreCase = true) }
                ?: MEDIUM
    }
}
