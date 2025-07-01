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

### Technical Features
- **Foreground Service**: Persistent monitoring that survives app backgrounding
- **Session Management**: Handles login sessions for authenticated web pages
- **Retry Logic**: Exponential backoff for failed requests
- **Status Indicators**: Visual feedback for monitoring state (Active/Paused/Offline)
- **Accessibility Support**: Screen reader announcements for status changes

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
```

### Key Files
- **MainActivity.kt**: Handles UI interactions and manual position fetching
- **PositionMonitorService.kt**: Core monitoring logic with login session management
- **SettingsActivity.kt**: Runtime configuration interface
- **activity_main.xml**: Main UI layout
- **build.gradle**: Dependencies and build configuration

### Testing
- **Unit Tests**: Located in `app/src/test/java/`
- **Instrumentation Tests**: Located in `app/src/androidTest/java/`
- **Manual Testing**: Use the pull-to-refresh feature for immediate testing

## Permissions

The app requires the following permissions:
- **INTERNET**: For web requests
- **ACCESS_NETWORK_STATE**: For WiFi connectivity checks
- **ACCESS_WIFI_STATE**: For WiFi SSID detection
- **POST_NOTIFICATIONS**: For position change alerts (Android 13+)
- **FOREGROUND_SERVICE**: For continuous monitoring

## Troubleshooting

### Common Issues
1. **"Not on store WiFi"**: Ensure you're connected to the configured WiFi network
2. **"Login failed"**: Check credentials in `PositionMonitorService.kt`
3. **"Selector not found"**: Verify the CSS selector matches your target page
4. **Notifications not showing**: Grant notification permissions in app settings

### Debug Information
- Enable Timber logging for detailed debug information
- Check logcat for service and network-related errors
- Verify WiFi SSID configuration matches exactly

## License
[Add your license information here]

## Contributing
[Add contribution guidelines here] 