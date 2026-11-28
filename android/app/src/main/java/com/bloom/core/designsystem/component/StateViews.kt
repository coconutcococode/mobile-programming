package com.bloom.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bloom.core.designsystem.theme.BloomDimens
import com.bloom.core.designsystem.theme.BloomTheme

/** 로딩 상태: 실제 레이아웃과 같은 자리에 회색 블록(스켈레톤)을 둬서 결과가 와도 화면이 튀지 않게 한다. */
@Composable
fun SkeletonBlock(modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(BloomTheme.colors.skeleton),
    )
}

@Composable
fun SkeletonList(
    modifier: Modifier = Modifier,
    rows: Int = 4,
    rowHeight: Dp = 88.dp,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(BloomDimens.CardGap)) {
        repeat(rows) { SkeletonBlock(Modifier.fillMaxWidth().height(rowHeight)) }
    }
}

/** 비어 있음 상태. */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector = Icons.Outlined.Info,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = BloomDimens.ScreenHorizontal),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (description != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = BloomTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            BloomSecondaryButton(actionLabel, onAction)
        }
    }
}

/** 오류 상태: 주황 테두리 + 경고 아이콘 + 원인 문구 + 다음 행동 버튼. */
@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    actionLabel: String = "다시 시도",
    onAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(BloomDimens.CardRadius))
            .background(BloomTheme.colors.warningBackground)
            .border(1.dp, BloomTheme.colors.warning, RoundedCornerShape(BloomDimens.CardRadius))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = BloomTheme.colors.warning, modifier = Modifier.size(32.dp))
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.titleMedium, color = BloomTheme.colors.textError, textAlign = TextAlign.Center)
        if (description != null) {
            Spacer(Modifier.height(4.dp))
            Text(description, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
        if (onAction != null || onSecondaryAction != null) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onAction != null) BloomPrimaryButton(actionLabel, onAction, modifier = Modifier.width(140.dp))
                if (secondaryActionLabel != null && onSecondaryAction != null) {
                    BloomSecondaryButton(secondaryActionLabel, onSecondaryAction)
                }
            }
        }
    }
}

enum class BannerTone { Info, Warning }

/** 화면 상단 배너: 부분 상태(오프라인·대체 데이터), 안내 중(S-01-B1), 경로 이탈(S-04-B3) 등. */
@Composable
fun NoticeBanner(
    message: String,
    modifier: Modifier = Modifier,
    tone: BannerTone = BannerTone.Info,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    // 값이 있으면 오른쪽 끝에 닫기(X) 버튼 표시
    onDismiss: (() -> Unit)? = null,
) {
    val isWarning = tone == BannerTone.Warning
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(BloomDimens.CardRadius))
            .background(if (isWarning) BloomTheme.colors.warningBackground else BloomTheme.colors.selectedBackground)
            .border(
                1.dp,
                if (isWarning) BloomTheme.colors.warning else MaterialTheme.colorScheme.outline,
                RoundedCornerShape(BloomDimens.CardRadius),
            )
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (isWarning) Icons.Outlined.WarningAmber else Icons.Outlined.Info,
            contentDescription = null,
            tint = if (isWarning) BloomTheme.colors.warning else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = if (isWarning) BloomTheme.colors.textError else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 10.dp),
        )
        if (actionLabel != null && onAction != null) BloomTextButton(actionLabel, onAction)
        if (onDismiss != null) {
            // IconButton 기본 터치 영역 48dp
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "닫기", modifier = Modifier.size(20.dp))
            }
        }
    }
}
