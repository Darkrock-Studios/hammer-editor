//
//  HammerUITest.swift
//  iosUITests
//
//  Base class + helpers for Hammer's iOS UI smoke tests.
//
//  These drive the *real* app on a simulator/device through XCUITest — the iOS analogue of the
//  Android Compose UI smoke tests in `android/src/androidTest`. The entire UI is Compose
//  Multiplatform, so we target Compose `testTag`s: Compose Multiplatform (1.8+) maps them to iOS
//  `accessibilityIdentifier`s automatically, loading the accessibility tree lazily the first time
//  the iOS accessibility engine queries it — which is exactly what XCUITest does. No app-side
//  configuration is required. `--uitesting` is passed as a marker for any future test-only hooks.
//

import XCTest

/// Compose `testTag` values, mirrored from the Kotlin sources so the tests read like their
/// Android counterparts. Keep these in sync with the `const val ..._TAG` declarations.
enum Tag {
    // Project selection (composeUi .../projectselection)
    static let createProjectButton = "create-project-button"   // ProjectListUi.kt
    static let createProjectName = "create-project-name"        // ProjectCreateDialog.kt
    static let projectCard = "project-card"                     // ProjectCard.kt

    // Project root navigation (ProjectRootScaffold.kt)
    static let navHome = "nav-Home"
    static let navEditor = "nav-Editor"
    static let navNotes = "nav-Notes"

    // Scene list / editor (SceneListUi.kt, SceneEditorUi.kt)
    static let sceneListAdd = "scene-list-add"
    static let sceneListAddScene = "scene-list-add-scene"
    static let createItemNameField = "create-item-name-field"
    static let sceneEditorText = "scene-editor-text"
    static let sceneEditorSave = "scene-editor-save"
    static let sceneGroupPrefix = "scene-group-"

    // Notes (BrowseNotesUi.kt, CreateNoteUi.kt)
    static let notesCreateFab = "notes-create-fab"
    static let notesCreateBody = "notes-create-body"
    static let notesCreateConfirm = "notes-create-confirm"
    static let notesCreateMeta = "notes-create-meta"
    static let noteCardPrefix = "note-card-"
}

class HammerUITest: XCTestCase {

    /// Default timeout for waiting on Compose to render an element. Generous because the first
    /// frame after launch (Koin init + data migration) and scene-editor focus can be slow.
    static let defaultTimeout: TimeInterval = 20

    var app: XCUIApplication!

    override func setUpWithError() throws {
        // UI tests should stop at the first failure rather than cascade.
        continueAfterFailure = false

        app = XCUIApplication()
        app.launchArguments = ["--uitesting"]
        app.launch()
    }

    override func tearDownWithError() throws {
        app = nil
    }

    // MARK: - Element lookup (by Compose testTag => accessibilityIdentifier)

    /// An element matched by its Compose `testTag`, regardless of its resolved element type.
    func element(_ tag: String) -> XCUIElement {
        app.descendants(matching: .any).matching(identifier: tag).firstMatch
    }

    /// First element whose `testTag` starts with `prefix` — for dynamic ids like `note-card-<uuid>`.
    func element(prefix: String) -> XCUIElement {
        let predicate = NSPredicate(format: "identifier BEGINSWITH %@", prefix)
        return app.descendants(matching: .any).matching(predicate).firstMatch
    }

    // MARK: - Waiting

    @discardableResult
    func waitFor(_ tag: String, timeout: TimeInterval = HammerUITest.defaultTimeout,
                 _ message: String? = nil, file: StaticString = #file, line: UInt = #line) -> XCUIElement {
        let el = element(tag)
        XCTAssertTrue(el.waitForExistence(timeout: timeout),
                      message ?? "Timed out after \(timeout)s waiting for tag: \(tag)",
                      file: file, line: line)
        return el
    }

    @discardableResult
    func waitForPrefix(_ prefix: String, timeout: TimeInterval = HammerUITest.defaultTimeout,
                       file: StaticString = #file, line: UInt = #line) -> XCUIElement {
        let el = element(prefix: prefix)
        XCTAssertTrue(el.waitForExistence(timeout: timeout),
                      "Timed out after \(timeout)s waiting for tag prefix: \(prefix)",
                      file: file, line: line)
        return el
    }

    // MARK: - Interaction

    /// Tap a Compose element by its center point. Compose renders to a single surface, so most
    /// elements surface to XCUITest as `Other`/`Button` nodes that it deems "not hittable"
    /// (`XCUIElement.tap()` then fails with a `{-1,-1}` hit point). Anchoring the coordinate to
    /// the application — which is always hittable — and offsetting by the element's frame center
    /// taps the right pixel regardless of that heuristic.
    func tapCenter(_ element: XCUIElement) {
        let frame = element.frame
        app.coordinate(withNormalizedOffset: .zero)
            .withOffset(CGVector(dx: frame.midX, dy: frame.midY))
            .tap()
    }

