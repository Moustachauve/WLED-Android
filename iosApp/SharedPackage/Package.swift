// swift-tools-version: 6.2
import PackageDescription

let package = Package(
    name: "SharedPackage",
    platforms: [
        .iOS(.v18)
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
