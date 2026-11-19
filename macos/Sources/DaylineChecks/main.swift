import Foundation
import DaylineCore

// Self-checks for DaylineCore. Run with: swift run DaylineChecks
var failures = 0, passed = 0
func check(_ ok: @autoclosure () -> Bool, _ what: String, file: String = #fileID, line: Int = #line) {
    if ok() { passed += 1 } else { failures += 1; print("FAIL \(what)  (\(file):\(line))") }
}
func eq<T: Equatable>(_ a: T, _ b: T, _ what: String, line: Int = #line) {
    if a == b { passed += 1 } else { failures += 1; print("FAIL \(what): got \(a), expected \(b)  (line \(line))") }
}

// MARK: Dates
let d = Day(year: 2026, month: 11, day: 17)
eq(d.epochDay, 20774, "epoch day of 2026-11-17")
eq(Day(epochDay: 0).description, "1970-01-01", "epoch zero")
eq(d.weekday, .tuesday, "2026-11-17 is a Tuesday")
eq(Day(year: 2024, month: 1, day: 31).plusMonths(1).description, "2024-02-29", "month end clamps (leap)")
eq(Day(year: 2023, month: 1, day: 31).plusMonths(1).description, "2023-02-28", "month end clamps")
eq(Day(year: 2026, month: 1, day: 15).minusMonths(2).description, "2025-11-15", "minus months across year")
eq(DateRules.startOfWeek(d, .monday).description, "2026-11-16", "week starts Monday")
eq(DateRules.startOfWeek(d, .sunday).description, "2026-11-15", "week starts Sunday")
eq(DateRules.nextWeekday(d, .tuesday).description, "2026-11-24", "next Tuesday is strictly after")
eq(DateRules.monthGrid(d, .monday).count, 42, "month grid size")
eq(DayTime(iso: "2026-11-17T09:00")?.iso, "2026-11-17T09:00", "LocalDateTime iso round trip")
eq(DayTime(iso: "2026-11-17T09:00:30")?.time.seconds, 9 * 3600 + 30, "LocalDateTime with seconds")
eq(DayTime(day: d, time: TimeOfDay(hour: 0, minute: 10)).minusMinutes(30).iso, "2026-11-16T23:40", "minus minutes crosses midnight")
for e in stride(from: -800, through: 80000, by: 997) { let x = Day(epochDay: e); let p = x.ymd; check(Day(year: p.year, month: p.month, day: p.day) == x, "civil round trip \(e)") }

// MARK: Repeats
var t = TaskItem(id: 1, title: "Water plants")
t.dueDate = d
t.recurrence = RecurrenceRule(frequency: .DAILY)
eq(RecurrenceEngine.nextOccurrence(t, completedOn: d)?.dueDate, d.plusDays(1), "daily next")
eq(RecurrenceEngine.nextOccurrence(t, completedOn: d.plusDays(5))?.dueDate, d.plusDays(6), "overdue daily skips past copies")
t.recurrence = RecurrenceRule(frequency: .WEEKLY, weekdays: [.monday, .thursday])
eq(RecurrenceEngine.nextOccurrence(t, completedOn: d)?.dueDate?.description, "2026-11-19", "weekly on Mon/Thu from Tue")
t.recurrence = RecurrenceRule(frequency: .MONTHLY)
t.dueDate = Day(year: 2026, month: 1, day: 31)
eq(RecurrenceEngine.nextOccurrence(t, completedOn: Day(year: 2026, month: 1, day: 31))?.dueDate?.description, "2026-02-28", "monthly clamps")
t.dueDate = d; t.startDate = d.minusDays(2); t.recurrence = RecurrenceRule(frequency: .WEEKLY)
let n = RecurrenceEngine.nextOccurrence(t, completedOn: d)
eq(n?.startDate, d.plusDays(5), "span kept: start"); eq(n?.dueDate, d.plusDays(7), "span kept: due")
t.recurrence = RecurrenceRule(frequency: .DAILY, endDate: d)
eq(RecurrenceEngine.nextOccurrence(t, completedOn: d), nil, "series end")
t.recurrence = RecurrenceRule(frequency: .DAILY, interval: 2, fromCompletion: true)
eq(RecurrenceEngine.nextOccurrence(t, completedOn: d.plusDays(3))?.dueDate, d.plusDays(5), "after completion")
eq(RecurrenceEngine.projected(RecurrenceRule(frequency: .WEEKLY), anchor: d, from: d, to: d.plusDays(30)).count, 4, "projected weekly")
eq(RecurrenceRule(frequency: .WEEKLY, weekdays: [.friday, .monday]).describe(), "Every Mon, Fri", "describe weekdays")

