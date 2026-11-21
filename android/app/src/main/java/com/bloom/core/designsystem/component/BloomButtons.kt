package com.bloom.core.designsystem.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bloom.core.designsystem.theme.BloomDimens

private val ButtonShape = RoundedCornerShape(BloomDimens.CardRadius)

/**
 * 주 버튼(네이비 채움). S-03·S-04 처럼 걸으며 누르는 버튼은 [large] = true (56dp), 그 외 52dp.
 */
@Composable
fun BloomPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    large: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = ButtonShape,
        modifier = modifier
            .widthIn(max = BloomDimens.PrimaryButtonMaxWidth)
            .fillMaxWidth()
            .heightIn(min = if (large) BloomDimens.PrimaryButtonHeight else BloomDimens.PrimaryButtonHeightCompact),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** 보조 버튼(테두리). */
@Composable
fun BloomSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = ButtonShape,
        modifier = modifier.heightIn(min = BloomDimens.MinTouchTarget),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** 위험 동작(삭제·종료 확인 등). */
@Composable
fun BloomDangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        ),
        modifier = modifier.heightIn(min = BloomDimens.MinTouchTarget),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** 글자 버튼 — 오탭 방지용 작은 동작('안내 종료' 등). */
@Composable
fun BloomTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(onClick = onClick, modifier = modifier.heightIn(min = BloomDimens.MinTouchTarget)) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}
