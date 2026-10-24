package com.simbridge.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.simbridge.core.ui.theme.Spacing
import com.simbridge.core.ui.theme.StatusTone
import com.simbridge.core.ui.theme.colors

@Composable
fun StatusChip(
    text: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
) {
    val c = tone.colors()
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = c.container,
        contentColor = c.content,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(c.accent, CircleShape),
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun StatusBanner(
    title: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
    message: String? = null,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val c = tone.colors()
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.medium,
        color = c.container,
        contentColor = c.content,
    ) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, tint = c.accent)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (message != null) {
                    Text(message, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}