// MARK: Habit streaks
var h = Habit(id: 1, name: "Read", color: 0, startDate: d.minusDays(30))
let done = (1...5).map { HabitCompletion(habitId: 1, date: d.minusDays($0), count: 1) }
var s = HabitStreaks.stats(h, done, today: d)
eq(s.currentStreak, 5, "today not done doesn't break the streak")
s = HabitStreaks.stats(h, done + [HabitCompletion(habitId: 1, date: d, count: 1)], today: d)
eq(s.currentStreak, 6, "today done extends the streak")
eq(s.longestStreak, 6, "longest streak")
h.targetPerDay = 2
eq(HabitStreaks.stats(h, done, today: d).currentStreak, 0, "below daily target isn't done")
h.targetPerDay = 1; h.frequency = .SPECIFIC_DAYS; h.weekdays = [.monday]
let mondays = [HabitCompletion(habitId: 1, date: Day(year: 2026, month: 11, day: 16), count: 1), HabitCompletion(habitId: 1, date: Day(year: 2026, month: 11, day: 9), count: 1)]
eq(HabitStreaks.stats(h, mondays, today: d).currentStreak, 2, "unscheduled days don't break")
h.frequency = .WEEKLY_TARGET; h.timesPerWeek = 2
let weekly = [Day(year: 2026, month: 11, day: 10), Day(year: 2026, month: 11, day: 12), Day(year: 2026, month: 11, day: 3), Day(year: 2026, month: 11, day: 4)].map { HabitCompletion(habitId: 1, date: $0, count: 1) }
let ws = HabitStreaks.stats(h, weekly, today: d)
eq(ws.currentStreak, 2, "weekly target streak"); eq(ws.streakUnit, "weeks", "weekly unit")

// MARK: Quick add
let q = QuickAddParser.parse("Call mum tomorrow !high #family", today: d)
eq(q.title, "Call mum", "quick add title"); eq(q.date, d.plusDays(1), "quick add date"); eq(q.priority, .HIGH, "quick add priority"); eq(q.tags, ["family"], "quick add tag")
eq(QuickAddParser.parse("Gym on fri", today: d).date?.description, "2026-11-20", "on fri")
eq(QuickAddParser.parse("Plan next week", today: d).date?.description, "2026-11-23", "next week")
eq(QuickAddParser.parse("Email @boss", today: d).title, "Email @boss", "@word stays in the title")
eq(QuickAddParser.parse("Monday standup notes", today: d).date?.description, "2026-11-23", "weekday name")

// MARK: Reminders
var rt = TaskItem(id: 2, title: "Dentist"); rt.dueDate = d; rt.dueTime = TimeOfDay(hour: 10, minute: 0)
let r = Reminder(id: 1, taskId: 2, type: .DEADLINE, offsetMinutes: 30)
eq(ReminderPlanner.triggerTime(r, task: rt, defaultTime: .nine)?.iso, "2026-11-17T09:30", "deadline reminder")
eq(ReminderPlanner.describeOffset(1440), "1 day before", "describe 1 day"); eq(ReminderPlanner.describeOffset(90), "1h 30m before", "describe 90")

// MARK: Focus timer
var cfg = FocusConfig(); cfg.focusMinutes = 25
var fs = FocusTimerMachine.idle(cfg)
fs = FocusTimerMachine.start(fs, now: 0)
let tr = FocusTimerMachine.complete(fs, now: 25 * 60_000, config: cfg)
eq(tr.record?.focusedMillis, 25 * 60_000, "focus recorded"); eq(tr.state.phase, .SHORT_BREAK, "then short break")
var stopped = FocusTimerMachine.stop(FocusTimerMachine.start(FocusTimerMachine.idle(cfg), now: 0), now: 30_000, config: cfg)
check(stopped.record == nil, "under a minute isn't recorded")
stopped = FocusTimerMachine.stop(FocusTimerMachine.start(FocusTimerMachine.idle(cfg), now: 0), now: 120_000, config: cfg)
eq(stopped.record?.completed, false, "stopped early is incomplete")

