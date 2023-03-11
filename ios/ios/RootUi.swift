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
    private let root: RootHolder

    @ObservedObject
    private var observableState: ObservableValue<RootState>
    private var state: RootState { observableState.value }

    init(_ root: RootHolder) {
        self.root = root
        observableState = ObservableValue(root.state)
    }

    var body: some View {
        VStack {
            if let holder = state.projectSelectComponent {
                ProjectSelectionUi(projectSelectionComponent: holder.component)
            } else if let holder = state.projectRootComponent {
                ProjectRootUi(component: holder.component) {
                    root.closeProject()
                }
            }
        }
    }
}
