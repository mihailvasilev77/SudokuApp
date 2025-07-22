package com.example.sudokuapp

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                replace(R.id.fragmentContainer, MenuFragment())
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val current = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
                if (current is GameFragment) {
                    // Go back to menu instead of exiting
                    supportFragmentManager.commit {
                        replace(R.id.fragmentContainer, MenuFragment())
                    }
                } else {
                    finish() // If already in menu, exit app
                }
            }
        })
    }
}