// MARK: Notes
let body = NoteCodec.encode("<p><b>Shop</b> #home</p>", checklist: [CheckItem(text: "Milk", done: true), CheckItem(text: "Bread")])
let decoded = NoteCodec.decode(body)
eq(decoded.html, "<p><b>Shop</b> #home</p>", "note html survives"); eq(decoded.checklist.count, 2, "checklist survives"); eq(decoded.checklist[0].done, true, "done flag")
eq(NoteCodec.plainText(body), "Shop #home\n☑ Milk\n☐ Bread", "plain text")
eq(NoteCodec.labels("Ideas #Work and #home, not &#39; or #123"), ["work", "home"], "labels")
eq(NoteCodec.decodeEntities("a &amp; b &#47; &#x2F; &nope;"), "a & b / / &nope;", "entities")
eq(NoteCodec.checkPrefix("\u{2611}\u{FE0E} done"), 3, "done check prefix")

// MARK: Note format (same vectors as the phone's NoteFormatTest)
let mixed = "<p>Intro <b>bold</b></p><blockquote><p>Quoted</p></blockquote><div class=\"callout\" data-kind=\"warning\"><p>Careful</p></div>" +
    "<pre><code class=\"language-kotlin\">val a = 1 &lt; 2\nprintln(a)</code></pre>" +
    "<table><tr><th>A</th><th>B</th></tr><tr><td>1</td><td>2 &amp; 3</td></tr></table><hr><p>Outro</p>"
eq(NoteFormat.split(mixed), [
    .rich("<p>Intro <b>bold</b></p>"), .quote("<p>Quoted</p>"), .callout(kind: "warning", html: "<p>Careful</p>"),
    .code(text: "val a = 1 < 2\nprintln(a)", language: "kotlin"), .table(rows: [["A", "B"], ["1", "2 & 3"]], header: true),
    .divider, .rich("<p>Outro</p>"),
], "split finds every block in order")
let joinedOnce = NoteFormat.join(NoteFormat.split(mixed))
eq(NoteFormat.join(NoteFormat.split(joinedOnce)), joinedOnce, "join/split stable")
eq(NoteFormat.split(joinedOnce), NoteFormat.split(mixed), "join/split same blocks")
eq(NoteFormat.split("<blockquote><p>a</p><blockquote><p>b</p></blockquote></blockquote><div><p>plain div</p></div>"),
   [.quote("<p>a</p><blockquote><p>b</p></blockquote>"), .rich("<div><p>plain div</p></div>")], "nested quote, plain div")
eq(NoteFormat.split("<p>a</p><blockquote><p>never closed</p>"), [.rich("<p>a</p><blockquote><p>never closed</p>")], "unclosed block stays text")
eq(NoteFormat.split("<div class=\"callout\" data-kind=\"weird\"><p>x</p></div>"), [.callout(kind: "note", html: "<p>x</p>")], "unknown callout kind")
eq(NoteFormat.blockHtml(.code(text: "<b>&</b>", language: "")), "<pre><code>&lt;b&gt;&amp;&lt;/b&gt;</code></pre>", "code escapes")
eq(NoteFormat.split("<pre><code>&lt;b&gt;&amp;&lt;/b&gt;</code></pre>"), [.code(text: "<b>&</b>", language: "")], "code unescapes")
eq(NoteFormat.split("<table><tr><td>a</td></tr><tr><td>b</td><td>c</td></tr></table>"), [.table(rows: [["a", ""], ["b", "c"]], header: false)], "ragged table padded")
eq(NoteFormat.split(""), [.rich("")], "empty note")
eq(NoteFormat.headings("<h1>One</h1><p><span style=\"font-size: 21.0px;\"><b>Two</b></span></p><p><span style=\"font-size: 18.0px; font-weight: 600;\">Three</span></p>" +
    "<p><span style=\"font-size: 1.12em;\"><b>Four</b></span></p><p>Body</p><h6>Six</h6><p><span style=\"font-size: 12.0px;\">small</span></p>"),
   [.init(level: 1, text: "One"), .init(level: 2, text: "Two"), .init(level: 3, text: "Three"), .init(level: 4, text: "Four"), .init(level: 6, text: "Six")], "headings")
