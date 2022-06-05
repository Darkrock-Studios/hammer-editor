//
//  ComponentHolder.swift
//  ios
//
//  Created by Adam Brown on 6/4/22.
//  Copyright © 2022 orgName. All rights reserved.
//

import SwiftUI
import Hammer

class ComponentHolder<T> {
    let lifecycle: LifecycleRegistry
    let component: T
    
    init(factory: (ComponentContext) -> T) {
        let lifecycle = LifecycleRegistryKt.LifecycleRegistry()
        let component = factory(DefaultComponentContext(lifecycle: lifecycle))
        self.lifecycle = lifecycle
        self.component = component
        
        lifecycle.onCreate()
    }
    
    deinit {
        lifecycle.onDestroy()
    }
}