    /// Tap a Compose element by its center and re-tap on a short sub-timeout until `reaction`
    /// becomes true. `tapCenter` synthesizes a best-effort *coordinate* tap onto Compose's single
    /// surface, and an individual tap can be silently dropped (mid-relayout, under simulator load,
    /// or while the software-keyboard geometry is shifting). A lone tap followed by one long wait is
    /// the classic XCUITest flake — a single dropped tap costs the entire timeout and fails the
    /// test — so re-tapping until the app demonstrably reacts converges instead. `reaction` is
    /// checked first, so an already-satisfied state never taps and a tap that *did* land isn't
    /// double-fired into a toggle.
    func tapUntil(_ element: XCUIElement, until reaction: () -> Bool,
                  timeout: TimeInterval = HammerUITest.defaultTimeout,
                  file: StaticString = #file, line: UInt = #line) {
        let deadline = Date().addingTimeInterval(timeout)
        repeat {
            if reaction() { return }
            tapCenter(element)
            if poll(reaction, timeout: 3) { return }
        } while Date() < deadline
        XCTFail("App never reacted after re-tapping for \(timeout)s", file: file, line: line)
    }

    /// Tap near a field's top-leading edge to focus it. For multiline Compose editors (scene text,
    /// note body) the focusable first line/cursor sits at the top; tapping the vertical center can
    /// land on empty space that doesn't take focus, leaving `typeText` with no keyboard focus.
    private func tapToFocus(_ element: XCUIElement) {
        let frame = element.frame
        app.coordinate(withNormalizedOffset: .zero)
            .withOffset(CGVector(dx: frame.minX + 24, dy: frame.minY + 24))
            .tap()
    }

    func tap(_ tag: String, timeout: TimeInterval = HammerUITest.defaultTimeout,
             file: StaticString = #file, line: UInt = #line) {
        tapCenter(waitFor(tag, timeout: timeout, file: file, line: line))
    }

    /// Tap the element tagged `tag`, re-tapping until the element tagged `expect` appears — i.e.
    /// until the tap has demonstrably registered (a menu opened, a screen navigated). Robust
    /// against dropped/stale Compose taps; see `tapUntil(_:until:)`. Use when a tap's effect is a
    /// *new* element surfacing.
    func tap(_ tag: String, expecting expect: String, timeout: TimeInterval = HammerUITest.defaultTimeout,
             file: StaticString = #file, line: UInt = #line) {
        let el = waitFor(tag, timeout: timeout, file: file, line: line)
        tapUntil(el, until: { self.element(expect).exists }, timeout: timeout, file: file, line: line)
    }

    /// Tap the element tagged `tag` until it disappears — e.g. the scene-editor save affordance,
    /// which is rendered only while the buffer is dirty and so vanishes once the save lands.
    ///
    /// The save button lives in the top bar, and right after the edit that surfaces it its
    /// accessibility frame can be briefly *stale* — reported up under the status bar (a dead pixel)
    /// before the layout settles. A blind coordinate tap there does nothing, and repeatedly tapping
    /// that dead spot can even scroll/navigate the app away. So only tap when the button is actually
    /// hittable (settled), using a hit-tested `tap()` that re-resolves its real position; otherwise
    /// wait for it to settle. Re-tap until it's gone, which also covers a dropped tap or a late IME
    /// keystroke re-dirtying the buffer right after a save.
    func tapUntilGone(_ tag: String, timeout: TimeInterval = HammerUITest.defaultTimeout,
                      file: StaticString = #file, line: UInt = #line) {
        let el = waitFor(tag, timeout: timeout, file: file, line: line)
        let deadline = Date().addingTimeInterval(timeout)
        repeat {
            if !el.exists { return }
            if el.isHittable {
                el.tap()
                if poll({ !el.exists }, timeout: 3) { return }
            } else {
                _ = poll({ el.isHittable || !el.exists }, timeout: 1)
            }
        } while Date() < deadline
        XCTFail("Element still present after \(timeout)s: \(tag)", file: file, line: line)
    }