eq(NoteFormat.tagPath("work/meetings/q4"), ["work", "work/meetings", "work/meetings/q4"], "tag path")
check(NoteFormat.tagMatches("work/meetings", "work") && NoteFormat.tagMatches("work", "work") && !NoteFormat.tagMatches("workshop", "work"), "tag matches")
eq(NoteCodec.labels("See #work/meetings and #home, not a#b or &#39;"), ["work/meetings", "home"], "nested labels")
eq(NoteFormat.wikiLinks("[[Plan]] and [[ Ideas 2026 |alias]] and [[plan]] and [[]] [[a\nb]]"), ["Plan", "Ideas 2026"], "wiki links")
check(NoteFormat.linksTo("see [[My Note]]", title: "my note") && !NoteFormat.linksTo("see [[My Note]]", title: ""), "links to")
check(NoteFormat.hasOpenTodo("<p>\u{2610} buy milk</p>") && !NoteFormat.hasOpenTodo("<p>\u{2611}\u{FE0E} bought</p>"), "open todo in text")
check(NoteFormat.hasOpenTodo(NoteCodec.encode("<p>x</p>", checklist: [CheckItem(text: "y")])) && !NoteFormat.hasOpenTodo(NoteCodec.encode("<p>x</p>", checklist: [CheckItem(text: "y", done: true)])), "open todo in checklist")
let st = NoteFormat.stats(title: "T", body: "<p>Hello world, it's me.</p><p>Second line</p>")
eq([st.words, st.paragraphs, st.readMinutes], [6, 2, 1], "stats")
eq(NoteFormat.stats(title: "", body: "").readMinutes, 0, "empty stats")
let mdBody = "<p><span style=\"font-size: 21.0px;\"><b>Plan</b></span></p>" +
    "<p>Some <b>bold</b>, <i>it</i>, <s>gone</s>, <code>x</code>, <mark>hi</mark> and <a href=\"https://a.b\">link</a></p>" +
    "<ul><li>one</li><li>two</li></ul><ol><li>first</li><li>second</li></ol><p>\u{2610} todo</p><p>\u{2611}\u{FE0E} done</p>" +
    "<blockquote><p>q</p></blockquote><div class=\"callout\" data-kind=\"tip\"><p>c</p></div><pre><code class=\"language-sh\">ls -la</code></pre>" +
    "<table><tr><th>A</th><th>B</th></tr><tr><td>1</td><td>x|y</td></tr></table><hr>"
eq(NoteFormat.toMarkdown(title: "Title", body: NoteCodec.encode(mdBody, checklist: [CheckItem(text: "end", done: true)])),
   "# Title\n\n## Plan\n\nSome **bold**, *it*, ~~gone~~, `x`, ==hi== and [link](https://a.b)\n\n- one\n- two\n\n1. first\n2. second\n\n- [ ] todo\n- [x] done\n\n> q\n\n> [!TIP]\n> c\n\n```sh\nls -la\n```\n\n| A | B |\n| --- | --- |\n| 1 | x\\|y |\n\n---\n\n- [x] end\n",
   "markdown export")
let imported = NoteFormat.fromMarkdown("# My Title\n\nPara one\nstill one with **b** and `a<b`\n\n## Sub\n- a\n- b\n1. x\n2) y\n- [ ] open\n- [x] shut\n> quote\n> more\n\n> [!WARNING]\n> hot\n\n```js\nlet a = \"<x>\";\n```\n| H1 | H2 |\n|----|:--:|\n| a \\| b | c |\n***", fallbackTitle: "fallback")
eq(imported.title, "My Title", "md import title")
eq(imported.html, "<p>Para one<br>still one with <b>b</b> and <code>a&lt;b</code></p><h2>Sub</h2><ul><li>a</li><li>b</li></ul><ol><li>x</li><li>y</li></ol>" +
   "<p>\u{2610} open</p><p>\u{2611}\u{FE0E} shut</p><blockquote><p>quote<br>more</p></blockquote><div class=\"callout\" data-kind=\"warning\"><p>hot</p></div>" +
   "<pre><code class=\"language-js\">let a = \"&lt;x&gt;\";</code></pre><table><tr><th>H1</th><th>H2</th></tr><tr><td>a | b</td><td>c</td></tr></table><hr>", "md import html")
