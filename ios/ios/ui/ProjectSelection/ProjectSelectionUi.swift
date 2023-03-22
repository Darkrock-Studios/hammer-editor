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
    
    @State
    private var component: ProjectSelection
    
    @ObservedObject
    private var observableState: ObservableValue<ProjectSelectionState>
    
    private var state: ProjectSelectionState { observableState.value }
    
    @State
    private var directory: String = ""
    
    var body: some View {
        VStack() {
            Button("Create Test Project") {
                component.createProject(projectName: "test project")
            }
            .buttonStyle(SelectButton())
           
            Button("Delete Test Project") {
                if(!state.projects.isEmpty) {
                    let def = state.projects[0].definition
                    component.deleteProject(projectDef: def)
                }
            }
            .buttonStyle(SelectButton())
            
            Text(MR.strings().settings_projects_directory.desc().localized())
                .padding()
            
            
            ScrollView {
                LazyVStack() {
                    // This isn't working yet, need to subscribe to it some how
                    ForEach(state.projects, id: \.self) { value in
                        ProjectItemUi(project: value, onProjectSelected: component.selectProject)
                    }
                    .padding()
                    .fontWeight(.semibold)
                }
                
            }
            .background(.thinMaterial)
        }
        .frame(maxWidth: 300, alignment: Alignment.center)
        .padding()
    }
}

/*
View displaying the project names themselves
*/
struct ProjectItemUi: View {
    
    private var project: ProjectData
    
    private var onProjectSelected: (ProjectDefinition) -> Void
    
    init(project: ProjectData, onProjectSelected: @escaping (ProjectDefinition) -> Void) {
        self.project = project
        self.onProjectSelected = onProjectSelected
    }
    
    var body: some View {
        Button(project.definition.name) {
            onProjectSelected(project.definition)
        }        
//        Text("Created " + project.metadata.info.created.formatLocal(format: "dd MMM `yy"))
    }
}

struct SelectButton: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .padding()
            .background(Color.purple.cornerRadius(8))
            .scaleEffect(configuration.isPressed ? 0.95 : 1)
            .foregroundColor(.white)
    }
}
