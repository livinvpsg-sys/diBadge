package com.fabxdi.dibadge.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme

@Composable
fun HomeMenu(
    firstName: String,
    onCloseDrawer: () -> Unit,
    onSignOut: () -> Unit = {}
) {
    ModalDrawerSheet(
        modifier = Modifier.fillMaxHeight().width(300.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(DiBadgeTheme.spacing.medium)
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            // Profile Section
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = firstName.take(1).uppercase(),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    text = firstName,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Menu Items
            val menuItems = listOf("Settings", "Certificates", "Claims", "Balance", "Sign Out")
            menuItems.forEach { item ->
                NavigationDrawerItem(
                    label = { 
                        Text(
                            text = item, 
                            style = MaterialTheme.typography.bodyLarge,
                            color = when (item) {
                                "Balance" -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                "Sign Out" -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        ) 
                    },
                    selected = false,
                    onClick = {
                        onCloseDrawer()
                        if (item == "Sign Out") {
                            onSignOut()
                        }
                    },
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = Color.Transparent
                    ),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}
