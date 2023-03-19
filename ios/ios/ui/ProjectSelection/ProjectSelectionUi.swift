//
//  ProjectSelectionUi.swift
//  ios
//
//  Created by Adam Brown on 6/4/22.
//  Copyright © 2022 orgName. All rights reserved.
//

import SwiftUI
import Hammer

struct ProjectSelectionUi: View {
    
    public init(projectSelectionComponent: ProjectSelection) {
        self.component = projectSelectionComponent
        observableState = ObservableValue(projectSelectionComponent.state)
        
    }
    
    @SwiftUI.State
    private var component: ProjectSelection
    
    @ObservedObject
    private var observableState: ObservableValue<ProjectSelectionState>
    
    private var state: ProjectSelectionState { observableState.value }
    
    @SwiftUI.State
    private var directory: String = ""
    
    var body: some View {
        VStack() {
            Button("Create Test Project") {
                component.createProject(projectName: "test project")
            }
            
            Button("Delete Test Project") {
                if(!state.projects.isEmpty) {
                    let def = state.projects[0].definition
                    component.deleteProject(projectDef: def)
                }
            }
            
            Text(MR.strings().settings_projects_directory.desc().localized())
            
            
            ScrollView {
                LazyVStack() {
                    // This isn't working yet, need to subscribe to it some how
                    ForEach(state.projects, id: \.self) { value in
                        ProjectItemUi(project: value, onProjectSelected: component.selectProject)
                    }
                }
                
            }
        }
        .frame(maxWidth: 300, alignment: Alignment.center)
        .padding()
    }
}

//struct ProjectSelectionUi_Previews: PreviewProvider {
//    static var previews: some View {
//        ProjectSelectionUi(
//            componentHolder: ComponentHolder { context in
//                ProjectSelectionComponent(
//                    componentContext: context) { Project in
//                        print("Project selected: " + Project.name)
//                    }
//            }
//        )
//    }
//}

struct ProjectItemUi: View {
    
    private var project: ProjectData
    
    private var onProjectSelected: (ProjectDefinition) -> Void
    
    init(project: ProjectData, onProjectSelected: @escaping (ProjectDefinition) -> Void) {
        self.project = project
        self.onProjectSelected = onProjectSelected
    }
    
    var body: some View {
        Text(project.definition.name)
            .onTapGesture {
                onProjectSelected(project.definition)
            }
        
        Text("Created " + project.metadata.info.created.formatLocal(format: "dd MMM `yy"))
    }
}
