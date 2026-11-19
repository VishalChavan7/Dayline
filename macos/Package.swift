// swift-tools-version:5.9
import PackageDescription
import Foundation

// Widgets: the compiler records the App Intents' constant values for Xcode's metadata tool (see build.sh).
let root = URL(fileURLWithPath: #filePath).deletingLastPathComponent().path

let package = Package(
    name: "Dayline",
    platforms: [.macOS(.v14)],
    targets: [
        // Data model, sync file format and all the rules (repeats, streaks, stats, quick add, focus timer).
        .target(name: "DaylineCore", path: "Sources/DaylineCore"),
        // The note editor's text engine (AppKit): HTML in and out, blocks, and every formatting command.
        .target(name: "DaylineEditor", dependencies: ["DaylineCore"], path: "Sources/DaylineEditor"),
        // The SwiftUI app.
        .executableTarget(name: "Dayline", dependencies: ["DaylineCore", "DaylineEditor"], path: "Sources/Dayline"),
        // Desktop widgets (bundled into Dayline.app as a WidgetKit extension by build.sh).
        .executableTarget(name: "DaylineWidgets", dependencies: ["DaylineCore"], path: "Sources/DaylineWidgets",
                          swiftSettings: [.unsafeFlags(["-application-extension", "-emit-const-values",
                                                         "-Xfrontend", "-const-gather-protocols-file",
                                                         "-Xfrontend", root + "/Resources/AppIntentsProtocols.json"])],
                          linkerSettings: [.linkedFramework("AppIntents"), .linkedFramework("WidgetKit"), .unsafeFlags(["-Xlinker", "-application_extension", "-Xlinker", "-e", "-Xlinker", "_NSExtensionMain"])]),
        // Self-checks for DaylineCore (runs without Xcode's XCTest).
        .executableTarget(name: "DaylineChecks", dependencies: ["DaylineCore", "DaylineEditor"], path: "Sources/DaylineChecks"),
    ]
)
