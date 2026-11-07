import AVFAudio
import Foundation
import Shared
import SnapshotTesting
import SwiftUI
import Testing
import UIKit
@testable import WLED

@MainActor
struct SoundSyncViewSnapshotTests {

    let database: AppDatabase

    init() {
        self.database = AppDatabase(inMemory: true)
    }

    @Test func testSoundSyncLightSnapshot() {
        assertSoundSyncSnapshot(style: .light)
    }

    @Test func testSoundSyncDarkSnapshot() {
        assertSoundSyncSnapshot(style: .dark)
    }

    // MARK: - Helpers

    private func assertSoundSyncSnapshot(
        style: UIUserInterfaceStyle,
        fileID: StaticString = #fileID,
        file: StaticString = #filePath,
        testName: String = #function,
        line: UInt = #line,
        column: UInt = #column
    ) {
        let viewModel = SoundSyncViewModel(
            soundSyncManager: FakeSoundSyncManager(permission: .granted),
            userPreferencesRepository: database.userPreferencesRepository,
            deviceRepository: database.deviceRepository
        )
        let view = SoundSyncView(viewModel: viewModel)

        assertSnapshot(
            of: view,
            as: .image(
                drawHierarchyInKeyWindow: true,
                precision: 0.99,
                layout: .device(config: .iPhone13Pro),
                traits: UITraitCollection(userInterfaceStyle: style)
            ),
            named: "1",
            fileID: fileID,
            file: file,
            testName: testName,
            line: line,
            column: column
        )
    }
}