    /// Tap a tagged field and type into it. Compose text fields don't report per-element keyboard
    /// focus, so `XCUIElement.typeText` fails with "Neither element nor any descendant has
    /// keyboard focus". Tapping focuses the Compose field (raising the keyboard); typing on the
    /// *application* then routes to whatever Compose has focused — the reliable path for
    /// Compose-on-iOS text entry.
    func type(_ text: String, into tag: String, timeout: TimeInterval = HammerUITest.defaultTimeout,
              file: StaticString = #file, line: UInt = #line) {
        tapToFocus(waitFor(tag, timeout: timeout, file: file, line: line))
        // The software keyboard must be up for typing to route to the focused Compose field.
        // If a hardware keyboard is "connected" on the simulator it stays hidden and typeText
        // fails with "no keyboard focus" — the run script disables that (see ios/scripts).
        XCTAssertTrue(app.keyboards.firstMatch.waitForExistence(timeout: 10),
                      "Software keyboard never appeared after focusing \(tag)", file: file, line: line)
        app.typeText(text)
    }

    /// Type into a Compose rich-text editor (the scene body) and retry until the edit lands.
    ///
    /// Unlike the note body — whose `MarkdownEditField` is always enabled — the scene editor's
    /// `SpellCheckingTextEditor` is gated `enabled = hasReceivedInitialBuffer`, so its platform IME
    /// input session only starts once the scene buffer has loaded. Focusing/typing before that
    /// silently drops the keystrokes, so a single injection can race the buffer load and never
    /// dirty the buffer (leaving the save affordance hidden). Re-focus and re-inject until `until`
    /// confirms the change, mirroring the Android `typeIntoEditor` retry loop.
    func typeIntoEditor(_ text: String, into tag: String, until condition: () -> Bool,
                        timeout: TimeInterval = HammerUITest.defaultTimeout,
                        file: StaticString = #file, line: UInt = #line) {
        let field = waitFor(tag, timeout: timeout, file: file, line: line)
        let deadline = Date().addingTimeInterval(timeout)
        repeat {
            tapToFocus(field)
            // The software keyboard only rises once the editor is enabled and focused; if it
            // doesn't this round, loop and re-focus rather than failing outright.
            if app.keyboards.firstMatch.waitForExistence(timeout: 3) {
                app.typeText(text)
            }
            if poll(condition, timeout: 1) { return }
        } while Date() < deadline
        XCTFail("Editor input for \(tag) never propagated within \(timeout)s", file: file, line: line)
    }

    /// Poll `condition` until it's true or `timeout` elapses, pumping the run loop between checks.
    private func poll(_ condition: () -> Bool, timeout: TimeInterval, interval: TimeInterval = 0.25) -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if condition() { return true }
            RunLoop.current.run(until: Date().addingTimeInterval(interval))
        }
        return condition()
    }

    // MARK: - Shared flows

    /// A unique project name so re-runs (and a persistent simulator) don't collide — mirrors
    /// `EditorTestHarness.seedProject` on Android using a uniquely-suffixed name.
    func uniqueProjectName(_ base: String) -> String {
        "\(base)-\(UInt64(Date().timeIntervalSince1970 * 1000))"
    }

    /// Create a project from the selection screen and open it, leaving the app in the editor.
    /// Returns the project's name. Equivalent to seeding + launching the editor on Android, but
    /// goes through the real onboarding UI (iOS boots into project selection).
    @discardableResult
    func createAndOpenProject(baseName: String, file: StaticString = #file, line: UInt = #line) -> String {
        let name = uniqueProjectName(baseName)

        tap(Tag.createProjectButton, file: file, line: line)
        type(name, into: Tag.createProjectName, file: file, line: line)
        // FormField submits on the IME action; the keyboard return key triggers it.
        app.typeText("\n")

        // The new project shows as a card on the list; tap the one we just made. openProjectCard
        // re-taps until the project root's nav rail renders, so on return we're in the open project.
        openProjectCard(named: name, file: file, line: line)
        return name
    }

    /// Tap the project card matching `name` to open it, falling back to the first card if the label
    /// isn't exposed (Compose may merge the card's text into the card node's label). The card tap is
    /// re-tried until the project root's nav rail renders — a single Compose coordinate tap can be
    /// dropped, which would otherwise fail the whole test on one miss (see `tapUntil`).
    private func openProjectCard(named name: String, file: StaticString, line: UInt) {
        let cards = app.descendants(matching: .any).matching(identifier: Tag.projectCard)
        let named = cards.matching(NSPredicate(format: "label CONTAINS[c] %@", name)).firstMatch
        let card = named.waitForExistence(timeout: 5) ? named : cards.firstMatch
        XCTAssertTrue(card.waitForExistence(timeout: HammerUITest.defaultTimeout),
                      "No project card appeared for \(name)", file: file, line: line)
        tapUntil(card, until: { self.element(Tag.navHome).exists }, file: file, line: line)
    }
}
