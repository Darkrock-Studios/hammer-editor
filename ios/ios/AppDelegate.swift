//
//  AppDelegate.swift
//  ios
//
//  Created by Adam Brown on 1/15/24.
//  Copyright © 2024 orgName. All rights reserved.
//

import Foundation
import SwiftUI
import UIKit
import Hammer

class AppDelegate: NSObject, UIApplicationDelegate {
    let root: IosRoot = IosRootComponent(
        componentContext: DefaultComponentContext(lifecycle: ApplicationLifecycle())
    )
}
