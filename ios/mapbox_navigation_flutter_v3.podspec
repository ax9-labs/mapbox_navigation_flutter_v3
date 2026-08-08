#
# To learn more about a Podspec see http://guides.cocoapods.org/syntax/podspec.html.
# Run `pod lib lint mapbox_navigation_flutter_v3.podspec` to validate before publishing.
#
Pod::Spec.new do |s|
  s.name             = 'mapbox_navigation_flutter_v3'
  s.version          = '0.0.1'
  s.summary          = 'A new Flutter plugin project.'
  s.description      = <<-DESC
A new Flutter plugin project.
                       DESC
  s.homepage         = 'http://example.com'
  s.license          = { :file => '../LICENSE' }
  s.author           = { 'Your Company' => 'email@example.com' }
  s.source           = { :path => '.' }
  s.source_files = 'mapbox_navigation_flutter_v3/Sources/mapbox_navigation_flutter_v3/**/*'
  s.dependency 'Flutter'
  s.platform = :ios, '14.0'

  # IMPORTANT: Mapbox Navigation SDK v3 for iOS is distributed via Swift
  # Package Manager only - there is no CocoaPods pod for
  # MapboxNavigationCore/MapboxNavigationUIKit v3. This podspec exists
  # because `flutter create --template=plugin` always generates one, but a
  # host app that resolves this plugin purely via CocoaPods will fail to
  # compile (the Mapbox imports won't resolve). Consumers must use
  # Flutter's Swift Package Manager plugin support (Flutter 3.24+) - see
  # README.md. The actual Mapbox dependency is declared in
  # mapbox_navigation_flutter_v3/Package.swift, not here.

  # Flutter.framework does not contain a i386 slice.
  s.pod_target_xcconfig = { 'DEFINES_MODULE' => 'YES', 'EXCLUDED_ARCHS[sdk=iphonesimulator*]' => 'i386' }
  s.swift_version = '5.0'

  # If your plugin requires a privacy manifest, for example if it uses any
  # required reason APIs, update the PrivacyInfo.xcprivacy file to describe your
  # plugin's privacy impact, and then uncomment this line. For more information,
  # see https://developer.apple.com/documentation/bundleresources/privacy_manifest_files
  # s.resource_bundles = {'mapbox_navigation_flutter_v3_privacy' => ['mapbox_navigation_flutter_v3/Sources/mapbox_navigation_flutter_v3/PrivacyInfo.xcprivacy']}
end
