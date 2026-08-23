package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CineLiveRed
import com.example.ui.theme.CineOutline
import com.example.ui.theme.CinePrimary

/**
 * Hardware-accelerated modifier extension to give cards, buttons, and items an explicit,
 * high-visibility D-Pad remote focus indicator without causing layout recalculation or scroll jank.
 */
fun Modifier.dpadFocusable(
    shape: Shape = RoundedCornerShape(16.dp),
    focusedBorderColor: Color = Color(0xFF2B62F6),
    unfocusedBorderColor: Color = Color.Transparent,
    focusedBorderWidth: Dp = 2.5.dp,
    unfocusedBorderWidth: Dp = 0.dp,
    scaleOnFocus: Float = 1.04f,
    elevationOnFocus: Dp = 8.dp,
    interactionSource: MutableInteractionSource? = null
): Modifier = composed {
    val actualSource = interactionSource ?: remember { MutableInteractionSource() }
    val isFocused by actualSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused) scaleOnFocus else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "dpad_scale"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isFocused) focusedBorderColor else unfocusedBorderColor,
        animationSpec = tween(durationMillis = 150),
        label = "dpad_border_color"
    )

    val borderWidth = if (isFocused) focusedBorderWidth else unfocusedBorderWidth

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            if (isFocused && elevationOnFocus > 0.dp) {
                shadowElevation = elevationOnFocus.toPx()
                this.shape = shape
                clip = true
            }
        }
        .then(
            if (isFocused && borderWidth > 0.dp) {
                Modifier.border(BorderStroke(borderWidth, borderColor), shape)
            } else {
                Modifier
            }
        )
        .focusable(interactionSource = actualSource)
}

