# UpListTracker

A Kotlin Android app that monitors a user's "up" position (queue or sales rank) from a web page, but only when connected to a user-specified store Wi-Fi SSID.

## Features

### Core Functionality
- **Wi-Fi SSID Monitoring**: Only fetches position data when connected to the configured store Wi-Fi
- **Real-time Monitoring**: Continuous background monitoring with configurable polling intervals (1-15 minutes)
- **Position Tracking**: Robustly extracts position data from web pages using multiple CSS selectors and pattern matching. Only valid position values are returned, even for generic selectors like :contains(Position). Compatible with HTML structures like #btnSalesUpStatus and 'Position # X' patterns.
- **Session Management**: Automatic login and session cookie caching with expiration handling
- **Foreground Service**: Persistent monitoring service with notification controls

### User Interface
- **Main Screen**: Displays current position with status indicators and pull-to-refresh
- **Copy Position**: One-tap copy of current position to clipboard for easy sharing
- **Settings Screen**: Comprehensive configuration interface with real-time validation
- **Status Indicators**: Visual feedback for monitoring states (Active, Paused, Offline)
- **Accessibility**: Full content descriptions and screen reader support

### Notifications
- **Position Change Alerts**: High-priority notifications when position changes
- **Monitoring Status**: Persistent notification with current position and last checked time
- **Quick Actions**: Restart and Stop actions directly from notification
- **Test Notifications**: Built-in notification testing for verification

### Advanced Features
- **Auto-start on Boot**: Optional automatic monitoring startup after device reboot
- **Network Resilience**: Automatic reconnection and monitoring resumption
- **Error Handling**: Comprehensive error handling with user-friendly messages
- **Debug Tools**: Force error simulation and enhanced logging (debug builds only)
- **Permission Management**: Runtime permission requests with user guidance

## Architecture

### Components
- **MainActivity**: Primary UI with position display and refresh functionality
- **SettingsActivity**: Configuration interface for all app settings
- **PositionMonitorService**: Foreground service for continuous monitoring
- **PositionRepository**: StateFlow-based position data management
- **PositionUtils**: Utility class for HTML parsing and authentication
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

## Recent Improvements (Latest Update)

### Authentication & Session Handling
- **Browser-like Login Flow**: Login logic now mimics browser AJAX login, posting to `/index.php/main/login` with all required fields and headers.
- **Detailed Logging**: All login POST fields, response headers, and response body (truncated) are logged for easier debugging.
- **Robust Error Handling**: Improved detection and handling of authentication/session issues, with clear log output and fallback logic.
- **Fresh Session Strategy**: **Always performs fresh login before each position check** to ensure the latest position data is fetched, preventing stale cached session issues.
- **Automatic Session Refresh**: Automatically detects stale session data and refreshes the session to ensure fresh position data is always fetched.
- **Enhanced Session Refresh Logic**: Improved stale data detection with detailed logging, better handling of empty position data, and more robust session refresh process.
- **URL Configuration Fix**: Fixed default URLs to use base domain without `/position` suffix to prevent 404 errors.

### Position Extraction
- **Hybrid Extraction Logic**: Supports both legacy and new HTML formats, ensuring compatibility with a wide range of position page structures.
- **Correct Element Targeting**: Properly extracts position from `#btnSalesUpStatus` element with "Position # X" format.

### UI/UX Enhancements
- **Copy Position Button**: Added one-tap copy functionality to main screen
- **Enhanced Layout**: Improved position display with dedicated copy button
- **Better Icons**: Added custom copy icon for improved visual clarity
- **Debug Features**: Force error button for testing error scenarios (debug builds only)

### Notification Improvements
- **Enhanced Notification**: Shows current position and last checked time
- **Restart Action**: Added restart button to notification for quick service restart
- **Better Status Display**: More informative notification content
- **Quick Controls**: Direct access to monitoring controls from notification

### Code Quality
- **Kotlin Best Practices**: Improved null safety and error handling
- **Better Error Messages**: More descriptive error messages for users
- **Accessibility**: Enhanced content descriptions and screen reader support
- **Debug Tools**: Added testing utilities for development and QA
- **Robust Position Extraction**: Extraction logic now ensures only valid position values are returned, never generic text. Compatible with a wide range of HTML formats, including #btnSalesUpStatus and 'Position # X'.

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
- **Copy Position**: Tap the "Copy" button to copy current position to clipboard
- **Settings**: Tap the Settings button to modify configuration
- **Clear Session**: Use "Clear Cached Session" in Settings to force re-login

