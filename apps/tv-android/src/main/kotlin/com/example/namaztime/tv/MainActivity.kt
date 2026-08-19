package com.example.namaztime.tv

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

/**
 * Temporary T001 entry point. Compose, navigation, focus behavior, and local
 * persistence are introduced by T003.
 */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply { setText(R.string.scaffold_message) })
    }
}
