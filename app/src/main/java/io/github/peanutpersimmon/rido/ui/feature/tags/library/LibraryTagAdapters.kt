package io.github.peanutpersimmon.rido.ui.feature.tags.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.peanutpersimmon.rido.ui.theme.AppTheme
import io.github.peanutpersimmon.rido.ui.components.AppBottomSheet
import io.github.peanutpersimmon.rido.ui.components.AppDialog
import io.github.peanutpersimmon.rido.ui.feature.editor.components.EditorIconAction
import io.github.peanutpersimmon.rido.ui.feature.editor.components.EditorTextAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close

// Compatibility adapter, not a second theme system or model.
internal typealias LibraryTag = io.github.peanutpersimmon.rido.domain.model.Tag
internal val LibraryTag.enabled get() = !isArchived
// Tag-sheet sizing comes from design tokens; library files read them through these adapters.
internal val CapsuleHeight @Composable get() = AppTheme.dimens.tagChipHeight
internal val TagRowHeight @Composable get() = AppTheme.dimens.tagRowHeight
internal val TagRowGap @Composable get() = AppTheme.dimens.tagRowGap
internal val TagSectionGap @Composable get() = AppTheme.dimens.tagSectionGap
internal val TagTouchHeight @Composable get() = AppTheme.dimens.minimumTouchTarget
internal val CapsuleHorizontalPadding = 12.dp
internal val CompactFieldHeight = 36.dp
internal val ScreenHorizontalPadding = 0.dp // RIDO sheet already supplies the page inset.
internal enum class SurfaceRole { CARD, DIALOG }
internal object LibraryShapes {
    val small @Composable get() = AppTheme.shapes.small
    val medium @Composable get() = AppTheme.shapes.medium
    val large @Composable get() = AppTheme.shapes.large
}
internal object LibraryAppearance {
    val colors @Composable get() = LibraryColors(MaterialTheme.colorScheme)
    val typography @Composable get() = LibraryType(AppTheme.type)
}
internal class LibraryColors(private val c: ColorScheme) {
    val textPrimary get() = c.onSurface
    val textSecondary get() = c.onSurfaceVariant
    val mutedText get() = c.onSurfaceVariant
    val accent get() = c.onSurface
    val onAccent get() = c.surface
    val subtleCard get() = c.surfaceContainer
    val border get() = c.outlineVariant
    val subtleBorder get() = c.surfaceContainerHigh
    val surfaces get() = this
    val card get() = c.surfaceContainerLow
}
internal class LibraryType(private val t: io.github.peanutpersimmon.rido.ui.theme.AppTypographyTokens) {
    val sectionTitle get() = t.sheetTitle
    val pageTitle get() = t.sheetTitle
    val metadata get() = t.caption
    val body get() = t.fieldValue
    val button get() = t.fieldLabel
    val input get() = t.editorInput
    val capsule get() = t.editorDescription
}
@Composable
internal fun Modifier.noRippleClickable(enabled: Boolean = true, onClick: () -> Unit): Modifier =
    clickable(remember { MutableInteractionSource() }, indication = null, enabled = enabled, onClick = onClick)

@Composable
internal fun AppThemeSurface(role: SurfaceRole, modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium, border: BorderStroke? = null,
    shadowElevation: Dp = 0.dp, tonalElevation: Dp = 0.dp, content: @Composable () -> Unit) {
    Surface(modifier, shape, color = if (role == SurfaceRole.CARD) MaterialTheme.colorScheme.surfaceContainerLow
        else MaterialTheme.colorScheme.surface, border = border, shadowElevation = 0.dp,
        tonalElevation = 0.dp, content = content)
}
@Composable
internal fun AppScreenContainer(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier) { content() }
}
@Composable
internal fun SimpleTopBar(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
        EditorIconAction(Icons.Outlined.Close, "关闭标签设置", onBack)
    }
}
@Composable
internal fun AppModalBottomSheet(onDismissRequest: () -> Unit, dragHandle: (@Composable () -> Unit)?,
    content: @Composable ColumnScope.() -> Unit) {
    AppBottomSheet(null, onDismissRequest, content = content)
}
@Composable
internal fun AppConfirmDialog(title: String, message: String, confirmText: String, dismissText: String,
    destructive: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AppDialog(title, onDismiss, actions = {
        EditorTextAction(dismissText, onDismiss)
        EditorTextAction(confirmText, onConfirm)
    }) { Text(message, style = AppTheme.type.editorDescription) }
}

