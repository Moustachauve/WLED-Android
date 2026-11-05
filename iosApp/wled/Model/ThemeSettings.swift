import SwiftUI
import UIKit
import Shared

extension ThemeSettings: @retroactive Identifiable {
    public var id: Self { self }

    var nameKey: String {
        switch self {
        case .auto: return "System Default"
        case .light: return "Light"
        case .dark: return "Dark"
        }
    }

    var userInterfaceStyle: UIUserInterfaceStyle {
        switch self {
        case .auto: return .unspecified
        case .light: return .light
        case .dark: return .dark
        }
    }

    /// Applies the user interface style to all active windows, working around SwiftUI's
    /// `.preferredColorScheme(nil)` not resetting presented sheets back to the system appearance.
    @MainActor
    func applyToWindows() {
        let style = userInterfaceStyle
        for scene in UIApplication.shared.connectedScenes {
            guard let windowScene = scene as? UIWindowScene else { continue }
            for window in windowScene.windows {
                window.overrideUserInterfaceStyle = style
            }
        }
    }
}
