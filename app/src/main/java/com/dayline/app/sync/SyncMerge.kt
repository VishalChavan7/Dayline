package com.dayline.app.sync

import org.json.JSONArray
import org.json.JSONObject

/**
 * Three-way merge of two Dayline sync files (this device and the cloud copy), using the last synced copy
 * as the common base. Works record by record, so changes made on different devices between syncs are
 * all kept:
 *  - added on one side → kept; added on both with the same id → both kept, one renumbered (with its
 *    subtasks, reminders, tags and links moved along);
 *  - changed on one side only → that change wins;
 *  - deleted on one side and untouched on the other → deleted; deleted on one side but edited on the
 *    other → the edit wins;
 *  - changed on both sides → the newer one (by `updatedAt`) wins, otherwise this device's.
 * Without a base (first sync after updating), records with the same id are treated as the same record.
 *
 * The Mac app implements the same rules (SyncMerge.swift); keep them in step.
 */
object SyncMerge {

    private data class Table(val name: String, val key: (JSONObject) -> String)

    private fun idKey(o: JSONObject) = o.opt("id")?.toString().orEmpty()
    private fun pair(a: String, b: String): (JSONObject) -> String = { o -> "${o.opt(a)}|${o.opt(b)}" }

    private val tables = listOf(
        Table("projects", ::idKey), Table("lists", ::idKey), Table("tasks", ::idKey), Table("subtasks", ::idKey),
        Table("tags", ::idKey), Table("taskTags", pair("taskId", "tagId")), Table("dependencies", pair("taskId", "dependsOnTaskId")),
        Table("attachments", pair("taskId", "uri")), Table("reminders", ::idKey), Table("habits", ::idKey),
        Table("habitCompletions", pair("habitId", "date")), Table("notes", ::idKey),
        Table("focusSessions") { o -> o.opt("startedAt")?.toString().orEmpty() },
    )

    /** Tables whose ids other tables point to: (table, [(child table, field)]). Parents come first. */
    private val references = listOf(
        "projects" to listOf("lists" to "projectId", "tasks" to "projectId", "notes" to "projectId", "focusSessions" to "projectId"),
        "lists" to listOf("tasks" to "listId"),
        "tasks" to listOf(
            "subtasks" to "taskId", "taskTags" to "taskId", "dependencies" to "taskId", "dependencies" to "dependsOnTaskId",
            "attachments" to "taskId", "reminders" to "taskId", "notes" to "taskId", "focusSessions" to "taskId",
        ),
        "tags" to listOf("taskTags" to "tagId"),
        "habits" to listOf("habitCompletions" to "habitId"),
        "subtasks" to emptyList(), "reminders" to emptyList(), "notes" to emptyList(),
    )

    fun merge(base: JSONObject?, local: JSONObject, remote: JSONObject, exportedAt: String): JSONObject {
        val l = JSONObject(local.toString()) // working copy (may be renumbered)
        if (base != null) renumberCollisions(base, l, remote)
        val out = JSONObject()
        out.put("app", local.optString("app", "dayline"))
        out.put("formatVersion", maxOf(local.optInt("formatVersion", 1), remote.optInt("formatVersion", 1)))
        out.put("exportedAt", exportedAt)
        for (t in tables) {
            val b = base?.let { index(it, t) }
            val li = index(l, t)
            val ri = index(remote, t)
            val result = JSONArray()
            val keys = LinkedHashSet<String>().apply { addAll(ri.keys); addAll(li.keys) }
            for (k in keys) pick(b?.get(k), li[k], ri[k], b != null)?.let { result.put(it) }
            out.put(t.name, result)
        }
        out.put("settings", mergeSettings(base?.optJSONObject("settings"), l.optJSONObject("settings") ?: JSONObject(), remote.optJSONObject("settings") ?: JSONObject()))
        return out
    }

    private fun index(root: JSONObject, t: Table): LinkedHashMap<String, JSONObject> {
        val map = LinkedHashMap<String, JSONObject>()
        val a = root.optJSONArray(t.name) ?: return map
        for (i in 0 until a.length()) a.optJSONObject(i)?.let { map[t.key(it)] = it }
        return map
    }

