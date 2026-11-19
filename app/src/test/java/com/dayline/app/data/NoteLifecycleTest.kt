package com.dayline.app.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dayline.app.core.data.backup.BackupManager
import com.dayline.app.core.data.db.DaylineDatabase
import com.dayline.app.core.model.Note
import com.dayline.app.testing.TestEnv
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class NoteLifecycleTest {

    /** A database written by 1.1.3 (schema version 1) opens in this version with every note intact. */
    @Test
    fun migratesVersion1Database() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "migrate-test.db"
        context.deleteDatabase(name)
        val schema = JSONObject(File("schemas/com.dayline.app.core.data.db.DaylineDatabase/1.json").readText()).getJSONObject("database")
        val path = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                val table = e.getString("tableName")
                db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
                e.optJSONArray("indices")?.let { ix -> for (j in 0 until ix.length()) db.execSQL(ix.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table)) }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.insert("notes", null, ContentValues().apply {
                put("id", 7L); put("title", "Old note"); put("body", "<p>kept</p>"); put("isPinned", 1); put("color", 0)
                put("createdAt", 1_000L); put("updatedAt", 2_000L)
            })
            db.version = 1
        }
        val room = Room.databaseBuilder(context, DaylineDatabase::class.java, name)
            .addMigrations(*DaylineDatabase.MIGRATIONS).allowMainThreadQueries().build()
        val note = runBlocking { room.noteDao().get(7) }!!   // Room checks the migrated schema here and throws if it differs.
        assertEquals("Old note", note.title)
        assertEquals("<p>kept</p>", note.body)
        assertTrue(note.isPinned)
        assertNull(note.archivedAt)
        assertNull(note.trashedAt)
        assertFalse(note.locked)
        room.close()
        context.deleteDatabase(name)
    }

    @Test
    fun archiveTrashRestoreAndEmpty() = runTest {
        val env = TestEnv(backgroundScope)
        val a = env.notes.save(Note(title = "Keep"))
        val b = env.notes.save(Note(title = "Archive me"))
        val c = env.notes.save(Note(title = "Bin me", body = "secret words"))
        env.time.advanceMinutes(5)

        env.notes.archive(b)
        env.notes.trash(c)
        assertEquals(listOf("Keep"), env.notes.notes.first().map { it.title })
        assertEquals(3, env.notes.allNotes.first().size)
        val trashed = env.notes.get(c)!!
        assertNotNull(trashed.trashedAt)
        assertEquals("Trashing counts as an edit, so sync carries it", env.time.now(), trashed.updatedAt)
        assertTrue(env.db.noteDao().search("secret").isEmpty())

        env.notes.restoreFromTrash(c)
        env.notes.unarchive(b)
        assertEquals(setOf("Keep", "Archive me", "Bin me"), env.notes.notes.first().map { it.title }.toSet())

        env.notes.trash(a)
        assertEquals(1, env.notes.emptyTrash())
        assertNull(env.notes.get(a))
        assertEquals(2, env.notes.allNotes.first().size)
        env.close()
    }

    @Test
    fun lockedNotesHideTheirTextFromSearch() = runTest {
        val env = TestEnv(backgroundScope)
        val id = env.notes.save(Note(title = "Bank", body = "pin is somewhere"))
        assertEquals(1, env.db.noteDao().search("somewhere").size)
        env.notes.setLocked(id, true)
        assertTrue(env.db.noteDao().search("somewhere").isEmpty())
        assertEquals("Title still finds it", 1, env.db.noteDao().search("Bank").size)
        env.close()
    }

    @Test
    fun backupKeepsArchiveTrashAndLock() = runTest {
        val env = TestEnv(backgroundScope)
        val a = env.notes.save(Note(title = "A"))
        val b = env.notes.save(Note(title = "B"))
        val c = env.notes.save(Note(title = "C"))
        env.notes.archive(a); env.notes.trash(b); env.notes.setLocked(c, true)
        val backup = BackupManager(env.context, env.db, env.settings, env.sync, env.time)
        val json = backup.exportJson()
        val notes = JSONObject(json).getJSONArray("notes")
        val plain = (0 until notes.length()).map { notes.getJSONObject(it) }.first { it.getString("title") == "C" }
        assertTrue("Unset fields are not written", plain.isNull("archivedAt") && plain.isNull("trashedAt"))

        listOf(a, b, c).forEach { env.notes.delete(it) }
        backup.importJson(json)
        val all = env.notes.allNotes.first().associateBy { it.title }
        assertNotNull(all.getValue("A").archivedAt)
        assertNotNull(all.getValue("B").trashedAt)
        assertTrue(all.getValue("C").locked)
        assertFalse(all.getValue("A").locked)
        env.close()
    }
}
