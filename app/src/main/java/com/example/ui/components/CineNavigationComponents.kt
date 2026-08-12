package com.example.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

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
                modifier = Modifier.padding(start = 12.dp)
            ) {
                Text(
                    text = "Cine ",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = CineTextPrimary
                )
                Text(
                    text = "Arena",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
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
            // Mode Switching Button (Left of Telegram icon)
            IconButton(
                onClick = onToggleDarkMode,
                modifier = Modifier
                    .size(38.dp)
                    .background(CinePrimaryContainer, CircleShape)
            ) {
                Icon(
                    imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = "Switch Theme Mode",
                    tint = if (isDarkMode) Color.White else CinePrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Telegram Action Button
            IconButton(
                onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/+052xVyIlae9lZTZl"))
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Opening Telegram...", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(38.dp)
                    .background(CinePrimaryContainer, CircleShape)
            ) {
                Icon(
                    Icons.Default.Send,
                    contentDescription = "Telegram",
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
    FAVORITES("Favorites", { Icon(Icons.Default.Favorite, contentDescription = "Favorites") })
}

@Composable
fun CineBottomNavBar(
    selectedTab: CineTab,
    onTabSelected: (CineTab) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        containerColor = CineSurfaceVariant,
        tonalElevation = 3.dp,
        modifier = modifier
    ) {
        CineTab.entries.forEach { tab ->
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
                    text = "Version: 5.0",
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
            icon = Icons.AutoMirrored.Filled.Chat,
            title = "Telegram",
            onClick = {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/+052xVyIlae9lZTZl")))
                } catch (e: Exception) {
                    Toast.makeText(context, "Telegram link copied", Toast.LENGTH_SHORT).show()
                }
                onCloseDrawer()
            }
        )

        DrawerMenuItem(
            icon = Icons.Default.Language,
            title = "Website",
            onClick = {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://cinexcricket.com")))
                } catch (e: Exception) {
                    Toast.makeText(context, "Opening website...", Toast.LENGTH_SHORT).show()
                }
                onCloseDrawer()
            }
        )

        DrawerMenuItem(
            icon = Icons.Default.Share,
            title = "Share App",
            onClick = {
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, "Check out Cine Arena for live streaming: https://cinexcricket.com")
                    type = "text/plain"
                }
                context.startActivity(Intent.createChooser(sendIntent, "Share Cine Arena"))
                onCloseDrawer()
            }
        )

        DrawerMenuItem(
            icon = Icons.Default.Info,
            title = "Copyright",
            onClick = {
                Toast.makeText(context, "Cine Arena v5.0 • All rights reserved", Toast.LENGTH_LONG).show()
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
