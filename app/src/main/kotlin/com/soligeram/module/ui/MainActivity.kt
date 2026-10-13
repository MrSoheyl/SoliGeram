package com.soligeram.module.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.soligeram.module.R

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, SettingsFragment())
                .commit()
        }

        supportActionBar?.title = "SoliGeram"
        supportActionBar?.subtitle = "v1.0.0 | @qlhlb | @magisk_iran"
    }
}
