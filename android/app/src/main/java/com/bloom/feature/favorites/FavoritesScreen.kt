package com.bloom.feature.favorites

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.bloom.core.designsystem.UiState
import com.bloom.core.designsystem.component.BannerTone
import com.bloom.core.designsystem.component.BloomTopBar
import com.bloom.core.designsystem.component.EmptyState
import com.bloom.core.designsystem.component.NoticeBanner
import com.bloom.core.designsystem.component.SkeletonList
import com.bloom.core.designsystem.theme.BloomDimens
import com.bloom.core.designsystem.theme.BloomTheme
import com.bloom.core.model.Favorite
import com.bloom.core.model.FavoriteLimits
import com.bloom.core.model.SyncState
import com.bloom.core.network.NetworkMonitor
import com.bloom.data.repository.AuthRepository
import com.bloom.data.repository.FavoriteRepository
import com.bloom.feature.place.MAX_ALIAS_LENGTH
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 수정, 삭제 실패 안내. [retry] 는 실패한 작업을 다시 실행 */
data class FavoriteActionError(val message: String, val retry: () -> Unit)

@HiltViewModel
class FavoritesViewModel internal constructor(
    authRepository: AuthRepository,
    private val favoriteRepository: FavoriteRepository,
    isOnlineFlow: Flow<Boolean>,
) : ViewModel() {

    /** Hilt 가 쓰는 생성자. NetworkMonitor 는 안드로이드 Context 가 필요해서 단위 테스트는 연결 상태 Flow 를 바로 받는 생성자를 씀 */
    @Inject constructor(
        authRepository: AuthRepository,
        favoriteRepository: FavoriteRepository,
        networkMonitor: NetworkMonitor,
    ) : this(authRepository, favoriteRepository, networkMonitor.isOnline)

    val isLoggedIn: StateFlow<Boolean> = authRepository.currentUser
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** S-07-1~3: 첫 목록을 받기 전에는 불러오는 중(S-07-2), 0건이면 비어 있음 */
    val favorites: StateFlow<UiState<List<Favorite>>> = favoriteRepository.observeFavorites()
        .map { if (it.isEmpty()) UiState.Empty else UiState.Success(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    /** S-07-5 부분 상태: 오프라인이면 동기화 대기 배너, 항목을 눌러도 경로 검색 대신 안내 */
    val isOnline: StateFlow<Boolean> = isOnlineFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** 별칭 수정 창(S-07-D1)을 연 항목. 화면 회전 후에도 창 유지 */
    private val _editing = MutableStateFlow<Favorite?>(null)
    val editing: StateFlow<Favorite?> = _editing.asStateFlow()

    /** 삭제 확인 창(S-07-D2)을 연 항목 */
    private val _deleting = MutableStateFlow<Favorite?>(null)
    val deleting: StateFlow<Favorite?> = _deleting.asStateFlow()

    private val _actionError = MutableStateFlow<FavoriteActionError?>(null)
    val actionError: StateFlow<FavoriteActionError?> = _actionError.asStateFlow()

    fun startEdit(favorite: Favorite) {
        _editing.value = favorite
    }

    fun cancelEdit() {
        _editing.value = null
    }

    /** 앞뒤 공백 제거 후 저장. 빈 별칭은 창에서 막음 */
    fun rename(favorite: Favorite, alias: String) {
        _editing.value = null
        viewModelScope.launch {
            _actionError.value = null
            favoriteRepository.rename(favorite.id, alias.trim()).onFailure {
                _actionError.value = FavoriteActionError("별칭을 바꾸지 못했어요") { rename(favorite, alias) }
            }
        }
    }

    fun startDelete(favorite: Favorite) {
        _deleting.value = favorite
    }

    fun cancelDelete() {
        _deleting.value = null
    }

    fun delete(favorite: Favorite) {
        _deleting.value = null
        viewModelScope.launch {
            _actionError.value = null
            favoriteRepository.delete(favorite.id).onFailure {
                _actionError.value = FavoriteActionError("삭제하지 못했어요") { delete(favorite) }
            }
        }
    }

    fun dismissError() {
        _actionError.value = null
    }
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
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val editing by viewModel.editing.collectAsStateWithLifecycle()
    val deleting by viewModel.deleting.collectAsStateWithLifecycle()
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { BloomTopBar(title = "즐겨찾기") },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = BloomDimens.ScreenHorizontal),
            verticalArrangement = Arrangement.spacedBy(BloomDimens.CardGap),
        ) {
            val list = (favorites as? UiState.Success)?.data.orEmpty()
            when {
                !isLoggedIn -> EmptyState(
                    title = "로그인이 필요해요",
                    description = "즐겨찾기는 로그인한 계정에 저장돼요. 휴대폰을 바꿔도 그대로 남아요.",
                    icon = Icons.Outlined.Lock,
                    actionLabel = "설정에서 로그인",
                    onAction = onLoginClick,
                )
                favorites is UiState.Loading -> {
                    Text("Google 계정에서 불러오는 중…", style = MaterialTheme.typography.bodyMedium, color = BloomTheme.colors.textSecondary)
                    SkeletonList(rows = 4, rowHeight = 56.dp)
                }
                else -> {
                    Text(
                        "${list.size} / ${FavoriteLimits.MAX_COUNT}개",
                        style = MaterialTheme.typography.bodySmall,
                        color = BloomTheme.colors.textSecondary,
                    )
                    if (!isOnline) {
                        val pending = list.count { it.syncState == SyncState.PENDING }
                        NoticeBanner(
                            if (pending > 0) "오프라인 · 동기화 대기 ${pending}건" else "오프라인 · 경로 검색은 온라인에서 가능해요",
                            tone = BannerTone.Warning,
                        )
                    }
                    actionError?.let { error ->
                        NoticeBanner(
                            "${error.message}. 다시 시도해 주세요",
                            tone = BannerTone.Warning,
                            actionLabel = "다시 시도",
                            onAction = error.retry,
                            onDismiss = viewModel::dismissError,
                        )
                    }
                    if (list.isEmpty()) {
                        EmptyState(
                            title = "저장한 장소가 없어요",
                            description = "장소 상세나 주변 장소에서 ☆를 누르면 여기에 모여요.",
                            icon = Icons.Outlined.StarOutline,
                            actionLabel = "목적지 검색하기",
                            onAction = onSearchClick,
                        )
                    } else {
                        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                            items(list, key = { it.id }) { favorite ->
                                FavoriteRow(
                                    favorite = favorite,
                                    // 오프라인에서는 재검색, 경로 검색을 할 수 없어 이동 대신 안내(NFR-05 표 4-3)
                                    onClick = {
                                        if (isOnline) onFavoriteClick(favorite)
                                        else scope.launch { snackbar.showSnackbar("경로 검색은 온라인에서 가능해요") }
                                    },
                                    onEdit = { viewModel.startEdit(favorite) },
                                    onDelete = { viewModel.startDelete(favorite) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    editing?.let { favorite ->
        EditAliasDialog(
            initialAlias = favorite.alias,
            onSave = { viewModel.rename(favorite, it) },
            onDismiss = viewModel::cancelEdit,
        )
    }
    deleting?.let { favorite ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text("즐겨찾기 삭제") },
            text = { Text("‘${favorite.alias}’ 즐겨찾기를 지울까요?") },
            confirmButton = { TextButton(onClick = { viewModel.delete(favorite) }) { Text("삭제") } },
            dismissButton = { TextButton(onClick = viewModel::cancelDelete) { Text("취소") } },
        )
    }
}

/** 항목 한 줄: 별칭, 검색어, 동기화 대기 배지, 수정 · 삭제(각 48dp) */
@Composable
private fun FavoriteRow(favorite: Favorite, onClick: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(favorite.alias, style = MaterialTheme.typography.titleMedium)
            if (favorite.searchKeyword.isNotBlank()) {
                Text("검색어: ${favorite.searchKeyword}", style = MaterialTheme.typography.bodySmall, color = BloomTheme.colors.textSecondary)
            }
        }
        if (favorite.syncState == SyncState.PENDING) {
            Surface(shape = RoundedCornerShape(BloomDimens.ChipRadius), color = MaterialTheme.colorScheme.secondaryContainer) {
                Text("동기화 대기", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }
        // IconButton 기본 터치 영역 48dp. TalkBack 이 어느 항목의 버튼인지 읽도록 별칭 포함
        IconButton(onClick = onEdit) {
            Icon(Icons.Outlined.Edit, contentDescription = "${favorite.alias} 별칭 수정")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.Delete, contentDescription = "${favorite.alias} 삭제")
        }
    }
}

/**
 * S-07-D1 별칭 수정. 바뀐 내용이 있는데 닫으면 '저장하지 않고 나갈까요?' 확인, 바뀐 내용이 없으면 바로 닫힘.
 * 빈 별칭과 같은 별칭은 저장 버튼 비활성
 */
@Composable
private fun EditAliasDialog(initialAlias: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    // rememberSaveable: 화면을 돌려도 입력 중인 글자, 확인 창 상태 유지. TextFieldValue: 커서를 기존 별칭 끝에 두기 위해 사용
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialAlias, selection = TextRange(initialAlias.length)))
    }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    val alias = field.text
    val changed = alias.trim() != initialAlias
    val close = { if (changed) confirmLeave = true else onDismiss() }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("저장하지 않고 나갈까요?") },
            text = { Text("수정한 별칭은 저장되지 않아요.") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("나가기") } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("계속 수정") } },
        )
        return
    }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("별칭 수정") },
        text = {
            OutlinedTextField(
                value = field,
                onValueChange = { if (it.text.length <= MAX_ALIAS_LENGTH) field = it },
                singleLine = true,
                supportingText = { Text("${alias.length} / $MAX_ALIAS_LENGTH") },
            )
        },
        confirmButton = { TextButton(onClick = { onSave(alias.trim()) }, enabled = alias.isNotBlank() && changed) { Text("저장") } },
        dismissButton = { TextButton(onClick = close) { Text("취소") } },
    )
}
