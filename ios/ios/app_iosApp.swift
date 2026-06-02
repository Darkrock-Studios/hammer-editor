//
//  app_iosApp.swift
//  ios
//

import SwiftUI
import Hammer

@main
struct app_iosApp: App {

    @UIApplicationDelegateAdaptor(AppDelegate.self)
    var appDelegate: AppDelegate

    init() {
        NapierProxyKt.debugBuild()
        HammerAppInitKt.initializeHammerApp()
    }

    var body: some SwiftUI.Scene {
        WindowGroup {
            ComposeContainer(root: appDelegate.root)
                .ignoresSafeArea()
        }
    }
}
