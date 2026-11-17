import Foundation
import Combine
import AppKit
import DaylineCore

/// Keeps the Mac in step with the phone through your own Google Drive, using the same private file
/// ("dayline-sync.json" in Drive's hidden app folder) and the same rules as the phone:
/// records are merged one by one (SyncMerge), so changes made on each device between syncs are all kept.
@MainActor
final class SyncService: ObservableObject {
    enum Status: Equatable { case idle, syncing, done(String), failed(String) }

    @Published private(set) var status: Status = .idle
    @Published private(set) var account: String?
    @Published private(set) var lastSync: Date?
    @Published private(set) var hasConflictCopy = false

    private let store: Store
    private let auth = GoogleAuth()
    private var soon: Task<Void, Never>?
    private var periodic: Timer?
    private var running = false
    private let d = UserDefaults.standard
    static let fileName = "dayline-sync.json"

    private let demo: Bool

    init(store: Store, demo: Bool = false) {
        self.store = store
        self.demo = demo
        // Demo data (screenshots) must never reach your Google Drive.
        account = demo ? nil : d.string(forKey: "sync.account")
        lastSync = d.object(forKey: "sync.lastAt") as? Date
        hasConflictCopy = FileManager.default.fileExists(atPath: conflictURL.path)
        // While Dayline is in front, look for the phone's changes every 20 s; otherwise every 5 minutes.
        let t = Timer(timeInterval: 20, repeats: true) { [weak self] _ in
            Task { @MainActor in
                guard let self else { return }
                let active = NSApp?.isActive ?? false
                if active || Date().timeIntervalSince(self.lastSync ?? .distantPast) > 300 { self.syncIfEnabled() }
            }
        }
        RunLoop.main.add(t, forMode: .common)
        periodic = t
    }

    var enabled: Bool { account != nil }
    private var localHash: String { get { d.string(forKey: "sync.localHash") ?? "" } set { d.set(newValue, forKey: "sync.localHash") } }
    private var remoteVersion: String { get { d.string(forKey: "sync.remoteVersion") ?? "" } set { d.set(newValue, forKey: "sync.remoteVersion") } }
    private var conflictURL: URL { Store.supportDirectory.appendingPathComponent("sync-conflict-copy.json") }

    // MARK: Account

    func connect(chooseAccount: Bool = false) {
        guard !demo else { return }
        Task {
            do {
                let t = try await auth.signIn(chooseAccount: chooseAccount)
                account = t.email
                d.set(t.email, forKey: "sync.account")
                localHash = ""; remoteVersion = ""
                await syncNow()
            } catch {
                status = .failed(error.localizedDescription)
            }
        }
    }

    /// Stops syncing and gives Drive access back to Google. The copy in Drive stays.
    func disconnect() {
        Task {
            await GoogleAuth.revoke()
            account = nil
            d.removeObject(forKey: "sync.account")
            localHash = ""; remoteVersion = ""
            try? FileManager.default.removeItem(at: baseURL)
            status = .idle
        }
    }

    func switchAccount() {
        Task {
            await GoogleAuth.revoke()
            account = nil
            d.removeObject(forKey: "sync.account")
            try? FileManager.default.removeItem(at: baseURL)
            connect(chooseAccount: true)
        }
    }

    // MARK: Syncing

    /// A quick sync shortly after you change something.
    func scheduleSoon(_ seconds: Double = 3) {
        guard enabled else { return }
        soon?.cancel()
        soon = Task { [weak self] in
            try? await Task.sleep(for: .seconds(seconds))
            guard !Task.isCancelled else { return }
            await self?.syncNow()
        }
    }

    func syncIfEnabled() { if enabled { Task { await syncNow() } } }

    func syncNow() async {
        guard enabled, !demo, !running else { return }
        running = true
        defer { running = false }
        status = .syncing
        do {
            guard let token = try await GoogleAuth.accessToken() else {
                status = .failed("Sign in again to keep syncing.")
                return
            }
            let outcome = try await round(Drive(token: token))
            lastSync = Date()
            d.set(lastSync, forKey: "sync.lastAt")
            status = .done(outcome)
        } catch let e as SyncError {
            status = .failed(e.message)
        } catch let e as URLError {
            status = .failed(e.code == .notConnectedToInternet ? "No connection — will try again later." : "Sync failed: \(e.localizedDescription)")
        } catch {
            status = .failed("Sync failed: \(error.localizedDescription)")
        }
    }

    private var baseURL: URL { Store.supportDirectory.appendingPathComponent("sync-base.json") }

