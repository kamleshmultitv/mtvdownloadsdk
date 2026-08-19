package com.app.videosdk.ui.chapter

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.app.videosdk.model.Chapter

@Composable
fun ChapterDrawer(
    chapters: List<Chapter>,
    currentChapter: Chapter?,
    onChapterClick: (Chapter) -> Unit
) {

    ModalDrawerSheet(
        drawerContainerColor = Color.Black
    ) {

        Column(modifier = Modifier.fillMaxSize()) {

            Text(
                text = "Chapters",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                modifier = Modifier.padding(8.dp)
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 8.dp),
                color = Color.White.copy(alpha = 0.3f)
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(chapters, key = { it.id }) { chapter ->
                    val isSelected = chapter.id == currentChapter?.id

                    ChapterItemCard(
                        chapter = chapter,
                        isSelected = isSelected,
                        onClick = { onChapterClick(chapter) }
                    )
                }
            }
        }
    }
}

