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
    
    public init(component: ComponentHolder<ProjectSelectionComponent>) {
        holder = component
    }
    
    @State
    private var holder: ComponentHolder<ProjectSelectionComponent>
    
    @State
    private var directory: String = ""
    
    var body: some View {
        VStack() {
            Text("Projects Directory")
            
            TextField(
                "Projects Directory",
                text: $directory
            )
            .onChange(of: directory) { newValue in holder.component.setProjectsDir(path:newValue)
            }
            .textInputAutocapitalization(.never)
            .disableAutocorrection(true)
            .border(.secondary)
            
            Button("Load") {
                holder.component.loadProjectList()
            }
            
            ScrollView {
                LazyVStack() {
                    // This isn't working yet, need to subscribe to it some how
                    ForEach(holder.component.state.value.projects,
                            id: \.self) { value in
                                        Text("Row \(value)")
                                    }
                }
            }
        }
    }
}

struct ProjectSelectionUi_Previews: PreviewProvider {
    static var previews: some View {
        ProjectSelectionUi(
            component: ComponentHolder { context in
                ProjectSelectionComponent(
                    componentContext: context) { Project in
                        print("Project selected: " + Project.name)
                    }
            }
        )
    }
}