eq(NoteFormat.fromMarkdown("just text").title, "", "md import without title")
let mdMixed = NoteFormat.toMarkdown(title: "T", body: mixed)
let mdBack = NoteFormat.fromMarkdown(mdMixed)
eq(NoteFormat.toMarkdown(title: mdBack.title, body: mdBack.html), mdMixed, "markdown round trip")
eq(NoteCodec.plainText(mixed), "Intro bold\nQuoted\nCareful\nval a = 1 < 2\nprintln(a)\nA  B\n1  2 & 3\n\n———\nOutro", "plain text of blocks")
check(NoteFormat.toHtmlPage(title: "<x>", body: "<p>a</p>").contains("<h1>&lt;x&gt;</h1><p>a</p>"), "html page escapes title")
for j in ["<", "<blockquote", "</blockquote>", "<pre><code>", "<table><tr>", "<div class=\"callout\">", "[[", "]]", "#", "#/", "```", "|", "| a |\n| --- |", "> [!", "<hr", "&#99999999;", "\u{0}",
          String(repeating: "<blockquote>", count: 200) + "x" + String(repeating: "</blockquote>", count: 200)] {
    _ = NoteFormat.join(NoteFormat.split(j)); _ = NoteFormat.headings(j); _ = NoteFormat.wikiLinks(j); _ = NoteFormat.stats(title: j, body: j)
    _ = NoteFormat.toMarkdown(title: j, body: j); _ = NoteFormat.fromMarkdown(j); _ = NoteFormat.toHtmlPage(title: j, body: j); _ = NoteCodec.plainText(j)
}
passed += 1

// MARK: Sync file
var data = DaylineData()
var task = TaskItem(id: 7, title: "Ship Mac app"); task.dueDate = d; task.dueTime = TimeOfDay(hour: 17, minute: 30); task.priority = .URGENT
task.recurrence = RecurrenceRule(frequency: .WEEKLY, interval: 2, weekdays: [.monday, .wednesday], endDate: d.plusDays(90))
task.createdAt = Date(epochMillis: 1_763_000_000_000); task.updatedAt = task.createdAt
data.tasks = [task]
data.projects = [Project(id: 3, name: "Dayline", color: -13664778)]
data.subtasks = [Subtask(id: 1, taskId: 7, title: "Icons")]
data.reminders = [Reminder(id: 4, taskId: nil, type: .CUSTOM, at: DayTime(day: d, time: .nine))]
data.habits = [Habit(id: 2, name: "Walk", color: -16737844, startDate: d)]
data.habitCompletions = [HabitCompletion(habitId: 2, date: d, count: 1)]
data.notes = [Note(id: 5, title: "Ideas")]
data.focusSessions = [FocusSession(startedAt: Date(epochMillis: 1_000), endedAt: Date(epochMillis: 1_501_000), focusedMillis: 1_500_000, plannedMillis: 1_500_000, taskId: 7, projectId: nil, completed: true)]
data.settings = ["accent_color": .int(-13664778), "week_start": .int(1), "theme_mode": .string("SYSTEM"), "auto_breaks": .bool(false)]
let json = BackupCodec.encode(data)
let back = try BackupCodec.decode(json)
eq(back, data, "sync file round trip")
eq(BackupCodec.fingerprint(back), BackupCodec.fingerprint(data), "fingerprint stable")

