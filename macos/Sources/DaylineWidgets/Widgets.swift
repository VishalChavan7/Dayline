import WidgetKit
import SwiftUI
import AppIntents
import DaylineCore

@main
struct DaylineWidgets: WidgetBundle {
    var body: some Widget {
        TasksWidget()
        HabitsWidget()
        ProgressWidget()
        FocusWidget()
        NextUpWidget()
        QuickAddWidget()
    }
}

// MARK: Timeline

struct DaylineEntry: TimelineEntry {
    let date: Date
    let s: WidgetSnapshot
}

struct DaylineProvider: TimelineProvider {
    func placeholder(in context: Context) -> DaylineEntry { DaylineEntry(date: Date(), s: .sample) }

    func getSnapshot(in context: Context, completion: @escaping (DaylineEntry) -> Void) {
        completion(DaylineEntry(date: Date(), s: context.isPreview ? .sample : (WidgetFiles.readSnapshot() ?? .sample)))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<DaylineEntry>) -> Void) {
        let s = WidgetFiles.readSnapshot() ?? .empty
        let now = Date()
        // The app refreshes widgets whenever something changes; this is the fallback.
        var next = now.addingTimeInterval(15 * 60)
        if let end = s.focus.endsAt, end > now { next = min(next, end.addingTimeInterval(1)) }
        let midnight = Calendar.current.startOfDay(for: now).addingTimeInterval(86_400 + 5)
        next = min(next, midnight)
        completion(Timeline(entries: [DaylineEntry(date: now, s: s)], policy: .after(next)))
    }
}

// MARK: Look

extension Color {
    init(rgb: UInt32) {
        self.init(.sRGB, red: Double((rgb >> 16) & 0xFF) / 255, green: Double((rgb >> 8) & 0xFF) / 255, blue: Double(rgb & 0xFF) / 255, opacity: 1)
    }
}

extension WidgetSnapshot {
    var accentColor: Color { monochrome ? .primary : Color(rgb: accent) }
    var accentFill: AnyShapeStyle {
        if let end = accentEnd { return AnyShapeStyle(LinearGradient(colors: [Color(rgb: accent), Color(rgb: end)], startPoint: .topLeading, endPoint: .bottomTrailing)) }
        return AnyShapeStyle(accentColor)
    }
    func priorityColor(_ p: Int) -> Color {
        switch p { case 3: Color(rgb: 0xD92D3A); case 2: Color(rgb: 0xD9700A); case 1: Color(rgb: 0x2F6BE0); default: Color(rgb: 0x5E7A68) }
    }
}

private struct Background: View {
    let s: WidgetSnapshot
    /// Which widget this is, for its background art ("tasks", "habits", …).
    var art: String? = nil
    var body: some View {
        ZStack {
            Color(nsColor: .windowBackgroundColor)
            LinearGradient(colors: [s.accentColor.opacity(0.10), .clear], startPoint: .topLeading, endPoint: .bottomTrailing)
            if let art, let style = (s.art ?? WidgetArt.defaults)[art] ?? WidgetArt.defaults[art], let img = WidgetArt.image(style) {
                Image(nsImage: img).resizable().aspectRatio(contentMode: .fill).opacity(0.85)
            }
        }
    }
}

/// The phone's widget background art: translucent shapes, chosen per widget in Settings.
enum WidgetArt {
    static let defaults = ["tasks": "waves", "habits": "leaves", "focus": "rings", "progress": "hills", "next_up": "confetti"]
    static func image(_ style: String) -> NSImage? {
        guard style != "none", let url = Bundle.main.url(forResource: "art-" + style, withExtension: "png") else { return nil }
        return NSImage(contentsOf: url)
    }
}

private struct Header: View {
    let title: String
    let symbol: String
    let s: WidgetSnapshot
    var trailing: String?
    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: symbol).font(.system(size: 11, weight: .bold)).foregroundStyle(s.accentColor)
                .frame(width: 20, height: 20)
                .background(RoundedRectangle(cornerRadius: 6, style: .continuous).fill(s.accentColor.opacity(0.15)))
            Text(title).font(.system(size: 13, weight: .semibold))
            Spacer(minLength: 0)
            if let trailing { Text(trailing).font(.system(size: 11, weight: .semibold).monospacedDigit()).foregroundStyle(.secondary) }
        }
    }
}

