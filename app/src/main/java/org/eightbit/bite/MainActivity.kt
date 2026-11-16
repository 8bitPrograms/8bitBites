// SPDX-License-Identifier: GPL-3.0-or-later
package org.eightbit.bite

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.eightbit.bite.ui.BiteApp
import android.content.Intent
import androidx.compose.runtime.*

class MainActivity : AppCompatActivity() {
    private var launchAction by mutableStateOf<String?>(null)
    private var launchId by mutableLongStateOf(0)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val localePrefs = getSharedPreferences("locale", MODE_PRIVATE)
        if (!localePrefs.getBoolean("initialized", false)) {
            localePrefs.edit().putBoolean("initialized", true).apply()
            if (AppCompatDelegate.getApplicationLocales().isEmpty)
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
        }
        enableEdgeToEdge()
        if(savedInstanceState==null) acceptIntent(intent)
        else { launchAction=savedInstanceState.getString("launchAction");launchId=savedInstanceState.getLong("launchId") }
        setContent { BiteApp(launchAction=launchAction,launchId=launchId) }
    }
    override fun onNewIntent(intent:Intent) { super.onNewIntent(intent);setIntent(intent);acceptIntent(intent) }
    private fun acceptIntent(intent:Intent) {
        launchAction=if(intent.action==Intent.ACTION_VIEW && intent.data?.scheme=="eightbitbites" && intent.data?.host=="settings" && intent.data?.path=="/ai") "ai-settings"
            else intent.getStringExtra("screen")?.takeIf { it in setOf("today","water","foods","profile","ai","camera","models","ai-settings") }
        launchId=System.nanoTime()
    }
    override fun onSaveInstanceState(outState:Bundle) { outState.putString("launchAction",launchAction);outState.putLong("launchId",launchId);super.onSaveInstanceState(outState) }
}
