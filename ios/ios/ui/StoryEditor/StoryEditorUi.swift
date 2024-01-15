//
//  StoryEditorUi.swift
//  ios
//
//  Created by Adam Brown on 6/5/22.
//  Copyright © 2022 orgName. All rights reserved.
//

import SwiftUI
import Hammer


private let listPaneWeight = CGFloat(0.4)
private let detailsPaneWeight = CGFloat(0.6)

struct StoryEditorUi: View {
    
    init(component: StoryEditor, onBackPressed: @escaping () -> Void) {
        self.component = component
        self.observedState = ObservableValue(component.state)
        self.listRouterState = ObservableValue(component.listRouterState)
        self.detailsRouterState = ObservableValue(component.detailsRouterState)
        self.onBackPressed = onBackPressed
    }
    
    private var onBackPressed: () -> Void
    
    @State
    private var component: StoryEditor
    
    @ObservedObject
    private var observedState: ObservableValue<StoryEditorState>
    
    @ObservedObject
    private var listRouterState: ObservableValue<ChildStack<AnyObject, StoryEditorChildDestination.List>>
    
    @ObservedObject
    private var detailsRouterState: ObservableValue<ChildStack<AnyObject, StoryEditorChildDestination.Detail>>
    
    private var state: StoryEditorState { observedState.value }
    private var activeListChild: StoryEditorChildDestination.List { listRouterState.value.active.instance }
    private var activeDetailsChild: StoryEditorChildDestination.Detail { detailsRouterState.value.active.instance }
    
    var body: some View {
        NavigationView {
            ListPane(listChild: activeListChild, isMultiPane: state.isMultiPane)
                .padding()
                .navigationTitle(state.projectDef.name)
                //.navigationBarTitleDisplayMode(.inline)
                .navigationBarBackButtonHidden(true)
            DetailsPane(detailsChild: activeDetailsChild, isMultiPane: state.isMultiPane).onAppear { component.setMultiPane(isMultiPane: deviceRequiresMultiPane()) }
                .padding()
                .navigationTitle("Scene")
                //.navigationBarTitleDisplayMode(.inline)
                .navigationBarBackButtonHidden(true)
                .toolbar(content: {
                    ToolbarItem (placement: .navigation)  {
                        Image(systemName: "arrow.left")
                            .foregroundColor(.white)
                            .onTapGesture {
                                if(!component.closeDetails()) {
                                    self.onBackPressed()
                                }
                            }
                    }
                })
        }
    }
}

/*
struct StoryEditorUi_Previews: PreviewProvider {
    static var previews: some View {
        StoryEditorUi(
            componentHolder: ComponentHolder { context in
                StoryEditorComponent(
                    componentContext: context,
                    projectDef: ProjectDefinition(name:"Test Proj", path: HPath(path: "/a/b", isAbsolute: true)),
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
*/

struct ListPane: View {
    let listChild: StoryEditorChildDestination.List
    let isMultiPane: Bool
    
    var body: some View {
        switch listChild {
        case let list as StoryEditorChildDestination.ListScenes:
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
    let detailsChild: StoryEditorChildDestination.Detail
    let isMultiPane: Bool
    
    var body: some View {

        switch detailsChild {
        case let details as StoryEditorChildDestination.DetailEditorDestination:
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