// A file written by the phone (shape copied from BackupManager.exportJson).
let phone = """
{"app":"dayline","formatVersion":1,"exportedAt":"2026-11-17T08:00:00Z",
 "projects":[{"id":1,"name":"Home","description":"","color":-13664778,"icon":"folder","startDate":null,"deadline":null,"isArchived":false,"sortOrder":0,"createdAt":1763000000000}],
 "lists":[],
 "tasks":[{"id":9,"title":"Pay rent","description":"","notes":"","startDate":null,"startTime":null,"dueDate":20774,"dueTime":32400,"priority":"HIGH","category":null,"projectId":1,"listId":42,"recurrenceFrequency":"MONTHLY","recurrenceInterval":1,"recurrenceWeekdays":0,"recurrenceFromCompletion":false,"recurrenceEndDate":null,"isCompleted":false,"completedAt":null,"progress":0,"sortOrder":3,"createdAt":1763000000000,"updatedAt":1763000000000}],
 "subtasks":[{"id":1,"taskId":99,"title":"orphan","isDone":false,"position":0}],
 "tags":[],"taskTags":[],"dependencies":[],"attachments":[],
 "reminders":[{"id":1,"taskId":9,"type":"DEADLINE","offsetMinutes":60,"at":null,"title":"","isPinned":false,"isDismissed":false}],
 "habits":[],"habitCompletions":[],"notes":[],"focusSessions":[],
 "settings":{"accent_color":-13664778,"dash_hidden":"QUOTE|INBOX","user_name":"Vishal"}}
"""
let p = try BackupCodec.decode(Data(phone.utf8))
eq(p.tasks.first?.dueTime, TimeOfDay(hour: 9, minute: 0), "phone due time")
eq(p.tasks.first?.listId, nil, "unknown list dropped like the phone does")
eq(p.subtasks.count, 0, "orphan subtask dropped")
eq(p.tasks.first?.recurrence?.frequency, .MONTHLY, "phone recurrence")
eq(p.settings["user_name"]?.string, "Vishal", "settings kept")
do { _ = try BackupCodec.decode(Data("{\"app\":\"other\"}".utf8)); check(false, "foreign file rejected") } catch { check(true, "") }


// MARK: Sync merge (same cases as the phone's SyncMergeTest)
func file(_ tables: [String: [[String: Any]]], settings: [String: Any] = [:]) -> Data {
    var o: [String: Any] = ["app": "dayline", "formatVersion": 1, "exportedAt": "x", "settings": settings]
    for (k, v) in tables { o[k] = v }
    return try! JSONSerialization.data(withJSONObject: o)
}
func mergedRows(_ d: Data, _ table: String) -> [[String: Any]] {
    ((try! JSONSerialization.jsonObject(with: d)) as! [String: Any])[table] as? [[String: Any]] ?? []
}
func titles(_ d: Data, _ t: String) -> [String] { mergedRows(d, t).compactMap { $0["title"] as? String }.sorted() }
func row(_ d: Data, _ t: String, _ title: String) -> [String: Any]? { mergedRows(d, t).first { $0["title"] as? String == title } }
func nt(_ id: Int, _ title: String, _ u: Int = 1) -> [String: Any] { ["id": id, "title": title, "body": "", "updatedAt": u] }

var m = try SyncMerge.merge(base: file(["notes": [nt(1, "Shared")]]), local: file(["notes": [nt(1, "Shared"), nt(2, "From Mac")]]), remote: file(["notes": [nt(1, "Shared"), nt(2, "From phone")]]))
eq(titles(m, "notes"), ["From Mac", "From phone", "Shared"], "note added on each device keeps both")
eq(row(m, "notes", "From Mac")?["id"] as? Int, 3, "this device's colliding note is renumbered")
m = try SyncMerge.merge(base: file(["tasks": [nt(1, "Old")], "subtasks": []]),
                        local: file(["tasks": [nt(1, "Old"), nt(2, "Mac task")], "subtasks": [["id": 1, "taskId": 2, "title": "mac step"]]]),
                        remote: file(["tasks": [nt(1, "Old"), nt(2, "Phone task")], "subtasks": [["id": 1, "taskId": 2, "title": "phone step"]]]))