private struct Check: View {
    let done: Bool
    let color: Color
    var size: CGFloat = 16
    var body: some View {
        ZStack {
            Circle().strokeBorder(color, lineWidth: 1.6)
            if done {
                Circle().fill(color)
                Image(systemName: "checkmark").font(.system(size: size * 0.5, weight: .bold)).foregroundStyle(.white)
            }
        }
        .frame(width: size, height: size)
    }
}

private struct TaskLine: View {
    let t: WidgetSnapshot.TaskRow
    let s: WidgetSnapshot
    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 8) {
            Button(intent: ToggleTaskIntent(id: t.id)) { Check(done: t.done, color: s.priorityColor(t.priority)) }
                .buttonStyle(.plain)
                .alignmentGuide(.firstTextBaseline) { $0[VerticalAlignment.center] + 4 }
            VStack(alignment: .leading, spacing: 1) {
                Text(t.title).font(.system(size: 12)).lineLimit(1).strikethrough(t.done).foregroundStyle(t.done ? .secondary : .primary)
                if let w = t.when { Text(w).font(.system(size: 10)).foregroundStyle(t.overdue ? Color(rgb: 0xC62A36) : .secondary).lineLimit(1) }
            }
            Spacer(minLength: 0)
        }
    }
}

// MARK: Tasks

struct TasksWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "DaylineTasks", provider: DaylineProvider()) { e in TasksView(s: e.s) }
            .configurationDisplayName("Today's tasks")
            .description("Overdue and today's tasks. Tick them off right here.")
            .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
    }
}

struct TasksView: View {
    let s: WidgetSnapshot
    @Environment(\.widgetFamily) var family
    var body: some View {
        let limit = family == .systemLarge ? 9 : family == .systemMedium ? 4 : 3
        let open = s.tasks.filter { !$0.done }
        VStack(alignment: .leading, spacing: 7) {
            Header(title: family == .systemSmall ? "Today" : "Today · \(s.weekday)", symbol: "sun.max.fill", s: s, trailing: open.isEmpty ? nil : "\(open.count)")
            if s.tasks.isEmpty {
                Spacer()
                Text("All clear for today ✨").font(.system(size: 12)).foregroundStyle(.secondary)
                Spacer()
            } else {
                ForEach(s.tasks.prefix(limit)) { t in TaskLine(t: t, s: s) }
                Spacer(minLength: 0)
                if s.tasks.count > limit { Text("+\(s.tasks.count - limit) more").font(.system(size: 10)).foregroundStyle(.secondary) }
            }
        }
        .widgetURL(URL(string: "dayline://open/today"))
        .containerBackground(for: .widget) { Background(s: s, art: "tasks") }
    }
}

// MARK: Habits

struct HabitsWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "DaylineHabits", provider: DaylineProvider()) { e in HabitsView(s: e.s) }
            .configurationDisplayName("Habits")
            .description("Today's habits with one-click check-ins.")
            .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
    }
}

