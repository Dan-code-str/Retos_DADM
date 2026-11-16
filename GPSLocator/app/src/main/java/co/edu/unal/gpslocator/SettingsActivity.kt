package co.edu.unal.gpslocator

import android.os.Bundle
import android.text.InputType
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.EditTextPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.appbar.MaterialToolbar
import java.text.DecimalFormat

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.settings_root)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    class SettingsFragment : PreferenceFragmentCompat() {

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)
            val number = DecimalFormat("#.##")

            findPreference<EditTextPreference>(Prefs.RADIUS_KM)?.apply {
                setOnBindEditTextListener { edit ->
                    edit.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                    edit.setSelection(edit.text.length)
                }
                summaryProvider = Preference.SummaryProvider<EditTextPreference> { pref ->
                    val km = Prefs.parseRadius(pref.text) ?: Prefs.DEFAULT_RADIUS_KM
                    getString(R.string.pref_radius_summary, number.format(km))
                }
                setOnPreferenceChangeListener { _, newValue ->
                    val valid = Prefs.parseRadius(newValue as? String) != null
                    if (!valid) {
                        Toast.makeText(
                            requireContext(),
                            getString(
                                R.string.pref_radius_invalid,
                                number.format(Prefs.MIN_RADIUS_KM),
                                number.format(Prefs.MAX_RADIUS_KM),
                            ),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                    valid
                }
            }

            findPreference<MultiSelectListPreference>(Prefs.CATEGORIES)?.summaryProvider =
                Preference.SummaryProvider<MultiSelectListPreference> { pref ->
                    val labels = pref.entryValues.indices
                        .filter { pref.entryValues[it].toString() in pref.values }
                        .map { pref.entries[it] }
                    if (labels.isEmpty()) getString(R.string.pref_categories_none)
                    else labels.joinToString(", ")
                }
        }
    }
}
