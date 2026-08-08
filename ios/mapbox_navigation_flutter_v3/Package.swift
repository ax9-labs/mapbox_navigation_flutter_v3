// swift-tools-version: 5.9
// The swift-tools-version declares the minimum version of Swift required to build this package.

import PackageDescription

let package = Package(
    name: "mapbox_navigation_flutter_v3",
    platforms: [
        // Mapbox Navigation SDK v3 for iOS requires iOS 14+ (see
        // mapbox/mapbox-navigation-ios's own Package.swift) - bumped from
        // the plugin template's default 13.0.
        .iOS("14.0")
    ],
    products: [
        .library(name: "mapbox-navigation-flutter-v3", targets: ["mapbox_navigation_flutter_v3"])
    ],
    dependencies: [
        .package(name: "FlutterFramework", path: "../FlutterFramework"),
        // Mapbox iOS Navigation SDK v3. SPM-only as of v3 (no CocoaPods
        // pod for this generation) - see README.md for the .netrc setup
        // this needs to authenticate against Mapbox's private package
        // registry (same DOWNLOADS:READ-scoped secret token used for
        // Android's MAPBOX_DOWNLOADS_TOKEN, different transport).
        .package(url: "https://github.com/mapbox/mapbox-navigation-ios.git", exact: "3.28.0")
    ],
    targets: [
        .target(
            name: "mapbox_navigation_flutter_v3",
            dependencies: [
                .product(name: "FlutterFramework", package: "FlutterFramework"),
                .product(name: "MapboxNavigationCore", package: "mapbox-navigation-ios"),
                .product(name: "MapboxNavigationUIKit", package: "mapbox-navigation-ios")
            ],
            resources: [
                // If your plugin requires a privacy manifest, for example if it uses any required
                // reason APIs, update the PrivacyInfo.xcprivacy file to describe your plugin's
                // privacy impact, and then uncomment these lines. For more information, see
                // https://developer.apple.com/documentation/bundleresources/privacy_manifest_files
                // .process("PrivacyInfo.xcprivacy"),

                // If you have other resources that need to be bundled with your plugin, refer to
                // the following instructions to add them:
                // https://developer.apple.com/documentation/xcode/bundling-resources-with-a-swift-package
            ]
        )
    ]
)
