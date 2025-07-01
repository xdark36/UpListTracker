# UpListTracker

## Overview
UpListTracker is an Android application that monitors queue positions or sales rankings from web pages. It features real-time monitoring with foreground service capabilities, WiFi-based activation, and push notifications for position changes.

## Features

### Core Functionality
- **Real-time Position Monitoring**: Continuously checks web pages for position changes using a foreground service
- **WiFi-Based Activation**: Only operates when connected to a specified store WiFi network
- **Push Notifications**: Sends high-priority notifications when position changes are detected
- **Manual Refresh**: Pull-to-refresh functionality for immediate position checks
- **Settings Management**: Runtime configuration of WiFi SSID, target URL, and polling intervals
- **Session Management**: Automatic login credential caching and session renewal

### Technical Features
- **Foreground Service**: Persistent monitoring that survives app backgrounding
- **Session Management**: Handles login sessions for authenticated web pages with cookie caching
- **Retry Logic**: Exponential backoff for failed requests
- **Status Indicators**: Visual feedback for monitoring state (Active/Paused/Offline)
- **Accessibility Support**: Screen reader announcements for status changes
- **Dark Mode Support**: Proper theming for both light and dark themes
- **Structured Logging**: Comprehensive logging with Timber for debugging and monitoring

### Security Features
- **Configurable Credentials**: Login URL, employee number, and password stored in SharedPreferences
- **Session Caching**: Reduces login overhead with 30-minute cookie caching
- **Automatic Session Renewal**: Detects expired sessions and re-authenticates automatically
- **Secure Storage**: No hard-coded credentials in source code

## Architecture

### Key Components
- **MainActivity**: Main UI with position display and pull-to-refresh
- **PositionMonitorService**: Foreground service for continuous monitoring
- **SettingsActivity**: Configuration interface for app settings
- **PositionUtils**: Utility class for HTML parsing
- **UpListTrackerApplication**: Application class with Hilt dependency injection

### Dependencies
- **OkHttp**: HTTP client for web requests
- **Jsoup**: HTML parsing and DOM manipulation
- **Hilt**: Dependency injection framework
- **Timber**: Advanced logging
- **Material Components**: Modern UI components
- **SwipeRefreshLayout**: Pull-to-refresh functionality
- **Robolectric**: Android testing framework
- **MockWebServer**: HTTP server mocking for tests

## Setup Instructions

### Prerequisites
- Android Studio Arctic Fox or later
- Android SDK 24+ (API level 24)
- Target device/emulator with Android 7.0+

### Installation
1. **Clone the repository:**
   ```bash
   git clone <your-repo-url>
   cd UpListTracker
   ```

2. **Open in Android Studio:**
   - Launch Android Studio
   - Select "Open an existing project"
   - Navigate to the UpListTracker directory

3. **Build the project:**
   ```bash
   ./gradlew clean build
   ```

4. **Install on device:**
   ```bash
   ./gradlew installDebug
   ```

### Configuration

#### Initial Setup
1. Launch the app
2. Tap the **Settings** button
3. Configure the following:
   - **Store WiFi SSID**: The name of your store's WiFi network (default: "Sales")
   - **Login URL**: The login page URL (default: "https://selling1.vcfcorp.com/")
   - **Employee Number**: Your employee ID (default: "90045")
   - **Password**: Your login password (default: "03")
   - **Position URL**: The web page URL to monitor (default: "https://selling.vcfcorp.com/")
   - **Polling Interval**: How often to check for changes (1-15 minutes)
   - **Real-time Monitoring**: Enable/disable continuous monitoring

#### CSS Selector Configuration
The app looks for position data using the CSS selector `#position-element`. If your target page uses a different selector:
1. Open `PositionMonitorService.kt`
2. Update the `POSITION_SELECTOR` constant (line 35)
3. Rebuild and install the app

### Usage

#### Starting Monitoring
1. Connect to your store's WiFi network
2. Open the app
3. Enable "Real-time Monitoring" in Settings
4. The app will start monitoring automatically

#### Manual Checks
- **Pull-to-refresh**: Swipe down on the main screen for immediate position check
- **Settings**: Tap the Settings button to modify configuration
- **Clear Session**: Use "Clear Cached Session" in Settings to force re-login

#### Monitoring States
- **Active**: Monitoring is running and connected to store WiFi
- **Paused**: Monitoring is temporarily stopped
- **Offline**: Not connected to store WiFi

## Development

### Project Structure
```
app/src/main/java/com/example/uplisttracker/
├── MainActivity.kt              # Main UI and refresh logic
├── PositionMonitorService.kt    # Foreground service for monitoring
├── SettingsActivity.kt          # Configuration interface
├── PositionUtils.kt             # HTML parsing utilities
├── UpListTrackerApplication.kt  # Application class with Hilt setup
└── ui/theme/                    # UI theming resources

app/src/test/java/com/example/uplisttracker/
├── PositionUtilsTest.kt         # HTML parsing tests
├── AuthenticationTest.kt        # Login and session tests
└── SharedPreferencesTest.kt     # Settings storage tests
```

