package com.doujinzip.android.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.doujinzip.android.R
import com.doujinzip.android.data.PresentationRecord
import com.doujinzip.android.model.*
import com.doujinzip.android.ui.theme.DoujinZipTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DetailUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun coverResetIsAnExplicitDetailAction() {
        val strings = InstrumentationRegistry.getInstrumentation().targetContext.resources
        var resets = 0
        compose.setContent { DoujinZipTheme { PresentationActions(GalleryPresentation("hitomi:42", "Fixture", LibraryMetadata()), null,
            emptyList(), false, null, { _, _ -> }, { _, _ -> }, {}, emptyList(), onResetCover = { resets++ }) } }
        compose.onNodeWithContentDescription(strings.getString(R.string.ui_detail_options)).performClick()
        compose.onNodeWithText(strings.getString(R.string.ui_reset_cover)).performClick()
        assertEquals(1, resets)
    }
    @Test fun webtoonClassificationShowsOneLocalizedTagWithoutChangingUserTags() {
        val strings = InstrumentationRegistry.getInstrumentation().targetContext.resources
        compose.setContent { DoujinZipTheme { WorkCard("Fixture", "Artist", "", listOf(CardTag(tagDisplayInfo("webtoon")), CardTag(tagDisplayInfo("user tag"))),
            "", grid = true, onClick = {}, webtoon = true) {} } }
        compose.onAllNodesWithText(strings.getString(R.string.ui_webtoon_tag)).assertCountEquals(1)
        compose.onNodeWithText("user tag").assertExists()
    }
    @Test fun metadataEditorSavesClearsAndKeepsOriginalSeriesSeparate() {
        val strings = InstrumentationRegistry.getInstrumentation().targetContext.resources
        val value = GalleryPresentation("hitomi:42", "Original title", LibraryMetadata(series = listOf("Original IP"), tags = listOf("source")))
        var saved: GalleryPresentation? = null
        compose.setContent { DoujinZipTheme { MetadataEditor(value, null, {}, { saved = it }) } }
        compose.onNodeWithText("Original title").performTextReplacement("Phone title")
        compose.onNodeWithText("Original IP").assertExists()
        compose.onNodeWithText(strings.getString(R.string.ui_source_series)).assertExists()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("source"))
        compose.onNodeWithText("source").performTextClearance()
        compose.onNodeWithText(strings.getString(R.string.ui_save)).performClick()
        assertEquals("Phone title", saved!!.title)
        assertTrue(saved!!.metadata.tags.isEmpty())
        assertEquals(listOf("Original IP"), saved!!.metadata.series)
    }
    @Test fun conflictMenuRetainsPhoneEditUntilTheUserChoosesPc() {
        val strings = InstrumentationRegistry.getInstrumentation().targetContext.resources
        val value = GalleryPresentation("hitomi:42", "Phone edit", LibraryMetadata())
        var accepted = false
        compose.setContent { DoujinZipTheme { PresentationActions(value, PresentationRecord(value, dirty = true, conflict = value.copy(title = "PC edit", version = 1)),
            emptyList(), false, null, { _, _ -> }, { _, _ -> }, { accepted = true }, emptyList()) } }
        compose.onNodeWithContentDescription(strings.getString(R.string.ui_detail_options)).performClick()
        compose.onNodeWithText(strings.getString(R.string.ui_metadata_conflict)).assertExists()
        compose.onNodeWithText(strings.getString(R.string.ui_cover_from_page)).assertIsNotEnabled()
        assertFalse(accepted)
        compose.onNodeWithText(strings.getString(R.string.ui_use_pc_edit)).performClick()
        assertTrue(accepted)
    }
    @Test fun undownloadedDetailDoesNotOfferMetadataOrCoverEditing() {
        val strings = InstrumentationRegistry.getInstrumentation().targetContext.resources
        compose.setContent { DoujinZipTheme { PresentationActions(GalleryPresentation("hitomi:42", "Online", LibraryMetadata()), null,
            emptyList(), false, null, { _, _ -> }, { _, _ -> }, {}, listOf(DetailMenuAction("Favorite", ActionSymbol.STAR) {}), editable = false) } }
        compose.onNodeWithContentDescription(strings.getString(R.string.ui_detail_options)).performClick()
        compose.onNodeWithText("Favorite").assertExists()
        compose.onNodeWithText(strings.getString(R.string.ui_edit_metadata)).assertDoesNotExist()
        compose.onNodeWithText(strings.getString(R.string.ui_cover_from_image)).assertDoesNotExist()
    }
}
