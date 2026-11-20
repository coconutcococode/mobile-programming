package com.bloom.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.bloom.core.model.Place
import com.bloom.feature.favorites.FavoritesScreen
import com.bloom.feature.guide.GuideScreen
import com.bloom.feature.home.HomeScreen
import com.bloom.feature.nearby.NearbyScreen
import com.bloom.feature.route.RouteCandidatesScreen
import com.bloom.feature.route.RouteDetailScreen
import com.bloom.feature.settings.DataSourceScreen
import com.bloom.feature.settings.LocationPermissionScreen
import com.bloom.feature.settings.SettingsScreen

/** 화면 연결은 이 파일 한 곳에서만 한다. 각 화면은 navController 를 모르고 콜백만 받는다. */
@Composable
fun BloomNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val toRouteCandidates: (Place) -> Unit = { place ->
        navController.navigate(RouteCandidatesRoute(place.id, place.name))
    }
    // '안내 시작' 시 S-02·S-03 은 스택에서 제거 → 스택은 항상 [홈, 안내]. S-04 뒤로가기 = 홈(안내는 계속).
    val toGuide: () -> Unit = {
        navController.navigate(GuideRoute) { popUpTo<HomeRoute>() }
    }

    NavHost(
        navController = navController,
        startDestination = HomeRoute,
        modifier = modifier,
    ) {
        composable<HomeRoute> {
            HomeScreen(
                onPlaceSelected = toRouteCandidates,
                onGuideBannerClick = toGuide,
                onLocationPermissionDenied = { navController.navigate(LocationPermissionRoute) },
                onLoginRequired = { navController.navigateToTab(TopLevelTab.SETTINGS) },
            )
        }
        composable<NearbyRoute> {
            NearbyScreen(
                onDirections = toRouteCandidates,
                onLoginClick = { navController.navigateToTab(TopLevelTab.SETTINGS) },
            )
        }
        composable<FavoritesRoute> {
            FavoritesScreen(
                onFavoriteClick = { favorite ->
                    navController.navigate(RouteCandidatesRoute(favorite.placeId, favorite.alias))
                },
                onLoginClick = { navController.navigateToTab(TopLevelTab.SETTINGS) },
                onSearchClick = { navController.navigateToTab(TopLevelTab.HOME) },
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(
                onDataSourceClick = { navController.navigate(DataSourceRoute) },
            )
        }
        composable<RouteCandidatesRoute> {
            RouteCandidatesScreen(
                onBack = { navController.popBackStack() },
                onCandidateClick = { candidate -> navController.navigate(RouteDetailRoute(candidate.id)) },
            )
        }
        composable<RouteDetailRoute> {
            RouteDetailScreen(
                onBack = { navController.popBackStack() },
                onGuideStarted = toGuide,
                onOpenActiveGuide = toGuide,
            )
        }
        composable<GuideRoute> {
            GuideScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable<LocationPermissionRoute> {
            LocationPermissionScreen(
                onBack = { navController.popBackStack() },
                onFavoritesOnly = { navController.navigateToTab(TopLevelTab.FAVORITES) },
            )
        }
        composable<DataSourceRoute> {
            DataSourceScreen(onBack = { navController.popBackStack() })
        }
    }
}
