//
//  NotesWorkflowUITests.swift
//  iosUITests
//
//  Adapted from android/src/androidTest/.../NotesWorkflowTest.kt. Navigates to Notes and opens
//  the create-note screen.
//
//  Note: the Android test creates a note and asserts its card appears. That can't be reproduced
//  here — a note requires body text, and the note body is a Compose rich-text editor that does
//  not report keyboard focus to XCUITest (only standard Compose text fields do), so its text
//  can't be driven. This smoke test therefore verifies navigation into note creation; the
//  create-and-list step is covered on Android.
//

import XCTest

final class NotesWorkflowUITests: HammerUITest {

    func testOpenNoteCreation() {
        createAndOpenProject(baseName: "iOS-Notes")

        tap(Tag.navNotes)

        // Opening the create-note screen renders its body editor and create affordance.
        tap(Tag.notesCreateFab)
        waitFor(Tag.notesCreateBody)
        waitFor(Tag.notesCreateConfirm)
    }
}
