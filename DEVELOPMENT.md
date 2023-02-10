# Development
======

## Running the Desktop app

`gradlew run`

## Running Tests

Our mocking library `mockk` does not yet support Kotlin/Native, thus we need to choose one of the **JVM** targets to
write the tests for. We chose desktop:

`gradlew desktopTest`

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