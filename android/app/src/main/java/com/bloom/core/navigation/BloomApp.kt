package com.bloom.core.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

/** 앱 최상위: 하단 탭 4개 + 탭별 스택(UI 설계서 2.1). */
@Composable
fun BloomApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination

    // 스택 화면(S-02 등)에 있어도 들어온 탭을 선택 상태로 유지한다.
    var currentTab by rememberSaveable { mutableStateOf(TopLevelTab.HOME) }
    LaunchedEffect(destination) {
        TopLevelTab.entries
            .firstOrNull { tab -> destination?.hasRoute(tab.route::class) == true }
            ?.let { currentTab = it }
    }

    // S-04 안내 진행 중에는 하단 탭을 숨긴다.
    val showBottomBar = destination?.hasRoute(GuideRoute::class) != true

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0), // 상단 인셋은 각 화면이 처리(지도는 상태바 아래까지)
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopLevelTab.entries.forEach { tab ->
                        val selected = tab == currentTab
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateToTab(tab) },
                            icon = { Icon(if (selected) tab.selectedIcon else tab.unselectedIcon, contentDescription = null) },
                            label = { Text(tab.label, style = MaterialTheme.typography.labelMedium) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        BloomNavHost(
            navController = navController,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        )
    }
}

/** 탭 전환: 탭별 스택·스크롤 상태를 저장·복원한다. */
fun NavController.navigateToTab(tab: TopLevelTab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
