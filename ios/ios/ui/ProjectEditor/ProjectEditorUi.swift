//
//  ProjectEditorUi.swift
//  ios
//
//  Created by Adam Brown on 6/5/22.
//  Copyright © 2022 orgName. All rights reserved.
//

import SwiftUI
import Hammer


private let listPaneWeight = CGFloat(0.4)
private let detailsPaneWeight = CGFloat(0.6)

struct ProjectEditorUi: View {
    
    init(componentHolder: ComponentHolder<ProjectEditorComponent>, onBackPressed: @escaping () -> Void) {
        self.holder = componentHolder
        self.observedState = ObservableValue(componentHolder.component.state)
        self.listRouterState = ObservableValue(componentHolder.component.listRouterState)
        self.detailsRouterState = ObservableValue(componentHolder.component.detailsRouterState)
        self.onBackPressed = onBackPressed
    }
    
    private var onBackPressed: () -> Void
    
    @State
    private var holder: ComponentHolder<ProjectEditorComponent>
    
    private var component: ProjectEditorRoot { holder.component }
    
    @ObservedObject
    private var observedState: ObservableValue<ProjectEditorRootState>
    
    @ObservedObject
    private var listRouterState: ObservableValue<RouterState<AnyObject, ProjectEditorRootChild.List>>
    
    @ObservedObject
    private var detailsRouterState: ObservableValue<RouterState<AnyObject, ProjectEditorRootChild.Detail>>
    
    private var state: ProjectEditorRootState { observedState.value }
    private var activeListChild: ProjectEditorRootChild.List { listRouterState.value.activeChild.instance }
    private var activeDetailsChild: ProjectEditorRootChild.Detail { detailsRouterState.value.activeChild.instance }
    
    var body: some View {
        NavigationView {
            ZStack(alignment: .top) {
                ListPane(listChild: activeListChild, isMultiPane: state.isMultiPane)
                DetailsPane(detailsChild: activeDetailsChild, isMultiPane: state.isMultiPane)
            }.onAppear { holder.component.setMultiPane(isMultiPane: deviceRequiresMultiPane()) }
                .navigationTitle(state.project.name)
                .navigationBarTitleDisplayMode(.inline)
                .navigationBarBackButtonHidden(true)
                .toolbar(content: {
                    ToolbarItem (placement: .navigation)  {
                        Image(systemName: "arrow.left")
                            .foregroundColor(.white)
                            .onTapGesture {
                                self.onBackPressed()
                            }
                    }
                })
        }
    }
}

struct ProjectEditorUi_Previews: PreviewProvider {
    static var previews: some View {
        ProjectEditorUi(
            componentHolder: ComponentHolder { context in
                ProjectEditorComponent(
                    componentContext: context,
                    project: Project(name:"Test Proj", path: "/a/b"),
                    addMenu: { menu in
                        NSLog("Add menu item")
                    },
                    removeMenu: { menuItemId in
                        NSLog("Remove menu item")
                    }
                )
            },
            onBackPressed: {
                
            }
        )
    }
}

struct ListPane: View {
    let listChild: ProjectEditorRootChild.List
    let isMultiPane: Bool
    
    var body: some View {
        switch listChild {
        case let list as ProjectEditorRootChild.ListScenes:
            GeometryReader { metrics in
                HStack {
                    SceneListUi(component: list.component)
                        .frame(width: isMultiPane ? metrics.size.width * listPaneWeight : metrics.size.width)
                    
                    if isMultiPane {
                        Spacer().frame(width: metrics.size.width * detailsPaneWeight)
                    }
                }
            }
            
        default: EmptyView()
        }
    }
}

struct DetailsPane: View {
    let detailsChild: ProjectEditorRootChild.Detail
    let isMultiPane: Bool
    
    var body: some View {
        switch detailsChild {
        case let details as ProjectEditorRootChild.DetailEditor:
            GeometryReader { metrics in
                HStack {
                    if isMultiPane {
                        Spacer().frame(width: metrics.size.width * listPaneWeight)
                    }
                    
                    SceneEditorUi(component: details.component)
                        .frame(width: isMultiPane ? metrics.size.width * detailsPaneWeight : metrics.size.width)
                }
            }
            
        default: EmptyView()
        }
    }
}

private func deviceRequiresMultiPane() -> Bool {
    return UIDevice.current.userInterfaceIdiom == .pad
}

/*
 // Need to get these previews setup and working again
 class MultiPanePreview: MultiPane {
 var listRouterState: Value<RouterState<AnyObject, MultiPaneListChild>> =
 simpleRouterState(.List(component: SceneListUi_Previews()))
 
 var detailsRouterState: Value<RouterState<AnyObject, MultiPaneDetailsChild>> =
 simpleRouterState(.Details(component: SceneEditorUi_Previews()))
 
 var models: Value<MultiPaneModel> = mutableValue(MultiPaneModel(isMultiPane: true))
 
 func setMultiPane(isMultiPane: Bool) {}
 }
 */
