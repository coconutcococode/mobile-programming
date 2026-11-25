package com.bloom.feature.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.bloom.core.designsystem.component.BloomPrimaryButton
import com.bloom.core.designsystem.component.BloomSecondaryButton
import com.bloom.core.designsystem.component.BloomTopBar
import com.bloom.core.designsystem.component.ScreenStub
import com.bloom.core.designsystem.theme.BloomDimens
import com.bloom.core.designsystem.theme.BloomTheme

/** S-08-P1 위치 권한 안내 — 거절 또는 '대략적인 위치'만 허용했을 때(NFR-08). */
@Composable
fun LocationPermissionScreen(
    onBack: () -> Unit,
    onFavoritesOnly: () -> Unit,
) {
    val context = LocalContext.current
    ScreenStub(
        screenId = "S-08-P1",
        title = "위치 권한 필요",
        description = "길안내는 정확한 위치로 단계를 바꿔요. 아래 2가지를 켜 주세요.",
        onBack = onBack,
    ) {
        Text("✓ '앱 사용 중에만 허용'", style = MaterialTheme.typography.titleMedium)
        Text("✓ '정확한 위치' 켜기", style = MaterialTheme.typography.titleMedium)
        Text(
            "권한을 켠 뒤 돌아와서 다시 검색해 주세요.",
            style = MaterialTheme.typography.bodySmall,
            color = BloomTheme.colors.textSecondary,
        )
        BloomPrimaryButton("설정으로 이동", onClick = {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        })
        BloomSecondaryButton("즐겨찾기만 보기", onClick = onFavoritesOnly)
    }
}

/**
 * S-08-I1 데이터 출처 — 기관별 제공 데이터·쓰는 곳·이용 조건(NFR-12).
 * TODO: 이용 조건 문구는 공공데이터 이용 조건 확인 후 확정.
 */
@Composable
fun DataSourceScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = { BloomTopBar(title = "데이터 출처", onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = BloomDimens.ScreenHorizontal),
            verticalArrangement = Arrangement.spacedBy(BloomDimens.CardGap),
        ) {
            Text("Bloom 이 사용하는 기관별 데이터와 쓰는 곳", style = MaterialTheme.typography.bodyMedium, color = BloomTheme.colors.textSecondary)
            Source("서울특별시", "실시간 버스·지하철 도착 정보", "안내 진행(승차 전)")
            Source("소상공인시장진흥공단", "상가(상권)정보", "주변 추천(대체 데이터)")
            Source("카카오", "지도·장소 검색·대중교통 경로", "홈·주변·경로")
        }
    }
}

@Composable
private fun Source(org: String, data: String, usedIn: String) {
    Text(org, style = MaterialTheme.typography.titleMedium)
    Text("$data · 쓰는 곳: $usedIn", style = MaterialTheme.typography.bodySmall, color = BloomTheme.colors.textSecondary)
}