eq(row(m, "subtasks", "mac step")?["taskId"] as? Int, row(m, "tasks", "Mac task")?["id"] as? Int, "renumbered task keeps its subtasks")
eq(row(m, "subtasks", "phone step")?["taskId"] as? Int, 2, "other device's subtask untouched")
m = try SyncMerge.merge(base: file(["notes": [nt(1, "Keep"), nt(2, "Delete me")]]), local: file(["notes": [nt(1, "Keep")]]), remote: file(["notes": [nt(1, "Keep"), nt(2, "Delete me")]]))
eq(titles(m, "notes"), ["Keep"], "deletion sticks")
m = try SyncMerge.merge(base: file(["notes": [nt(1, "Draft")]]), local: file(["notes": []]), remote: file(["notes": [nt(1, "Draft edited", 5)]]))
eq(titles(m, "notes"), ["Draft edited"], "edit beats deletion")
m = try SyncMerge.merge(base: file(["notes": [nt(1, "A"), nt(2, "B")]]), local: file(["notes": [nt(1, "A mac", 10), nt(2, "B mac", 10)]]), remote: file(["notes": [nt(1, "A"), nt(2, "B phone", 20)]]))
eq(titles(m, "notes"), ["A mac", "B phone"], "one-sided edit wins; newer wins when both edit")
m = try SyncMerge.merge(base: nil, local: file(["notes": [nt(1, "Same"), nt(2, "Only here")]]), remote: file(["notes": [nt(1, "Same"), nt(3, "Only there")]]))
eq(titles(m, "notes"), ["Only here", "Only there", "Same"], "no base: union, no duplicates")
m = try SyncMerge.merge(base: file([:], settings: ["accent_color": 1, "user_name": "V"]), local: file([:], settings: ["accent_color": 2, "user_name": "V"]), remote: file([:], settings: ["accent_color": 1, "user_name": "Vishal"]))
let ms = ((try JSONSerialization.jsonObject(with: m)) as! [String: Any])["settings"] as! [String: Any]
eq(ms["accent_color"] as? Int, 2, "setting changed here kept"); eq(ms["user_name"] as? String, "Vishal", "setting changed there taken")
eq(SyncMerge.canon(["b": 1, "a": "x", "c": NSNull()]), SyncMerge.canon(["a": "x", "b": Int64(1)]), "canon ignores order, nulls and int types")
check(SyncMerge.canon(["t": true]) != SyncMerge.canon(["t": 1]), "canon keeps booleans apart from numbers")
// A real round: the merged file decodes back into the model.
let decodedMerged = try BackupCodec.decode(try SyncMerge.merge(base: nil, local: BackupCodec.encode(data), remote: Data(phone.utf8)))
eq(decodedMerged.tasks.count, 2, "merged Mac + phone files decode (tasks from both)")

// MARK: Note archive, trash and lock in the sync file
var nd = DaylineData()
var na = Note(id: 1, title: "A"); na.archivedAt = Date(epochMillis: 1_700_000_000_000)
var nb = Note(id: 2, title: "B"); nb.trashedAt = Date(epochMillis: 1_700_000_100_000)
var nc = Note(id: 3, title: "C"); nc.locked = true
nd.notes = [na, nb, nc, Note(id: 4, title: "D")]
let ndBack = try BackupCodec.decode(BackupCodec.encode(nd))
eq(ndBack.notes, nd.notes, "note states survive the sync file")
let ndJson = try JSONSerialization.jsonObject(with: BackupCodec.encode(nd)) as! [String: Any]
let dRecord = (ndJson["notes"] as! [[String: Any]]).first { $0["title"] as? String == "D" }!
check([dRecord["archivedAt"], dRecord["trashedAt"], dRecord["locked"]].allSatisfy { $0 == nil || $0 is NSNull }, "unset note states are not written")
// A phone file with the new fields reads the same.
let phoneNotes = #"{"app":"\#(BackupCodec.appId)","formatVersion":1,"notes":[{"id":9,"title":"P","body":"","isPinned":false,"color":0,"createdAt":1,"updatedAt":2,"archivedAt":3,"locked":true}]}"#
let pn = try BackupCodec.decode(Data(phoneNotes.utf8)).notes.first!
check(pn.archivedAt == Date(epochMillis: 3) && pn.trashedAt == nil && pn.locked, "phone note states decode")

MainActor.assumeIsolated { runBlockLayoutChecks(); runEditorChecks() }

print(failures == 0 ? "All \(passed) checks passed." : "\(failures) failed, \(passed) passed.")
exit(failures == 0 ? 0 : 1)
