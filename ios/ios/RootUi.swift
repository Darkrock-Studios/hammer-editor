//
//  RootUi.swift
//  ios
//
//  Created by Adam Brown on 6/10/22.
//  Copyright © 2022 orgName. All rights reserved.
//

import SwiftUI
import Hammer

struct RootUi: View {
    private let appDelegate: AppDelegate

    @ObservedObject
    private var observableState: ObservableValue<ChildSlot<IosRootConfig, IosRootDestination>>
    private var slot: ChildSlot<IosRootConfig, IosRootDestination> { observableState.value }

    init(_ appDelegate: AppDelegate) {
        self.appDelegate = appDelegate
        observableState = ObservableValue(appDelegate.root.slot)
    }

    var body: some View {
        let child = slot.child
        if(child != nil) {
            VStack {
                if let destination = child?.instance as? IosRootDestination.ProjectSelectDestination {
                    ProjectSelectionUi(projectSelectionComponent: destination.component)
                } else if let destination = child?.instance as? IosRootDestination.ProjectRootDestination {
                    ProjectRootUi(component: destination.component) {
                        appDelegate.root.closeProject()
                    }
                }
            }
        }
    }
}
