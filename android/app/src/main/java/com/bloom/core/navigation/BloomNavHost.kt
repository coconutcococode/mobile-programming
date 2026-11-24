package com.bloom.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.bloom.core.model.Favorite
import com.bloom.core.model.Place
import com.bloom.feature.favorites.FavoritesScreen
import com.bloom.feature.guide.GuideScreen
import com.bloom.feature.home.HomeScreen
import com.bloom.feature.nearby.NearbyScreen
import com.bloom.feature.route.RouteCandidatesScreen
import com.bloom.feature.route.RouteDetailScreen
import com.bloom.feature.search.SearchScreen
import com.bloom.feature.settings.DataSourceScreen
import com.bloom.feature.settings.LocationPermissionScreen
import com.bloom.feature.settings.SettingsScreen

/** 화면 연결은 이 파일 한 곳에서만 한다. 각 화면은 navController 를 모르고 콜백만 받는다. */
@Composable
fun BloomNavHost(
    navController: NavHostController,
    onNavigateToTab: (TopLevelTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 지도 핀, 주변 목록(S-09 길찾기)에서 올 때는 사용자가 입력한 검색어가 없다
    val toRouteCandidates: (Place) -> Unit = { place ->
        navController.navigate(RouteCandidatesRoute(place.id, place.name, searchQuery = null))
    }
    // 즐겨찾기는 저장된 placeId 로 바로 넘긴다(장소 조회는 경로 후보 화면이 맡음).
    // 검색 없이 저장한 즐겨찾기(지도 핀 · 주변)는 검색어가 빈 값이라 '검색어 없음(null)'으로 넘긴다
    val toFavoriteRouteCandidates: (Favorite) -> Unit = { favorite ->
        navController.navigate(RouteCandidatesRoute(favorite.placeId, favorite.alias, searchQuery = favorite.searchKeyword.ifBlank { null }))
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
                onSearchClick = { navController.navigate(SearchRoute) },
                onPlaceSelected = toRouteCandidates,
                onFavoriteClick = toFavoriteRouteCandidates,
                // 최근 목적지는 장소 이름을 저장하지 않으므로 표시 이름도 검색어를 쓴다
                onRecentClick = { recent ->
                    navController.navigate(RouteCandidatesRoute(recent.placeId, recent.query, searchQuery = recent.query))
                },
                onGuideBannerClick = toGuide,
                onLocationPermissionDenied = { navController.navigate(LocationPermissionRoute) },
                onLoginRequired = { onNavigateToTab(TopLevelTab.SETTINGS) },
            )
        }
        composable<SearchRoute> {
            SearchScreen(
                onBack = { navController.popBackStack() },
                // 검색 화면은 스택에서 빼고 S-02 로 간다 → S-02 에서 뒤로가기 = 홈
                onDestinationChosen = { placeId, displayName, typedQuery ->
                    navController.navigate(RouteCandidatesRoute(placeId, displayName, searchQuery = typedQuery)) {
                        popUpTo<SearchRoute> { inclusive = true }
                    }
                },
                onLocationPermissionDenied = { navController.navigate(LocationPermissionRoute) },
            )
        }
        composable<NearbyRoute> {
            NearbyScreen(
                onDirections = toRouteCandidates,
                onLoginClick = { onNavigateToTab(TopLevelTab.SETTINGS) },
            )
        }
        composable<FavoritesRoute> {
            FavoritesScreen(
                onFavoriteClick = toFavoriteRouteCandidates,
                onLoginClick = { onNavigateToTab(TopLevelTab.SETTINGS) },
                onSearchClick = { onNavigateToTab(TopLevelTab.HOME) },
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
                onFavoritesOnly = { onNavigateToTab(TopLevelTab.FAVORITES) },
            )
        }
        composable<DataSourceRoute> {
            DataSourceScreen(onBack = { navController.popBackStack() })
        }
    }
}
