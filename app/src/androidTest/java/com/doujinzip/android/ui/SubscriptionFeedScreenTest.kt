package com.doujinzip.android.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.doujinzip.android.R
import com.doujinzip.android.model.*
import com.doujinzip.android.ui.theme.DoujinZipTheme
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SubscriptionFeedScreenTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val strings get()=InstrumentationRegistry.getInstrumentation().targetContext.resources
    @Before fun screenOn() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        java.io.File(context.filesDir,"subscription-feed").deleteRecursively()
        context.getSharedPreferences("subscription_feed",android.content.Context.MODE_PRIVATE).edit().clear().commit()
        compose.activityRule.scenario.onActivity { it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    private fun gallery(id:Long)=GallerySummary(id,"Synthetic $id",artists=emptyList(),series=emptyList(),galleryType=null,thumbnailUrl=null,pageCount=1,language=null,tags=emptyList())
    @Test fun pagesAllResultsAndRetriesFailedItemsWithoutSkippingThem() {
        val loaded=mutableListOf<Long>()
        var fail=true
        var opened:Long?=null
        compose.setContent { DoujinZipTheme { SubscriptionFeedScreen(MainUiState(),PaddingValues(0.dp),false,2,
            loadIds={ _ ->(1L..27L).toList().reversed()},loadGallery={id ->
                if(id==2L && fail) { fail=false; error("Synthetic failure") }
                loaded+=id; gallery(id)
            },onLoaded={},onBack={},onOpen={opened=it},onLongClick={},onFavorite={},onFacet={}) } }
        compose.waitUntil(10_000) { loaded.size==25 }
        compose.onNodeWithTag("dynamic-scrollbar").performTouchInput {
            down(androidx.compose.ui.geometry.Offset(centerX,height*.9f))
            advanceEventTime(650)
            moveTo(androidx.compose.ui.geometry.Offset(centerX,height.toFloat()))
            up()
        }
        compose.onNodeWithText(strings.getString(R.string.feed_more)).assertIsDisplayed()
        compose.onNodeWithText(strings.getString(R.string.feed_more)).performClick()
        compose.waitUntil(5_000) { !fail }
        compose.onNodeWithText("Synthetic failure").assertExists()
        compose.onNodeWithText(strings.getString(R.string.ui_try_again)).performClick()
        compose.waitUntil(5_000) { loaded.size==27 }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Synthetic 1"))
        compose.onNodeWithText("Synthetic 1").performClick()
        compose.runOnIdle { assertEquals(1L,opened); assertEquals((1L..27L).toList().reversed(),loaded) }
    }

    @Test fun reopensCachedResultsOfflineWithoutRefetchingGalleryDetails() {
        var visible by mutableStateOf(true)
        var offline=false
        var details=0
        compose.setContent { DoujinZipTheme { if(visible) SubscriptionFeedScreen(MainUiState(),PaddingValues(0.dp),false,2,
            loadIds={ _ ->if(offline) error("Synthetic offline");listOf(42L)},loadGallery={details++;gallery(it)},
            onLoaded={},onBack={},onOpen={},onLongClick={},onFavorite={},onFacet={}) } }
        compose.waitUntil(5000) { details==1 }
        compose.onNodeWithText("Synthetic 42").assertExists()
        compose.waitUntil(5000) { compose.onAllNodes(hasProgressBarRangeInfo(androidx.compose.ui.semantics.ProgressBarRangeInfo.Indeterminate)).fetchSemanticsNodes().isEmpty() }
        compose.runOnIdle { visible=false }
        compose.waitForIdle()
        compose.runOnIdle {
            offline=true
            val prefs=InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("subscription_feed",android.content.Context.MODE_PRIVATE)
            prefs.all.keys.filter { it.endsWith("-success") }.forEach { prefs.edit().putLong(it,1).commit() }
            visible=true
        }
        compose.waitUntil(5000) { compose.onAllNodesWithText("Synthetic offline").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Synthetic 42").assertExists()
        compose.runOnIdle { assertEquals(1,details) }
    }
    @Test fun filtersSubscriptionsWithoutPausingThemAndRemembersSort() {
        val values=listOf("Alpha","Beta").map { com.doujinzip.android.model.SearchSubscription(id=it,name=it,query="artist:${it.lowercase()}",enabled=true) }
        val state=MainUiState(subscriptions=values.map { com.doujinzip.android.data.SubscriptionRecord(it,null,null,null,0,null) })
        var selection=emptyList<String>()
        compose.setContent { DoujinZipTheme { SubscriptionFeedScreen(state,PaddingValues(0.dp),false,2,
            loadIds={selected -> selection=selected.map { it.id };if(selected.isEmpty()) emptyList() else listOf(42L,21L)},loadGallery={gallery(it)},
            onLoaded={},onBack={},onOpen={},onLongClick={},onFavorite={},onFacet={}) } }
        compose.waitUntil(5000) { selection.size==2 }
        compose.onNodeWithText(strings.getString(R.string.feed_filter)).performClick()
        compose.onNodeWithText("Alpha").performClick()
        compose.waitUntil(5000) { selection==listOf("Beta") }
        androidx.test.espresso.Espresso.pressBack()
        compose.waitUntil(5000) { compose.onAllNodes(hasProgressBarRangeInfo(androidx.compose.ui.semantics.ProgressBarRangeInfo.Indeterminate)).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText(strings.getString(R.string.ui_sort)).performClick()
        compose.onNodeWithText(strings.getString(R.string.ui_oldest_first)).performClick()
        compose.waitUntil(5000) { InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("subscription_feed",android.content.Context.MODE_PRIVATE).getBoolean("oldest-",false) }
        compose.runOnIdle { assertTrue(state.subscriptions.all { it.value.enabled }) }
    }
    @Test fun freshCacheSkipsNetworkAndExpiredCacheOffersNewItemsWithoutReplacingResults() {
        var visible by mutableStateOf(true)
        var calls=0
        compose.setContent { DoujinZipTheme { if(visible) SubscriptionFeedScreen(MainUiState(),PaddingValues(0.dp),false,2,
            loadIds={ _ -> calls++;if(calls==1) listOf(42L) else listOf(50L,42L)},loadGallery={gallery(it)},
            onLoaded={},onBack={},onOpen={},onLongClick={},onFavorite={},onFacet={}) } }
        fun waitIdle() { compose.waitUntil(5000) { compose.onAllNodes(hasProgressBarRangeInfo(androidx.compose.ui.semantics.ProgressBarRangeInfo.Indeterminate)).fetchSemanticsNodes().isEmpty() } }
        compose.waitUntil(5000) { calls==1 };waitIdle()
        compose.runOnIdle { visible=false };compose.waitForIdle()
        compose.runOnIdle { visible=true };compose.waitForIdle();waitIdle()
        compose.onNodeWithText("Synthetic 42").assertExists()
        compose.runOnIdle { assertEquals(1,calls);visible=false };compose.waitForIdle()
        compose.runOnIdle {
            val prefs=InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("subscription_feed",android.content.Context.MODE_PRIVATE)
            prefs.all.keys.filter { it.endsWith("-success") }.forEach { prefs.edit().putLong(it,1).commit() }
            visible=true
        }
        compose.waitUntil(5000) { calls==2 };waitIdle()
        compose.onNodeWithText(strings.getString(R.string.feed_new_items)).assertExists()
        compose.onNodeWithText("Synthetic 42").assertExists()
        compose.onNodeWithText("Synthetic 50").assertDoesNotExist()
        compose.onNodeWithText(strings.getString(R.string.feed_new_items)).performClick()
        compose.waitUntil(5000) { calls==3 };waitIdle()
        compose.onNodeWithText("Synthetic 50").assertExists()
    }
    @Test fun unchangedProbeAvoidsFullReloadButManualRefreshBypassesIt() {
        var visible by mutableStateOf(true)
        var fullLoads=0
        var probes=0
        compose.setContent { DoujinZipTheme { if(visible) SubscriptionFeedScreen(MainUiState(),PaddingValues(0.dp),false,2,
            loadIds={ _ -> fullLoads++;listOf(42L)},loadGallery={gallery(it)},probeIds={ _ ->probes++;"same"},
            onLoaded={},onBack={},onOpen={},onLongClick={},onFavorite={},onFacet={}) } }
        fun idle() { compose.waitUntil(5000) { compose.onAllNodes(hasProgressBarRangeInfo(androidx.compose.ui.semantics.ProgressBarRangeInfo.Indeterminate)).fetchSemanticsNodes().isEmpty() } }
        compose.waitUntil(5000) { fullLoads==1 };idle()
        compose.runOnIdle { visible=false };compose.waitForIdle()
        compose.runOnIdle {
            val prefs=InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("subscription_feed",android.content.Context.MODE_PRIVATE)
            prefs.all.keys.filter { it.endsWith("-success") }.forEach { prefs.edit().putLong(it,1).commit() }
            visible=true
        }
        compose.waitUntil(5000) { probes==2 };idle()
        compose.runOnIdle { assertEquals(1,fullLoads) }
        compose.onNodeWithText(strings.getString(R.string.ui_refresh)).performClick()
        compose.waitUntil(5000) { fullLoads==2 };idle()
    }
    @Test fun showsEmptyFeedAndRefreshesAfterAnIndexFailure() {
        var calls=0
        compose.setContent { DoujinZipTheme { SubscriptionFeedScreen(MainUiState(),PaddingValues(0.dp),false,2,
            loadIds={ _ ->calls++; if(calls==1) error("Synthetic offline");emptyList()},loadGallery={gallery(it)},
            onLoaded={},onBack={},onOpen={},onLongClick={},onFavorite={},onFacet={}) } }
        compose.waitUntil(5_000) { calls==1 }
        compose.onNodeWithText(strings.getString(R.string.ui_try_again)).performClick()
        compose.waitUntil(5_000) { calls==2 }
        compose.waitUntil(5000) { compose.onAllNodesWithText(strings.getString(R.string.feed_empty)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(strings.getString(R.string.feed_empty)).assertExists()
    }
}
