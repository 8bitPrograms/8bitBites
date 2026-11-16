package org.eightbit.bite.ai

import android.app.Application
import org.eightbit.bite.core.AiProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class AiProviderStoreTest {
    @Test fun legacyLocalAndApiChoicesSurviveTheProviderMigration() {
        val context=RuntimeEnvironment.getApplication() as android.content.Context
        val prefs=context.getSharedPreferences("ai-settings",0)
        prefs.edit().clear().putBoolean("api",true).commit()
        assertEquals(AiProvider.REMOTE,selectedAiProvider(context))
        prefs.edit().putBoolean("api",false).commit()
        assertEquals(AiProvider.LOCAL,selectedAiProvider(context))
        prefs.edit().putString("provider","CHATGPT").putBoolean("api",true).commit()
        assertEquals(AiProvider.CHATGPT,selectedAiProvider(context))
        prefs.edit().putString("provider","unknown").commit()
        assertEquals(AiProvider.LOCAL,selectedAiProvider(context))
    }
    @Test fun accountReadinessDoesNotExposeCredentialsAndSeparatesSignInFromPlanPermission() {
        val account=ChatGptAccount("profile","client","subject","test@example.com",access="secret-access",refresh="secret-refresh",idToken="id")
        assertTrue(account.connected);assertFalse(account.sharing)
        assertTrue(account.copy(scopes="resource.invoke chatgpt.tokens.use.direct").sharing)
        assertFalse(account.copy(scopes="unrelated.chatgpt.tokens.use.direct").sharing)
        assertFalse(account.toString().contains("secret"))
        val roundTrip=ChatGptAccount.read(account.json())
        assertEquals("secret-refresh",roundTrip.refresh);assertEquals(account.id,roundTrip.id)
    }
}
