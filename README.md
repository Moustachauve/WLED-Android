<a href='https://play.google.com/store/apps/details?id=ca.cgagnier.wlednativeandroid&utm_source=github&pcampaignid=pcampaignidMKT-Other-global-all-co-prtnr-py-PartBadge-Mar2515-1'><img alt='Get it on Google Play' src='https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png' height='80'/></a>
<a href='https://apps.apple.com/us/app/wled-native/id6446207239'><img alt='Download on the App Store' src='https://developer.apple.com/assets/elements/badges/download-on-the-app-store.svg' height='80'/></a>
<a href='https://apt.izzysoft.de/packages/ca.cgagnier.wlednativeandroid'><img alt='Get it at IzzyOnDroid' src='https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png' height='80'/></a>

# WLED-android

Native applications for Android and iOS for discovering and controlling your [WLED](https://github.com/Aircoookie/WLED) devices easily!  
This aims to replace the previous WLED app [found here](https://github.com/Aircoookie/WLED-App).

### Features
- Automatic device detection (mDNS)
- All lights are accessible from one list
- Custom names
- Opens control UI immediately if connected to WLED-AP
- Hide or delete devices
- Light and dark mode
- Tablet and iPad support for a better experience on wider screens

## Project Structure
- **`app/`**: Native Android application built with Jetpack Compose and Material 3.
- **`iosApp/`**: Native iOS application built with SwiftUI (see [iosApp/README.md](iosApp/README.md)).
- **`shared/`**: Kotlin Multiplatform (KMP) shared business logic, database, models, and network layer.

## Changelog
You can view the release notes and keep track of new features inside the [Changelog Directory](app/src/main/assets/changelog/). 
If you are contributing to the project, please try to keep the latest changelog up to date with your changes!

## Contributing
See [CONTRIBUTING.md](CONTRIBUTING.md) for branch strategy, coding guidelines, and pull request workflows.

## License
This project is licensed under the [GNU General Public License v3.0](LICENSE).

## Disclaimer

This project is not an official Google project. It is not supported by
Google and Google specifically disclaims all warranties as to its quality,
merchantability, or fitness for a particular purpose.

Google Play and the Google Play logo are trademarks of Google LLC.
Apple, the Apple logo, iPhone, and iPad are trademarks of Apple Inc., registered in the U.S. and other countries. App Store is a service mark of Apple Inc.
