package org.eightbit.bite.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.eightbit.bite.ai.*
import org.eightbit.bite.core.*
import org.eightbit.bite.data.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class,qualifiers="w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AiUiTest {
    @get:Rule val compose=createComposeRule()
    private lateinit var root:android.view.View
    private fun capture(name:String)=compose.runOnIdle {
        val bitmap=Bitmap.createBitmap(root.width,root.height,Bitmap.Config.ARGB_8888)
        root.draw(android.graphics.Canvas(bitmap))
        File("build/reports/ui/$name.png").also { it.parentFile!!.mkdirs() }.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    @Test fun providerSelectionChangesTheChoiceAndEveryProviderHasADistinctIcon() {
        var selected by mutableStateOf(AiProvider.LOCAL)
        compose.setContent { MaterialTheme { AiProviderChoices(selected,true,{selected=it}) } }
        compose.onNodeWithTag("provider-CHATGPT").performClick()
        assertEquals(AiProvider.CHATGPT,selected)
        assertEquals(3,AiProvider.entries.map(::aiProviderDrawable).toSet().size)
        compose.onNodeWithTag("provider-REMOTE").performClick()
        assertEquals(AiProvider.REMOTE,selected)
    }
    @Test fun chatGptSettingsOfferRealSignInInsteadOfAnApiKeyField() {
        var started=false
        compose.setContent { BiteTheme(Preferences()) { Surface(Modifier.fillMaxSize()) {
            root=LocalView.current
            AiSettingsContent(Modifier.fillMaxSize(),AiState(provider=AiProvider.CHATGPT),ChatGptState(),
                AiSettingsActions(connect={_,_->started=true}))
        } } }
        compose.onNodeWithText("API key (if required)").assertDoesNotExist()
        compose.onNodeWithText("Continue with ChatGPT").performScrollTo().performClick()
        assertTrue(started)
        capture("ai-settings-chatgpt")
    }
    @Test fun chatHasOnlyConversationAndComposerWithASettingsLinkWhenUnconnected() {
        var settings=false
        compose.setContent { MaterialTheme { AiChatContent(Modifier.fillMaxSize(),AiState(provider=AiProvider.CHATGPT),"Connect in Settings",false,
            {},{},{},{},{},{},{},{},{},{settings=true},{}) } }
        compose.onNodeWithText("API key (if required)").assertDoesNotExist()
        compose.onNodeWithText("Browse & download models").assertDoesNotExist()
        compose.onNodeWithText("Open AI settings").performClick()
        assertTrue(settings)
        compose.onNodeWithContentDescription("Send message").assertIsNotEnabled()
        compose.onNodeWithTag("chat-composer").assertExists()
    }
    @Test @Config(qualifiers="tr-w393dp-h852dp-xxhdpi") fun turkishConversationShowsCorrectionsAndRequiresMealReview() {
        val food=AiFoodMention("Peynir",150.0,false,"")
        val message=AiChatMessage(id="answer",user=false,text="Peyniri 150 g olarak güncelledim. Öğünü kaydetmeden önce kontrol edebilirsin.",foods=listOf(food))
        val state=AiState(provider=AiProvider.CHATGPT,messages=listOf(
            AiChatMessage(id="first",user=true,text="100 g peynir ve 2 dilim ekmek yedim."),
            AiChatMessage(id="previous",user=false,text="Öğününü hazırladım. Ekmeğin gramını biliyor musun?"),
            AiChatMessage(id="correction",user=true,text="Peynir 150 g olsun."),message),
            draft=listOf(AiReview(mention=food)),draftMessageId="answer")
        var reviewed=false
        compose.setContent { BiteTheme(Preferences()) { Surface(Modifier.fillMaxSize()) {
            root=LocalView.current
            AiChatContent(Modifier.fillMaxSize(),state,"Hesabındaki model",true,{},{},{},{},{},{},{},{reviewed=true},{},{},{})
        } } }
        compose.onNodeWithText("Öğünü incele").performScrollTo().performClick()
        assertTrue(reviewed)
        compose.onNodeWithText("Onayla ve öğünü kaydet").assertDoesNotExist()
        compose.onNodeWithText("API anahtarı (gerekiyorsa)").assertDoesNotExist()
        capture("ai-chat-tr")
    }
    @Test fun mealsAreAvailableAsTheirOwnFoodsSectionWithTheOriginalEntryActions() {
        val date=LocalDate.of(2026,11,16)
        val entry=DiaryEntry(id="meal",foodId="test",name="Cheese",source="label",grams=100.0,mealType=1,
            time=date.atTime(10,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),offsetSeconds=0,
            kcal=200.0,protein=15.0,carbs=2.0,fat=12.0,fiber=null,sugar=null,sodium=null)
        var edited=false
        compose.setContent { MaterialTheme { MealsScreen(Modifier.fillMaxSize(),date,listOf(entry),{},{},{edited=true},{},{}) } }
        compose.onNodeWithText("Cheese").assertExists()
        compose.onNodeWithContentDescription("Entry actions").performClick()
        compose.onNodeWithText("Edit").performClick()
        assertTrue(edited)
    }
    @Test fun retryingAnUnfinishedRegistrationReusesItsIssuedClientMapping() {
        var selected:String?=null
        compose.setContent { MaterialTheme {
            AiSettingsContent(Modifier.fillMaxSize(),AiState(provider=AiProvider.CHATGPT),
                ChatGptState(profiles=listOf(ChatGptProfile("pending-profile","ChatGPT · 1",false,false))),
                AiSettingsActions(connect={id,_->selected=id}))
        } }
        compose.onNodeWithText("Continue with ChatGPT").performScrollTo().performClick()
        assertEquals("pending-profile",selected)
    }
    @Test fun aPlanLimitOffersUsageManagementInsteadOfRetryingTheSameRequest() {
        var managed=false
        val state=AiState(provider=AiProvider.CHATGPT,messages=listOf(AiChatMessage(user=true,text="my meal")),
            error="Usage limit reached",errorCode="subscription_sharing_usage_limit_exceeded")
        compose.setContent { MaterialTheme { AiChatContent(Modifier.fillMaxSize(),state,"Account model",true,
            {},{},{},{},{},{},{},{},{},{},{},manageUsage={managed=true}) } }
        compose.onNodeWithText("Manage ChatGPT usage").performScrollTo().performClick()
        assertTrue(managed)
        compose.onNodeWithText("Retry").assertDoesNotExist()
    }
}
