package io.github.peanutpersimmon.rido.ui.feature.categories

import io.github.peanutpersimmon.rido.domain.model.CategoryColorKey

internal data class CategoryColorDraft(
    val originalKey: String,
    val selectedKey: String = originalKey,
) {
    fun select(key: CategoryColorKey): CategoryColorDraft = copy(selectedKey = key.storageKey)
}
