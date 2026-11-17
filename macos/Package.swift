// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "Dayline",
    platforms: [.macOS(.v14)],
    targets: [
        // Data model, sync file format and all the rules (repeats, streaks, stats, quick add, focus timer).
        .target(name: "DaylineCore", path: "Sources/DaylineCore"),
        // The SwiftUI app.
        .executableTarget(name: "Dayline", dependencies: ["DaylineCore"], path: "Sources/Dayline"),
        // Self-checks for DaylineCore (runs without Xcode's XCTest).
        .executableTarget(name: "DaylineChecks", dependencies: ["DaylineCore"], path: "Sources/DaylineChecks"),
    ]
)
