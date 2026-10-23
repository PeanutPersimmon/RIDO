package io.github.peanutpersimmon.rido.ui.feature.categories

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.peanutpersimmon.rido.domain.model.Category
import io.github.peanutpersimmon.rido.domain.model.CategoryColorKey
import io.github.peanutpersimmon.rido.domain.model.KeywordRule
import io.github.peanutpersimmon.rido.domain.service.CategoryKeywordNormalizer
import io.github.peanutpersimmon.rido.domain.service.ReservedKeywordPolicy
import io.github.peanutpersimmon.rido.ui.components.AppBottomSheet
import io.github.peanutpersimmon.rido.ui.components.AppDialog
import io.github.peanutpersimmon.rido.ui.components.CompactChip
import io.github.peanutpersimmon.rido.ui.components.CompactChipFlow
import io.github.peanutpersimmon.rido.ui.components.CurrentRootItemRow
import io.github.peanutpersimmon.rido.ui.components.InlineNameEditor
import io.github.peanutpersimmon.rido.ui.components.MetadataAction
import io.github.peanutpersimmon.rido.ui.components.MetadataActionDialog
import io.github.peanutpersimmon.rido.ui.components.RootChipRow
import io.github.peanutpersimmon.rido.ui.feature.editor.components.EditorIconAction
import io.github.peanutpersimmon.rido.ui.feature.editor.components.EditorTextAction
import io.github.peanutpersimmon.rido.ui.feature.settings.LocalMetadataState
import io.github.peanutpersimmon.rido.ui.feature.settings.LocalMetadataViewModel
import io.github.peanutpersimmon.rido.ui.theme.AppTheme
import io.github.peanutpersimmon.rido.ui.theme.resolveCategoryColors

