package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CineLiveRed
import com.example.ui.theme.CinePrimary
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    var startAnimation by remember { mutableStateOf(false) }

    // Animated values
    val scaleAnim = animateFloatAsState(
        targetValue = if (startAnimation) 1.0f else 0.3f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "logoScale"
    )

    val alphaAnim = animateFloatAsState(
        targetValue = if (startAnimation) 1.0f else 0.0f,
        animationSpec = tween(durationMillis = 800, easing = LinearOutSlowInEasing),
        label = "logoAlpha"
    )

    val textYAnim = animateFloatAsState(
        targetValue = if (startAnimation) 0f else 40f,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "textOffset"
    )

    // Infinite pulse animation for glow ring
    val infiniteTransition = rememberInfiniteTransition(label = "glowPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    LaunchedEffect(Unit) {
        startAnimation = true
        delay(2200) // Display splash for 2.2 seconds
        onSplashFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F0C20),
                        Color(0xFF191330),
                        Color(0xFF0B0818)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Ambient background glow
        Box(
            modifier = Modifier
                .size(240.dp)
                .scale(pulseScale)
                .alpha(pulseAlpha)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            CinePrimary,
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .scale(scaleAnim.value)
                .alpha(alphaAnim.value)
                .offset(y = textYAnim.value.dp)
        ) {
            // Main Animated Logo Badge
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(110.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF7F52FF),
                                CinePrimary,
                                Color(0xFF4A2CB3)
                            )
                        )
                    )
            ) {
                // Inner icon combination
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = "CineArena Logo",
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(42.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = CineLiveRed,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // App Title
            Text(
                text = "CineArena",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 36.sp,
                    letterSpacing = 1.sp
                ),
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Tagline with Live Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(CineLiveRed, shape = CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "LIVE SPORTS & TV STREAMS",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 2.sp
                    ),
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }

        // Bottom Loading Progress Line
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 56.dp)
                .width(120.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.15f))
        ) {
            val progressAnim by animateFloatAsState(
                targetValue = if (startAnimation) 1f else 0f,
                animationSpec = tween(durationMillis = 2000, easing = LinearEasing),
                label = "progress"
            )

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progressAnim)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                CinePrimary,
                                CineLiveRed
                            )
                        )
                    )
            )
        }
    }
}
