package com.doujinzip.android.ui

import androidx.activity.ComponentActivity
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
    @Before fun screenOn() { compose.activityRule.scenario.onActivity { it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) } }
    private fun gallery(id:Long)=GallerySummary(id,"Synthetic $id",artists=emptyList(),series=emptyList(),galleryType=null,thumbnailUrl=null,pageCount=1,language=null,tags=emptyList())
    @Test fun pagesAllResultsAndRetriesFailedItemsWithoutSkippingThem() {
        val loaded=mutableListOf<Long>()
        var fail=true
        var opened:Long?=null
        compose.setContent { DoujinZipTheme { SubscriptionFeedScreen(MainUiState(),PaddingValues(0.dp),false,2,
            loadIds={(1L..27L).toList().reversed()},loadGallery={id ->
                if(id==2L && fail) { fail=false; error("Synthetic failure") }
                loaded+=id; gallery(id)
            },onLoaded={},onBack={},onOpen={opened=it},onLongClick={},onFavorite={},onFacet={}) } }
        compose.waitUntil(10_000) { loaded.size==25 }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(strings.getString(R.string.feed_more)))
        compose.onNodeWithText(strings.getString(R.string.feed_more)).performClick()
        compose.waitUntil(5_000) { !fail }
        compose.onNodeWithText("Synthetic failure").assertExists()
        compose.onNodeWithText(strings.getString(R.string.ui_try_again)).performClick()
        compose.waitUntil(5_000) { loaded.size==27 }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Synthetic 1"))
        compose.onNodeWithText("Synthetic 1").performClick()
        compose.runOnIdle { assertEquals(1L,opened); assertEquals((1L..27L).toList().reversed(),loaded) }
    }
    @Test fun showsEmptyFeedAndRefreshesAfterAnIndexFailure() {
        var calls=0
        compose.setContent { DoujinZipTheme { SubscriptionFeedScreen(MainUiState(),PaddingValues(0.dp),false,2,
            loadIds={calls++; if(calls==1) error("Synthetic offline");emptyList()},loadGallery={gallery(it)},
            onLoaded={},onBack={},onOpen={},onLongClick={},onFavorite={},onFacet={}) } }
        compose.waitUntil(5_000) { calls==1 }
        compose.onNodeWithText(strings.getString(R.string.ui_try_again)).performClick()
        compose.waitUntil(5_000) { calls==2 }
        compose.onNodeWithText(strings.getString(R.string.feed_empty)).assertExists()
    }
}
