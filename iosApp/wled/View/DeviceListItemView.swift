import SwiftUI

struct DeviceListItemView: View {
    @Environment(\.managedObjectContext) private var viewContext
    @Environment(\.colorScheme) var colorScheme
    @ObservedObject var device: DeviceWithState

    var isSelected: Bool = false

    // MARK: - Actions
    var onTogglePower: (Bool) -> Void
    var onChangeBrightness: (Int) -> Void

    @State private var brightness: Double = 0.0

    var body: some View {
        let fixedDeviceColor = device.currentColor.fixDisplayColor(colorScheme: colorScheme)
        let backgroundColor = isSelected
        ? fixedDeviceColor.opacity(DeviceSelectionStyle.Style.selectedOpacity)
        : fixedDeviceColor.opacity(DeviceSelectionStyle.Style.unselectedOpacity)

        Card(style: .device(color: backgroundColor)) {
            HStack {
                DeviceInfoTwoRows(device: device)

                Toggle("Turn On/Off", isOn: isOnBinding)
                    .labelsHidden()
                    .frame(alignment: .trailing)
                    // On iOS 16, tapping the Toggle also triggers the parent row's .onTapGesture.
                    // This empty handler "consumes" the SwiftUI tap at the child level,
                    // while the underlying UISwitch still receives the UIKit event.
                    // This can be removed once the minimum deployment target is iOS 17+.
                    .onTapGesture { }
            }

            Slider(
                value: $brightness,
                in: 0.0...255.0,
                onEditingChanged: { editing in
                    // Call the brightness closure when dragging ends
                    if !editing {
                        onChangeBrightness(Int(brightness))
                    }
                }
            )
        }
        .applyDeviceSelectionStyle(isSelected: isSelected, color: fixedDeviceColor)
        .animation(.linear(duration: 0.3), value: fixedDeviceColor)
        .listRowInsets(EdgeInsets(top: 4, leading: 8, bottom: 4, trailing: 8))
        .onAppear {
            brightness = Double(device.stateInfo?.state.brightness ?? 0)
        }
        .onChange(of: device.stateInfo?.state.brightness) { _ in
            withAnimation(.spring()) {
                self.brightness = Double(device.stateInfo?.state.brightness ?? 0)
            }
        }
    }

    private var isOnBinding: Binding<Bool> {
        Binding(get: {
            device.stateInfo?.state.isOn ?? false
        }, set: { isOn in
            onTogglePower(isOn)
        })
    }

}

struct DeviceListItemView_Previews: PreviewProvider {
    static var previews: some View {
        let device = PreviewData.onlineDevice

        VStack(alignment: .leading) {
            Text("1st Selected, 2nd unselected")
            DeviceListItemView(
                device: device,
                isSelected: true,
                onTogglePower: { isOn in
                    print("Preview: Power toggled to \(isOn)")
                    device.stateInfo?.state.isOn = isOn
                },
                onChangeBrightness: { val in
                    print("Preview: Brightness changed to \(val)")
                    device.stateInfo?.state.brightness = Int64(val)
                }
            )
            DeviceListItemView(
                device: device,
                onTogglePower: { isOn in
                    print("Preview: Power toggled to \(isOn)")
                    device.stateInfo?.state.isOn = isOn
                },
                onChangeBrightness: { val in
                    print("Preview: Brightness changed to \(val)")
                    device.stateInfo?.state.brightness = Int64(val)
                }
            )
        }
        .padding()
        .previewLayout(.sizeThatFits)
    }
}