@Composable
fun CategoryManagementSheet(onDismiss: () -> Unit) {
    val model = LocalMetadataViewModel.current ?: return
    val state = LocalMetadataState.current
    val focusManager = LocalFocusManager.current
    val active = state.categories.filterNot(Category::isArchived)
    val archived = state.categories.filter(Category::isArchived)
    var selectedId by remember { mutableStateOf<Long?>(null) }
    val selected = active.firstOrNull { it.id == selectedId } ?: active.firstOrNull()
    var addingCategory by remember { mutableStateOf(false) }
    var newColorDraft by remember { mutableStateOf(CategoryColorDraft(CategoryColorKey.NEUTRAL.storageKey)) }
    var renamingCategoryId by remember { mutableStateOf<Long?>(null) }
    var renamingArchivedId by remember { mutableStateOf<Long?>(null) }
    var addingKeyword by remember { mutableStateOf(false) }
    var renamingKeywordId by remember { mutableStateOf<Long?>(null) }
    var colorEditingId by remember { mutableStateOf<Long?>(null) }
    var colorDraft by remember { mutableStateOf(CategoryColorDraft(CategoryColorKey.NEUTRAL.storageKey)) }
    var keywordAction by remember { mutableStateOf<KeywordRule?>(null) }
    var archiveConfirmId by remember { mutableStateOf<Long?>(null) }
    var deletingKeyword by remember { mutableStateOf<Pair<Long, KeywordRule>?>(null) }
    val inputOpen = addingCategory || renamingCategoryId != null || renamingArchivedId != null ||
        addingKeyword || renamingKeywordId != null

    fun cancelInput() {
        addingCategory = false
        renamingCategoryId = null
        renamingArchivedId = null
        addingKeyword = false
        renamingKeywordId = null
        colorEditingId = null
        focusManager.clearFocus()
        model.clearError()
    }
    fun closeStep() {
        when {
            inputOpen -> cancelInput()
            colorEditingId != null -> colorEditingId = null
            else -> onDismiss()
        }
    }
    BackHandler(inputOpen || colorEditingId != null) { closeStep() }
    LaunchedEffect(selected?.id, renamingKeywordId, state.categoryKeywords) {
        if (renamingKeywordId != null && selected?.let { category ->
                state.categoryKeywords[category.id].orEmpty().none { it.id == renamingKeywordId }
            } != false) cancelInput()
        if (selected == null && addingKeyword) cancelInput()
    }

    AppBottomSheet(
        title = "分类与关键词",
        bottomVisual = when {
            archived.isNotEmpty() -> io.github.peanutpersimmon.rido.ui.components.SheetBottomVisual.BodyTextRow
            selected == null -> io.github.peanutpersimmon.rido.ui.components.SheetBottomVisual.Surface
            else -> io.github.peanutpersimmon.rido.ui.components.SheetBottomVisual.ChipSlot
        },
        onDismissRequest = ::closeStep,
        headerAction = { EditorIconAction(Icons.Outlined.Close, "关闭分类与关键词", ::closeStep, compactSheetAction = true) },
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.spaceXs),
        ) {
            RootChipRow(
                items = active,
                id = Category::id,
                name = Category::name,
                selectedId = selected?.id,
                adding = addingCategory,
                renamingId = renamingCategoryId,
                errorMessage = state.error,
                onSelect = { cancelInput(); selectedId = it },
                onAdd = {
                    cancelInput()
                    newColorDraft = CategoryColorDraft(CategoryColorKey.NEUTRAL.storageKey)
                    addingCategory = true
                },
                onCreate = { name ->
                    model.createCategory(name, newColorDraft.selectedKey, emptyList()) { id ->
                        selectedId = id
                        addingCategory = false
                        focusManager.clearFocus()
                    }
                },
                onRename = { id, name ->
                    active.firstOrNull { it.id == id }?.let { category ->
                        model.updateCategory(id, name, category.colorKey, category.sortOrder) {
                            renamingCategoryId = null
                            focusManager.clearFocus()
                        }
                    }
                },
                onCancelInput = ::cancelInput,
                onDraftChange = model::clearError,
                validateNew = { draft ->
                    when {
                        active.any { it.name.equals(draft, ignoreCase = true) } -> "已有同名分类"
                        archived.any { it.name.equals(draft, ignoreCase = true) } -> "存在同名已归档分类，请恢复该分类"
                        else -> null
                    }
                },
                validateRename = { category, draft ->
                    if (active.any { it.id != category.id && it.name.equals(draft, ignoreCase = true) })
                        "已有同名分类" else null
                },
                onReorder = model::reorderCategories,
            )

            if (addingCategory) {
                Text("颜色", style = AppTheme.type.fieldLabel)
                CategoryColorPalette(newColorDraft.selectedKey) { newColorDraft = newColorDraft.select(it) }
            }

            if (selected == null) {
                Text("暂无分类", style = AppTheme.type.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val category = selected
                val keywords = state.categoryKeywords[category.id].orEmpty()
                CurrentRootItemRow(
                    name = category.name,
                    detail = "关键词 ${keywords.size} 个",
                    leading = { CategorySwatch(category.colorKey) },
                    onRename = {
                        cancelInput()
                        renamingCategoryId = category.id
                    },
                    onColor = {
                        if (colorEditingId == category.id) colorEditingId = null
                        else {
                            cancelInput()
                            colorDraft = CategoryColorDraft(category.colorKey)
                            colorEditingId = category.id
                        }
                    },
                    actionIcon = Icons.Outlined.Archive,
                    actionDescription = "归档",
                    onAction = {
                        cancelInput()
                        archiveConfirmId = category.id
                    },
                )
                if (colorEditingId == category.id) {
                    Text("颜色", style = AppTheme.type.fieldLabel)
                    CategoryColorPalette(colorDraft.selectedKey) { colorDraft = colorDraft.select(it) }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.spaceS)) {
                        EditorTextAction("取消", { colorEditingId = null }, compactSheetAction = true)
                        EditorTextAction("保存颜色", {
                            model.updateCategory(category.id, category.name, colorDraft.selectedKey, category.sortOrder) {
                                colorEditingId = null
                            }
                        }, compactSheetAction = true)
                    }
                }
                CompactChipFlow {
                    keywords.forEach { rule ->
                        if (renamingKeywordId == rule.id) {
                            key(rule.id) {
                                InlineNameEditor(
                                    initialValue = rule.keyword,
                                    placeholder = "关键词",
                                    onSubmit = { keyword ->
                                        model.updateCategoryKeyword(category.id, rule.id, keyword, rule.priority) {
                                            renamingKeywordId = null
                                            focusManager.clearFocus()
                                        }
                                    },
                                    onCancel = ::cancelInput,
                                    normalize = CategoryKeywordNormalizer::normalize,
                                    onDraftChange = model::clearError,
                                    validate = { keyword -> keywordError(keyword, keywords, rule.id) },
                                    errorMessage = state.error,
                                )
                            }
                        } else CompactChip(rule.keyword, { cancelInput(); keywordAction = rule })
                    }
                    if (addingKeyword) {
                        key("add-${category.id}") {
                            InlineNameEditor(
                                initialValue = "",
                                placeholder = "输入关键词",
                                onSubmit = { keyword ->
                                    model.addCategoryKeyword(category.id, keyword) {
                                        addingKeyword = false
                                        focusManager.clearFocus()
                                    }
                                },
                                onCancel = ::cancelInput,
                                normalize = CategoryKeywordNormalizer::normalize,
                                onDraftChange = model::clearError,
                                validate = { keyword -> keywordError(keyword, keywords) },
                                errorMessage = state.error,
                            )
                        }
                    } else CompactChip("+", { cancelInput(); addingKeyword = true })
                }
            }

            ArchivedCategorySection(archived, ::cancelInput) { category ->
                    CurrentRootItemRow(
                        name = category.name,
                        detail = "已归档",
                        leading = { CategorySwatch(category.colorKey) },
                        onRename = {
                            cancelInput()
                            renamingArchivedId = category.id
                        },
                        onColor = {
                            if (colorEditingId == category.id) colorEditingId = null
                            else {
                                cancelInput()
                                colorDraft = CategoryColorDraft(category.colorKey)
                                colorEditingId = category.id
                            }
                        },
                        actionIcon = Icons.Outlined.Restore,
                        actionDescription = "恢复",
                        onAction = { model.restoreCategory(category.id) },
                    )
                    if (renamingArchivedId == category.id) {
                        key(category.id) {
                            InlineNameEditor(
                                initialValue = category.name,
                                placeholder = "分类名称",
                                onSubmit = { name ->
                                    model.updateCategory(category.id, name, category.colorKey, category.sortOrder) {
                                        renamingArchivedId = null
                                        focusManager.clearFocus()
                                    }
                                },
                                onCancel = ::cancelInput,
                                onDraftChange = model::clearError,
                                errorMessage = state.error,
                                primary = true,
                            )
                        }
                    }
                    if (colorEditingId == category.id) {
                        CategoryColorPalette(colorDraft.selectedKey) { colorDraft = colorDraft.select(it) }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.spaceS)) {
                            EditorTextAction("取消", { colorEditingId = null }, compactSheetAction = true)
                            EditorTextAction("保存颜色", {
                                model.updateCategory(category.id, category.name, colorDraft.selectedKey, category.sortOrder) {
                                    colorEditingId = null
                                }
                            }, compactSheetAction = true)
                        }
                    }
            }
            state.error?.let { InlineError(it) }
            state.message?.let { InlineInfo(it) }
        }
    }

    keywordAction?.let { rule ->
        MetadataActionDialog(rule.keyword, listOf(
            MetadataAction("重命名") {
                keywordAction = null
                cancelInput()
                renamingKeywordId = rule.id
            },
            MetadataAction("删除关键词规则") {
                keywordAction = null
                selected?.let { deletingKeyword = it.id to rule }
            },
        )) { keywordAction = null }
    }
    state.categories.firstOrNull { it.id == archiveConfirmId }?.let { category ->
        AppDialog("归档「${category.name}」？", { archiveConfirmId = null }, actions = {
            EditorTextAction("取消", { archiveConfirmId = null })
            EditorTextAction("归档", {
                model.archiveCategory(category.id) {
                    selectedId = null
                }
                archiveConfirmId = null
            })
        }) { Text("归档后不再参与自动分类；关键词规则保留，可在已归档列表恢复。",
            style = AppTheme.type.editorDescription) }
    }
    deletingKeyword?.let { (categoryId, rule) ->
        AppDialog("删除关键词规则「${rule.keyword}」？", { deletingKeyword = null }, actions = {
            EditorTextAction("取消", { deletingKeyword = null })
            EditorTextAction("删除", {
                model.deleteCategoryKeyword(categoryId, rule.id)
                deletingKeyword = null
            })
        }) { Text("删除后该关键词规则不再用于自动分类。",
            style = AppTheme.type.editorDescription) }
    }
}

