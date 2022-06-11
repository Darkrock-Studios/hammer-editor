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
        if(state.projectSelected == nil) {
            createProjectSelect(root: root)
        }
        else {
            createProjectEditor(project: state.projectSelected!, root: root)
        }
    }
}

private func createProjectSelect(root: RootHolder) -> ProjectSelectionUi {
    let projectSelectionHolder = ComponentHolder<ProjectSelectionComponent> { context in
        ProjectSelectionComponent(
            componentContext: context) { project in
                print("Project selected: " + project.name)
                root.selectProject(project: project)
            }
    }
    
    // Create the SwiftUI view that provides the window contents.
    return ProjectSelectionUi(componentHolder: projectSelectionHolder)
}

private func createProjectEditor(project: Project, root: RootHolder) -> ProjectEditorUi {
    let component = ComponentHolder<ProjectEditorComponent> { context in
        ProjectEditorComponent(
            componentContext: context,
            project: project,
            addMenu: { menu in
                NSLog("Add menu item")
            },
            removeMenu: { menuItemId in
                NSLog("Remove menu item")
            }
        )
    }
    
    let projectEditorView = ProjectEditorUi(componentHolder: component) {
        root.closeProject()
    }
    
    return projectEditorView
}
