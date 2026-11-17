package dev.jdtech.jellyfin.presentation.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.WideNavigationRailDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldState
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable

@Composable
fun FindroidNavigationScaffold(
    layoutType: NavigationSuiteType,
    state: NavigationSuiteScaffoldState,
    navigationItems: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    NavigationSuiteScaffold(
        navigationItems = navigationItems,
        navigationSuiteType = layoutType,
        state = state,
        containerColor = MaterialTheme.colorScheme.surface,
        navigationSuiteColors =
            NavigationSuiteDefaults.colors(
                shortNavigationBarContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                wideNavigationRailColors =
                    WideNavigationRailDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
            ),
        content = content,
    )
}
