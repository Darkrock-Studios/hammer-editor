import SwiftUI
import Hammer

func greet() -> String {
    return PlatformKt.getPlatformName()
}

struct ContentView: View {
    var body: some View {
        Text(greet())
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}
