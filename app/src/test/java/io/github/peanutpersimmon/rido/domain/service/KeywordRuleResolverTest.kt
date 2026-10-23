package io.github.peanutpersimmon.rido.domain.service

import io.github.peanutpersimmon.rido.domain.model.KeywordRule
import org.junit.Assert.assertEquals
import org.junit.Test

class KeywordRuleResolverTest {
    @Test
    fun noRules_returnsEmptyInitialClassification() {
        val result = KeywordRuleResolver.resolveInitialClassification("Start making PPT", emptyList())

        assertEquals(null, result.categoryId)
        assertEquals(emptySet<Long>(), result.tagIds)
    }

    @Test
    fun containsMatch_isCaseInsensitiveAndTrimsKeyword() {
        val result = KeywordRuleResolver.resolveInitialClassification(
            input = "Start making ppt slides",
            rules = listOf(rule(id = 1, keyword = "  PPT  ", categoryId = 7, tagIds = setOf(9))),
        )

        assertEquals(7L, result.categoryId)
        assertEquals(setOf(9L), result.tagIds)
    }

    @Test
    fun disabledRule_doesNotProvideInitialClassification() {
        val disabled = rule(id = 1, categoryId = 7).copy(isEnabled = false)
        val result = KeywordRuleResolver.resolveInitialClassification("keyword", listOf(disabled))

        assertEquals(null, result.categoryId)
        assertEquals(emptySet<Long>(), result.tagIds)
    }

    @Test
    fun tags_areUnionedAndDeduplicated() {
        val result = KeywordRuleResolver.resolveMatchedRules(
            listOf(rule(id = 1, tagIds = setOf(1, 2)), rule(id = 2, tagIds = setOf(2, 3))),
        )

        assertEquals(setOf(1L, 2L, 3L), result.tagIds)
    }

    @Test
    fun higherPriorityCategoryWins() {
        val result = KeywordRuleResolver.resolveMatchedRules(
            listOf(
                rule(id = 1, keyword = "long keyword", categoryId = 1, priority = 1),
                rule(id = 2, keyword = "short", categoryId = 2, priority = 2),
            ),
        )

        assertEquals(2L, result.categoryId)
    }

    @Test
    fun longerKeywordWinsWhenPriorityMatches() {
        val result = KeywordRuleResolver.resolveMatchedRules(
            listOf(
                rule(id = 1, keyword = "meeting", categoryId = 1, priority = 2),
                rule(id = 2, keyword = "group meeting", categoryId = 2, priority = 2),
            ),
        )

        assertEquals(2L, result.categoryId)
    }

    @Test
    fun keywordLengthTieBreak_ignoresSurroundingWhitespace() {
        val result = KeywordRuleResolver.resolveMatchedRules(
            listOf(
                rule(id = 1, keyword = "  PPT  ", categoryId = 1, priority = 2),
                rule(id = 2, keyword = "meeting", categoryId = 2, priority = 2),
            ),
        )

        assertEquals(2L, result.categoryId)
    }

    @Test
    fun smallerIdWinsFinalTie() {
        val result = KeywordRuleResolver.resolveMatchedRules(
            listOf(
                rule(id = 8, keyword = "same", categoryId = 8, priority = 2),
                rule(id = 3, keyword = "same", categoryId = 3, priority = 2),
            ),
        )

        assertEquals(3L, result.categoryId)
    }

    @Test
    fun categorySortOrderWinsBeforeRuleId() {
        val result = KeywordRuleResolver.resolveMatchedRules(
            matchedRules = listOf(
                rule(id = 1, keyword = "same", categoryId = 10, priority = 2),
                rule(id = 2, keyword = "same", categoryId = 20, priority = 2),
            ),
            categorySortOrderById = mapOf(10L to 8, 20L to 1),
        )

        assertEquals(20L, result.categoryId)
    }

    @Test
    fun categoryHint_usesOnlyWinningRulesCategoryAndTags() {
        val result = KeywordRuleResolver.resolveCategoryHint(
            input = "group meeting",
            rules = listOf(
                rule(id = 1, keyword = "meeting", categoryId = 1, tagIds = setOf(10), priority = 1),
                rule(id = 2, keyword = "group meeting", categoryId = 2, tagIds = setOf(20), priority = 1),
            ),
        )

        assertEquals(2L, result.categoryId)
        assertEquals(setOf(20L), result.tagIds)
    }

    private fun rule(
        id: Long,
        keyword: String = "keyword",
        categoryId: Long? = null,
        tagIds: Set<Long> = emptySet(),
        priority: Int = 0,
    ) = KeywordRule(
        id = id,
        keyword = keyword,
        categoryId = categoryId,
        tagIds = tagIds,
        priority = priority,
        createdAtMillis = 1,
        updatedAtMillis = 1,
    )
}
