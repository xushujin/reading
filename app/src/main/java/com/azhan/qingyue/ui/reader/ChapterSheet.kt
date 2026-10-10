package com.azhan.qingyue.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azhan.qingyue.ui.neu.neuInner
import com.azhan.qingyue.ui.theme.LocalReaderPalette

@Composable
fun ChapterSheet(
    titles: List<String>,
    currentChapter: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalReaderPalette.current
    val listState = rememberLazyListState()

    LaunchedEffect(currentChapter) {
        if (currentChapter in titles.indices) listState.scrollToItem(currentChapter)
    }

    PanelScaffold(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "目录",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "共 ${titles.size} 章",
                    fontSize = 12.sp,
                    color = palette.textSecondary,
                )
            }

            Spacer(Modifier.height(8.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
            ) {
                itemsIndexed(titles) { index, title ->
                    ChapterRow(
                        index = index,
                        title = title,
                        active = index == currentChapter,
                        onClick = { onSelect(index) },
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun ChapterRow(
    index: Int,
    title: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalReaderPalette.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .then(
                if (active) {
                    Modifier
                        .background(palette.surface, RoundedCornerShape(12.dp))
                        .neuInner(12.dp, palette, 3.dp)
                } else {
                    Modifier
                },
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${index + 1}",
            fontSize = 11.sp,
            color = if (active) palette.accent else palette.textSecondary,
            modifier = Modifier.width(30.dp),
        )
        Text(
            text = title.ifBlank { "第 ${index + 1} 章" },
            fontSize = 14.sp,
            color = if (active) palette.accent else palette.textPrimary,
            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
