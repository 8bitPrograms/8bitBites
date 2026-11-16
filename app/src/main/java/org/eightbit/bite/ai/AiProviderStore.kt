// SPDX-License-Identifier: GPL-3.0-or-later
package org.eightbit.bite.ai

import android.content.Context
import org.eightbit.bite.core.AiProvider

internal fun selectedAiProvider(context:Context):AiProvider {
    val prefs=context.getSharedPreferences("ai-settings",Context.MODE_PRIVATE)
    return runCatching { AiProvider.valueOf(prefs.getString("provider",null) ?: if(prefs.getBoolean("api",false)) "REMOTE" else "LOCAL") }.getOrDefault(AiProvider.LOCAL)
}
