package com.executiveos.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.executiveos.R
import com.executiveos.core.designsystem.theme.Spacing

@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    @DrawableRes iconRes: Int? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (iconRes != null) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
    message: String = stringResource(R.string.loading_default),
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        CircularProgressIndicator()
        Text(text = message, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_error),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        if (onRetry != null) {
            OutlinedButton(onClick = onRetry) {
                Text(stringResource(R.string.action_retry))
            }
        }
    }
}
