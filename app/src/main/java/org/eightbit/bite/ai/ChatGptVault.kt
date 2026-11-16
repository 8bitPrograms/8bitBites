// SPDX-License-Identifier: GPL-3.0-or-later
package org.eightbit.bite.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** One encrypted, atomically replaced credential document; never included in backup or diagnostics. */
internal class ChatGptVault(context:Context) {
    private val prefs=context.getSharedPreferences("chatgpt-credentials",Context.MODE_PRIVATE)
    private fun key():SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("bite-chatgpt",null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("bite-chatgpt",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized fun read():String? {
        val value=prefs.getString("accounts",null) ?: return null
        val data=Base64.decode(value,Base64.NO_WRAP)
        return Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,data.copyOfRange(0,12))) }
            .doFinal(data.copyOfRange(12,data.size)).toString(Charsets.UTF_8)
    }
    @Synchronized fun write(value:String) {
        require(value.length<=1024*1024)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE,key()) }
        val bytes=cipher.iv+cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        check(prefs.edit().putString("accounts",Base64.encodeToString(bytes,Base64.NO_WRAP)).commit()) { "Could not protect the ChatGPT connection" }
    }
}
