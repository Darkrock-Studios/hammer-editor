//
//  NotesWorkflowUITests.swift
//  iosUITests
//
//  Port of android/src/androidTest/.../NotesWorkflowTest.kt: create a note via the FAB + modal
//  (typing into the note body) and confirm the new note card appears.
//

import XCTest

final class NotesWorkflowUITests: HammerUITest {

    func testCreateNoteThenSeeItListed() {
        createAndOpenProject(baseName: "iOS-Notes")

        tap(Tag.navNotes)

        tap(Tag.notesCreateFab)

        type("E2E smoke note body", into: Tag.notesCreateBody)
        tap(Tag.notesCreateConfirm)

        // The new note card appears (id unknown, match by prefix).
        waitForPrefix(Tag.noteCardPrefix)
    }
}
