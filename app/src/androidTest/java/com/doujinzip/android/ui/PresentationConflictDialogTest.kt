package com.doujinzip.android.ui

import androidx.compose.ui.test.*
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.doujinzip.android.R
import com.doujinzip.android.data.PresentationRecord
import com.doujinzip.android.model.*
import com.doujinzip.android.ui.theme.DoujinZipTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.Before

class PresentationConflictDialogTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun keepTestScreenOn() {
        compose.activityRule.scenario.onActivity { it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    private val strings get() = InstrumentationRegistry.getInstrumentation().targetContext.resources
    private val phone = GalleryPresentation("hitomi:42", "Synthetic phone title", LibraryMetadata(artists=listOf("Unchanged artist"), tags=listOf("phone tag")))
    private val record = PresentationRecord(phone, dirty=true, conflict=phone.copy(title="Synthetic PC title", metadata=phone.metadata.copy(tags=listOf("PC tag")), version=2))

    @Test fun onlyChangedFieldsAreShownAndPhoneCanBeChosen() {
        var chosen: Boolean? = null
        compose.setContent { DoujinZipTheme { PresentationConflictDialog(record, { chosen=it }, {}) } }
        compose.onNodeWithText("Synthetic PC title").assertExists()
        compose.onNodeWithText("phone tag").assertExists()
        compose.onNodeWithText("PC tag").assertExists()
        compose.onNodeWithText("Unchanged artist").assertDoesNotExist()
        compose.onNodeWithText(strings.getString(R.string.conflict_use_phone)).performClick()
        assertEquals(true, chosen)
    }
    @Test fun pcCanBeChosen() {
        var chosen: Boolean? = null
        compose.setContent { DoujinZipTheme { PresentationConflictDialog(record, { chosen=it }, {}) } }
        compose.onNodeWithText(strings.getString(R.string.ui_use_pc_edit)).performClick()
        assertEquals(false, chosen)
    }
    @Test fun laterPreservesBothVersions() {
        var choices=0
        var later=false
        compose.setContent { DoujinZipTheme { PresentationConflictDialog(record, { choices++ }, { later=true }) } }
        compose.onNodeWithText(strings.getString(R.string.ui_later)).performClick()
        assertTrue(later)
        assertEquals(0, choices)
    }
}
