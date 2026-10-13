package com.soligeram.module.ui

import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.soligeram.module.R
import com.soligeram.module.service.PrefManager

/**
 * Single PreferenceFragmentCompat that renders every feature toggle.
 * PreferenceDataStore bridges to PrefManager so the hooks and the UI
 * always read the same backing store.
 */
class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        // Wire up the download boost list to the PrefManager int
        findPreference<ListPreference>("boost_download_level")?.apply {
            value = PrefManager.getInt(PrefManager.KEY_BOOST_DOWNLOAD_LEVEL).toString()
            setOnPreferenceChangeListener { _, newValue ->
                PrefManager.setInt(PrefManager.KEY_BOOST_DOWNLOAD_LEVEL, (newValue as String).toInt())
                true
            }
        }
    }
}
