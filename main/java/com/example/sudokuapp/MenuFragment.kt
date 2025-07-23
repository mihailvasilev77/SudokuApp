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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_menu, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val spinner = view.findViewById<Spinner>(R.id.spinnerDifficulty)
        val btnStart = view.findViewById<Button>(R.id.btnStartGame)
        val btnReturn = view.findViewById<Button>(R.id.btnReturnGame)

        val difficulties = listOf("Easy", "Medium", "Hard")
        spinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, difficulties)

        GamePreferences.loadGame(requireContext(), viewModel)
        btnReturn.visibility = if (viewModel.isGameGenerated) View.VISIBLE else View.GONE


        btnStart.setOnClickListener {
            GamePreferences.clearGame(requireContext())
            val selected = spinner.selectedItem.toString().lowercase()
            viewModel.difficulty = selected
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
