package com.bloom.feature.favorites

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.bloom.core.designsystem.component.EmptyState
import com.bloom.core.designsystem.component.ScreenStub
import com.bloom.core.designsystem.theme.BloomTheme
import com.bloom.core.model.Favorite
import com.bloom.core.model.FavoriteLimits
import com.bloom.data.repository.AuthRepository
import com.bloom.data.repository.FavoriteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    authRepository: AuthRepository,
    favoriteRepository: FavoriteRepository,
) : ViewModel() {
    val isLoggedIn: StateFlow<Boolean> = authRepository.currentUser
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val favorites: StateFlow<List<Favorite>> = favoriteRepository.observeFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/**
 * S-07 즐겨찾기. 로그인 사용자 전용(비로그인 = S-07-6).
 * TODO(트랙 A): 별칭 수정(S-07-D1)·삭제 확인(S-07-D2), 동기화 상태·대기 배지(S-07-5), 재검색 결과 불일치(S-07-4), 로딩 스켈레톤.
 */
@Composable
fun FavoritesScreen(
    onFavoriteClick: (Favorite) -> Unit,
    onLoginClick: () -> Unit,
    onSearchClick: () -> Unit,
    viewModel: FavoritesViewModel = hiltViewModel(),
) {
    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()

    ScreenStub(
        screenId = "S-07",
        title = "즐겨찾기",
        description = if (isLoggedIn) "${favorites.size} / ${FavoriteLimits.MAX_COUNT} · 항목을 누르면 장소를 재검색해 경로 후보로 간다" else "",
    ) {
        when {
            !isLoggedIn -> EmptyState(
                title = "로그인이 필요해요",
                description = "즐겨찾기는 Google 계정에 저장돼서 기기를 바꿔도 그대로 남아요",
                icon = Icons.Outlined.Lock,
                actionLabel = "설정에서 로그인",
                onAction = onLoginClick,
            )
            favorites.isEmpty() -> EmptyState(
                title = "저장한 장소가 없어요",
                description = "장소에서 ☆를 누르면 여기에 모여요",
                icon = Icons.Outlined.StarOutline,
                actionLabel = "목적지 검색하기",
                onAction = onSearchClick,
            )
            else -> favorites.forEach { favorite ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onFavoriteClick(favorite) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(favorite.alias, style = MaterialTheme.typography.titleMedium)
                        if (favorite.searchKeyword.isNotBlank()) {
                            Text(favorite.searchKeyword, style = MaterialTheme.typography.bodySmall, color = BloomTheme.colors.textSecondary)
                        }
                    }
                }
            }
        }
    }
}
