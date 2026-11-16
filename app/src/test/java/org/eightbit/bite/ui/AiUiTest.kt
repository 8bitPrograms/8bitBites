package org.eightbit.bite.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.eightbit.bite.ai.*
import org.eightbit.bite.BiteApplication
import org.eightbit.bite.core.*
import org.eightbit.bite.data.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class,qualifiers="w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AiUiTest {
    @Test fun anAiEstimateCanBeApprovedWithoutDatabaseMatching() {
        var approved=false
        val mention=AiFoodMention("Cheese",150.0,true,"Estimated portion",Nutrients(300.0,20.0,2.0,24.0))
        compose.setContent { MaterialTheme { AiEstimateReview(LocalDate.now(),AiState(draft=listOf(AiReview(mention=mention))),1,{_,_->},{},{},{approved=true}) } }
        compose.onNodeWithText("Find the matching food").assertDoesNotExist()
        compose.onNodeWithText("Create food from label").assertDoesNotExist()
        compose.onNodeWithTag("approve-ai-meal").performScrollTo().assertIsEnabled().performClick()
        assertTrue(approved)
    }
    @Test fun historyCanOpenAConversationAndConfirmsDeletion() {
        var opened="";var deleted=""
        val row=ChatSummary("conversation","Breakfast",AiProvider.CHATGPT,"model",1000,4)
        compose.setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { AiHistoryContent(listOf(row),false,false,null,"",{}, {opened=it},{deleted=it}) } } }
        compose.onNodeWithText("Breakfast").performClick();assertEquals("conversation",opened)
        compose.onNodeWithContentDescription("Conversation options").performClick()
        compose.onNodeWithText("Delete conversation").performClick()
        assertEquals("",deleted)
        compose.onNodeWithText("Delete",useUnmergedTree=true).performClick();assertEquals("conversation",deleted)
    }
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
    @OptIn(ExperimentalMaterial3Api::class)
    @Test fun embeddedChatStaysBetweenTheAppBars() {
        val application=BiteApplication()
        Application::class.java.getDeclaredMethod("attach",android.content.Context::class.java).apply { isAccessible=true }
            .invoke(application,RuntimeEnvironment.getApplication())
        val vm=AiViewModel(application)
        compose.setContent { MaterialTheme {
            Scaffold(topBar={ Box(Modifier.fillMaxWidth().height(80.dp).testTag("app-top")) },
                bottomBar={ Box(Modifier.fillMaxWidth().height(96.dp).testTag("app-bottom")) }) { insets ->
                AiScreen(LocalDate.now(),{},vm=vm,embedded=true,
                    modifier=Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets))
            }
        } }
        val top=compose.onNodeWithTag("app-top").getUnclippedBoundsInRoot()
        val bottom=compose.onNodeWithTag("app-bottom").getUnclippedBoundsInRoot()
        val header=compose.onNodeWithText("Choose a model in Settings").getUnclippedBoundsInRoot()
        val composer=compose.onNodeWithTag("chat-composer").getUnclippedBoundsInRoot()
        assertTrue("Chat header must clear the app bar",header.top>=top.bottom)
        assertTrue("Composer must clear bottom navigation",composer.bottom<=bottom.top)
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
        var prefs by mutableStateOf(Preferences(theme="clean-light"))
        compose.setContent { BiteTheme(prefs) { Surface(Modifier.fillMaxSize()) {
            root=LocalView.current
            AiChatContent(Modifier.fillMaxSize(),state,"Hesabındaki model",true,{},{},{},{},{},{},{},{reviewed=true},{},{},{})
        } } }
        compose.onNodeWithText("Öğünü incele").performScrollTo().performClick()
        assertTrue(reviewed)
        compose.onNodeWithText("Onayla ve öğünü kaydet").assertDoesNotExist()
        compose.onNodeWithText("API anahtarı (gerekiyorsa)").assertDoesNotExist()
        capture("ai-chat-clean-light")
        compose.runOnIdle { prefs=prefs.copy(theme="clean-dark") }
        capture("ai-chat-clean-dark")
    }
    @Test fun mealsKeepTheOriginalEntryActions() {
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
    @Test fun aVerifiedAccountRemainsSignedInWhenModelLoadingFails() {
        compose.setContent { MaterialTheme {
            AiSettingsContent(Modifier.fillMaxSize(),AiState(provider=AiProvider.CHATGPT),
                ChatGptState(connected=true,sharing=true,email="test@example.com",error="http_403"),AiSettingsActions())
        } }
        compose.onNodeWithText("Connected").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Continue with ChatGPT").assertDoesNotExist()
        compose.onNodeWithText("Sign out").assertExists()
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

    @Test fun attachmentsStayInTheComposerMenuAndStopReplacesSendWhileBusy() {
        var state by mutableStateOf(AiState(provider=AiProvider.REMOTE))
        var photos=0;var cameras=0;var stopped=false
        compose.setContent { MaterialTheme { AiChatContent(Modifier.fillMaxSize(),state,"Remote model",true,
            {state=state.copy(text=it)},{},{stopped=true},{},{photos++},{cameras++},{},{},{},{},{}) } }
        compose.onNodeWithText("Take photo").assertDoesNotExist()
        compose.onNodeWithContentDescription("Add attachment").performClick()
        compose.onNodeWithText("Take photo").performClick();assertEquals(1,cameras)
        compose.onNodeWithText("Take photo").assertDoesNotExist()
        compose.onNodeWithContentDescription("Add attachment").performClick()
        compose.onNodeWithText("Attach photo").performClick();assertEquals(1,photos)
        compose.onNodeWithTag("chat-composer").performTextInput("My meal")
        compose.onNodeWithContentDescription("Send message").assertIsEnabled()
        compose.runOnIdle { state=state.copy(busy=true,status="Thinking…") }
        compose.onNodeWithContentDescription("Send message").assertDoesNotExist()
        compose.onNodeWithContentDescription("Stop").performClick();assertTrue(stopped)
    }

    @Test fun aSavedMealShowsOneReceiptWithoutRepeatingTheAcknowledgment() {
        val state=AiState(provider=AiProvider.CHATGPT,savedMessages=setOf("proposal"),messages=listOf(
            AiChatMessage(id="proposal",user=false,text="Estimated breakfast"),
            AiChatMessage(user=false,text="Meal added to your diary.",acknowledges="proposal")))
        compose.setContent { MaterialTheme { AiChatContent(Modifier.fillMaxSize(),state,"Account model",true,
            {},{},{},{},{},{},{},{},{},{},{}) } }
        compose.onAllNodesWithText("Saved to diary").assertCountEquals(1)
        compose.onNodeWithText("Meal added to your diary.").assertDoesNotExist()
        compose.onNodeWithText("Estimated breakfast").assertIsDisplayed()
    }

    @Test fun foodSearchAndFavoritesAreAvailableWithinTheDismissibleAddFlow() {
        var favorites by mutableStateOf(false)
        var visible by mutableStateOf(true)
        compose.setContent { BiteTheme(Preferences()) {
            if(visible) FoodPicker(favorites,{favorites=it},{visible=false}) { modifier ->
                Text(if(favorites) "Favorite items" else "Search results",modifier)
            }
        } }
        compose.onNodeWithText("Search results").assertIsDisplayed()
        compose.onNodeWithText("Favorites").performClick()
        compose.onNodeWithText("Favorite items").assertIsDisplayed()
        compose.onNodeWithText("Search").performClick()
        compose.onNodeWithText("Search results").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close").performClick()
        compose.onNodeWithTag("food-picker").assertDoesNotExist()
    }

    @Test fun cleanThemesCanBeSelectedWithoutUnlockingTheExtraPalettePack() {
        var prefs by mutableStateOf(Preferences(theme="system",themesUnlocked=false))
        compose.setContent { BiteTheme(prefs) { ThemePicker(prefs,{prefs=it}) } }
        compose.onNodeWithTag("theme-picker").performClick()
        compose.onNodeWithText("Dark · Charcoal").performClick()
        assertEquals("clean-dark",prefs.theme)
        compose.onNodeWithTag("theme-picker").performClick()
        compose.onNodeWithText("Light",useUnmergedTree=true).performClick()
        assertEquals("clean-light",prefs.theme)
    }

    @Test fun anAmoledBackgroundStaysBlackWhenItsAccentChanges() {
        var prefs by mutableStateOf(Preferences(theme="clean-amoled"))
        var background=androidx.compose.ui.graphics.Color.Unspecified
        var primary=androidx.compose.ui.graphics.Color.Unspecified
        compose.setContent { BiteTheme(prefs) {
            background=MaterialTheme.colorScheme.background;primary=MaterialTheme.colorScheme.primary
            Surface(Modifier.fillMaxSize()) { ThemePicker(prefs,{prefs=it}) }
        } }
        compose.onNodeWithText("Blue").performClick()
        assertEquals("blue",prefs.accent)
        compose.runOnIdle { assertEquals(androidx.compose.ui.graphics.Color.Black,background);assertEquals(accentColors.getValue("blue"),primary) }
        compose.onNodeWithTag("theme-picker").performClick()
        compose.onNodeWithText("Midnight").performClick()
        assertEquals("clean-midnight",prefs.theme);assertEquals("blue",prefs.accent)
    }
    @Test fun darkThemesKeepMessageFillsVisibleAndTextReadableForEveryAccent() {
        val palettes=loadPalettes(RuntimeEnvironment.getApplication())
        fun contrast(a:androidx.compose.ui.graphics.Color,b:androidx.compose.ui.graphics.Color):Float {
            val x=a.luminance();val y=b.luminance();return (maxOf(x,y)+.05f)/(minOf(x,y)+.05f)
        }
        for(id in listOf("clean-dark","clean-amoled","clean-midnight","clean-forest","clean-dusk")) {
            for(accent in listOf("neutral")+accentColors.keys) {
                val scheme=paletteColorScheme(resolvePalette(Preferences(theme=id,accent=accent),palettes,true)!!)
                assertTrue("Bubble merges with background: $id/$accent",contrast(scheme.primaryContainer,scheme.background)>=1.8f)
                assertTrue("Composer merges with background: $id/$accent",contrast(scheme.surfaceContainerHigh,scheme.background)>=1.35f)
                assertTrue("Unreadable bubble text: $id/$accent",contrast(scheme.onPrimaryContainer,scheme.primaryContainer)>=4.5f)
            }
        }
    }
}
