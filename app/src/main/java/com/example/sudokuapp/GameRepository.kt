package com.example.sudokuapp

/**
 * Abstraction over game state persistence.
 *
 * The ViewModel and Fragments interact with this interface,
 * never with the concrete storage mechanism. This means you can:
 *   - Swap SharedPreferences for Room without touching ViewModel code
 *   - Inject a fake implementation in unit tests
 *   - Add caching, migration, or encryption in one place
 */
interface GameRepository {

    /** Save the current game state. */
    fun saveGame(state: GameState)

    /**
     * Load a previously saved game.
     * @return the saved [GameState], or null if no game exists.
     */
    fun loadGame(): GameState?

    /** Returns true if a saved game exists. */
    fun hasSavedGame(): Boolean

    /** Delete any saved game data. */
    fun clearGame()
}
