package com.doujinzip.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.semantics.text
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.SemanticsProperties

internal data class CardTag(val info: TagDisplayInfo, val onClick: (() -> Unit)? = null)
internal data class TagPlacement(val index: Int, val x: Int, val row: Int)
internal data class PackedTags(val tags: List<TagPlacement>, val overflow: TagPlacement?)

internal fun packTags(widths: List<Int>, width: Int, rows: Int, gap: Int, overflowWidth: Int, hasMore: Boolean = false): PackedTags {
    if (width <= 0 || rows <= 0) return PackedTags(emptyList(), null)
    val placed = mutableListOf<TagPlacement>()
    var x = 0
    var row = 0
    for ((index, rawWidth) in widths.withIndex()) {
        val itemWidth = rawWidth.coerceIn(0, width)
        if (x > 0 && x + itemWidth > width) { row++; x = 0 }
        if (row >= rows) break
        placed += TagPlacement(index, x, row)
        x += itemWidth + gap
    }
    if (placed.size == widths.size && !hasMore) return PackedTags(placed, null)
    if (placed.size == widths.size && x + overflowWidth > width && row + 1 < rows) row++
    row = minOf(row, rows - 1)
    while (placed.isNotEmpty()) {
        val last = placed.last()
        val nextX = last.x + widths[last.index].coerceIn(0, width) + gap
        if (last.row < row) return PackedTags(placed, TagPlacement(-1, 0, row))
        if (nextX + overflowWidth <= width) return PackedTags(placed, TagPlacement(-1, nextX, row))
        placed.removeAt(placed.lastIndex)
    }
    return PackedTags(emptyList(), TagPlacement(-1, 0, row))
}

@Composable
private fun WorkTags(tags: List<CardTag>, rows: Int, modifier: Modifier) {
    val style = MaterialTheme.typography.bodyMedium
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer(cacheSize = 32)
    val labels = tags.map { it.info.text }
    val colors = TagStyle.entries.associateWith { tagColors(it) }
    val overflowColor = LocalContentColor.current
    BoxWithConstraints(modifier) {
        val width = constraints.maxWidth
        val padding = with(density) { 9.dp.roundToPx() }
        val gap = with(density) { 4.dp.roundToPx() }
        val verticalGap = with(density) { 3.dp.roundToPx() }
        // Measure text once and draw it directly. Avoid a Surface/Text subcomposition per pill per layout.
        val measured = remember(labels, width, rows, style, measurer, padding, gap) {
            buildList {
                var row = 0
                var x = 0
                for (label in labels) {
                    val text = measurer.measure(AnnotatedString(label), style, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, constraints = Constraints(maxWidth = (width - padding * 2).coerceAtLeast(0)))
                    add(text)
                    val tagWidth = (text.size.width + padding * 2).coerceAtMost(width)
                    if (x > 0 && x + tagWidth > width) { row++; x = 0 }
                    if (row >= rows) break
                    x += tagWidth + gap
                }
            }
        }
        val overflow = measurer.measure(AnnotatedString("…"), style, maxLines = 1, constraints = Constraints(maxWidth = width))
        val widths = measured.map { (it.size.width + padding * 2).coerceAtMost(width) }
        val packed = remember(widths, width, rows, gap, overflow.size.width, tags.size) {
            packTags(widths, width, rows, gap, overflow.size.width, measured.size < tags.size)
        }
        val hasTagActions = tags.any { it.onClick != null }
        // Read noninteractive tags together; actionable tags retain their individual buttons.
        val tagSemantics = if (hasTagActions) Modifier else Modifier.semantics {
            this[SemanticsProperties.Text] = packed.tags.map { AnnotatedString(tags[it.index].info.text) } +
                if (packed.overflow != null) listOf(AnnotatedString("…")) else emptyList()
        }
        Layout(modifier = Modifier.fillMaxSize().then(tagSemantics), content = {
            packed.tags.forEach { item ->
                val tag = tags[item.index]
                val (background, foreground) = colors.getValue(tag.info.style)
                Canvas((if (hasTagActions) Modifier.semantics { text = AnnotatedString(tag.info.text); if (tag.onClick != null) role = Role.Button } else Modifier)
                    .then(if (tag.onClick != null) Modifier.clip(RoundedCornerShape(50)).clickable(onClick = tag.onClick) else Modifier)) {
                    drawRoundRect(background, cornerRadius = CornerRadius(size.height / 2))
                    drawText(measured[item.index], foreground, topLeft = Offset(padding.toFloat(), 4.dp.toPx()))
                }
            }
            if (packed.overflow != null) Canvas(if (hasTagActions) Modifier.semantics { text = AnnotatedString("…") } else Modifier) { drawText(overflow, overflowColor) }
        }) { measurables, bounds ->
            val rowHeight = ((bounds.maxHeight - (rows - 1) * verticalGap) / rows).coerceAtLeast(1)
            val children = packed.tags.mapIndexed { index, item -> measurables[index].measure(Constraints.fixed(widths[item.index], rowHeight)) }
            val ellipsis = packed.overflow?.let { measurables.last().measure(Constraints.fixed(overflow.size.width, rowHeight)) }
            layout(bounds.maxWidth, bounds.maxHeight) {
                packed.tags.forEachIndexed { index, item -> children[index].placeRelative(item.x, item.row * (rowHeight + verticalGap)) }
                packed.overflow?.let { ellipsis?.placeRelative(it.x, it.row * (rowHeight + verticalGap)) }
            }
        }
    }
}

