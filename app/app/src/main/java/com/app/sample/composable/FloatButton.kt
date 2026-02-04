package com.app.sample.composable

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.app.sample.model.PlaybackConfig

@Composable
fun FloatButton(
    onSubmit: (PlaybackConfig) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var config by remember { mutableStateOf(PlaybackConfig()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(end = 16.dp, bottom = 60.dp)
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primary
        ) {
            AnimatedContent(
                targetState = expanded,
                label = "fab_expand"
            ) { open ->

                if (open) {
                    CardWithTextFieldsAndButton(
                        initialConfig = config,
                        onSubmit = {
                            config = it
                            onSubmit(it) // 🔥 only here
                        },
                        expanded = { expanded = it }
                    )
                }

                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Expand")
                }
            }
        }
    }
}



