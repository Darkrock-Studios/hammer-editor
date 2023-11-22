//
//  ProjectRootUi.swift
//  ios
//
//  Created by Adam Brown on 3/2/23.
//  Copyright © 2023 orgName. All rights reserved.
//

import SwiftUI
import Hammer

class TestObj: ObservableObject {
    @Published var value = 0
}

struct ProjectRootUi: View {
    
    private let component: ProjectRoot

    private var routerState: StateValue<ChildStack<AnyObject, ProjectRootDestination<AnyObject>>>
    private var stack: ChildStack<AnyObject, ProjectRootDestination<AnyObject>> { routerState.wrappedValue }
    
    private var activeDestination: ProjectRootDestination<AnyObject> { routerState.wrappedValue.active.instance }
    
    init(component: ProjectRoot, closeProject: @escaping () -> Void) {
        self.component = component
        self.routerState = StateValue(component.routerState)
    }
    
    func destinationTitle(destination: ProjectRootDestination<some AnyObject>) -> String {
        switch destination {
        case is ProjectRootDestinationEditorDestination:
            return "Editor"
        case is ProjectRootDestinationNotesDestination:
            return "Notes"
        case is ProjectRootDestinationEncyclopediaDestination:
            return "Encyclopedia"
        case is ProjectRootDestinationTimeLineDestination:
            return "Timeline"
        case is ProjectRootDestinationHomeDestination:
            return "Home"
        default:
            //throw KotlinIllegalStateException("Unhandled destination")
            return "unhandled destination"
        }
    }
    
    var body: some View {
        VStack {
            ScrollView {
                HStack {
                    Button("Home") {
                        component.showHome()
                    }
                    Button("Editor") {
                        component.showEditor()
                    }
                    Button("Notes") {
                        //Napier().d(message: "Switch to notes", throwable: nil, tag: "Hammer")
                        component.showNotes()
                    }
                    Button("Timeline") {
                        component.showTimeLine()
                    }
                    Button("Encyclopedia") {
                        component.showEncyclopedia()
                    }
                }
            }.frame(height: 40)

            StackView(
                stackValue: routerState,
                getTitle: { (destination) -> String in
                    destinationTitle(destination: destination)
                },
                onBack: { (toIndex) in
                    
                }, //stack.active.instance.onBack,
                childContent: { destination in

                    switch destination.component {
                    case is ProjectEditor:
                        ProjectEditorUi(component: destination.component as! ProjectEditor, onBackPressed: {})
                    case is Notes:
                        Text("Notes")
                    case is Encyclopedia:
                        Text("Encyclopedia")
                    case is TimeLine:
                        Text("Time Line")
                    case is ProjectHome:
                        Text("Home")
                    default:
                        //throw KotlinIllegalStateException("Unhandled destination")
                        Text("unhandled destination")
                    }
                }
            )
        }
    }
}

//struct ProjectRootUi_Previews: PreviewProvider {
//    static var previews: some View {
//        ProjectRootUi()
//    }
//}
