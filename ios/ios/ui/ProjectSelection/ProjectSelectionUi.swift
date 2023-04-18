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
        observableState = ObservableValue(projectSelectionComponent.slot)
        
    }
    
    @State
    private var component: ProjectSelection
    
    @ObservedObject
    private var observableState: ObservableValue<ChildSlot<ProjectSelectionConfig, ProjectSelectionDestination>>
    
    private var slot: ChildSlot<ProjectSelectionConfig, ProjectSelectionDestination> { observableState.value }
    
    var body: some View {
        Text("")
    
        if let settings = slot.child?.instance as? ProjectSelectionDestination.AccountSettingsDestination {
            AccountSettingsUi(component: settings.component)
        } else if let projList = slot.child?.instance as? ProjectSelectionDestination.ProjectsListDestination {
            ProjectsListUi(component: projList.component)
        } else {
            Text("error")
        }
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
