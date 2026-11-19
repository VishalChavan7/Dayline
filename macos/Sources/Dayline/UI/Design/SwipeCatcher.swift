import SwiftUI
import AppKit

/// Reports a two-finger sideways swipe on the trackpad over the view it sits behind.
/// `true` = fingers moved right to left. One report per swipe; vertical scrolling and views
/// that scroll sideways themselves (a row of chips, a wide table) are left alone.
struct SwipeCatcher: NSViewRepresentable {
    var onSwipe: (_ leftward: Bool) -> Void

    func makeNSView(context: Context) -> CatcherView {
        let v = CatcherView()
        v.onSwipe = onSwipe
        return v
    }

    func updateNSView(_ v: CatcherView, context: Context) { v.onSwipe = onSwipe }

    final class CatcherView: NSView {
        var onSwipe: ((Bool) -> Void)?
        private var monitor: Any?
        private var dx: CGFloat = 0
        private var dy: CGFloat = 0
        private var fired = false
        private var tracking = false

        override func hitTest(_ point: NSPoint) -> NSView? { nil }   // never takes clicks

        override func viewDidMoveToWindow() {
            super.viewDidMoveToWindow()
            if window != nil, monitor == nil {
                monitor = NSEvent.addLocalMonitorForEvents(matching: .scrollWheel) { [weak self] e in
                    self?.handle(e)
                    return e
                }
            } else if window == nil, let m = monitor {
                NSEvent.removeMonitor(m)
                monitor = nil
            }
        }

        deinit { if let monitor { NSEvent.removeMonitor(monitor) } }

        func handle(_ e: NSEvent) {
            guard let window, e.window === window, e.momentumPhase.isEmpty else { return }
            if e.phase.contains(.began) {
                let p = convert(e.locationInWindow, from: nil)
                tracking = bounds.contains(p) && !scrollsSideways(at: e.locationInWindow)
                dx = 0; dy = 0; fired = false
            }
            guard tracking, e.phase.contains(.began) || e.phase.contains(.changed) else { return }
            // Finger movement: positive = to the right, with natural scrolling on or off.
            dx += e.isDirectionInvertedFromDevice ? e.scrollingDeltaX : -e.scrollingDeltaX
            dy += abs(e.scrollingDeltaY)
            guard !fired, abs(dx) > 60, abs(dx) > dy * 1.6 else { return }
            fired = true
            onSwipe?(dx < 0)
        }

        /// True over a scroll view whose content is wider than it is, so it needs sideways scrolling.
        private func scrollsSideways(at point: NSPoint) -> Bool {
            var v = window?.contentView?.hitTest(point)
            while let view = v {
                if let s = view as? NSScrollView, let doc = s.documentView, doc.frame.width > s.contentView.bounds.width + 1 { return true }
                v = view.superview
            }
            return false
        }
    }
}
