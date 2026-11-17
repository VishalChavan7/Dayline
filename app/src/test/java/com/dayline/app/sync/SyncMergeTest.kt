package com.dayline.app.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncMergeTest {

    private fun root(vararg tables: Pair<String, List<JSONObject>>, settings: JSONObject = JSONObject()) = JSONObject().apply {
        put("app", "dayline"); put("formatVersion", 1); put("exportedAt", "x")
        tables.forEach { (name, rows) -> put(name, JSONArray().apply { rows.forEach { put(it) } }) }
        put("settings", settings)
    }
    private fun note(id: Long, title: String, updated: Long = 1L) = JSONObject().put("id", id).put("title", title).put("body", "").put("updatedAt", updated)
    private fun task(id: Long, title: String, updated: Long = 1L) = JSONObject().put("id", id).put("title", title).put("updatedAt", updated)
    private fun sub(id: Long, taskId: Long, title: String) = JSONObject().put("id", id).put("taskId", taskId).put("title", title)
    private fun titles(o: JSONObject, table: String): List<String> {
        val a = o.getJSONArray(table)
        return (0 until a.length()).map { a.getJSONObject(it).getString("title") }.sorted()
    }
    private fun find(o: JSONObject, table: String, title: String): JSONObject? {
        val a = o.getJSONArray(table)
        return (0 until a.length()).map { a.getJSONObject(it) }.firstOrNull { it.optString("title") == title }
    }

    @Test
    fun noteAddedOnEachDeviceKeepsBoth() {
        val base = root("notes" to listOf(note(1, "Shared")))
        val local = root("notes" to listOf(note(1, "Shared"), note(2, "From phone")))
        val remote = root("notes" to listOf(note(1, "Shared"), note(2, "From Mac")))
        val m = SyncMerge.merge(base, local, remote, "now")
        assertEquals(listOf("From Mac", "From phone", "Shared"), titles(m, "notes"))
        assertEquals(2L, find(m, "notes", "From Mac")!!.getLong("id"))
        assertEquals(3L, find(m, "notes", "From phone")!!.getLong("id"))
    }

    @Test
    fun renumberedTaskTakesItsSubtasksAlong() {
        val base = root("tasks" to listOf(task(1, "Old")), "subtasks" to emptyList())
        val local = root("tasks" to listOf(task(1, "Old"), task(2, "Phone task")), "subtasks" to listOf(sub(1, 2, "phone step")))
        val remote = root("tasks" to listOf(task(1, "Old"), task(2, "Mac task")), "subtasks" to listOf(sub(1, 2, "mac step")))
        val m = SyncMerge.merge(base, local, remote, "now")
        val phoneTask = find(m, "tasks", "Phone task")!!
        assertEquals(3L, phoneTask.getLong("id"))
        assertEquals(phoneTask.getLong("id"), find(m, "subtasks", "phone step")!!.getLong("taskId"))
        assertEquals(2L, find(m, "subtasks", "mac step")!!.getLong("taskId"))
        assertEquals(2L, find(m, "subtasks", "phone step")!!.getLong("id"))
    }

    @Test
    fun deletionOnOneSideSticks() {
        val base = root("notes" to listOf(note(1, "Keep"), note(2, "Delete me")))
        val local = root("notes" to listOf(note(1, "Keep")))
        val remote = root("notes" to listOf(note(1, "Keep"), note(2, "Delete me")))
        assertEquals(listOf("Keep"), titles(SyncMerge.merge(base, local, remote, "now"), "notes"))
    }

    @Test
    fun editBeatsDeletion() {
        val base = root("notes" to listOf(note(1, "Draft")))
        val local = root("notes" to emptyList())
        val remote = root("notes" to listOf(note(1, "Draft, edited on Mac", 5)))
        assertEquals(listOf("Draft, edited on Mac"), titles(SyncMerge.merge(base, local, remote, "now"), "notes"))
    }

    @Test
    fun oneSidedEditWinsAndNewerWinsWhenBothEdit() {
        val base = root("notes" to listOf(note(1, "A"), note(2, "B")))
        val local = root("notes" to listOf(note(1, "A phone", 10), note(2, "B phone", 10)))
        val remote = root("notes" to listOf(note(1, "A"), note(2, "B mac", 20)))
        val m = SyncMerge.merge(base, local, remote, "now")
        assertEquals(listOf("A phone", "B mac"), titles(m, "notes"))
    }

    @Test
    fun withoutBaseNothingIsDeletedOrDuplicated() {
        val local = root("notes" to listOf(note(1, "Same"), note(2, "Only here")))
        val remote = root("notes" to listOf(note(1, "Same"), note(3, "Only there")))
        val m = SyncMerge.merge(null, local, remote, "now")
        assertEquals(listOf("Only here", "Only there", "Same"), titles(m, "notes"))
    }

    @Test
    fun settingsMergePerKey() {
        val base = root(settings = JSONObject().put("accent_color", 1).put("user_name", "V"))
        val local = root(settings = JSONObject().put("accent_color", 2).put("user_name", "V"))
        val remote = root(settings = JSONObject().put("accent_color", 1).put("user_name", "Vishal"))
        val s = SyncMerge.merge(base, local, remote, "now").getJSONObject("settings")
        assertEquals(2, s.getInt("accent_color"))
        assertEquals("Vishal", s.getString("user_name"))
    }

    @Test
    fun canonIgnoresKeyOrderNullsAndNumberTypes() {
        val a = JSONObject().put("b", 1L).put("a", "x").put("c", JSONObject.NULL)
        val b = JSONObject().put("a", "x").put("b", 1)
        assertEquals(SyncMerge.canon(a), SyncMerge.canon(b))
        assertTrue(SyncMerge.canon(JSONObject().put("t", true)) != SyncMerge.canon(JSONObject().put("t", 1)))
        assertNull(null)
    }
}
