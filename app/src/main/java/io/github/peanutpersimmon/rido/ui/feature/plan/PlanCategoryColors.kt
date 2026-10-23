package io.github.peanutpersimmon.rido.ui.feature.plan

import androidx.compose.material3.ColorScheme
import io.github.peanutpersimmon.rido.domain.model.CategoryColorKey
import io.github.peanutpersimmon.rido.ui.theme.CategoryColors
import io.github.peanutpersimmon.rido.ui.theme.resolveCategoryColors

/** A missing category stays absent; the stored neutral preset remains a real category color. */
internal fun planCategoryColors(
    categoryId: Long?,
    categoryColorKeys: Map<Long, String>,
    colorScheme: ColorScheme,
): CategoryColors? {
    val key = categoryId?.let(categoryColorKeys::get) ?: return null
    if (CategoryColorKey.fromStorage(key) == null) return null
    return resolveCategoryColors(key, colorScheme)
}
