package io.github.peanutpersimmon.rido.ui.feature.tags

import io.github.peanutpersimmon.rido.domain.model.Tag
import org.junit.Assert.*
import org.junit.Test

class TagSearchPolicyTest {
    private val tags = listOf(
        Tag(1, "Work", createdAtMillis = 0, updatedAtMillis = 0),
        Tag(2, "Life", createdAtMillis = 0, updatedAtMillis = 0),
        Tag(3, "Research", createdAtMillis = 0, updatedAtMillis = 0, parentId = 1),
        Tag(4, "Research trip", createdAtMillis = 0, updatedAtMillis = 0, parentId = 2),
    )
    @Test fun searchStaysWithinRoot() {
        assertEquals(listOf(3L), contextualTagSearch(tags, 1, "research", true).visibleTags.map { it.id })
    }
    @Test fun exactRootNameOnlyReturnsRoot() {
        assertEquals(listOf(1L), contextualTagSearch(tags, 1, " WORK ", true).visibleTags.map { it.id })
    }
    @Test fun emptyQueryIncludesRootAndChildrenLikeMyLibrary() {
        assertEquals(listOf(1L, 3L), contextualTagSearch(tags, 1, "", true).visibleTags.map { it.id })
    }
    @Test fun duplicateCannotBeCreatedInSameScope() {
        assertFalse(contextualTagSearch(tags, 1, "Research", true).canCreate)
        assertTrue(contextualTagSearch(tags, 2, "Research", true).canCreate)
    }
    @Test fun absentRootCannotCreate() {
        assertFalse(contextualTagSearch(tags, null, "New", true).canCreate)
    }
}
