package io.github.peanutpersimmon.rido.data.local.room

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Adds only the hierarchy. Existing tag IDs and every cross-reference remain unchanged. */
object TagHierarchyMigration : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // SQLite permits a nullable REFERENCES column with foreign keys enabled.
        db.execSQL("ALTER TABLE tags ADD COLUMN parentId INTEGER REFERENCES tags(id) ON UPDATE NO ACTION ON DELETE SET NULL")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_tags_parentId ON tags(parentId)")
        // Existing flat tags become children of a new root. Never rename or renumber them.
        val hasTags = db.query("SELECT EXISTS(SELECT 1 FROM tags)").use { it.moveToFirst(); it.getInt(0) != 0 }
        if (hasTags) {
            val now = System.currentTimeMillis()
            db.execSQL("""INSERT INTO tags(name, isArchived, sortOrder, createdAtMillis, updatedAtMillis, parentId)
                VALUES ('原有标签', 0, 0, ?, ?, NULL)""", arrayOf(now, now))
            val rootId = db.query("SELECT last_insert_rowid()").use { it.moveToFirst(); it.getLong(0) }
            db.execSQL("UPDATE tags SET parentId = ? WHERE id != ?", arrayOf(rootId, rootId))
        }
    }
}
