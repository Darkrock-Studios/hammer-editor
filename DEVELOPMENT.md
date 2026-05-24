# Development

## Running The App

There are several run configurations provided for IntelliJ, stored in `/.run`.

### Desktop App

`gradlew :desktop:run --args='--dev'` This will run in development mode. To run in developmeny mode manually, simply
pass `--dev` as an
argument when running it. Passing nothing will run in release mode.

`dev` mode will use a separate config directory so that you don't accidentally mess with production data.

### Android App

Select the `Android` run target in the IDE and run it.

You can install the development version alongside a production version, they will have different names and icons so you
can tell them apart.

### Running the Server

`gradlew server:run`

## Running Tests

Our mocking library [mockk](https://mockk.io/) does not yet support Kotlin/Native, thus we need to choose one of the **JVM** targets to
write the tests for. We chose desktop:

`gradlew desktopTest`

And for the Server:

`gradlew server:test`

#### Checking code coverage

`gradlew koverHtmlReport`

The results of which will be here:
[Code Coverage Report](./build/reports/kover/html/index.html)

## Writing Tests

### `Common` Module Tests:

Most tests live in the `desktopTest` source set, but a few do live in `commonTest`

#### Testing utilities:

`BaseTest` sets you up for injecting with Koin and dealing with coroutines for testing.

`TestProjectUtils.kt` has functions for generating test data.

### `ComposeUI` Module Tests:

Again, most tests live in the `desktopTest` source set, but a few live in `commonTest`

Useful reference for UI
testing: [Compose Test Cheatsheet](https://developer.android.com/reference/kotlin/androidx/compose/ui/test/package-summary)

## Overal Project Structure (modules)
```mermaid
flowchart TD
	Base(["Base"]) --> Server["Server"] & Common(["Common"])
	Common --> ComposeUI(["ComposeUI"]) & iOS["iOS"]
	ComposeUI --> Android["Android"] & Desktop["Desktop"]

	Server:::serverColor
	iOS:::iosColor
	Android:::androidColor
	Desktop:::desktopColor
	
	classDef serverColor fill:#f94144,stroke:#333,stroke-width:2px,color:#FFFFFF;
	classDef iosColor fill:#f8961e,stroke:#333,stroke-width:2px,color:#FFFFFF;
	classDef androidColor fill:#90be6d,stroke:#333,stroke-width:2px,color:#FFFFFF;
	classDef desktopColor fill:#577590,stroke:#333,stroke-width:2px,color:#FFFFFF;
```

## Client Development

### Client Architecture

Please check out the Architecture doc for a deeper dive into
the [Client Architecture](docs/ARCHITECTURE.md#client-architecture)

### Coroutines

### Repository Layer

Repositories will need to declare their own coroutine scope, there is no common base class to do so.
```kotlin
	// The various dispatcher can be injected as such
	private val mainDispatcher by injectMainDispatcher()
	private val defaultDispatcher by injectDefaultDispatcher()
	private val ioDispatcher by injectIoDispatcher()
```

#### Component layer
Component base class `ComponentBase` has a coroutine scope defined already: `scope`

This scope will be canceled for you when the component is destroyed.

You can inject the various contexts as such:
```kotlin
	private val mainDispatcher by injectMainDispatcher()
	private val defaultDispatcher by injectDefaultDispatcher()
	private val ioDispatcher by injectIoDispatcher()

	// `scope` here is from the `ComponentBase` parent class
	scope.launch {
        // Scope uses the default dispatcher, so make sure to switch contexts when necessary
        withContext(mainDispatcher) {
			// Make sure you update all of your state variables on the main thread
		}
	}
```

#### UI Layer: Compose
```kotlin
	// Define your own, or use scope hoisting to a parent Composable
	val scope = rememberCoroutineScope()

	// inject which ever dispatcher you need
	val mainDispatcher = rememberMainDispatcher()
val defaultDispatcher = rememberDefaultDispatcher()
val ioDispatcher = rememberIoDispatcher()

scope.launch(defaultDispatcher) {
	// Do stuff in background
	withContext(mainDispatcher) {
		// Back on main thread
	}
}
```

## Logging

## Client

On the client you can log using `Napier` it works on all supported platforms:

```kotlin
Napier.i("message")
Napier.w("message")
Napier.e("message")
Napier.d("message")
```

## Server

On server you can log anywhere you have access to the ktor `Application`

```kotlin
log.info("message")
log.debug("message")
```

You can also access it from a ktor `Call` object:
`call.application.environment.log.info("Hello from a Call!")`

If you need logging below the HTTP layer 🤷 Pass the logger down? Idk we don't have a great solution
for this yet.

## Synchronization

The protocol for synchronizing data between client and server is outlined here:
[SYNCING-PROTOCOL.md](docs/SYNCING-PROTOCOL.md)

## How to Release

When `develop` is ready to release, run: `./gradlew prepareForRelease`

For full instructions check out the full doc [here](docs/HOW-TO-RELEASE.md).

## Re-generate open source library data

This data drives the Opensource Licenses UI in the apps.

**Desktop Target:**
Must be regenerated manually when an open source dependency is added/changed:
`./gradlew :desktop:exportLibraryDefinitions -P"aboutLibraries.exportPath=src\jvmMain\resources"`

**Android Target:**
Auto-generated on every build by the `aboutlibraries.plugin.android` plugin into
`android/build/generated/aboutLibraries/<variant>/res/raw/aboutlibraries.json`. No manual step required.

**iOS Target:**
???

## Asset Generation

All graphical assets (app icons, store-listing graphics, MSIX tiles, favicons,
the Play Store feature graphic, the Snap featured banner, etc.) are generated
from a single manifest at `scripts/assets.yaml`. Run `scripts/generate-assets.sh`
to (re)build everything.

See [ASSET-GENERATION.md](docs/ASSET-GENERATION.md) for the manifest schema,
dependencies, asset types, and how to add or modify outputs.
