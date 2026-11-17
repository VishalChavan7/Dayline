import Foundation
import AppKit
import Combine

/// Checks GitHub Releases for a newer Mac build ("Dayline-mac-<version>.zip" on a "mac-v<version>" release),
/// downloads it, and swaps it in place of the running app.
@MainActor
final class Updater: ObservableObject {
    enum State: Equatable {
        case idle, checking, upToDate
        case available(version: String, notes: String)
        case downloading
        case ready(version: String)
        case failed(String)
    }

    static let repo = "VishalChavan7/Dayline"
    @Published private(set) var state: State = .idle
    @Published var showPrompt = false
    private var assetURL: URL?
    private var downloaded: URL?
    private var timer: Timer?
    private let d = UserDefaults.standard

    var current: String { Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "0" }
    var isBundled: Bool { Bundle.main.bundleURL.pathExtension == "app" }

    init() {
        let t = Timer(timeInterval: 12 * 3600, repeats: true) { [weak self] _ in Task { @MainActor in await self?.check(userInitiated: false) } }
        RunLoop.main.add(t, forMode: .common)
        timer = t
    }

    func checkSoonIfStale() {
        let last = d.object(forKey: "update.lastCheck") as? Date ?? .distantPast
        if Date().timeIntervalSince(last) > 6 * 3600 { Task { await check(userInitiated: false) } }
    }

    func check(userInitiated: Bool) async {
        guard isBundled else { if userInitiated { state = .failed("Updates work in the installed app.") }; return }
        if case .downloading = state { return }
        state = .checking
        d.set(Date(), forKey: "update.lastCheck")
        do {
            var r = URLRequest(url: URL(string: "https://api.github.com/repos/\(Self.repo)/releases?per_page=30")!)
            r.setValue("application/vnd.github+json", forHTTPHeaderField: "Accept")
            let (data, resp) = try await URLSession.shared.data(for: r)
            guard (resp as? HTTPURLResponse)?.statusCode == 200, let list = try JSONSerialization.jsonObject(with: data) as? [[String: Any]] else {
                state = userInitiated ? .failed("Couldn't reach GitHub. Try again later.") : .idle
                return
            }
            for rel in list {
                guard (rel["draft"] as? Bool) != true, (rel["prerelease"] as? Bool) != true,
                      let tag = rel["tag_name"] as? String, tag.hasPrefix("mac-v"),
                      let assets = rel["assets"] as? [[String: Any]],
                      let asset = assets.first(where: { (($0["name"] as? String) ?? "").hasPrefix("Dayline-mac") && (($0["name"] as? String) ?? "").hasSuffix(".zip") }),
                      let url = (asset["browser_download_url"] as? String).flatMap(URL.init(string:)) else { continue }
                let version = String(tag.dropFirst(5))
                if Self.isNewer(version, than: current) {
                    assetURL = url
                    let notes = ((rel["body"] as? String) ?? "").components(separatedBy: "## Install").first?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                    state = .available(version: version, notes: notes)
                    await download(version: version)
                } else {
                    state = userInitiated ? .upToDate : .idle
                }
                return
            }
            state = userInitiated ? .upToDate : .idle
        } catch {
            state = userInitiated ? .failed("Couldn't check for updates: \(error.localizedDescription)") : .idle
        }
    }

    private func download(version: String) async {
        guard let assetURL else { return }
        state = .downloading
        do {
            let (tmp, resp) = try await URLSession.shared.download(from: assetURL)
            guard (resp as? HTTPURLResponse)?.statusCode == 200 else { throw SyncError("Download failed.") }
            let dir = FileManager.default.temporaryDirectory.appendingPathComponent("dayline-update-\(version)", isDirectory: true)
            try? FileManager.default.removeItem(at: dir)
            try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
            try run("/usr/bin/ditto", ["-x", "-k", tmp.path, dir.path])
            let app = dir.appendingPathComponent("Dayline.app")
            guard let b = Bundle(url: app), b.bundleIdentifier == Bundle.main.bundleIdentifier,
                  let v = b.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String, v == version else {
                throw SyncError("The downloaded update isn't a valid Dayline app.")
            }
            try run("/usr/bin/codesign", ["--verify", "--deep", app.path])
            downloaded = app
            state = .ready(version: version)
            showPrompt = true
        } catch {
            state = .failed("Update download failed: \(error.localizedDescription)")
        }
    }

    /// Replaces this app with the downloaded one and relaunches.
    func installAndRelaunch() {
        guard let new = downloaded else { return }
        let target = Bundle.main.bundleURL.path
        let pid = ProcessInfo.processInfo.processIdentifier
        let script = """
        while kill -0 \(pid) 2>/dev/null; do sleep 0.2; done
        rm -rf "\(target).old" && mv "\(target)" "\(target).old" && mv "\(new.path)" "\(target)" && rm -rf "\(target).old"
        xattr -dr com.apple.quarantine "\(target)" 2>/dev/null
        open "\(target)"
        """
        let p = Process()
        p.executableURL = URL(fileURLWithPath: "/bin/sh")
        p.arguments = ["-c", script]
        try? p.run()
        NSApp.terminate(nil)
    }

    private func run(_ tool: String, _ args: [String]) throws {
        let p = Process()
        p.executableURL = URL(fileURLWithPath: tool)
        p.arguments = args
        p.standardOutput = FileHandle.nullDevice
        p.standardError = FileHandle.nullDevice
        try p.run()
        p.waitUntilExit()
        if p.terminationStatus != 0 { throw SyncError("\(URL(fileURLWithPath: tool).lastPathComponent) failed") }
    }

    static func isNewer(_ a: String, than b: String) -> Bool {
        let x = a.split(separator: ".").map { Int($0) ?? 0 }, y = b.split(separator: ".").map { Int($0) ?? 0 }
        for i in 0..<max(x.count, y.count) {
            let p = i < x.count ? x[i] : 0, q = i < y.count ? y[i] : 0
            if p != q { return p > q }
        }
        return false
    }

    var caption: String {
        switch state {
        case .idle: return "Version \(current)"
        case .checking: return "Checking for updates…"
        case .upToDate: return "Dayline \(current) is the latest version"
        case .available(let v, _): return "Version \(v) is available"
        case .downloading: return "Downloading the update…"
        case .ready(let v): return "Version \(v) is ready to install"
        case .failed(let m): return m
        }
    }
}
