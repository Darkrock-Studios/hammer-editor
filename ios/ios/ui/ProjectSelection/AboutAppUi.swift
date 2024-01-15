//
//  AboutAppUi.swift
//  ios
//
//  Created by Adam Brown on 1/15/24.
//  Copyright © 2024 orgName. All rights reserved.
//

import SwiftUI
import Hammer

struct AboutAppUi: View {
    private let component: AboutApp
    
    init(component: AboutApp) {
        self.component = component
    }
    
    var body: some View {
        Text("About App")
    }
}

//struct AboutAppUi_Previews: PreviewProvider {
//    static var previews: some View {
//        AboutAppUi()
//    }
//}
