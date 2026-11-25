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
        let compose = MainViewControllerKt.MainViewController(root: root)
        return ShortcutHostController(root: root, content: compose)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
    }
}

/// Compose routes key events along the focus path, so a screen with nothing focused never sees
/// them. UIKit key commands travel the responder chain instead and always reach this controller.
final class ShortcutHostController: UIViewController {

    private let root: IosRoot
    private let content: UIViewController

    init(root: IosRoot, content: UIViewController) {
        self.root = root
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
        guard let actions = root.currentActions() else { return [] }
        return IosKeyCommandsKt.iosKeyCommandSpecs(keymap: actions.keymap).map { spec in
            var flags: UIKeyModifierFlags = []
            if spec.command { flags.insert(.command) }
            if spec.shift { flags.insert(.shift) }
            return UIKeyCommand(
                action: #selector(performAction(_:)),
                input: spec.input,
                modifierFlags: flags,
                propertyList: spec.actionId
            )
        }
    }

    /// A command whose action is not live right now is refused, so its key falls through to Compose.
    override func canPerformAction(_ action: Selector, withSender sender: Any?) -> Bool {
        guard action == #selector(performAction(_:)) else {
            return super.canPerformAction(action, withSender: sender)
        }
        guard let actionId = (sender as? UIKeyCommand)?.propertyList as? String,
              let actions = root.currentActions() else {
            return false
        }
        return IosActionBridgeKt.isLiveRaw(actions, actionId: actionId)
    }

    @objc private func performAction(_ sender: UIKeyCommand) {
        guard let actionId = sender.propertyList as? String,
              let actions = root.currentActions() else {
            return
        }
        _ = IosActionBridgeKt.invokeRaw(actions, actionId: actionId)
    }
}
