//
//  SceneEditorWorkflowUITests.swift
//  iosUITests
//
//  Port of android/src/androidTest/.../SceneEditorWorkflowTest.kt (`editSceneTextThenSave`):
//  create+open a scene, type into the editor, and save. The save action only appears while the
//  buffer is dirty, so its appearance (after the edit) then disappearance (after the tap) confirms
//  the full edit + save round trip.
//

import XCTest

final class SceneEditorWorkflowUITests: HammerUITest {

    func testEditSceneTextThenSave() {
        createAndOpenProject(baseName: "iOS-Scene")

        tap(Tag.navEditor, expecting: Tag.sceneListAdd)

        // Create a scene and open it. Each coordinate tap onto Compose can be dropped, so drive each
        // step by the element its tap surfaces (the add menu, then the create-item dialog).
        tap(Tag.sceneListAdd, expecting: Tag.sceneListAddScene)
        tap(Tag.sceneListAddScene, expecting: Tag.createItemNameField)

        // The scene-name dialog auto-focuses, so typing + IME submit works.
        type("Opening", into: Tag.createItemNameField)
        app.typeText("\n")

        // Creating a scene auto-opens it in the editor.
        waitFor(Tag.sceneEditorText)

        // The save action only appears once the typed edit dirties the buffer. The editor is gated
        // on its initial buffer load, so retry the injection until the save affordance surfaces.
        typeIntoEditor("Once upon a time", into: Tag.sceneEditorText) {
            self.element(Tag.sceneEditorSave).exists
        }

        // Saving clears the dirty buffer, so the save action disappears. Re-tap until it does: the
        // coordinate tap can be dropped, or a late IME keystroke can re-dirty the buffer just after
        // the save and bring the button back.
        tapUntilGone(Tag.sceneEditorSave)
    }
}
