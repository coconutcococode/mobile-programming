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

/**
 * 앱 최상위: 하단 탭 4개 + 탭별 스택(UI 설계서 2.1).
 * @param openGuideRequest 안내 알림을 누를 때마다 바뀌는 값. 바뀌면 안내 진행(S-04)으로 간다.
 */
@Composable
fun BloomApp(openGuideRequest: Int = 0) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination

    // 스택 화면(S-02 등)에 있어도 들어온 탭을 선택 상태로 유지한다.
    var currentTab by rememberSaveable { mutableStateOf(TopLevelTab.HOME) }

    // 탭 이동은 반드시 이 함수로 한다. 저장된 탭 스택이 하위 화면(S-02 등)으로 복원돼도 선택 탭이 맞게 바뀐다.
    val navigateToTab: (TopLevelTab) -> Unit = { tab ->
        currentTab = tab
        navController.navigateToTab(tab)
    }

    // 뒤로가기로 다른 탭의 루트 화면에 도착한 경우(예: 주변 → 홈)에도 선택 탭을 맞춘다.
    LaunchedEffect(destination) {
        TopLevelTab.entries
            .firstOrNull { tab -> destination?.hasRoute(tab.route::class) == true }
            ?.let { currentTab = it }
    }

    LaunchedEffect(openGuideRequest) {
        if (openGuideRequest > 0) navController.navigate(GuideRoute) { launchSingleTop = true }
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
                            onClick = { navigateToTab(tab) },
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
            onNavigateToTab = navigateToTab,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        )
    }
}

/** 탭 전환: 탭별 스택·스크롤 상태를 저장·복원한다. 화면 코드에서는 BloomApp 의 navigateToTab 을 쓴다. */
private fun NavController.navigateToTab(tab: TopLevelTab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
