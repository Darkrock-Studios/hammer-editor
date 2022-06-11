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
                    ForEach(state.scenes,
                            id: \.self) { value in
                        SceneItemUi(scene: value, onSceneSelected: component.onSceneSelected)
                    }
                }
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

struct SceneItemUi_Previews: PreviewProvider {
    static var previews: some View {
        SceneItemUi(
            scene: Hammer.Scene(project: Project(name: "test prog", path: "/a/b"), scene: "test"),
            onSceneSelected: { scene in }
            )
    }
}

struct SceneItemUi: View {
    
    private var scene: Hammer.Scene
    
    private var onSceneSelected: (Hammer.Scene) -> Void
    
    init(scene: Hammer.Scene, onSceneSelected: @escaping (Hammer.Scene) -> Void) {
        self.scene = scene
        self.onSceneSelected = onSceneSelected
    }
    
    var body: some View {
        Text("Row \(scene.scene)")
            .onTapGesture {
                onSceneSelected(scene)
            }
    }
}
