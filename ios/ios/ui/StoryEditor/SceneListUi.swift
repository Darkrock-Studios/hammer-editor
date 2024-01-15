//
//  SceneListUi.swift
//  ios
//
//  Created by Adam Brown on 6/10/22.
//  Copyright © 2022 orgName. All rights reserved.
//

import SwiftUI
import Hammer


struct SceneListUi: View {
    private let component: SceneList

    @ObservedObject
    private var observableState: ObservableValue<SceneListState>

    private var state: SceneListState { observableState.value }

    init(component: SceneList) {
        self.component = component
        self.observableState = ObservableValue(component.state)
    }

    var body: some View {
        VStack {
            Text("Scene List")
            ScrollView {
                LazyVStack() {
                    if let sceneTree = state.sceneSummary?.sceneTree {
                        if(sceneTree.totalChildren == 0) {
                            Text("No Scenes")
                        } else {
                            ForEach(sceneTree.list(), id: \.self) { treeNode in
                                if let sceneItem = treeNode.value {
                                    if sceneItem.isRootScene == false {
                                        SceneItemUi(scene: sceneItem, onSceneSelected: component.onSceneSelected)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/*
struct SceneListUi_Previews: PreviewProvider {
    static var previews: some View {
        let lifecycle = LifecycleRegistryKt.LifecycleRegistry()
        let context = DefaultComponentContext(lifecycle: lifecycle)
        let fakeFlow = SharedFlowKt.MutableSharedFlow(replay: 1,
                                                      extraBufferCapacity: 0,
                                                      onBufferOverflow: BufferOverflow.dropOldest)

        return SceneListUi(
            component: SceneListComponent(
                componentContext: context,
                projectDef: ProjectDefinition(
                    name: "Test Proj",
                    path: HPath(path: "/a/b", isAbsolute: true)),
                selectedSceneDef: fakeFlow,
                sceneSelected: { scene in }
            )
        )
    }
}

struct SceneItemUi_Previews: PreviewProvider {
    static var previews: some View {
        SceneItemUi(
            scene: SceneSummary(
                sceneDef: SceneDefinition(
                    projectDef: ProjectDefinition(
                        name: "test prog",
                        path: HPath(
                            path: "/a/b",
                            isAbsolute: false
                        )
                    ),
                    id: 0,
                    name: "test",
                    order: 0
                ),
                hasDirtyBuffer: false
            ),
            onSceneSelected: { scene in }
        )
    }
}
*/

struct SceneItemUi: View {
    
    private var sceneItem: SceneItem
    
    private var onSceneSelected: (SceneItem) -> Void
    
    init(scene: SceneItem, onSceneSelected: @escaping (SceneItem) -> Void) {
        self.sceneItem = scene
        self.onSceneSelected = onSceneSelected
    }
    
    var body: some View {
        Text("Row \(sceneItem.name)")
            .onTapGesture {
                onSceneSelected(sceneItem)
            }
    }
}