@Composable
internal fun WorkCard(
    title: String,
    artist: String,
    metadata: String,
    tags: List<CardTag>,
    footer: String,
    footerError: Boolean = false,
    longClickLabel: String? = null,
    grid: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    border: BorderStroke? = null,
    selected: Boolean = false,
    webtoon: Boolean = false,
    artistLinks: List<Pair<String, () -> Unit>> = emptyList(),
    cover: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    val typography = MaterialTheme.typography
    val cardSize = LocalWorkCardSize.current
    val rows = if (grid) 3 else if (cardSize == WorkCardSize.SMALL) 1 else 2
    val tagHeight = with(density) { typography.bodyMedium.lineHeight.toDp() } + 8.dp
    val minimumBodyHeight = with(density) {
        typography.titleSmall.lineHeight.toDp() * 2 + typography.bodySmall.lineHeight.toDp() + typography.labelSmall.lineHeight.toDp() * 2
    } + tagHeight * rows + 3.dp * (rows - 1) + 36.dp
    val bodyHeight = if (grid) minimumBodyHeight else maxOf(minimumBodyHeight, minimumBodyHeight * cardSize.listScale)
    @Composable fun Body(modifier: Modifier) {
        Column(modifier.height(bodyHeight).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = typography.titleSmall, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val artistText = buildAnnotatedString {
                if (artistLinks.isEmpty()) append(artist)
                artistLinks.forEachIndexed { index, (name, search) ->
                    if (index > 0) append(", ")
                    withLink(LinkAnnotation.Clickable(name, linkInteractionListener = { search() })) { append(name) }
                }
            }
            Text(artistText, style = typography.bodySmall, color = MaterialTheme.colorScheme.primary, minLines = 1, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(metadata, style = typography.labelSmall, minLines = 1, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val displayTags = if (webtoon) listOf(CardTag(TagDisplayInfo(androidx.compose.ui.platform.LocalResources.current[com.doujinzip.android.R.string.ui_webtoon_tag], TagStyle.DEFAULT))) +
                tags.filterNot { it.info.text.equals("webtoon", true) } else tags
            if (displayTags.isNotEmpty()) {
                WorkTags(displayTags, rows, Modifier.fillMaxWidth().height(tagHeight * rows + 3.dp * (rows - 1)))
            }
            Text(footer, color = if (footerError) MaterialTheme.colorScheme.error else LocalContentColor.current, style = typography.labelSmall, minLines = 1, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    val selectionTint = MaterialTheme.colorScheme.primary.copy(alpha = .10f)
    @Composable fun Cover(modifier: Modifier) {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
            cover()
            if (selected) Box(Modifier.matchParentSize().background(selectionTint))
        }
    }
    Card(modifier = Modifier.fillMaxWidth().semantics { isTraversalGroup = true; if (selected) this.selected = true }
        .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = longClickLabel), border = border,
        colors = appCardColors(selected)) {
        if (grid) Column {
            Cover(Modifier.fillMaxWidth().aspectRatio(.72f))
            Body(Modifier.fillMaxWidth())
        } else Row(Modifier.fillMaxWidth().height(bodyHeight)) {
            Cover(Modifier.width(cardSize.listCoverWidth.dp).fillMaxHeight())
            Body(Modifier.weight(1f))
        }
    }
}
