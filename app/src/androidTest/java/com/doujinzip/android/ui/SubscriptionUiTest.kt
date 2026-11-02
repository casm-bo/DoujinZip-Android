package com.doujinzip.android.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.doujinzip.android.R
import com.doujinzip.android.model.SearchSubscription
import com.doujinzip.android.data.SubscriptionNotice
import com.doujinzip.android.security.SubscriptionOptions
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SubscriptionUiTest {
    @get:Rule val compose=createComposeRule()
    private fun text(id:Int)=InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
    @Test fun noticeOpeningAndMarkAllAreExplicitAndScreenEntryDoesNotRead() {
        val reads=mutableListOf<Long?>()
        compose.setContent { MaterialTheme { SubscriptionScreen(MainUiState(subscriptionNotices=listOf(SubscriptionNotice(42,"Synthetic release","Example",null,false,listOf("Example subscription")))),{}, {}, {}, {reads+=it}, {}, {}, {_,_->}, {}) } }
        compose.onNodeWithText("Synthetic release").assertIsDisplayed()
        assertTrue(reads.isEmpty())
        compose.onNodeWithText("Synthetic release").performClick()
        assertEquals(listOf(42L),reads)
        compose.onNodeWithText(text(R.string.sub_all_read)).performClick()
        assertEquals(listOf(42L,null),reads)
    }
    @Test fun keywordShortcutCannotEnableMonitoringButCanBeSavedInactive() {
        var saved:SearchSubscription?=null
        compose.setContent { MaterialTheme { SubscriptionScreen(MainUiState(subscriptionTab=1,subscriptionEditor=SearchSubscription(name="Example",query="title words",enabled=true)),{}, {}, {}, {}, {}, {saved=it}, {_,_->}, {}) } }
        compose.onNodeWithText(text(R.string.ui_save)).performClick()
        compose.runOnIdle { assertNull(saved) }
        compose.onNodeWithTag("subscription-editor-monitor").performClick()
        compose.onNodeWithText(text(R.string.ui_save)).performClick()
        compose.runOnIdle { assertEquals("title words",saved!!.query);assertFalse(saved!!.enabled) }
    }
    @Test fun privacyStartsEnabledAndCanBeDisabledAndIntervalCanChange() {
        var options=SubscriptionOptions()
        compose.setContent { var state by remember { mutableStateOf(options) }; MaterialTheme { SubscriptionSettings(state,{state=it;options=it},{}) } }
        compose.onNodeWithTag("subscription-auto-sync").assertDoesNotExist()
        compose.onNodeWithTag("subscription-hide-locked").assertIsOn().performClick().assertIsOff()
        compose.runOnIdle { assertFalse(options.hideWhenLocked) }
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        compose.onNodeWithTag("subscription-check-interval-60").assertTextContains(context.getString(R.string.sub_hours,1))
        compose.onNodeWithTag("subscription-check-interval-1440").assertTextContains(text(R.string.sub_day))
        compose.onNodeWithTag("subscription-check-interval-5").assertDoesNotExist()
        compose.onNodeWithTag("subscription-check-interval-10").assertDoesNotExist()
        compose.onNodeWithTag("subscription-check-interval-15").performClick()
        compose.runOnIdle { assertEquals(15,options.intervalMinutes) }
        compose.onNodeWithTag("subscription-check-interval-30").performClick()
        compose.runOnIdle { assertEquals(30,options.intervalMinutes) }
        compose.onNodeWithTag("subscription-check-interval-360").performClick()
        compose.runOnIdle { assertEquals(360,options.intervalMinutes) }
    }
    @Test fun subscriptionEditorUsesLanguagePickerAndTagSuggestions() {
        var saved:SearchSubscription?=null
        var changed=""
        compose.setContent { MaterialTheme {
            SubscriptionScreen(MainUiState(subscriptionTab=1,
                availableLanguages=listOf(com.doujinzip.android.network.AvailableLanguage("english","English"),com.doujinzip.android.network.AvailableLanguage("korean","한국어")),
                subscriptionEditor=SearchSubscription(name="Synthetic tags",query="tag:exam",enabled=true),
                subscriptionSuggestions=listOf(com.doujinzip.android.network.FilterSuggestion("tag:example","example"))),
                {}, {}, {}, {}, {}, {saved=it}, {_,_->}, {}, onQueryChange={changed=it})
        } }
        compose.onNodeWithText(text(R.string.ui_language)+" (0)").performClick()
        compose.onNodeWithText("English").performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.ui_language)+" (1)").performScrollTo().performClick()
        compose.onNodeWithContentDescription(text(R.string.sub_tags_placeholder)).performScrollTo().performClick()
        compose.onNodeWithText("tag:example",useUnmergedTree=true).performScrollTo().performClick()
        compose.runOnIdle { assertEquals("tag:example, ",changed) }
        compose.onNodeWithContentDescription(text(R.string.ui_return)).performClick()
        compose.onNodeWithText(text(R.string.ui_save)).performClick()
        compose.runOnIdle { assertEquals("tag:example",saved!!.query);assertEquals(setOf("english"),saved!!.languages) }
    }
    @Test fun syncErrorsStayInsideOptionsAndAutomaticSyncCanBeConfigured() {
        val error="Synthetic offline sync status"
        var synced=0
        var options=SubscriptionOptions()
        compose.setContent { var current by remember { mutableStateOf(options) }; MaterialTheme {
            SubscriptionScreen(MainUiState(selectedProfileId="synthetic-desktop",subscriptionSyncError=error,subscriptionOptions=current),
                {}, {}, {}, {}, {}, {}, {_,_->}, {}, onOptions={current=it;options=it},onSyncNow={synced++})
        } }
        compose.onNodeWithText(error).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.sub_sync_options)).performClick()
        compose.onNodeWithText(error).assertExists()
        compose.onNodeWithTag("subscription-sync-interval-60").assertDoesNotExist()
        compose.onNodeWithTag("subscription-auto-sync").assertDoesNotExist()
        compose.onNodeWithTag("subscription-sync-now").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1,synced) }
    }
}