struct HabitsView: View {
    let s: WidgetSnapshot
    @Environment(\.widgetFamily) var family
    var body: some View {
        let limit = family == .systemLarge ? 8 : family == .systemMedium ? 3 : 3
        let doneCount = s.habits.filter(\.done).count
        VStack(alignment: .leading, spacing: 7) {
            Header(title: "Habits", symbol: "figure.mind.and.body", s: s, trailing: s.habits.isEmpty ? nil : "\(doneCount)/\(s.habits.count)")
            if s.habits.isEmpty {
                Spacer()
                Text("No habits due today.").font(.system(size: 12)).foregroundStyle(.secondary)
                Spacer()
            } else {
                ForEach(s.habits.prefix(limit)) { h in
                    let c = Color(rgb: h.color)
                    HStack(spacing: 8) {
                        Image(systemName: h.symbol).font(.system(size: 10, weight: .semibold)).foregroundStyle(c)
                            .frame(width: 22, height: 22)
                            .background(RoundedRectangle(cornerRadius: 6, style: .continuous).fill(c.opacity(0.16)))
                        VStack(alignment: .leading, spacing: 0) {
                            Text(h.name).font(.system(size: 12)).lineLimit(1)
                            if family != .systemSmall { Text("🔥 \(h.streak)").font(.system(size: 10)).foregroundStyle(.secondary) }
                        }
                        Spacer(minLength: 0)
                        Button(intent: ToggleHabitIntent(id: h.id)) {
                            HStack(spacing: 3) {
                                if h.target > 1 && family != .systemSmall { Text("\(h.count)/\(h.target)").font(.system(size: 10, weight: .semibold).monospacedDigit()) }
                                Image(systemName: h.done ? "checkmark" : (h.target > 1 ? "plus" : "checkmark")).font(.system(size: 10, weight: .bold))
                            }
                            .foregroundStyle(h.done ? .white : c)
                            .padding(.horizontal, 7).frame(minWidth: 24, minHeight: 22)
                            .background(RoundedRectangle(cornerRadius: 7, style: .continuous).fill(h.done ? c : c.opacity(0.16)))
                        }
                        .buttonStyle(.plain)
                    }
                }
                Spacer(minLength: 0)
            }
        }
        .widgetURL(URL(string: "dayline://open/habits"))
        .containerBackground(for: .widget) { Background(s: s, art: "habits") }
    }
}

// MARK: Progress

struct ProgressWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "DaylineProgress", provider: DaylineProvider()) { e in ProgressWidgetView(s: e.s) }
            .configurationDisplayName("Daily progress")
            .description("How much of today's tasks and habits is done.")
            .supportedFamilies([.systemSmall])
    }
}

struct ProgressWidgetView: View {
    let s: WidgetSnapshot
    var body: some View {
        let p = s.total == 0 ? 0 : Double(s.done) / Double(s.total)
        VStack(spacing: 8) {
            ZStack {
                Circle().stroke(s.accentColor.opacity(0.15), lineWidth: 10)
                Circle().trim(from: 0, to: p).stroke(s.accentFill, style: StrokeStyle(lineWidth: 10, lineCap: .round)).rotationEffect(.degrees(-90))
                Text("\(Int((p * 100).rounded()))%").font(.system(size: 20, weight: .semibold).monospacedDigit())
            }
            .padding(5)
            Text(s.total == 0 ? "A clear day" : s.done == s.total ? "All done" : "\(s.done) of \(s.total) done")
                .font(.system(size: 12, weight: .semibold))
        }
        .widgetURL(URL(string: "dayline://open/today"))
        .containerBackground(for: .widget) { Background(s: s, art: "progress") }
    }
}

// MARK: Focus

struct FocusWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "DaylineFocus", provider: DaylineProvider()) { e in FocusWidgetView(s: e.s) }
            .configurationDisplayName("Focus timer")
            .description("Start, pause and watch your focus session.")
            .supportedFamilies([.systemSmall, .systemMedium])
    }
}

struct FocusWidgetView: View {
    let s: WidgetSnapshot
    @Environment(\.widgetFamily) var family
    var body: some View {
        let f = s.focus
        VStack(alignment: .leading, spacing: 6) {
            Header(title: f.running ? f.phase : f.paused ? "Paused" : "Focus", symbol: "timer", s: s)
            Spacer(minLength: 0)
            Group {
                if f.running, let end = f.endsAt, end > Date() {
                    Text(timerInterval: Date()...end, countsDown: true)
                } else {
                    Text(String(format: "%d:%02d", f.remainingSeconds / 60, f.remainingSeconds % 60))
                }
            }
            .font(.system(size: family == .systemSmall ? 30 : 36, weight: .semibold).monospacedDigit())
            .lineLimit(1).minimumScaleFactor(0.7)
            if family == .systemMedium {
                Text("\(f.minutesToday)m of \(f.goalMinutes >= 60 ? "\(f.goalMinutes / 60)h" : "\(f.goalMinutes)m") goal today").font(.system(size: 11)).foregroundStyle(.secondary)
            }
            Spacer(minLength: 0)
            Button(intent: ToggleFocusIntent()) {
                Label(f.running ? "Pause" : f.paused ? "Resume" : "Start", systemImage: f.running ? "pause.fill" : "play.fill")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(s.monochrome ? Color(nsColor: .windowBackgroundColor) : .white)
                    .frame(maxWidth: .infinity, minHeight: 28)
                    .background(RoundedRectangle(cornerRadius: 8, style: .continuous).fill(s.accentFill))
            }
            .buttonStyle(.plain)
        }
        .widgetURL(URL(string: "dayline://open/focus"))
        .containerBackground(for: .widget) { Background(s: s, art: "focus") }
    }
}

