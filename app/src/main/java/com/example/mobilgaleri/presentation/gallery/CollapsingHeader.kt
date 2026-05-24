package com.example.mobilgaleri.presentation.gallery

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.mobilgaleri.R

@Composable
fun CollapsingHeader(
    state: LazyGridState,
    title: String,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isScrolled by remember {
        derivedStateOf {
            state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 0
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (isScrolled) 0f else 1f,
        animationSpec = tween(250),
        label = "headerAlpha"
    )

    val headerBg = colorResource(R.color.header_background)
    val headerAccent = colorResource(R.color.header_accent)

    if (alpha == 0f) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(headerBg)
            .height(56.dp)
            .alpha(alpha)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = headerAccent
            )

            Spacer(modifier = Modifier.weight(1f))

            IconButton(onClick = onToggleTheme) {
                Image(
                    painter = painterResource(R.drawable.ic_theme_toggle),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(Modifier.width(8.dp))

            Image(
                painter = painterResource(R.drawable.ic_header),
                contentDescription = null,
                modifier = Modifier.size(40.dp)
            )
        }
    }
}