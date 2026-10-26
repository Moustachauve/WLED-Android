import SwiftUI
import WidgetKit

/// Entry view bridging `WledDeviceWidgetEntry` and `@Environment(\.widgetFamily)` to `WledDeviceWidgetView`.
struct WledDeviceWidgetEntryView: View {
    @Environment(\.widgetFamily) private var widgetFamily

    let entry: WledDeviceWidgetEntry

    var body: some View {
        WledDeviceWidgetView(
            data: entry.data,
            layoutStyle: widgetFamily == .systemSmall ? .narrow : .wide
        )
    }
}

/// Home Screen widget for controlling and monitoring a single WLED device on iOS.
struct WledDeviceWidget: Widget {
    let kind: String = WledWidgetSnapshotStore.widgetKind

    var body: some WidgetConfiguration {
        AppIntentConfiguration(
            kind: kind,
            intent: SelectWledDeviceWidgetIntent.self,
            provider: WledDeviceWidgetTimelineProvider()
        ) { entry in
            WledDeviceWidgetEntryView(entry: entry)
        }
        .configurationDisplayName("Single Device")
        .description("Control a single WLED device")
        .supportedFamilies([.systemSmall, .systemMedium])
        .contentMarginsDisabled()
        .promptsForUserConfiguration()
    }
}
