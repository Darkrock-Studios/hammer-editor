//
//  app_iosApp.swift
//  ios
//
//  Created by Adam Brown on 6/10/22.
//  Copyright © 2022 orgName. All rights reserved.
//

import Foundation
import SwiftUI
import Hammer

@main
struct app_iosApp: App {
    @StateObject
    var rootHolder = RootHolder()
    
    init() {
        Theme.navigationBarColors(background: .purple, titleColor: .white)

        NapierProxyKt.debugBuild()
        
        PlatformKt.initializeKoin()
    }
    
    var body: some SwiftUI.Scene {
            WindowGroup {
                RootUi(rootHolder)
                    .onAppear { LifecycleRegistryExtKt.resume(self.rootHolder.lifecycle) }
                    .onDisappear { LifecycleRegistryExtKt.stop(self.rootHolder.lifecycle) }
            }
    }
}

struct Previews_app_iosApp_Previews: PreviewProvider {
    static var previews: some View {
        let rootHolder = RootHolder()
            RootUi(rootHolder)
                .onAppear { LifecycleRegistryExtKt.resume(rootHolder.lifecycle) }
                .onDisappear { LifecycleRegistryExtKt.stop(rootHolder.lifecycle) }
        }
    }

