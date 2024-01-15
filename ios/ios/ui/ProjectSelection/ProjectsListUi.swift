//
//  ProjectListUi.swift
//  ios
//
//  Created by Adam Brown on 4/17/23.
//  Copyright © 2023 orgName. All rights reserved.
//

import SwiftUI
import Hammer

struct ProjectsListUi: View {
    private let component: ProjectsList
    
    @ObservedObject
    private var observableState: ObservableValue<ProjectsListState>
    
    private var state: ProjectsListState { observableState.value }
    
    init(component: ProjectsList) {
        self.component = component
        self.observableState = ObservableValue(component.state)
    }
    
    var body: some View {
        Text("Projects List")

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
struct ProjectListUi_Previews: PreviewProvider {
    static var previews: some View {
        ProjectListUi()
    }
}
*/