#### Monitoring States
- **Active**: Monitoring is running and connected to store WiFi
- **Paused**: Monitoring is temporarily stopped
- **Offline**: Not connected to store WiFi

#### Notification Controls
- **Restart**: Tap "Restart" in notification to restart monitoring service
- **Stop**: Tap "Stop" in notification to stop monitoring
- **Test**: Use "Test Notification" in Settings to verify notification permissions

## Development

### Project Structure
```
app/src/main/java/com/example/uplisttracker/
├── MainActivity.kt              # Main UI and refresh logic
├── SettingsActivity.kt          # Configuration interface
├── PositionMonitorService.kt    # Foreground service for monitoring
├── PositionRepository.kt        # StateFlow-based data management
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
- **activity_main.xml**: Main UI layout with copy functionality
- **build.gradle**: Dependencies and build configuration

### Testing

#### Comprehensive Test Suite
The app includes a comprehensive testing strategy covering multiple aspects:

##### Unit Tests
```bash
./gradlew testDebugUnitTest
```
- **PositionUtils Tests**: HTML parsing, validation, and utility functions
- **Security Tests**: XSS prevention, SQL injection protection, input validation
- **Performance Tests**: Execution time, memory usage, concurrent operations
- **Authentication Tests**: Login flow, session management, credential validation
- **Data Persistence Tests**: Settings storage and retrieval

##### Integration Tests
```bash
./gradlew connectedAndroidTest
```
- **Service Integration Tests**: PositionMonitorService lifecycle and functionality
- **UI Tests**: MainActivity interactions and user interface validation
- **Real-world Scenarios**: End-to-end testing of monitoring workflows

##### Manual Testing Checklist
See `TESTING_CHECKLIST.md` for comprehensive manual testing scenarios including:
- Core position monitoring functionality
- UI/UX interactions and accessibility
- Data persistence and settings management
- Notification system and permissions
- Error handling and edge cases
- Performance benchmarks and device compatibility

#### Test Coverage Areas
- **Core Functionality**: Position extraction, Wi-Fi detection, session management
- **Security**: Input validation, XSS prevention, secure data handling
- **Performance**: Memory usage, execution time, concurrent operations
- **UI/UX**: User interactions, accessibility, visual feedback
- **Error Handling**: Network failures, authentication errors, edge cases
- **Integration**: Service lifecycle, activity transitions, data flow

#### Debug Features
- **Force Error**: Available in debug builds for testing error scenarios
- **Enhanced Logging**: Detailed logs for debugging and development
- **Test Notifications**: Built-in notification testing
- **Performance Monitoring**: Memory and execution time tracking

## Permissions

The app requires the following permissions:
- **ACCESS_FINE_LOCATION**: Required to detect Wi-Fi SSID (Android 9+)
- **POST_NOTIFICATIONS**: Required for position change alerts (Android 13+)
- **FOREGROUND_SERVICE**: Required for continuous monitoring
- **RECEIVE_BOOT_COMPLETED**: Required for auto-start on boot (optional)

## Troubleshooting

### Common Issues
1. **"WiFi SSID unknown"**: Ensure location permission is granted
2. **"Login failed"**: Verify credentials in Settings
3. **"Offline" status**: Check if connected to correct WiFi network
4. **No notifications**: Grant notification permission in app settings

### Debug Tools
- Use "Test Notification" in Settings to verify notification setup
- Use "Force Error" (debug builds) to test error handling
- Check logs with `adb logcat` for detailed debugging information

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests for new functionality
5. Submit a pull request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Changelog

### Latest Update
- Fixed build system: aligned Hilt, Kotlin, and Android Gradle Plugin versions
- Added Hilt test dependencies for proper annotation processing
- Fixed type mismatch issues in MainActivity
- Removed enableEdgeToEdge references for compatibility
- Added position history tracking - stores last 10 position changes with timestamps
- Added "View History" button to display position history in a dialog
- Added "Clear History" option to reset position history
- Improved UI layout with better button organization
- Enhanced position display formatting
- Added copy position functionality with dedicated button
- Enhanced notification with restart action and better status display
- Added debug force error button for testing

### Previous Updates
- Implemented StateFlow for real-time position updates
- Added comprehensive testing suite
- Enhanced accessibility features
- Improved notification system
- Added auto-start on boot functionality 