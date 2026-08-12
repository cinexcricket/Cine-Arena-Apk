package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CineLiveRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin
import kotlin.random.Random

data class FloatingHeart(
    val id: Long,
    val xRatio: Float, // 0.6f to 0.9f
    val color: Color,
    val size: Dp
)

@Composable
fun FloatingHeartsOverlay(
    activeHearts: List<FloatingHeart>,
    onHeartFinished: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize(),
        contentAlignment = Alignment.BottomEnd
    ) {
        activeHearts.forEach { heart ->
            SingleFloatingHeart(
                heart = heart,
                onFinished = { onHeartFinished(heart.id) }
            )
        }
    }
}

@Composable
private fun SingleFloatingHeart(
    heart: FloatingHeart,
    onFinished: () -> Unit
) {
    val animProgress = remember { Animatable(0f) }

    LaunchedEffect(heart.id) {
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 2000,
                easing = FastOutSlowInEasing
            )
        )
        onFinished()
    }

    val progress = animProgress.value
    // Floating calculations: moves UP towards -280.dp, wobbles on X
    val offsetY = - (progress * 260) // in dp
    val wobbleX = (sin(progress * Math.PI * 3) * 28.0).toFloat() // wobble left/right
    val alpha = if (progress < 0.7f) 1f else (1f - (progress - 0.7f) / 0.3f)
    val scale = if (progress < 0.2f) progress * 5f else 1f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = offsetY * density
                translationX = (wobbleX - (1f - heart.xRatio) * 60) * density
                this.alpha = alpha.coerceIn(0f, 1f)
                scaleX = scale
                scaleY = scale
            },
        contentAlignment = Alignment.BottomEnd
    ) {
        Icon(
            imageVector = Icons.Default.Favorite,
            contentDescription = "Floating Heart Reaction",
            tint = heart.color,
            modifier = Modifier
                .padding(end = (30 * heart.xRatio).dp, bottom = 12.dp)
                .size(heart.size)
        )
    }
}

fun generateRandomHeart(): FloatingHeart {
    val colors = listOf(
        Color(0xFFEF4444),
        Color(0xFFFF1744),
        Color(0xFFFF4081),
        Color(0xFFFF9100),
        Color(0xFFE040FB),
        Color(0xFF00E676),
        Color(0xFFFFD600)
    )
    return FloatingHeart(
        id = System.nanoTime() + Random.nextLong(1000),
        xRatio = Random.nextFloat() * 0.4f + 0.5f,
        color = colors.random(),
        size = (24 + Random.nextInt(14)).dp
    )
}
