import SwiftUI

enum CardStyle {
    case `default`
    case device(color: Color)
    case warning
}

struct Card<Content: View>: View {
    let style: CardStyle
    @ViewBuilder let content: () -> Content

    init(style: CardStyle = .default, @ViewBuilder content: @escaping () -> Content) {
        self.style = style
        self.content = content
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            content()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(backgroundView)
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    @ViewBuilder
    private var backgroundView: some View {
        switch style {
        case .default:
            Color(UIColor.secondarySystemBackground)
        case .device(let color):
            ZStack {
                Rectangle().fill(color)
                Rectangle().fill(.thickMaterial)
            }
        case .warning:
            ZStack {
                Color(UIColor.secondarySystemBackground)
                Color.red.opacity(0.1)
            }
        }
    }
}

struct Card_Previews: PreviewProvider {
    static var previews: some View {
        VStack {
            Card {
                Text("Default Card")
            }
            Card(style: .device(color: .blue)) {
                Text("Device Card")
            }
            Card(style: .warning) {
                Text("Warning Card")
            }
        }
        .padding()
        .previewLayout(.sizeThatFits)
    }
}
