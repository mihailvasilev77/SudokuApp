package com.example.sudokuapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit

class MenuFragment : Fragment() {

    private val viewModel: GameViewModel by activityViewModels()

    private val repository: GameRepository by lazy {
        SharedPreferencesGameRepository(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? =
        inflater.inflate(R.layout.fragment_menu, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val spinner = view.findViewById<Spinner>(R.id.spinnerDifficulty)
        val btnStart = view.findViewById<Button>(R.id.btnStartGame)
        val btnReturn = view.findViewById<Button>(R.id.btnReturnGame)

        spinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            Difficulty.entries.map { it.label }
        )

        // Check for saved game via repository — no more loading into ViewModel just to check
        btnReturn.visibility = if (repository.hasSavedGame()) View.VISIBLE else View.GONE

        btnStart.setOnClickListener {
            repository.clearGame()
            viewModel.difficulty = Difficulty.fromLabel(spinner.selectedItem.toString())
            viewModel.isGameGenerated = false
            parentFragmentManager.commit {
                replace(R.id.fragmentContainer, GameFragment())
            }
        }

        btnReturn.setOnClickListener {
            parentFragmentManager.commit {
                replace(R.id.fragmentContainer, GameFragment())
            }
        }
    }
}
