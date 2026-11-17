import Foundation
import AppKit
import AuthenticationServices
import CryptoKit

/// Google sign-in for the Mac app (OAuth 2 with PKCE, "iOS / macOS" client type, no client secret).
/// Tokens are kept in a private file in Application Support (readable only by your user account).
@MainActor
final class GoogleAuth: NSObject, ASWebAuthenticationPresentationContextProviding {
    /// OAuth client for "Dayline for Mac" in the same Google Cloud project as the phone app, so both
    /// apps see the same hidden Drive app folder.
    static let clientId = "932079264593-3v4i1gcj6j3qsif1ke138uo8ppg63ump.apps.googleusercontent.com"
    static var isConfigured: Bool { !clientId.hasPrefix("__") }
    static let scopes = "https://www.googleapis.com/auth/drive.appdata email"

    struct Tokens: Codable {
        var accessToken: String
        var refreshToken: String
        var expiresAt: Date
        var email: String
    }

    private var session: ASWebAuthenticationSession?
    private static var tokenFile: URL { Store.supportDirectory.appendingPathComponent("google-auth.json") }

    static func loadTokens() -> Tokens? {
        guard let d = try? Data(contentsOf: tokenFile) else { return nil }
        return try? JSONDecoder().decode(Tokens.self, from: d)
    }

    static func save(_ t: Tokens?) {
        guard let t else { try? FileManager.default.removeItem(at: tokenFile); return }
        guard let d = try? JSONEncoder().encode(t) else { return }
        try? d.write(to: tokenFile, options: [.atomic])
        try? FileManager.default.setAttributes([.posixPermissions: 0o600], ofItemAtPath: tokenFile.path)
    }

    private var redirectScheme: String { Self.clientId.components(separatedBy: ".").reversed().joined(separator: ".") }
    private var redirectURI: String { "\(redirectScheme):/oauth2redirect" }

    nonisolated func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        MainActor.assumeIsolated { NSApp.keyWindow ?? NSApp.windows.first ?? NSWindow() }
    }

    /// Opens Google's sign-in page and returns tokens for the chosen account.
    func signIn(chooseAccount: Bool) async throws -> Tokens {
        guard Self.isConfigured else { throw SyncError("Google sign-in isn't set up in this build yet.") }
        let verifier = Self.randomString(64)
        let challenge = Data(SHA256.hash(data: Data(verifier.utf8))).base64URL
        var c = URLComponents(string: "https://accounts.google.com/o/oauth2/v2/auth")!
        c.queryItems = [
            .init(name: "client_id", value: Self.clientId), .init(name: "redirect_uri", value: redirectURI),
            .init(name: "response_type", value: "code"), .init(name: "scope", value: Self.scopes),
            .init(name: "code_challenge", value: challenge), .init(name: "code_challenge_method", value: "S256"),
            .init(name: "access_type", value: "offline"), .init(name: "prompt", value: chooseAccount ? "select_account consent" : "consent"),
        ]
        let callback: URL = try await withCheckedThrowingContinuation { cont in
            let s = ASWebAuthenticationSession(url: c.url!, callbackURLScheme: redirectScheme) { url, error in
                if let url { cont.resume(returning: url) }
                else if let e = error as? ASWebAuthenticationSessionError, e.code == .canceledLogin { cont.resume(throwing: SyncError("Sign-in was cancelled.")) }
                else { cont.resume(throwing: error ?? SyncError("Google sign-in failed.")) }
            }
            s.presentationContextProvider = self
            s.prefersEphemeralWebBrowserSession = chooseAccount
            session = s
            if !s.start() { cont.resume(throwing: SyncError("Couldn't open Google sign-in.")) }
        }
        let items = URLComponents(url: callback, resolvingAgainstBaseURL: false)?.queryItems ?? []
        if let err = items.first(where: { $0.name == "error" })?.value { throw SyncError(err == "access_denied" ? "Access wasn't granted." : "Google sign-in failed (\(err)).") }
        guard let code = items.first(where: { $0.name == "code" })?.value else { throw SyncError("Google didn't return access. Please try again.") }
        let json = try await Self.post("https://oauth2.googleapis.com/token", [
            "client_id": Self.clientId, "code": code, "code_verifier": verifier,
            "grant_type": "authorization_code", "redirect_uri": redirectURI,
        ])
        guard let access = json["access_token"] as? String, let refresh = json["refresh_token"] as? String else {
            throw SyncError("Google didn't return access. Please try again.")
        }
        let expires = (json["expires_in"] as? NSNumber)?.doubleValue ?? 3600
        let email = (try? await Self.email(access)) ?? "Google account"
        let t = Tokens(accessToken: access, refreshToken: refresh, expiresAt: Date().addingTimeInterval(expires - 60), email: email)
        Self.save(t)
        return t
    }

    /// A valid access token, refreshed when needed; nil when you need to sign in again.
    static func accessToken() async throws -> String? {
        guard var t = loadTokens() else { return nil }
        if t.expiresAt > Date() { return t.accessToken }
        do {
            let json = try await post("https://oauth2.googleapis.com/token", [
                "client_id": clientId, "grant_type": "refresh_token", "refresh_token": t.refreshToken,
            ])
            guard let access = json["access_token"] as? String else { return nil }
            t.accessToken = access
            t.expiresAt = Date().addingTimeInterval(((json["expires_in"] as? NSNumber)?.doubleValue ?? 3600) - 60)
            save(t)
            return access
        } catch let e as SyncError where e.code == 400 || e.code == 401 {
            return nil
        }
    }

    static func revoke() async {
        guard let t = loadTokens() else { return }
        _ = try? await post("https://oauth2.googleapis.com/revoke", ["token": t.refreshToken])
        save(nil)
    }

    static func email(_ token: String) async throws -> String {
        var r = URLRequest(url: URL(string: "https://www.googleapis.com/oauth2/v3/userinfo")!)
        r.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        let (d, _) = try await URLSession.shared.data(for: r)
        return ((try JSONSerialization.jsonObject(with: d)) as? [String: Any])?["email"] as? String ?? ""
    }

    static func post(_ url: String, _ form: [String: String]) async throws -> [String: Any] {
        var r = URLRequest(url: URL(string: url)!)
        r.httpMethod = "POST"
        r.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        var c = URLComponents(); c.queryItems = form.map { URLQueryItem(name: $0.key, value: $0.value) }
        r.httpBody = c.percentEncodedQuery?.replacingOccurrences(of: "+", with: "%2B").data(using: .utf8)
        let (d, resp) = try await URLSession.shared.data(for: r)
        let code = (resp as? HTTPURLResponse)?.statusCode ?? 0
        guard (200..<300).contains(code) else { throw SyncError("Google sign-in failed (\(code)).", code: code) }
        return ((try? JSONSerialization.jsonObject(with: d)) as? [String: Any]) ?? [:]
    }

    static func randomString(_ n: Int) -> String {
        let chars = Array("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~")
        return String((0..<n).map { _ in chars.randomElement()! })
    }
}

struct SyncError: LocalizedError {
    let message: String
    var code: Int = 0
    init(_ m: String, code: Int = 0) { message = m; self.code = code }
    var errorDescription: String? { message }
}

extension Data {
    var base64URL: String {
        base64EncodedString().replacingOccurrences(of: "+", with: "-").replacingOccurrences(of: "/", with: "_").replacingOccurrences(of: "=", with: "")
    }
}
