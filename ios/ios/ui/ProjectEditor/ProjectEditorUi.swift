//
//  ProjectEditorUi.swift
//  ios
//
//  Created by Adam Brown on 6/5/22.
//  Copyright © 2022 orgName. All rights reserved.
//

import SwiftUI
import Hammer

struct ProjectEditorUi: View {
    
    init(componentHolder: ComponentHolder<ProjectEditorComponent>) {
        self.holder = componentHolder
        self.state = ObservableValue(componentHolder.component.state)
    }
    
    @State
    private var holder: ComponentHolder<ProjectEditorComponent>
    
    @ObservedObject
    private var state: ObservableValue<ProjectEditorRootState>
    
    var body: some View {
        Text("Project Editor: " + state.value.project.name)
    }
}

struct ProjectEditorUi_Previews: PreviewProvider {
    static var previews: some View {
        ProjectEditorUi(
            componentHolder: ComponentHolder { context in
                ProjectEditorComponent(
                    componentContext: context,
                    project: Project(name:"Test Proj", path: "/a/b")) { menu in
                        NSLog("Add menu item")
                    } removeMenu: { menuItemId in
                        NSLog("Remove menu item")
                    }
            }
        )
    }
}
