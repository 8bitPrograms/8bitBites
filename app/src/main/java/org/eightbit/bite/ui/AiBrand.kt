// SPDX-License-Identifier: GPL-3.0-or-later
package org.eightbit.bite.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import org.eightbit.bite.R
import org.eightbit.bite.core.AiProvider

internal fun aiProviderDrawable(provider:AiProvider)=when(provider) {
    AiProvider.LOCAL -> R.drawable.ic_ai_local
    AiProvider.REMOTE -> R.drawable.ic_ai_remote
    AiProvider.CHATGPT -> R.drawable.ic_ai_chatgpt
}
@Composable internal fun aiProviderName(provider:AiProvider)=when(provider) {
    AiProvider.LOCAL -> ai("On-device AI","Yerel AI")
    AiProvider.REMOTE -> ai("Remote server","Uzak sunucu")
    AiProvider.CHATGPT -> "ChatGPT"
}
@Composable internal fun AiProviderIcon(provider:AiProvider,modifier:Modifier=Modifier,description:String?=null,color:Color=MaterialTheme.colorScheme.onSurface) {
    Icon(painterResource(aiProviderDrawable(provider)),description,modifier,tint=color)
}
