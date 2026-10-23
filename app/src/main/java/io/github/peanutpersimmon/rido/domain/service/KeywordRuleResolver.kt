package io.github.peanutpersimmon.rido.domain.service

import io.github.peanutpersimmon.rido.domain.model.KeywordRule

data class ResolvedClassification(
    val categoryId: Long?,
    val tagIds: Set<Long>,
)

object KeywordRuleResolver {
    fun findMatches(
        input: String,
        rules: Collection<KeywordRule>,
    ): List<KeywordRule> {
        val normalizedInput = CategoryKeywordNormalizer.normalize(input)
        return rules.filter { rule ->
            val keyword = CategoryKeywordNormalizer.normalize(rule.keyword)
            rule.isEnabled && keyword.isNotEmpty() && normalizedInput.contains(keyword)
        }
    }

    fun resolveInitialClassification(
        input: String,
        rules: Collection<KeywordRule>,
        categorySortOrderById: Map<Long, Int> = emptyMap(),
    ): ResolvedClassification = resolveMatchedRules(findMatches(input, rules), categorySortOrderById)

    fun resolveMatchedRules(
        matchedRules: Collection<KeywordRule>,
        categorySortOrderById: Map<Long, Int> = emptyMap(),
    ): ResolvedClassification {
        val enabledRules = matchedRules.filter(KeywordRule::isEnabled)
        val selectedCategory = enabledRules
            .asSequence()
            .filter { it.categoryId != null }
            .sortedWith(ruleComparator(categorySortOrderById))
            .firstOrNull()
            ?.categoryId

        val tagIds = enabledRules
            .flatMapTo(sortedSetOf()) { it.tagIds }

        return ResolvedClassification(
            categoryId = selectedCategory,
            tagIds = tagIds,
        )
    }

    /** Reserved for category_hint: only the single winning rule contributes category and tags. */
    fun resolveCategoryHint(
        input: String,
        rules: Collection<KeywordRule>,
        categorySortOrderById: Map<Long, Int> = emptyMap(),
    ): ResolvedClassification {
        val winner = findMatches(input, rules)
            .sortedWith(ruleComparator(categorySortOrderById))
            .firstOrNull()
        return ResolvedClassification(
            categoryId = winner?.categoryId,
            tagIds = winner?.tagIds.orEmpty(),
        )
    }

    private fun ruleComparator(categorySortOrderById: Map<Long, Int>) =
        compareByDescending<KeywordRule> { it.priority }
            .thenByDescending { CategoryKeywordNormalizer.normalize(it.keyword).length }
            .thenBy { rule -> rule.categoryId?.let(categorySortOrderById::get) ?: Int.MAX_VALUE }
            .thenBy { it.id }
}
