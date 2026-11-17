import Foundation

/// Three-way merge of two Dayline sync files (this Mac and the cloud copy) against the last synced copy.
/// Same rules as the phone's SyncMerge.kt — keep them in step:
///  - added on one side → kept; added on both with the same id → both kept, this device's renumbered
///    (with its subtasks, reminders, tags and links moved along);
///  - changed on one side only → that change wins; changed on both → newer `updatedAt` wins, else this device's;
///  - deleted on one side and untouched on the other → deleted; deleted on one side but edited on the other → kept.
/// Without a base (first sync), records with the same id are the same record and nothing is deleted.
public enum SyncMerge {
    typealias Obj = [String: Any]

    struct Table { let name: String; let key: (Obj) -> String }

    static func str(_ v: Any?) -> String {
        guard let v, !(v is NSNull) else { return "null" }
        if let n = v as? NSNumber, CFGetTypeID(n) != CFBooleanGetTypeID() { return String(n.int64Value) }
        return "\(v)"
    }
    static func idKey(_ o: Obj) -> String { str(o["id"]) }
    static func pair(_ a: String, _ b: String) -> (Obj) -> String { { o in "\(str(o[a]))|\(str(o[b]))" } }

    static let tables: [Table] = [
        Table(name: "projects", key: idKey), Table(name: "lists", key: idKey), Table(name: "tasks", key: idKey),
        Table(name: "subtasks", key: idKey), Table(name: "tags", key: idKey), Table(name: "taskTags", key: pair("taskId", "tagId")),
        Table(name: "dependencies", key: pair("taskId", "dependsOnTaskId")), Table(name: "attachments", key: pair("taskId", "uri")),
        Table(name: "reminders", key: idKey), Table(name: "habits", key: idKey), Table(name: "habitCompletions", key: pair("habitId", "date")),
        Table(name: "notes", key: idKey), Table(name: "focusSessions", key: { o in str(o["startedAt"]) }),
    ]

    static let references: [(String, [(String, String)])] = [
        ("projects", [("lists", "projectId"), ("tasks", "projectId"), ("notes", "projectId"), ("focusSessions", "projectId")]),
        ("lists", [("tasks", "listId")]),
        ("tasks", [("subtasks", "taskId"), ("taskTags", "taskId"), ("dependencies", "taskId"), ("dependencies", "dependsOnTaskId"),
                   ("attachments", "taskId"), ("reminders", "taskId"), ("notes", "taskId"), ("focusSessions", "taskId")]),
        ("tags", [("taskTags", "tagId")]),
        ("habits", [("habitCompletions", "habitId")]),
        ("subtasks", []), ("reminders", []), ("notes", []),
    ]

    /// Merges sync files given as JSON data. Returns the merged file.
    public static func merge(base: Data?, local: Data, remote: Data) throws -> Data {
        func obj(_ d: Data) throws -> Obj {
            guard let o = try JSONSerialization.jsonObject(with: d) as? Obj else { throw BackupFormatError(message: "Not a Dayline file.") }
            return o
        }
        let b = try base.map(obj)
        let m = merge(base: b, local: try obj(local), remote: try obj(remote))
        return try JSONSerialization.data(withJSONObject: m, options: [.prettyPrinted, .sortedKeys])
    }

    static func merge(base: Obj?, local: Obj, remote: Obj) -> Obj {
        var l = local
        if let base { renumberCollisions(base, &l, remote) }
        var out: Obj = ["app": local["app"] ?? "dayline",
                        "formatVersion": max((local["formatVersion"] as? NSNumber)?.intValue ?? 1, (remote["formatVersion"] as? NSNumber)?.intValue ?? 1),
                        "exportedAt": ISO8601DateFormatter().string(from: Date())]
        for t in tables {
            let bi = base.map { index($0, t) }
            let li = index(l, t), ri = index(remote, t)
            var keys: [String] = [], seen = Set<String>()
            for k in ri.order + li.order where seen.insert(k).inserted { keys.append(k) }
            out[t.name] = keys.compactMap { k in pick(bi?.map[k], li.map[k], ri.map[k], hasBase: base != nil) }
        }
        out["settings"] = mergeSettings(base?["settings"] as? Obj, (l["settings"] as? Obj) ?? [:], (remote["settings"] as? Obj) ?? [:], hasBase: base != nil)
        return out
    }

    struct Index { var map: [String: Obj] = [:]; var order: [String] = [] }

    static func index(_ root: Obj, _ t: Table) -> Index {
        var ix = Index()
        for o in (root[t.name] as? [Any] ?? []).compactMap({ $0 as? Obj }) {
            let k = t.key(o)
            if ix.map[k] == nil { ix.order.append(k) }
            ix.map[k] = o
        }
        return ix
    }

