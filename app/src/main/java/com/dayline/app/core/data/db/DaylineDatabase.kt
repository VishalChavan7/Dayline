package com.dayline.app.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ProjectEntity::class,
        ListEntity::class,
        TaskEntity::class,
        SubtaskEntity::class,
        TagEntity::class,
        TaskTagCrossRef::class,
        TaskDependencyEntity::class,
        AttachmentEntity::class,
        ReminderEntity::class,
        HabitEntity::class,
        HabitCompletionEntity::class,
        NoteEntity::class,
        FocusSessionEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class DaylineDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun tagDao(): TagDao
    abstract fun projectDao(): ProjectDao
    abstract fun habitDao(): HabitDao
    abstract fun noteDao(): NoteDao
    abstract fun reminderDao(): ReminderDao
    abstract fun focusDao(): FocusDao

    companion object {
        const val NAME = "dayline.db"

        /**
         * Explicit schema migrations. Every future schema change must add a Migration here (schemas are exported
         * to app/schemas for verification). Destructive fallback is deliberately NOT enabled so user data is
         * never silently wiped by an upgrade.
         */
        val MIGRATIONS: Array<Migration> = arrayOf(
            // 1.1.4: notes can be archived, trashed and locked.
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE notes ADD COLUMN archivedAt INTEGER")
                    db.execSQL("ALTER TABLE notes ADD COLUMN trashedAt INTEGER")
                    db.execSQL("ALTER TABLE notes ADD COLUMN locked INTEGER NOT NULL DEFAULT 0")
                }
            },
        )
    }
}
