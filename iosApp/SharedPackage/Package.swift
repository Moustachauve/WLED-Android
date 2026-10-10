// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "SharedPackage",
    platforms: [
        .iOS(.v16)
    ],
    products: [
        .library(
            name: "Shared",
            targets: ["Shared"]
        ),
    ],
    targets: [
        .binaryTarget(
            name: "Shared",
            path: "../../shared/build/XCFrameworks/Shared.xcframework"
        )
    ]
)