    static func pick(_ b: Obj?, _ l: Obj?, _ r: Obj?, hasBase: Bool) -> Obj? {
        switch (l, r) {
        case (nil, nil): return nil
        case let (l?, nil): return (!hasBase || b == nil) ? l : (same(l, b!) ? nil : l)
        case let (nil, r?): return (!hasBase || b == nil) ? r : (same(r, b!) ? nil : r)
        case let (l?, r?):
            if same(l, r) { return l }
            if let b, same(l, b) { return r }
            if let b, same(r, b) { return l }
            let lu = (l["updatedAt"] as? NSNumber)?.int64Value ?? .min, ru = (r["updatedAt"] as? NSNumber)?.int64Value ?? .min
            return ru > lu ? r : l
        }
    }

    static func mergeSettings(_ base: Obj?, _ l: Obj, _ r: Obj, hasBase: Bool) -> Obj {
        var out: Obj = [:]
        for k in Set(l.keys).union(r.keys) {
            let lv = l[k].flatMap { $0 is NSNull ? nil : $0 }, rv = r[k].flatMap { $0 is NSNull ? nil : $0 }
            let bv = base?[k].flatMap { $0 is NSNull ? nil : $0 }
            let v: Any?
            switch (lv, rv) {
            case (nil, let rv): v = (hasBase && bv != nil && canon(rv) == canon(bv)) ? nil : rv
            case (let lv, nil): v = (hasBase && bv != nil && canon(lv) == canon(bv)) ? nil : lv
            case let (lv?, rv?):
                if canon(lv) == canon(rv) { v = lv } else if hasBase && canon(lv) == canon(bv) { v = rv } else { v = lv }
            }
            if let v { out[k] = v }
        }
        return out
    }

    static func renumberCollisions(_ base: Obj, _ local: inout Obj, _ remote: Obj) {
        for (table, refs) in references {
            guard let t = tables.first(where: { $0.name == table }), var rows = local[table] as? [Any] else { continue }
            let baseIds = Set(index(base, t).order)
            let remoteById = index(remote, t).map
            var next = ([base, local, remote].flatMap { ids($0, table) }.max() ?? 0) + 1
            for i in rows.indices {
                guard var o = rows[i] as? Obj else { continue }
                let id = idKey(o)
                guard !baseIds.contains(id), let theirs = remoteById[id], !same(o, theirs) else { continue }
                let old = (o["id"] as? NSNumber)?.int64Value ?? 0
                let new = next; next += 1
                o["id"] = NSNumber(value: new)
                rows[i] = o
                local[table] = rows
                for (child, field) in refs {
                    guard var arr = local[child] as? [Any] else { continue }
                    for j in arr.indices {
                        guard var c = arr[j] as? Obj, let v = c[field] as? NSNumber, v.int64Value == old else { continue }
                        c[field] = NSNumber(value: new)
                        arr[j] = c
                    }
                    local[child] = arr
                }
                rows = local[table] as? [Any] ?? rows
            }
        }
    }

    static func ids(_ root: Obj, _ table: String) -> [Int64] {
        (root[table] as? [Any] ?? []).compactMap { (($0 as? Obj)?["id"] as? NSNumber)?.int64Value }
    }

    static func same(_ a: Obj, _ b: Obj) -> Bool { canon(a) == canon(b) }

    /// Order- and type-stable text for comparing records (missing and null are the same).
    public static func canon(_ v: Any?) -> String {
        guard let v, !(v is NSNull) else { return "null" }
        if let o = v as? Obj {
            return "{" + o.keys.filter { !(o[$0] is NSNull) }.sorted().map { "\"\($0)\":" + canon(o[$0]) }.joined(separator: ",") + "}"
        }
        if let a = v as? [Any] { return "[" + a.map(canon).joined(separator: ",") + "]" }
        if let n = v as? NSNumber {
            if CFGetTypeID(n) == CFBooleanGetTypeID() { return n.boolValue ? "true" : "false" }
            let d = n.doubleValue
            return d.rounded() == d && abs(d) < 9e15 ? String(n.int64Value) : String(d)
        }
        if let b = v as? Bool { return b ? "true" : "false" }
        if let i = v as? Int64 { return String(i) }
        if let i = v as? Int { return String(i) }
        let s = "\(v)"
        let quoted = (try? JSONSerialization.data(withJSONObject: [s], options: [.fragmentsAllowed])).flatMap { String(data: $0, encoding: .utf8) } ?? "[\"\(s)\"]"
        return String(quoted.dropFirst().dropLast())
    }

    /// Canonical text of a whole sync file without its export time.
    public static func canonFile(_ d: Data) -> String {
        guard var o = (try? JSONSerialization.jsonObject(with: d)) as? Obj else { return "" }
        o.removeValue(forKey: "exportedAt")
        return canon(o)
    }
}
