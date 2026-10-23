package io.github.peanutpersimmon.rido.ui.feature.tags.library

// Ported from MyLibrary/ui/settings/TagManagementScreen.kt; RIDO data/theme adapters live in LibraryTagAdapters.
internal fun deleteDialogTitle(
    tag: LibraryTag,
    usageCount: Int?,
    childCount: Int
): String = when {
    tag.parentId == null && childCount > 0 ->
        "「${tag.name}」下还有 $childCount 个二级标签。"
    tag.parentId != null && usageCount != null && usageCount > 0 ->
        "「${tag.name}」正在被 $usageCount 条记录使用。"
    else -> "确认删除「${tag.name}」？"
}

internal fun deleteDialogMessage(tag: LibraryTag): String =
    if (tag.parentId == null) "此一级标签及其二级标签将从可选列表移除，历史记录的关联保留。"
    else "此标签将从可选列表移除，历史记录的关联保留。"

