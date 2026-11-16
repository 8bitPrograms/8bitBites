// SPDX-License-Identifier: GPL-3.0-or-later
package org.eightbit.bite.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import org.eightbit.bite.R
import java.io.ByteArrayOutputStream

internal object ChatGptCallbackPage {
    fun render(context:Context):String {
        val tr=context.resources.configuration.locales[0].language=="tr"
        val bitmap=BitmapFactory.decodeResource(context.resources,R.drawable.launcher_art)
        val icon=try { ByteArrayOutputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.PNG,100,output);Base64.encodeToString(output.toByteArray(),Base64.NO_WRAP) } }
            finally { bitmap.recycle() }
        val title=if(tr) "Uygulamaya dön" else "Back to the app"
        val description=if(tr) "Giriş bilgisi 8bitBites'a iletildi. Bağlantını ve modellerini uygulamada görebilirsin."
            else "Your sign-in was sent to 8bitBites. Your connection and available models are in the app."
        val button=if(tr) "8bitBites'ı aç" else "Open 8bitBites"
        val help=if(tr) "Açılmazsa son uygulamalardan 8bitBites'ı seçebilirsin."
            else "If it doesn't open, choose 8bitBites from your recent apps."
        return """<!doctype html>
<html lang="${if(tr) "tr" else "en"}"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<meta name="referrer" content="no-referrer"><meta name="color-scheme" content="light dark">
<title>8bitBites</title><style>
:root{color-scheme:light dark;--bg:#f2f7f4;--card:#fff;--ink:#142820;--muted:#53645c;--accent:#145c47;--on-accent:#fff;--line:#dce7df;--soft:#e7f3eb}
@media(prefers-color-scheme:dark){:root{--bg:#0a1511;--card:#14231c;--ink:#e4eee7;--muted:#aabdb0;--accent:#a4d5b5;--on-accent:#10271a;--line:#30463a;--soft:#203b2a}}
*{box-sizing:border-box}body{margin:0;min-height:100vh;min-height:100svh;display:grid;place-items:center;padding:24px;padding-top:max(24px,env(safe-area-inset-top));padding-bottom:max(24px,env(safe-area-inset-bottom));background:var(--bg);color:var(--ink);font:16px/1.55 system-ui,sans-serif}
main{width:100%;max-width:440px;padding:28px;border:1px solid var(--line);border-radius:28px;background:var(--card)}
header{display:flex;align-items:center;gap:14px;margin-bottom:36px}header img{width:64px;height:64px;border-radius:18px}header strong{font-size:20px}header small{display:block;color:var(--muted);font-size:13px;margin-top:2px}
.status{display:inline-flex;align-items:center;gap:8px;margin:0 0 16px;padding:7px 12px;border-radius:30px;background:var(--soft);color:var(--ink);font-size:13px;font-weight:600}
h1{font-size:clamp(28px,7vw,36px);line-height:1.15;letter-spacing:-.7px;margin:0 0 16px;overflow-wrap:anywhere}p{margin:0;color:var(--muted)}
a{margin-top:30px;display:flex;align-items:center;justify-content:center;gap:12px;width:100%;min-height:56px;padding:15px 16px;border-radius:18px;background:var(--accent);color:var(--on-accent);font-size:16px;font-weight:700;text-decoration:none;text-align:center;-webkit-tap-highlight-color:transparent}a:focus-visible{outline:3px solid var(--accent);outline-offset:4px}a:active{opacity:.85}.help{margin-top:18px;font-size:13px;text-align:center;line-height:1.5}
@media(max-width:359px){body{padding:16px}main{padding:22px}header{margin-bottom:28px}}
</style></head><body><main>
<header><img src="data:image/png;base64,$icon" alt=""><div><strong>8bitBites</strong><small>ChatGPT</small></div></header>
<p class="status"><span aria-hidden="true">&#10003;</span>${if(tr) "Tarayıcı adımı tamamlandı" else "Browser step complete"}</p>
<h1>$title</h1><p>$description</p>
<a href="eightbitbites://settings/ai">$button<span aria-hidden="true">&#8599;</span></a>
<p class="help">$help</p></main><script>history.replaceState(null,"","/auth/complete")</script></body></html>"""
    }
}
