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
import com.example.yapzy.phone.Contact
import com.example.yapzy.ui.screens.*
import androidx.compose.ui.unit.dp
import com.google.gson.Gson


sealed class Screen(val route: String) {
    object Calls : Screen("calls")
    object Messages : Screen("messages")
    object Contacts : Screen("contacts")
    object ContactDetails : Screen("contact_details/{contactJson}") {
        fun createRoute(contactJson: String) = "contact_details/$contactJson"
    }
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
    object Contacts : BottomNavItem(
        Screen.Contacts.route,
        Icons.Filled.Contacts,
        Icons.Outlined.Contacts,
        "Contacts"
    )
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    initialPhoneNumber: String? = null,
    initialContactJson: String? = null
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val gson = remember { Gson() }

    // Navigate to contact details if provided from PostCallActivity
    LaunchedEffect(initialContactJson) {
        if (initialContactJson != null) {
            navController.navigate(Screen.ContactDetails.createRoute(initialContactJson)) {
                popUpTo(Screen.Contacts.route)
            }
        }
    }

    // Determine if we should show bottom bar
    val showBottomBar = currentDestination?.route in listOf(
        Screen.Calls.route,
        Screen.Messages.route,
        Screen.Contacts.route
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
                        BottomNavItem.Messages,
                        BottomNavItem.Contacts
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

            composable(Screen.Contacts.route) {
                ContactsScreen(
                    onContactClick = { contact: Contact ->
                        val contactJson = java.net.URLEncoder.encode(
                            gson.toJson(contact),
                            "UTF-8"
                        )
                        navController.navigate(Screen.ContactDetails.createRoute(contactJson))
                    }
                )
            }

            composable(
                route = Screen.ContactDetails.route,
                arguments = listOf(
                    navArgument("contactJson") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val contactJson = backStackEntry.arguments?.getString("contactJson") ?: return@composable
                val decodedJson = java.net.URLDecoder.decode(contactJson, "UTF-8")
                val contact = gson.fromJson(decodedJson, Contact::class.java)

                ContactDetailsScreen(
                    contact = contact,
                    onBackClick = { navController.navigateUp() },
                    onCallClick = { navController.navigateUp() },
                    onMessageClick = {
                        // Navigate to messages with this contact
                        navController.navigate(Screen.Chat.createRoute(contact.phoneNumber)) {
                            popUpTo(Screen.Contacts.route)
                        }
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