package com.example.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.dpadFocusable
import com.example.ui.theme.*
import java.util.Locale

fun formatLiveCount(count: Int): String {
    return when {
        count >= 1_000_000 -> String.format(Locale.ENGLISH, "%.1fM", count / 1_000_000.0)
        count >= 10_000 -> String.format(Locale.ENGLISH, "%.1fk", count / 1000.0)
        count >= 1_000 -> String.format(Locale.ENGLISH, "%,d", count)
        else -> count.toString()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CineTopAppBar(
    onOpenDrawer: () -> Unit,
    isDarkMode: Boolean = true,
    onToggleDarkMode: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = CineSurfaceVariant
        ),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 6.dp)
            ) {
                Text(
                    text = "Cine ",
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
                    color = CineTextPrimary
                )
                Text(
                    text = "Arena",
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
                    color = CinePrimary
                )
            }
        },
        navigationIcon = {
            Box(modifier = Modifier.padding(start = 12.dp)) {
                IconButton(
                    onClick = onOpenDrawer,
                    modifier = Modifier
                        .size(38.dp)
                        .background(CinePrimaryContainer, RoundedCornerShape(10.dp))
                        .dpadFocusable(shape = RoundedCornerShape(10.dp), focusedBorderColor = Color.White)
                ) {
                    Icon(
                        Icons.Default.Menu,
                        contentDescription = "Menu",
                        tint = CineOnPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        actions = {
            // Mode Switching Button
            IconButton(
                onClick = onToggleDarkMode,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(38.dp)
                    .background(CinePrimaryContainer, CircleShape)
                    .dpadFocusable(shape = CircleShape, focusedBorderColor = Color.White)
            ) {
                Icon(
                    imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = "Switch Theme Mode",
                    tint = if (isDarkMode) Color.White else CinePrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        },
        modifier = modifier
    )
}

enum class CineTab(val label: String, val icon: @Composable () -> Unit) {
    HOME("Home", { Icon(Icons.Default.Home, contentDescription = "Home") }),
    SPORTS("Sports", { Icon(Icons.Default.EmojiEvents, contentDescription = "Sports") }),
    TV("TV", { Icon(Icons.Default.Tv, contentDescription = "TV") }),
    HISTORY("History", { Icon(Icons.Default.History, contentDescription = "History") }),
    IPTV_PLAYER("IPTV Player", { Icon(Icons.Default.PlaylistPlay, contentDescription = "IPTV Player") }),
    JIO_TV_WW("Jio-Tv (World Wide)", { Icon(Icons.Default.Public, contentDescription = "Jio-Tv World Wide") }),
    AIRTEL_TV("Airtel TV", { Icon(Icons.Default.LiveTv, contentDescription = "Airtel TV") }),
    JIO_TV("Jio Tv (India Only)", { Icon(Icons.Default.Tv, contentDescription = "Jio TV") }),
    FAVORITES("Favorites", { Icon(Icons.Default.Favorite, contentDescription = "Favorites") }),
    MOVIES("Movies", { Icon(Icons.Default.Movie, contentDescription = "Movies") })
}

@Composable
fun CineBottomNavBar(
    selectedTab: CineTab,
    onTabSelected: (CineTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val bottomTabs = remember {
        listOf(CineTab.HOME, CineTab.SPORTS, CineTab.TV, CineTab.MOVIES)
    }

    NavigationBar(
        containerColor = CineSurfaceVariant,
        tonalElevation = 3.dp,
        modifier = modifier
    ) {
        bottomTabs.forEach { tab ->
            val isSelected = (selectedTab == tab)
            NavigationBarItem(
                selected = isSelected,
                onClick = { onTabSelected(tab) },
                icon = tab.icon,
                label = {
                    Text(
                        text = tab.label,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                },
                modifier = Modifier.dpadFocusable(
                    shape = RoundedCornerShape(12.dp),
                    focusedBorderColor = CinePrimary,
                    scaleOnFocus = 1.08f
                ),
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = CineOnPrimaryContainer,
                    unselectedIconColor = CineTextSecondary,
                    selectedTextColor = CineOnPrimaryContainer,
                    unselectedTextColor = CineTextSecondary,
                    indicatorColor = CinePrimaryContainer
                )
            )
        }
    }
}

@Composable
fun CineDrawerContent(
    onOpenNetworkStream: () -> Unit,
    onOpenHistory: () -> Unit = {},
    onOpenIptvPlayer: () -> Unit = {},
    onOpenJioTvWw: () -> Unit = {},
    onOpenAirtelTv: () -> Unit = {},
    onOpenJioTv: () -> Unit = {},
    onOpenFavorites: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onCheckForUpdates: (() -> Unit)? = null,
    onCloseDrawer: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(280.dp)
            .background(CineSurfaceVariant)
            .padding(16.dp)
    ) {
        // App Header Branding
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    color = CinePrimaryContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Tv,
                            contentDescription = "Logo",
                            tint = CinePrimary,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Cine Arena",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = CineTextPrimary
                )
                Text(
                    text = "Version: ${com.example.BuildConfig.VERSION_NAME}",
                    fontSize = 12.sp,
                    color = CineTextSecondary
                )
            }
        }

        HorizontalDivider(color = CineOutline, modifier = Modifier.padding(vertical = 8.dp))

        // Navigation Items
        DrawerMenuItem(
            icon = Icons.Default.Stream,
            title = "Network Stream",
            onClick = {
                onCloseDrawer()
                onOpenNetworkStream()
            }
        )

        DrawerMenuItem(
            icon = Icons.Default.History,
            title = "History",
            onClick = {
                onCloseDrawer()
                onOpenHistory()
            }
        )

        DrawerMenuItem(
            icon = Icons.Default.PlaylistPlay,
            title = "IPTV Player",
            onClick = {
                onCloseDrawer()
                onOpenIptvPlayer()
            }
        )

        DrawerMenuItem(
            icon = Icons.Default.Public,
            title = "Jio-Tv (World Wide)",
            onClick = {
                onCloseDrawer()
                onOpenJioTvWw()
            }
        )

        DrawerMenuItem(
            icon = Icons.Default.LiveTv,
            title = "Airtel Tv",
            onClick = {
                onCloseDrawer()
                onOpenAirtelTv()
            }
        )

        DrawerMenuItem(
            icon = Icons.Default.Tv,
            title = "Jio Tv (India Only)",
            onClick = {
                onCloseDrawer()
                onOpenJioTv()
            }
        )

        DrawerMenuItem(
            icon = Icons.Default.Favorite,
            title = "Favorites",
            tint = CineTextPrimary,
            onClick = {
                onCloseDrawer()
                onOpenFavorites()
            }
        )

        DrawerMenuItem(
            icon = Icons.Default.Settings,
            title = "Settings",
            tint = CineTextPrimary,
            onClick = {
                onCloseDrawer()
                onOpenSettings()
            }
        )

        DrawerMenuItem(
            icon = Icons.Default.Share,
            title = "Share App",
            onClick = {
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, "Check out Cine Arena for live cricket & streaming: https://cinexcricket.com/download/apk/")
                    type = "text/plain"
                }
                context.startActivity(Intent.createChooser(sendIntent, "Share Cine Arena"))
                onCloseDrawer()
            }
        )

        DrawerMenuItem(
            icon = Icons.Default.SystemUpdate,
            title = "Check for Updates",
            onClick = {
                onCloseDrawer()
                if (onCheckForUpdates != null) {
                    onCheckForUpdates()
                } else {
                    Toast.makeText(context, "Checking for latest updates...", Toast.LENGTH_SHORT).show()
                }
            }
        )

        DrawerMenuItem(
            icon = Icons.Default.Info,
            title = "Copyright",
            onClick = {
                Toast.makeText(context, "Cine Arena v${com.example.BuildConfig.VERSION_NAME} • All rights reserved", Toast.LENGTH_LONG).show()
                onCloseDrawer()
            }
        )

        Spacer(modifier = Modifier.weight(1f))

        DrawerMenuItem(
            icon = Icons.AutoMirrored.Filled.ExitToApp,
            title = "Exit",
            tint = CineLiveRed,
            onClick = onCloseDrawer
        )
    }
}

@Composable
fun DrawerMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    tint: Color = CineTextPrimary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .dpadFocusable(
                shape = RoundedCornerShape(8.dp),
                focusedBorderColor = CinePrimary,
                scaleOnFocus = 1.03f
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            color = tint,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
