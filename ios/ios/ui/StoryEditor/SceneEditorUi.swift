//
//  SceneEditorUi.swift
//  ios
//
//  Created by Adam Brown on 6/10/22.
//  Copyright © 2022 orgName. All rights reserved.
//

import SwiftUI
import Hammer

struct SceneEditorUi: View {
    
    private let component: SceneEditor
    
    @ObservedObject
    private var observableState: ObservableValue<SceneEditorState>
    
    private var state: SceneEditorState { observableState.value }
    
    init(component: SceneEditor) {
        self.component = component
        self.observableState = ObservableValue(component.state)
    }
    
    var body: some View {
        Text("SceneEditor: " + state.sceneItem.name)
    }
}

//struct SceneEditorUi_Previews: PreviewProvider {
//    static var previews: some View {
//        let lifecycle = LifecycleRegistryKt.LifecycleRegistry()
//        let context = DefaultComponentContext(lifecycle: lifecycle)
//        
//        let projectDef = ProjectDefinition(name: "Test Proj", path: HPath(path: "/a/b", isAbsolute: true))
//        
//        SceneEditorUi(component: SceneEditorComponent(
//            componentContext: context,
//            sceneDef: SceneDefinition(
//                projectDef: projectDef,
//                id: 0,
//                name: "some text",
//                order: 0),
//            addMenu: { MenuDescriptor in },
//            removeMenu: { menuId in },
//            closeSceneEditor: {}
//        )
//        )
//    }
//}
