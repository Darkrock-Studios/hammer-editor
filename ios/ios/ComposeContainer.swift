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
        MainViewControllerKt.MainViewController(root: root)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
    }
}
