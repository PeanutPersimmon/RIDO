package io.github.peanutpersimmon.rido.data.local.room.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import io.github.peanutpersimmon.rido.data.local.room.entity.KeywordRuleEntity
import io.github.peanutpersimmon.rido.data.local.room.entity.KeywordRuleTagCrossRef
import io.github.peanutpersimmon.rido.data.local.room.relation.KeywordRuleWithTags
import kotlinx.coroutines.flow.Flow

@Dao
interface KeywordRuleDao {
    @Insert
    suspend fun insert(rule: KeywordRuleEntity): Long

    @Update
    suspend fun update(rule: KeywordRuleEntity)

    @Delete
    suspend fun delete(rule: KeywordRuleEntity)

    @Query("SELECT * FROM keyword_rules WHERE id = :id")
    suspend fun getById(id: Long): KeywordRuleEntity?

    @Transaction
    @Query("SELECT * FROM keyword_rules WHERE id = :id")
    suspend fun getWithTags(id: Long): KeywordRuleWithTags?

    @Transaction
    @Query(
        """
        SELECT keyword_rules.* FROM keyword_rules
        LEFT JOIN categories ON categories.id = keyword_rules.categoryId
        WHERE keyword_rules.isEnabled = 1
          AND (keyword_rules.categoryId IS NULL OR categories.isArchived = 0)
        ORDER BY keyword_rules.priority DESC,
                 length(keyword_rules.keyword) DESC,
                 COALESCE(categories.sortOrder, 2147483647) ASC,
                 keyword_rules.id ASC
        """,
    )
    suspend fun getEnabledOrdered(): List<KeywordRuleWithTags>

    @Transaction
    @Query(
        """
        SELECT * FROM keyword_rules
        WHERE categoryId = :categoryId
          AND NOT EXISTS (
              SELECT 1 FROM keyword_rule_tag_cross_refs
              WHERE keywordRuleId = keyword_rules.id
          )
        ORDER BY priority DESC, length(keyword) DESC, id ASC
        """,
    )
    fun observePureCategoryRules(categoryId: Long): Flow<List<KeywordRuleWithTags>>

    @Transaction
    @Query("SELECT * FROM keyword_rules ORDER BY id ASC")
    suspend fun getAllWithTags(): List<KeywordRuleWithTags>

    @Insert
    suspend fun insertTagRefs(refs: List<KeywordRuleTagCrossRef>)

    @Query("DELETE FROM keyword_rule_tag_cross_refs WHERE keywordRuleId = :ruleId")
    suspend fun deleteTagRefs(ruleId: Long)

    @Transaction
    suspend fun replaceTags(ruleId: Long, tagIds: Set<Long>) {
        deleteTagRefs(ruleId)
        if (tagIds.isNotEmpty()) {
            insertTagRefs(tagIds.map { tagId -> KeywordRuleTagCrossRef(ruleId, tagId) })
        }
    }
}