    /// One sync: merge this Mac with the cloud copy record by record (see SyncMerge), apply, upload.
    private func round(_ drive: Drive) async throws -> String {
        let local = BackupCodec.encode(store.data)
        let localNow = BackupCodec.fingerprint(store.data)
        guard let remote = try await drive.find(Self.fileName) else {
            let created = try await drive.create(Self.fileName, local)
            try? local.write(to: baseURL, options: .atomic)
            localHash = localNow; remoteVersion = created.version
            return "Backed up to Google Drive"
        }
        let localChanged = localNow != localHash
        let remoteChanged = remote.version != remoteVersion
        if !localChanged && !remoteChanged { return "Up to date" }
        let base = remoteVersion.isEmpty ? nil : try? Data(contentsOf: baseURL)
        let remoteData = (remoteChanged || base == nil) ? try await drive.download(remote.id) : base!
        let merged = try SyncMerge.merge(base: base, local: local, remote: remoteData)
        let mergedCanon = SyncMerge.canonFile(merged)
        var pulled = false, pushed = false
        if mergedCanon != SyncMerge.canonFile(local) {
            store.replaceAll(with: try BackupCodec.decode(merged), keepSafetyCopy: false)
            pulled = true
        }
        var version = remote.version
        if mergedCanon != SyncMerge.canonFile(remoteData) {
            version = try await drive.update(remote.id, merged).version
            pushed = true
        }
        try? merged.write(to: baseURL, options: .atomic)
        localHash = BackupCodec.fingerprint(store.data)
        remoteVersion = version
        return pulled && pushed ? "Merged changes from your phone" : pulled ? "Updated from your phone" : pushed ? "Synced" : "Up to date"
    }

    func restoreConflictCopy() {
        guard let data = try? Data(contentsOf: conflictURL), let d = try? BackupCodec.decode(data) else { return }
        store.replaceAll(with: d)
        try? FileManager.default.removeItem(at: conflictURL)
        hasConflictCopy = false
        Task { await syncNow() }
    }

    func discardConflictCopy() {
        try? FileManager.default.removeItem(at: conflictURL)
        hasConflictCopy = false
    }

    var statusText: String {
        switch status {
        case .idle: return enabled ? (lastSync.map { "Synced \($0.formatted(.relative(presentation: .named)))" } ?? "Not synced yet") : "Sync is off"
        case .syncing: return "Syncing…"
        case .done(let m): return m
        case .failed(let m): return m
        }
    }
}

/// Minimal Drive v3 client for one file in the private app-data folder.
struct Drive {
    let token: String
    struct RemoteFile { let id: String; let version: String }

    func find(_ name: String) async throws -> RemoteFile? {
        var c = URLComponents(string: "https://www.googleapis.com/drive/v3/files")!
        c.queryItems = [.init(name: "spaces", value: "appDataFolder"), .init(name: "q", value: "name = '\(name)' and trashed = false"),
                        .init(name: "fields", value: "files(id,version,modifiedTime)")]
        let o = try await json("GET", c.url!)
        guard let f = (o["files"] as? [[String: Any]])?.first, let id = f["id"] as? String else { return nil }
        return RemoteFile(id: id, version: (f["version"] as? String) ?? (f["modifiedTime"] as? String) ?? "")
    }

    func download(_ id: String) async throws -> Data { try await request("GET", URL(string: "https://www.googleapis.com/drive/v3/files/\(id)?alt=media")!) }

    func create(_ name: String, _ content: Data) async throws -> RemoteFile {
        let boundary = "dayline\(UInt64(Date().timeIntervalSince1970 * 1000))"
        let meta = try JSONSerialization.data(withJSONObject: ["name": name, "parents": ["appDataFolder"]])
        var body = Data("--\(boundary)\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n".utf8)
        body += meta
        body += Data("\r\n--\(boundary)\r\nContent-Type: application/json\r\n\r\n".utf8)
        body += content
        body += Data("\r\n--\(boundary)--".utf8)
        let o = try await json("POST", URL(string: "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,version")!, body, "multipart/related; boundary=\(boundary)")
        return RemoteFile(id: o["id"] as? String ?? "", version: o["version"] as? String ?? "")
    }

    func update(_ id: String, _ content: Data) async throws -> RemoteFile {
        let o = try await json("PATCH", URL(string: "https://www.googleapis.com/upload/drive/v3/files/\(id)?uploadType=media&fields=id,version")!, content, "application/json")
        return RemoteFile(id: o["id"] as? String ?? id, version: o["version"] as? String ?? "")
    }

    private func json(_ method: String, _ url: URL, _ body: Data? = nil, _ type: String? = nil) async throws -> [String: Any] {
        let d = try await request(method, url, body, type)
        return ((try? JSONSerialization.jsonObject(with: d)) as? [String: Any]) ?? [:]
    }

    private func request(_ method: String, _ url: URL, _ body: Data? = nil, _ type: String? = nil) async throws -> Data {
        var r = URLRequest(url: url, timeoutInterval: 30)
        r.httpMethod = method
        r.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        if let body { r.httpBody = body; r.setValue(type ?? "application/json", forHTTPHeaderField: "Content-Type") }
        let (d, resp) = try await URLSession.shared.data(for: r)
        let code = (resp as? HTTPURLResponse)?.statusCode ?? 0
        guard (200..<300).contains(code) else {
            let err = String(data: d, encoding: .utf8) ?? ""
            switch code {
            case 401: throw SyncError("Google sign-in expired — reconnect in Settings.", code: 401)
            case 403: throw SyncError(err.contains("accessNotConfigured") || err.contains("has not been used") ? "Google Drive sync is temporarily unavailable. Please try again later." : "Google Drive refused access (403).", code: 403)
            default: throw SyncError("Google Drive error \(code).", code: code)
            }
        }
        return d
    }
}
