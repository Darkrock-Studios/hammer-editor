//
//  ComposeContainer.swift
//  ios
//
//  Hosts the shared Compose Multiplatform UI inside SwiftUI.
//

import SwiftUI
import UIKit
import Hammer

struct ComposeContainer: UIViewControllerRepresentable {
    let root: IosRoot

    func makeUIViewController(context: Context) -> UIViewController {
        let shortcutHost = ProjectShortcutHost()
        let compose = MainViewControllerKt.MainViewController(root: root, shortcutHost: shortcutHost)
        return ShortcutHostController(shortcutHost: shortcutHost, content: compose)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
    }
}

/// Compose routes key events along the focus path, so a screen with nothing focused never sees
/// them. UIKit key commands travel the responder chain instead and always reach this controller.
final class ShortcutHostController: UIViewController {

    /// UIKit reports function keys as these private-use characters (NSF3FunctionKey).
    private static let f3Input = String(UnicodeScalar(0xF706)!)

    private let shortcutHost: ProjectShortcutHost
    private let content: UIViewController

    init(shortcutHost: ProjectShortcutHost, content: UIViewController) {
        self.shortcutHost = shortcutHost
        self.content = content
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) is not used")
    }

    override func viewDidLoad() {
        super.viewDidLoad()

        addChild(content)
        content.view.frame = view.bounds
        content.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(content.view)
        content.didMove(toParent: self)
    }

    override var childForStatusBarStyle: UIViewController? { content }

    override var childForStatusBarHidden: UIViewController? { content }

    override var canBecomeFirstResponder: Bool { true }

    override var keyCommands: [UIKeyCommand]? {
        [
            UIKeyCommand(input: Self.f3Input, modifierFlags: [], action: #selector(startSync)),
            UIKeyCommand(input: "s", modifierFlags: [.command, .alternate], action: #selector(saveAll)),
            UIKeyCommand(input: "s", modifierFlags: [.control, .alternate], action: #selector(saveAll)),
        ]
    }

    @objc private func startSync() {
        _ = shortcutHost.startProjectSync()
    }

    @objc private func saveAll() {
        _ = shortcutHost.saveAllBuffers()
    }
}