@Composable
internal fun ArchivedCategorySection(
    archived: List<Category>,
    onCollapse: () -> Unit,
    content: @Composable (Category) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    if (archived.isEmpty()) return
    Text("已归档分类 ${archived.size}  ${if (expanded) "⌄" else "›"}",
        Modifier.fillMaxWidth().clickable {
            if (expanded) onCollapse()
            expanded = !expanded
        }.semantics { stateDescription = if (expanded) "已展开" else "已收起" }
            .padding(top = AppTheme.dimens.spaceS + AppTheme.dimens.spaceXs / 2,
                bottom = AppTheme.dimens.spaceS - AppTheme.dimens.spaceXs / 2),
        style = AppTheme.type.fieldLabel,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (expanded) archived.forEach { content(it) }
}

private fun keywordError(keyword: String, rules: List<KeywordRule>, excludingId: Long? = null): String? = when {
    ReservedKeywordPolicy.isReserved(keyword) -> "该关键词由睡眠体系处理"
    rules.any { it.id != excludingId && CategoryKeywordNormalizer.normalize(it.keyword) ==
        CategoryKeywordNormalizer.normalize(keyword) } -> "关键词已存在"
    else -> null
}

@Composable
private fun CategorySwatch(colorKey: String) {
    val colors = resolveCategoryColors(colorKey, MaterialTheme.colorScheme)
    Surface(Modifier.size(AppTheme.dimens.icon), shape = AppTheme.shapes.pill,
        color = colors.container, border = BorderStroke(AppTheme.dimens.border, colors.border)) {}
}

@Composable
private fun InlineError(message: String) {
    Text(message, Modifier.padding(vertical = AppTheme.dimens.spaceXs),
        style = AppTheme.type.caption, color = MaterialTheme.colorScheme.error)
}

@Composable
private fun InlineInfo(message: String) {
    Text(message, Modifier.padding(vertical = AppTheme.dimens.spaceXs),
        style = AppTheme.type.caption, color = MaterialTheme.colorScheme.primary)
}

