package com.sergsave.pocat.screens.soundselection

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.sergsave.pocat.R
import com.sergsave.pocat.helpers.setToolbarAsActionBar
import kotlinx.android.synthetic.main.activity_sound_selection.*

class SoundSelectionActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sound_selection)
        setToolbarAsActionBar(toolbar, showBackButton = true)

        if(savedInstanceState != null)
            return

        supportFragmentManager
            .beginTransaction()
            .add(R.id.container, SoundSelectionFragment())
            .commit()
    }
}