// MARK: Next up

struct NextUpWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "DaylineNextUp", provider: DaylineProvider()) { e in NextUpView(s: e.s) }
            .configurationDisplayName("Next up")
            .description("The next task on your plate.")
            .supportedFamilies([.systemSmall, .systemMedium])
    }
}

struct NextUpView: View {
    let s: WidgetSnapshot
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Header(title: "Next up", symbol: "arrow.right.circle.fill", s: s)
            Spacer(minLength: 0)
            if let t = s.next {
                Text(t.title).font(.system(size: 15, weight: .semibold)).lineLimit(3).strikethrough(t.done)
                if let w = t.when { Text(w).font(.system(size: 11)).foregroundStyle(t.overdue ? Color(rgb: 0xC62A36) : .secondary) }
                Spacer(minLength: 0)
                Button(intent: ToggleTaskIntent(id: t.id)) {
                    Label(t.done ? "Done" : "Complete", systemImage: "checkmark")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(s.accentColor)
                        .frame(maxWidth: .infinity, minHeight: 26)
                        .background(RoundedRectangle(cornerRadius: 8, style: .continuous).fill(s.accentColor.opacity(0.15)))
                }
                .buttonStyle(.plain)
            } else {
                Text("Nothing left for today 🎉").font(.system(size: 13, weight: .semibold))
                Spacer(minLength: 0)
            }
        }
        .widgetURL(URL(string: s.next.map { "dayline://task/\($0.id)" } ?? "dayline://open/today"))
        .containerBackground(for: .widget) { Background(s: s, art: "next_up") }
    }
}

// MARK: Quick add

struct QuickAddWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "DaylineQuickAdd", provider: DaylineProvider()) { e in QuickAddView(s: e.s) }
            .configurationDisplayName("Quick add")
            .description("Add a task in one click.")
            .supportedFamilies([.systemSmall])
    }
}

struct QuickAddView: View {
    let s: WidgetSnapshot
    var body: some View {
        VStack(spacing: 10) {
            Image(systemName: "plus").font(.system(size: 26, weight: .semibold))
                .foregroundStyle(s.monochrome ? Color(nsColor: .windowBackgroundColor) : .white)
                .frame(width: 58, height: 58)
                .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(s.accentFill))
            Text("Add a task").font(.system(size: 13, weight: .semibold))
        }
        .widgetURL(URL(string: "dayline://new-task"))
        .containerBackground(for: .widget) { Background(s: s) }
    }
}

// MARK: Samples

extension WidgetSnapshot {
    static var empty: WidgetSnapshot { WidgetSnapshot() }
    static var sample: WidgetSnapshot {
        var s = WidgetSnapshot()
        s.weekday = "Wednesday"
        s.tasks = [
            .init(id: 1, title: "Review homepage mockups", done: false, when: nil, priority: 3, overdue: false),
            .init(id: 2, title: "Draft launch announcement", done: false, when: "11:00 AM", priority: 2, overdue: false),
            .init(id: 3, title: "Call the plumber", done: false, when: "4:30 PM", priority: 1, overdue: false),
            .init(id: 4, title: "Morning run", done: true, when: nil, priority: 1, overdue: false),
        ]
        s.habits = [
            .init(id: 1, name: "Read 20 pages", symbol: "book.fill", color: 0x4F5BD5, count: 1, target: 1, done: true, streak: 3),
            .init(id: 2, name: "Drink water", symbol: "drop.fill", color: 0x00A2C7, count: 5, target: 8, done: false, streak: 12),
            .init(id: 3, name: "Meditate", symbol: "figure.mind.and.body", color: 0x0E9F8E, count: 0, target: 1, done: false, streak: 2),
        ]
        s.done = 3; s.total = 8
        s.next = s.tasks[1]
        s.focus.minutesToday = 50
        return s
    }
}
