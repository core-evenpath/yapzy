package com.example.yapzy.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.example.yapzy.ui.screens.CallsScreen
import com.example.yapzy.ui.screens.ChatScreen
import com.example.yapzy.ui.screens.ConversationListScreen
import androidx.compose.ui.unit.dp


sealed class Screen(val route: String) {
    object Calls : Screen("calls")
    object Messages : Screen("messages")
    object Chat : Screen("chat/{conversationId}") {
        fun createRoute(conversationId: String) = "chat/$conversationId"
    }
}

sealed class BottomNavItem(
    val route: String,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String
) {
    object Calls : BottomNavItem(
        Screen.Calls.route,
        Icons.Filled.Phone,
        Icons.Outlined.Phone,
        "Calls"
    )
    object Messages : BottomNavItem(
        Screen.Messages.route,
        Icons.Filled.Message,
        Icons.Outlined.Message,
        "Messages"
    )
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    initialPhoneNumber: String? = null
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    
    // Determine if we should show bottom bar
    val showBottomBar = currentDestination?.route in listOf(
        Screen.Calls.route,
        Screen.Messages.route
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    val items = listOf(
                        BottomNavItem.Calls,
                        BottomNavItem.Messages
                    )
                    
                    items.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                        
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label
                                )
                            },
                            label = {
                                Text(
                                    item.label,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            selected = selected,
                            onClick = {
                                if (!selected) {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Calls.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.Calls.route) {
                CallsScreen(initialPhoneNumber = initialPhoneNumber)
            }
            
            composable(Screen.Messages.route) {
                ConversationListScreen(
                    onConversationClick = { conversationId ->
                        navController.navigate(Screen.Chat.createRoute(conversationId))
                    }
                )
            }
            
            composable(
                route = Screen.Chat.route,
                arguments = listOf(
                    navArgument("conversationId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val conversationId = backStackEntry.arguments?.getString("conversationId") ?: return@composable
                ChatScreen(
                    conversationId = conversationId,
                    onBackClick = { navController.navigateUp() }
                )
            }
        }
    }
}
