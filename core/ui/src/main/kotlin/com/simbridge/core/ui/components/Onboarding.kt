package com.simbridge.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.simbridge.core.ui.theme.CodeTextStyle
import com.simbridge.core.ui.theme.Spacing

@Composable
fun StepIndicator(current: Int, total: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.semantics { contentDescription = "Step $current of $total" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(total) { index ->
            val active = index < current
            Box(
                Modifier
                    .height(6.dp)
                    .width(if (index == current - 1) 28.dp else 12.dp)
                    .background(
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape,
                    ),
            )
        }
    }
}

/**
 * Shared layout for every onboarding step: progress, icon, title, explanation,
 * optional body content and a pinned action area.
 */
@Composable
fun OnboardingScaffold(
    step: Int,
    totalSteps: Int,
    icon: ImageVector,
    title: String,
    description: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    primaryEnabled: Boolean = true,
    primaryLoading: Boolean = false,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen, vertical = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            StepIndicator(current = step, total = totalSteps)
            IconBadge(icon = icon, modifier = Modifier.padding(top = Spacing.lg))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content()
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screen, vertical = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            PrimaryButton(
                text = primaryLabel,
                onClick = onPrimary,
                enabled = primaryEnabled,
                loading = primaryLoading,
            )
            if (secondaryLabel != null && onSecondary != null) {
                SecondaryButton(text = secondaryLabel, onClick = onSecondary)
            }
        }
    }
}

/** Six-digit short authentication string shown identically on both phones. */
@Composable
fun SasCodeDisplay(code: String, modifier: Modifier = Modifier) {
    val spaced = if (code.length == 6) code.substring(0, 3) + " " + code.substring(3) else code
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Verification code ${code.toCharArray().joinToString(" ")}" },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(vertical = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Verification code", style = MaterialTheme.typography.labelLarge)
            Text(spaced, style = CodeTextStyle)
        }
    }
}
