package io.github.peanutpersimmon.rido.domain.usecase

import io.github.peanutpersimmon.rido.domain.repository.RidoCoreRepository
import io.github.peanutpersimmon.rido.domain.service.KeywordRuleResolver
import io.github.peanutpersimmon.rido.domain.service.ResolvedClassification

data class InitialClassificationInput(
    val title: String,
    val explicitCategoryId: Long? = null,
    val categoryWasExplicitlySet: Boolean = false,
    val explicitTagIds: Set<Long> = emptySet(),
    val tagsWereExplicitlySet: Boolean = false,
    val applyKeywordRules: Boolean,
)

class InitialClassificationUseCase(private val repository: RidoCoreRepository) {
    suspend operator fun invoke(input: InitialClassificationInput): ResolvedClassification {
        val automatic = if (input.applyKeywordRules) {
            val categorySortOrderById = repository.getCategories()
                .filterNot { it.isArchived }
                .associate { it.id to it.sortOrder }
            val activeTagIds = repository.getTags().asSequence()
                .filterNot { it.isArchived }.map { it.id }.toSet()
            KeywordRuleResolver.resolveInitialClassification(
                input.title,
                repository.getEnabledKeywordRules(),
                categorySortOrderById,
            ).let { it.copy(tagIds = it.tagIds.intersect(activeTagIds)) }
        } else {
            ResolvedClassification(categoryId = null, tagIds = emptySet())
        }
        return ResolvedClassification(
            categoryId = if (input.categoryWasExplicitlySet) {
                input.explicitCategoryId
            } else {
                automatic.categoryId
            },
            tagIds = if (input.tagsWereExplicitlySet) input.explicitTagIds else automatic.tagIds,
        )
    }
}