### Key Files
- **MainActivity.kt**: Handles UI interactions and manual position fetching
- **PositionMonitorService.kt**: Core monitoring logic with login session management
- **SettingsActivity.kt**: Runtime configuration interface
- **activity_main.xml**: Main UI layout
- **build.gradle**: Dependencies and build configuration

### Testing

#### Running Tests
```bash
# Run all unit tests
./gradlew test

# Run specific test class
./gradlew test --tests PositionUtilsTest

# Run with coverage
./gradlew testDebugUnitTestCoverage
```

#### Test Coverage
- **HTML Parsing**: Tests for various HTML structures and edge cases
- **Authentication**: Login success/failure, session management, timeout handling
- **Settings**: SharedPreferences storage and retrieval
- **Network**: HTTP response codes, cookie handling, error scenarios

#### Test Dependencies
- **MockWebServer**: Simulates HTTP responses for testing
- **Robolectric**: Android framework testing without emulator
- **JUnit**: Core testing framework
- **Kotlin Coroutines Test**: Async code testing

### Logging and Debugging

#### Structured Logging
The app uses Timber for comprehensive logging:
- **Debug builds**: Full logging to logcat
- **Release builds**: Custom crash reporting tree (configurable)

#### Key Log Messages
- `Position changed from 'X' to 'Y'`
- `Login successful, cached X cookies`
- `Session expired, clearing cached cookies`
- `Using cached cookies (age: Xs)`

#### Debug Information
- Enable Timber logging for detailed debug information
- Check logcat for service and network-related errors
- Verify WiFi SSID configuration matches exactly
- Monitor session cookie expiration and renewal

## Permissions

- `INTERNET`: Required for network access to fetch queue/sales position.
- `ACCESS_WIFI_STATE`, `ACCESS_NETWORK_STATE`: Required to check if the device is on the correct store WiFi.
- `FOREGROUND_SERVICE`: Required for real-time monitoring in the background.
- `POST_NOTIFICATIONS`: Required for sending notifications (Android 13+).
- `RECEIVE_BOOT_COMPLETED`: Allows the app to auto-restart monitoring after device reboot (if enabled in settings).
- `ACCESS_FINE_LOCATION`: **Required on Android 9+** to read the current WiFi SSID. The app requests this permission at runtime. If denied, monitoring is paused and a banner is shown.

## Session & Cookie Management

- All login, session, and cookie logic is now unified in `PositionUtils`. Both the Activity and Service use this utility for authentication, session caching, and making authenticated requests. This ensures robust, DRY, and consistent session handling throughout the app.

## User Experience

- If location permission is denied, the app will show a banner and pause monitoring until permission is granted.
- The app is resilient to network interruptions and process kills, and will always request the necessary permissions at runtime.

## Troubleshooting

### Common Issues
1. **"Not on store WiFi"**: Ensure you're connected to the configured WiFi network
2. **"Login failed"**: Check credentials in Settings
3. **"Selector not found"**: Verify the CSS selector matches your target page
4. **"Session expired"**: Use "Clear Cached Session" in Settings
5. **Notifications not showing**: Grant notification permissions in app settings

### Debug Information
- Enable Timber logging for detailed debug information
- Check logcat for service and network-related errors
- Verify WiFi SSID configuration matches exactly
- Monitor session cookie expiration and renewal

### Performance Optimization
- Session cookies are cached for 30 minutes to reduce login overhead
- Exponential backoff for failed requests prevents server overload
- Foreground service ensures reliable background monitoring

## Contributing

### Development Setup
1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests for new functionality
5. Ensure all tests pass
6. Submit a pull request

### Code Style
- Follow Kotlin coding conventions
- Use meaningful variable and function names
- Add comments for complex logic
- Include unit tests for new features

## License
[Add your license information here]

## Changelog

### Version 1.0
- Initial release with basic position monitoring
- Foreground service implementation
- WiFi-based activation
- Push notifications for position changes
- Settings management
- Session caching and management
- Comprehensive automated testing
- Dark mode support
- Accessibility improvements
- Structured logging with Timber

## Recent Updates

- **Manifest & Application ID**: Ensured correct manifest formatting and applicationId quoting in build.gradle.
- **Polling Interval Picker**: The Settings screen now features a NumberPicker with a live-updating label showing the current interval in minutes.
- **Banner Timestamp**: The banner now displays a "Last checked at..." timestamp for better user feedback.
- **Android 13+ Notification Permission**: The app now requests the POST_NOTIFICATIONS permission at runtime on Android 13+ devices, ensuring notifications work as expected.

## Wi-Fi SSID Robustness

- If your device returns `SSID_UNKNOWN` or `<unknown ssid>` (common on some Android devices), the app will now retry after a short delay instead of immediately going offline. If this state persists, a warning banner is shown in the app. 