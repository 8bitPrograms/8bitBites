// SPDX-License-Identifier: GPL-3.0-or-later
package org.eightbit.bite.ai

import java.util.Locale

/** Keep the first received bytes visible even while integer percent/MB would round to zero. */
internal fun modelDownloadAmount(done:Long,total:Long):String {
    val received=done.coerceAtLeast(0)
    val amount=when {
        received==0L -> "0 KB"
        received<1024 -> "$received B"
        received<1024*1024 -> String.format(Locale.ROOT,"%.1f KB",received/1024.0)
        else -> String.format(Locale.ROOT,"%.1f MB",received/(1024.0*1024))
    }
    return "$amount / "+String.format(Locale.ROOT,"%.1f MB",total/(1024.0*1024))
}
