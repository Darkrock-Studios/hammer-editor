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
            Button("test scene") {
                component.onSceneSelected(scene: Scene(project: state.project, scene: "some text"))
            }
        }
    }
}

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
                    project: Project(name: "Test Proj", path: "/a/b"),
                    selectedScene: fakeFlow,
                    sceneSelected: { scene in }
                )
        )
    }
}
