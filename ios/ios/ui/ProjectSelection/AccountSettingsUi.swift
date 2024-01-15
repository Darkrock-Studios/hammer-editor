//
//  AccountSettingsUi.swift
//  ios
//
//  Created by Adam Brown on 4/17/23.
//  Copyright © 2023 orgName. All rights reserved.
//

import SwiftUI
import Hammer

struct AccountSettingsUi: View {
    private let component: AccountSettings
    
    @ObservedObject
    private var observableState: ObservableValue<AccountSettingsState>
    
    private var state: AccountSettingsState { observableState.value }
    
    init(component: AccountSettings) {
        self.component = component
        self.observableState = ObservableValue(component.state)
    }
    
    var body: some View {
        VStack {
            Text("Account Settings")
            
            Button("Install Example Project") {
                component.reinstallExampleProject { success in
                    Napier().d(message: "Install did something", throwable: nil, tag: "Hammer")
                }
            }
            .buttonStyle(SelectButton())
        }
    }
}

/*
struct AccountSettingsUi_Previews: PreviewProvider {
    static var previews: some View {
        AccountSettingsUi()
    }
}
*/
