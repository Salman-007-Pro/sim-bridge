package com.simbridge.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.simbridge.core.ui.theme.LargeTouchTarget
import com.simbridge.core.ui.theme.Spacing

private val ButtonHeight = 52.dp

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ButtonHeight),
        shape = MaterialTheme.shapes.medium,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text(text)
        }
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ButtonHeight),
        shape = MaterialTheme.shapes.medium,
        colors = if (destructive) {
            ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        } else {
            ButtonDefaults.outlinedButtonColors()
        },
    ) {
        Text(text)
    }
}

/** Large round action used for answer / reject / hang up. */
@Composable
fun RoundActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    container: Color,
    modifier: Modifier = Modifier,
    content: Color = Color.White,
    size: Dp = LargeTouchTarget,
    enabled: Boolean = true,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(size),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = container, contentColor = content),
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(size * 0.4f))
        }
        Box(Modifier.size(Spacing.sm))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}
