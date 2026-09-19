/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Switch
import android.widget.Toast
import androidx.annotation.Keep
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import com.osfans.trime.R
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.prefs.PreferenceDelegate
import com.osfans.trime.data.prefs.PreferenceDelegateFragment
import com.osfans.trime.sdk.AiBackend
import com.osfans.trime.sdk.AiConfiguration
import com.osfans.trime.sdk.AiProviderRoute
import com.osfans.trime.sdk.AiSettings
import com.osfans.trime.sdk.TrimeSdk

class AdvancedSettingsFragment : PreferenceDelegateFragment(AppPrefs.defaultInstance().advanced) {

    private val uiMode = AppPrefs.defaultInstance().advanced.uiMode

    private val showAppIcon = AppPrefs.defaultInstance().advanced.showAppIcon

    @Keep
    private val onUiModeChange = PreferenceDelegate.OnChangeListener<AppPrefs.Advanced.UiMode> { _, v ->
        val mode = when (v) {
            AppPrefs.Advanced.UiMode.AUTO -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            AppPrefs.Advanced.UiMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            AppPrefs.Advanced.UiMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
        }
        (requireActivity() as AppCompatActivity).delegate.localNightMode = mode
    }

    @Keep
    private val onShowAppIconChange = PreferenceDelegate.OnChangeListener<Boolean> { _, v ->
        showAppIcon(requireContext(), v)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        uiMode.registerOnChangeListener(onUiModeChange)
        showAppIcon.registerOnChangeListener(onShowAppIconChange)
    }

    override fun onPreferenceUiCreated(screen: PreferenceScreen) {
        screen.findPreference<Preference>(showAppIcon.key)?.isVisible = TrimeSdk.launcherComponent != null
        screen.addPreference(
            Preference(screen.context).apply {
                key = "ai_builtin_settings"
                setTitle(R.string.ai_builtin_settings)
                setSummary(R.string.ai_builtin_settings_summary)
                isIconSpaceReserved = false
                setOnPreferenceClickListener {
                    showBuiltInAiDialog()
                    true
                }
            },
        )
    }

    private fun showBuiltInAiDialog() {
        val stored = TrimeSdk.loadBuiltInAiSettings()
        val configuration = stored.configuration
        val density = resources.displayMetrics.density
        val padding = (20 * density).toInt()
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding / 2, padding, 0)
        }
        val enabled = Switch(requireContext()).apply {
            text = getString(R.string.ai_builtin_enabled)
            isChecked = stored.route == AiProviderRoute.BUILT_IN && configuration != null
        }
        val backend = Spinner(requireContext()).apply {
            adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_item,
                AiBackend.entries.map { it.name },
            ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            setSelection((configuration?.backend ?: AiBackend.GROK).ordinal)
        }
        val endpoint = editField(
            configuration?.endpoint ?: "https://api.cc2.cx",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI,
        )
        val model = editField(configuration?.model ?: "grok-4.6", InputType.TYPE_CLASS_TEXT)
        val key = editField("", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        endpoint.hint = getString(R.string.ai_builtin_endpoint)
        model.hint = getString(R.string.ai_builtin_model)
        key.hint = getString(R.string.ai_builtin_key)
        container.addView(enabled)
        container.addView(backend)
        container.addView(endpoint)
        container.addView(model)
        container.addView(key)
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.ai_builtin_dialog_title)
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (!enabled.isChecked) {
                    TrimeSdk.clearBuiltInAiSettings()
                } else {
                    val apiKey = key.text.toString().trim().ifEmpty { configuration?.apiKey.orEmpty() }
                    if (apiKey.isEmpty()) {
                        Toast.makeText(requireContext(), R.string.ai_builtin_save_failed, Toast.LENGTH_SHORT).show()
                    } else {
                        val selectedBackend = AiBackend.entries[backend.selectedItemPosition.coerceIn(0, AiBackend.entries.lastIndex)]
                        val selected = if (selectedBackend == AiBackend.GROK) {
                            AiConfiguration.grok(apiKey, endpoint.text.toString().trim(), model.text.toString().trim())
                        } else {
                            AiConfiguration.openAi(apiKey, endpoint.text.toString().trim(), model.text.toString().trim())
                        }
                        val persisted = TrimeSdk.saveBuiltInAiSettings(AiSettings(AiProviderRoute.BUILT_IN, selected))
                        Toast.makeText(
                            requireContext(),
                            if (persisted) R.string.ai_builtin_saved else R.string.ai_builtin_save_failed,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            }
            .show()
    }

    private fun editField(value: String, inputType: Int) = EditText(requireContext()).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setSingleLine(true)
        this.inputType = inputType
        setText(value)
    }

    override fun onDestroy() {
        uiMode.unregisterOnChangeListener(onUiModeChange)
        showAppIcon.unregisterOnChangeListener(onShowAppIconChange)
        super.onDestroy()
    }

    companion object {
        fun showAppIcon(context: Context, enable: Boolean) {
            val component = TrimeSdk.launcherComponent ?: return
            val state = if (enable) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            context.packageManager.setComponentEnabledSetting(
                component,
                state,
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}
