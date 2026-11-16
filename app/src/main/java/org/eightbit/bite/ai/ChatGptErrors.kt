// SPDX-License-Identifier: GPL-3.0-or-later
package org.eightbit.bite.ai

import android.content.Context

internal fun chatGptError(context:Context,code:String):String {
    val tr=context.resources.configuration.locales[0].language=="tr"
    val text=when(code) {
        "subscription_sharing_usage_limit_exceeded" -> "Your ChatGPT usage limit has been reached." to "ChatGPT kullanım sınırına ulaşıldı."
        "subscription_sharing_usage_unavailable" -> "ChatGPT plan usage is temporarily unavailable. Try later." to "ChatGPT plan kullanımı geçici olarak kullanılamıyor. Daha sonra dene."
        "plan_permission_required","missing_scopes","access_denied" -> "ChatGPT plan usage was not authorised. Review the connection in Settings." to "ChatGPT plan kullanımına izin verilmedi. Ayarlar'dan bağlantıyı kontrol et."
        "sign_in_required","invalid_grant","http_401" -> "Sign in to ChatGPT again in Settings." to "Ayarlar'dan ChatGPT hesabına yeniden giriş yap."
        "invalid_id_token","account_mismatch","invalid_state","invalid_client_id" -> "The ChatGPT connection could not be verified. Sign in again." to "ChatGPT bağlantısı doğrulanamadı. Yeniden giriş yap."
        "revocation_unconfirmed" -> "Signed out on this device. Remote revocation was not confirmed; manage the connection in ChatGPT settings." to "Bu cihazdan çıkış yapıldı. Uzaktaki oturumun iptali doğrulanamadı; bağlantıyı ChatGPT ayarlarından yönetebilirsin."
        "no_models","select_model" -> "Choose an available ChatGPT model in Settings." to "Ayarlar'dan hesabında kullanılabilen bir ChatGPT modeli seç."
        "stream_interrupted","response_incomplete" -> "The reply did not finish. Nothing was added to the diary. You can retry." to "Yanıt tamamlanamadı. Günlüğe kayıt eklenmedi. Yeniden deneyebilirsin."
        "login_timeout" -> "Sign-in timed out. Start again in Settings." to "Giriş süresi doldu. Ayarlar'dan yeniden başlat."
        "credential_storage_unavailable" -> "Protected connection storage is unavailable. Reconnect your account." to "Korunan bağlantı bilgisi açılamadı. Hesabını yeniden bağla."
        else -> "ChatGPT could not complete this request. Check your connection and account access." to "ChatGPT bu isteği tamamlayamadı. Bağlantını ve hesap erişimini kontrol et."
    }
    return if(tr) text.second else text.first
}
