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

class RootState {
    var projectSelected: ProjectDefinition? = nil
    
    init(_ project: ProjectDefinition?) {
        self.projectSelected = project
    }
}

class RootHolder : ObservableObject {
    let lifecycle: LifecycleRegistry
    var state: MutableValue<RootState>
    
    init() {
        state = MutableValueBuilderKt.MutableValue(initialValue: RootState(nil)) as! MutableValue<RootState>
        
        lifecycle = LifecycleRegistryKt.LifecycleRegistry()
        lifecycle.onCreate()
    }
    
    func selectProject(project: ProjectDefinition) {
        state.reduce { reducer in
            RootState(project)
        }
    }
    
    func closeProject() {
        state.reduce { reducer in
            RootState(nil)
        }
    }
    
    deinit {
        lifecycle.onDestroy()
    }
}
