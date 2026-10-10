import SwiftUI
import UIKit

extension Color {
    private struct HSBA {
        var h: CGFloat
        var s: CGFloat
        var b: CGFloat
        var a: CGFloat
    }

    private var hsbaComponents: HSBA? {
        let uiColor = UIColor(self)
        var h = CGFloat(0), s = CGFloat(0), b = CGFloat(0), a = CGFloat(0)

        guard uiColor.getHue(&h, saturation: &s, brightness: &b, alpha: &a) else {
            return nil
        }
        return HSBA(h: h, s: s, b: b, a: a)
    }

    /// Fixes the color if it is too dark or too bright depending of the dark/light theme
    func fixDisplayColor(colorScheme: ColorScheme) -> Color {
        guard let components = hsbaComponents else {
            return self
        }

        let newB = colorScheme == .dark ? fmax(components.b, 0.2) : fmin(components.b, 0.75)
        return Color(UIColor(hue: components.h, saturation: components.s, brightness: newB, alpha: components.a))
    }

    /// Ensures a minimum of contrast against white UI elements like Toggle switches
    func ensureContrast(colorScheme: ColorScheme) -> Color {
        guard let components = hsbaComponents else {
            return self
        }

        var newB = components.b

        if colorScheme == .dark {
            // In dark mode, if the color is too close to white (low saturation, high brightness),
            // lower its brightness so that it contrasts well with the white thumb of a Toggle switch
            if components.s < 0.2 && components.b > 0.7 {
                newB = 0.7
            }
        } else {
            // In light mode, ensure it's not too bright either for contrast purposes
            if components.s < 0.2 && components.b > 0.75 {
                newB = 0.75
            }
        }

        return Color(UIColor(hue: components.h, saturation: components.s, brightness: newB, alpha: components.a))
    }
}
