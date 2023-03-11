//
//  Root.swift
//  ios
//
//  Created by Adam Brown on 3/6/23.
//  Copyright © 2023 orgName. All rights reserved.
//

import Foundation
import Hammer

class RootState {
    var projectSelectComponent: ComponentHolder<ProjectSelection>? = nil
    var projectRootComponent: ComponentHolder<ProjectRoot>? = nil

    init(_ component: ComponentHolder<ProjectSelection>) {
        self.projectSelectComponent = component
    }
    
    init(_ component: ComponentHolder<ProjectRoot>) {
        self.projectRootComponent = component
    }
    
    init() {
        
    }
}

class RootHolder : ObservableObject {
    let lifecycle: LifecycleRegistry
    var state: MutableValue<RootState>

    init() {
        state = MutableValueBuilderKt.MutableValue(initialValue: RootState()) as! MutableValue<RootState>

        lifecycle = LifecycleRegistryKt.LifecycleRegistry()
        lifecycle.onCreate()
        
        state.reduce { reducer in
            RootState(self.createProjectSelect())
        }
    }

    func selectProject(project: ProjectDefinition) {
        state.reduce { reducer in
            RootState(self.createProjectRoot(project: project))
        }
    }

    func closeProject() {
        state.reduce { reducer in
            RootState(self.createProjectSelect())
        }
    }

    deinit {
        lifecycle.onDestroy()
    }
    
    private func createProjectSelect() -> ComponentHolder<ProjectSelection> {
        let componentHolder = ComponentHolder<ProjectSelection> { context in
            ProjectSelectionComponent(
                componentContext: context, showProjectDirectory: false) { project in
                    print("Project selected: " + project.name)
                    self.selectProject(project: project)
                }
        }

        return componentHolder
    }

    private func createProjectRoot(project: ProjectDefinition) -> ComponentHolder<ProjectRoot> {
        let componentHolder = ComponentHolder<ProjectRoot> { context in
            ProjectRootComponent(
                componentContext: context,
                projectDef: project,
                addMenu: { menu in
                    NSLog("Add menu item")
                },
                removeMenu: { menuItemId in
                    NSLog("Remove menu item")
                }
            )
        }

        return componentHolder
    }
}
