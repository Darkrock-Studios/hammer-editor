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

Our mocking library `mockk` does not yet support Kotlin/Native, thus we need to choose one of the **JVM** targets to
write the tests for. We chose desktop:

`gradlew desktopTest`

And for the Server:

`gradlew server:test`

#### Checking code coverage

`gradlew koverMergedHtmlReport`

The results of which will be here:
[Code Coverage Report](./build/reports/kover/merged/html/index.html)

## Writing Tests

### `Common` Module Tests:

Most tests live in the `desktopTest` source set, but a few do live in `commonTest`

#### Testing utilities:

`BaseTest` sets you up for injecting wit Koin and dealing with coroutines for testing.

`TestProjectUtils.kt` has functions for generating test data.

### `ComposeUI` Module Tests:

Again, most tests live in the `desktopTest` source set, but a few live in `commonTest`

Useful reference for UI
testing: [Compose Test Cheatsheet](https://developer.android.com/reference/kotlin/androidx/compose/ui/test/package-summary)

## Overal Project Structure (modules)

![Project Modules](readme/modules.png)

## Client Development

### Client Architecture

![Client Architecture Layers](readme/client-architecture-layers.png)

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

## How to Release

- Merge `develop` into `release`
- Tag the latest commit to make the release from in the [semvar](https://semver.org) format of `v1.1.1`
- Push to origin
- This will trigger the `release` action on GitHub which will create a new **Release**, and build all of the artifacts
- Once the `release` action is complete open the new **Release** on GitHub
- Click _Edit_
- Enter change notes in the description field, this will be used as the change log in each store
- Uncheck "_Set as a pre-release_" and instead check "_Set as the latest release_"
- Click the **Publish Release** button
- This will trigger the `publish` action which will upload artifacts to stores, deploy
  to [hammer.ink](https://hammer.ink), and notify the **Discord** channel of a new release
- All done!