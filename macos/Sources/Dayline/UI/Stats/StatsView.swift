import SwiftUI
import Charts
import DaylineCore

struct StatsView: View {
    @EnvironmentObject var store: Store
    @State private var period: StatsPeriod = .week
    @State private var anchor = Day.today()

    var body: some View {
        let r = StatsCalculator.report(store.data, period: period, anchor: anchor, today: store.today, weekStart: store.weekStart)
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                ScreenTitle(title: "Statistics", subtitle: rangeText(r))
                HStack {
                    Segmented(options: StatsPeriod.allCases, selection: $period) { $0.label }
                    Spacer()
                    IconButton(symbol: "chevron.left", help: "Previous") { anchor = shift(-1) }
                    Text(rangeText(r)).font(.system(size: 13, weight: .semibold)).frame(minWidth: 180)
                    IconButton(symbol: "chevron.right", help: "Next") { anchor = shift(1) }.disabled(!(r.end < store.today))
                    Button("Now") { anchor = store.today }.buttonStyle(.tonal)
                }

                if !r.hasAnyData {
                    EmptyState(symbol: "chart.bar", title: "No activity yet", message: "Complete tasks, check in habits or run a focus session, and your trends show up here.")
                } else {
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 170), spacing: 12)], spacing: 12) {
                        tile("Completed", "\(r.completed)", change: r.completedChange, symbol: "checkmark.circle.fill", color: StatusColor.success)
                        tile("Completion rate", "\(Int((r.completionRate * 100).rounded()))%", sub: "\(r.remaining) still due", symbol: "percent", color: .accentColor)
                        tile("Focus", Formats.minutes(r.focusMinutes), change: r.focusChange, sub: "\(r.focusSessions) sessions · avg \(r.averageSessionMinutes)m", symbol: "timer", color: .orange)
                        tile("Overdue now", "\(r.overdueNow)", sub: "\(r.overdueInPeriod) missed this \(period.label.lowercased())", symbol: "exclamationmark.circle.fill", color: StatusColor.overdue)
                        tile("Habits", "\(Int((r.habitConsistency * 100).rounded()))%", sub: "\(r.habitCheckIns) check-ins", symbol: "flame.fill", color: StatusColor.streak)
                    }

                    HStack(alignment: .top, spacing: 12) {
                        chartCard("Tasks completed") {
                            Chart(r.completedSeries, id: \.date) { v in
                                BarMark(x: .value("Day", v.date.date(), unit: .day), y: .value("Completed", v.value)).foregroundStyle(StatusColor.success.gradient).cornerRadius(3)
                            }
                        }
                        chartCard("Focus minutes") {
                            Chart(r.focusSeries, id: \.date) { v in
                                BarMark(x: .value("Day", v.date.date(), unit: .day), y: .value("Minutes", v.value)).foregroundStyle(Color.orange.gradient).cornerRadius(3)
                            }
                        }
                    }
                    HStack(alignment: .top, spacing: 12) {
                        chartCard("Overdue over time") {
                            Chart(r.overdueSeries, id: \.date) { v in
                                AreaMark(x: .value("Day", v.date.date(), unit: .day), y: .value("Overdue", v.value)).foregroundStyle(StatusColor.overdue.opacity(0.2))
                                LineMark(x: .value("Day", v.date.date(), unit: .day), y: .value("Overdue", v.value)).foregroundStyle(StatusColor.overdue)
                            }
                            .chartXAxis { AxisMarks(values: .stride(by: .day, count: max(1, r.overdueSeries.count / 7))) { _ in AxisGridLine(); AxisValueLabel(format: .dateTime.day().month(.abbreviated)) } }
                        }
                        chartCard("By priority") {
                            let items = Priority.allCases.reversed().map { ($0, r.priorityDistribution[$0] ?? 0) }.filter { $0.1 > 0 }
                            if items.isEmpty { Text("No tasks due in this period.").foregroundStyle(.secondary).frame(maxWidth: .infinity, maxHeight: .infinity) }
                            else {
                                Chart(items, id: \.0) { p, n in
                                    SectorMark(angle: .value("Tasks", n), innerRadius: .ratio(0.6), angularInset: 1.5)
                                        .foregroundStyle(p.color).cornerRadius(3)
                                        .annotation(position: .overlay) { Text("\(n)").font(.caption.weight(.semibold)).foregroundStyle(.white) }
                                }
                                .chartLegend(.hidden)
                                .overlay(alignment: .trailing) {
                                    VStack(alignment: .leading, spacing: 4) {
                                        ForEach(items, id: \.0) { p, n in HStack(spacing: 6) { Circle().fill(p.color).frame(width: 8, height: 8); Text("\(p.label) \(n)").font(.caption) } }
                                    }
                                }
                            }
                        }
                    }
                    Card {
                        VStack(alignment: .leading, spacing: 10) {
                            Text("Completed tasks — last 18 weeks").font(.headline)
                            Heatmap(values: r.heatmap)
                        }
                    }
                }
            }
            .padding(.horizontal, 28).padding(.vertical, 22)
            .frame(maxWidth: 1100)
            .frame(maxWidth: .infinity)
        }
        .navigationTitle("Statistics")
    }

    func shift(_ n: Int) -> Day {
        switch period { case .day: anchor.plusDays(n); case .week: anchor.plusWeeks(n); case .month: anchor.plusMonths(n) }
    }

    func rangeText(_ r: StatsReport) -> String {
        let f = Formats(store: store)
        switch period {
        case .day: return f.date(r.start)
        case .week: return "\(f.date(r.start, relative: false)) – \(f.date(r.end, relative: false))"
        case .month: return r.start.date().formatted(.dateTime.month(.wide).year())
        }
    }

    func tile(_ title: String, _ value: String, change: Int? = nil, sub: String? = nil, symbol: String, color: Color) -> some View {
        Card(padding: 14) {
            VStack(alignment: .leading, spacing: 4) {
                Image(systemName: symbol).font(.system(size: 15, weight: .semibold)).foregroundStyle(color)
                HStack(alignment: .firstTextBaseline, spacing: 6) {
                    Text(value).font(.system(size: 24, weight: .semibold).monospacedDigit())
                    if let change {
                        Text("\(change >= 0 ? "▲" : "▼") \(abs(change))%").font(.caption.weight(.semibold))
                            .foregroundStyle(change >= 0 ? StatusColor.success : StatusColor.overdue)
                            .help("Compared with the previous \(period.label.lowercased())")
                    }
                }
                Text(title).font(.system(size: 12.5, weight: .medium))
                Text(sub ?? " ").font(.caption).foregroundStyle(.secondary)
            }
        }
    }

    func chartCard<C: View>(_ title: String, @ViewBuilder _ content: () -> C) -> some View {
        Card {
            VStack(alignment: .leading, spacing: 10) {
                Text(title).font(.headline)
                content().frame(height: 170)
            }
        }
    }
}

struct Heatmap: View {
    let values: [DayValue]
    var body: some View {
        let weeks = stride(from: 0, to: values.count, by: 7).map { Array(values[$0..<min($0 + 7, values.count)]) }
        let maxV = max(1, values.map(\.value).max() ?? 1)
        HStack(alignment: .top, spacing: 3) {
            ForEach(weeks.indices, id: \.self) { w in
                VStack(spacing: 3) {
                    ForEach(weeks[w], id: \.date) { v in
                        RoundedRectangle(cornerRadius: 3)
                            .fill(v.value < 0 ? Color.clear : v.value == 0 ? Color.primary.opacity(0.07) : Color.accentColor.opacity(0.25 + 0.75 * Double(v.value) / Double(maxV)))
                            .frame(width: 16, height: 16)
                            .help(v.value < 0 ? "" : "\(v.date.date().formatted(date: .abbreviated, time: .omitted)): \(v.value) completed")
                    }
                }
            }
        }
    }
}