    /** Decides one record. `hasBase` false means "no common history": absence is not a deletion. */
    private fun pick(b: JSONObject?, l: JSONObject?, r: JSONObject?, hasBase: Boolean): JSONObject? = when {
        l == null && r == null -> null
        l != null && r == null -> when {
            !hasBase || b == null -> l
            same(l, b) -> null // deleted on the other device
            else -> l          // edited here, deleted there: keep the edit
        }
        l == null && r != null -> when {
            !hasBase || b == null -> r
            same(r, b) -> null
            else -> r
        }
        else -> {
            l!!; r!!
            when {
                same(l, r) -> l
                b != null && same(l, b) -> r
                b != null && same(r, b) -> l
                else -> if (r.optLong("updatedAt", Long.MIN_VALUE) > l.optLong("updatedAt", Long.MIN_VALUE)) r else l
            }
        }
    }

    private fun mergeSettings(base: JSONObject?, l: JSONObject, r: JSONObject): JSONObject {
        val out = JSONObject()
        val keys = LinkedHashSet<String>().apply { r.keys().forEach { add(it) }; l.keys().forEach { add(it) } }
        for (k in keys) {
            val lv = if (l.has(k)) l.opt(k) else null
            val rv = if (r.has(k)) r.opt(k) else null
            val bv = if (base != null && base.has(k)) base.opt(k) else null
            val v = when {
                lv == null -> if (base != null && bv != null && canon(rv) == canon(bv)) null else rv
                rv == null -> if (base != null && bv != null && canon(lv) == canon(bv)) null else lv
                canon(lv) == canon(rv) -> lv
                base != null && canon(lv) == canon(bv) -> rv
                else -> lv
            }
            if (v != null && v != JSONObject.NULL) out.put(k, v)
        }
        return out
    }

    /**
     * Records added on both devices since the last sync can get the same id. Give this device's record a
     * new id (above every id in use) and move everything that points at it.
     */
    private fun renumberCollisions(base: JSONObject, local: JSONObject, remote: JSONObject) {
        for ((table, refs) in references) {
            val t = tables.first { it.name == table }
            val baseIds = index(base, t).keys
            val remoteById = index(remote, t)
            val localArr = local.optJSONArray(table) ?: continue
            var next = (listOf(base, local, remote).flatMap { ids(it, table) }.maxOrNull() ?: 0L) + 1
            for (i in 0 until localArr.length()) {
                val o = localArr.optJSONObject(i) ?: continue
                val id = idKey(o)
                if (id in baseIds) continue
                val theirs = remoteById[id] ?: continue
                if (same(o, theirs)) continue
                val old = o.optLong("id")
                val new = next++
                o.put("id", new)
                for ((child, field) in refs) {
                    val arr = local.optJSONArray(child) ?: continue
                    for (j in 0 until arr.length()) {
                        val c = arr.optJSONObject(j) ?: continue
                        if (!c.isNull(field) && c.optLong(field) == old) c.put(field, new)
                    }
                }
            }
        }
    }

    private fun ids(root: JSONObject, table: String): List<Long> {
        val a = root.optJSONArray(table) ?: return emptyList()
        return (0 until a.length()).mapNotNull { a.optJSONObject(it)?.optLong("id") }
    }

    private fun same(a: JSONObject, b: JSONObject) = canon(a) == canon(b)

    /** Order- and type-stable text for comparing records (missing and null are the same). */
    fun canon(v: Any?): String = when (v) {
        null, JSONObject.NULL -> "null"
        is JSONObject -> v.keys().asSequence().filter { !v.isNull(it) }.sorted().joinToString(",", "{", "}") { "\"$it\":" + canon(v.opt(it)) }
        is JSONArray -> (0 until v.length()).joinToString(",", "[", "]") { canon(v.opt(it)) }
        is Boolean -> v.toString()
        is Number -> {
            val d = v.toDouble()
            if (d == Math.floor(d) && !d.isInfinite() && kotlin.math.abs(d) < 9.0E15) v.toLong().toString() else d.toString()
        }
        else -> JSONObject.quote(v.toString())
    }
}
