# Manual Testing Guide for UpListTracker

## ✅ What We Can Test Right Now

### 1. **App Installation & Basic Functionality**
- [ ] Install the app on a device/emulator
- [ ] Verify the app launches without crashes
- [ ] Check that the main screen displays correctly
- [ ] Verify settings screen opens and works

### 2. **Position Extraction Testing**
Since our unit tests show this works, let's test it manually:

#### Test HTML Parsing
1. **Create test HTML files** with different position values:
   ```html
   <!-- test1.html -->
   <div id='position-element'>42</div>
   
   <!-- test2.html -->
   <html><body><div id='position-element'>123</div></body></html>
   
   <!-- test3.html -->
   <div>No position here</div>
   ```

2. **Test the parsing manually** by:
   - Opening the HTML files in a browser
   - Using browser dev tools to verify the selector works
   - Manually checking if `#position-element` finds the right content

### 3. **UI Testing**
- [ ] **Main Activity**: Verify all buttons are present and clickable
- [ ] **Settings Activity**: Test all input fields and validation
- [ ] **Copy Position**: Test clipboard functionality
- [ ] **Share Position**: Test share intent
- [ ] **Pull-to-refresh**: Test refresh gesture
- [ ] **Accessibility**: Test with screen reader enabled

### 4. **Network Testing**
- [ ] **Wi-Fi Detection**: Test on different Wi-Fi networks
- [ ] **Network Permissions**: Verify location permission requests
- [ ] **Offline Handling**: Test behavior when network is unavailable
- [ ] **Slow Network**: Test on slow connections

### 5. **Service Testing**
- [ ] **Service Start**: Verify foreground service starts correctly
- [ ] **Service Persistence**: Test service survives app backgrounding
- [ ] **Service Stop**: Test service stops when requested
- [ ] **Notification**: Verify notification appears and works

### 6. **Data Persistence Testing**
- [ ] **Settings Save**: Change settings and restart app
- [ ] **Session Cookies**: Test cookie caching and expiration
- [ ] **Position History**: Verify position data is stored
- [ ] **Default Values**: Test app behavior with default settings

### 7. **Error Handling Testing**
- [ ] **Invalid Credentials**: Test with wrong login info
- [ ] **Network Errors**: Test with no internet connection
- [ ] **Server Errors**: Test with invalid URLs
- [ ] **Permission Denial**: Test when permissions are denied

### 8. **Performance Testing**
- [ ] **App Startup**: Time how long app takes to start
- [ ] **Memory Usage**: Monitor memory usage during extended use
- [ ] **Battery Impact**: Check battery usage during monitoring
- [ ] **UI Responsiveness**: Test UI remains responsive during operations

## 🧪 Test Scenarios

### Scenario 1: Happy Path
1. Install app
2. Configure settings with valid credentials
3. Connect to store Wi-Fi
4. Start monitoring
5. Verify position updates
6. Test copy/share functionality

### Scenario 2: Error Recovery
1. Start with invalid credentials
2. Verify error messages
3. Correct credentials
4. Verify monitoring starts working

### Scenario 3: Network Changes
1. Start monitoring on store Wi-Fi
2. Switch to different Wi-Fi
3. Verify monitoring stops
4. Switch back to store Wi-Fi
5. Verify monitoring resumes

### Scenario 4: App Lifecycle
1. Start monitoring
2. Background the app
3. Kill the app process
4. Restart the app
5. Verify monitoring state is preserved

## 📊 Test Results Template

```
Test Date: _______________
Device: _______________
Android Version: _______________

### Core Functionality
- [ ] App launches without crashes
- [ ] Settings screen works
- [ ] Wi-Fi detection works
- [ ] Position extraction works
- [ ] Monitoring starts/stops correctly

### UI/UX
- [ ] All buttons are clickable
- [ ] Copy position works
- [ ] Share position works
- [ ] Pull-to-refresh works
- [ ] Accessibility features work

### Notifications
- [ ] Position change notifications appear
- [ ] Monitoring status notification works
- [ ] Notification actions work (Restart/Stop)

### Error Handling
- [ ] Invalid credentials show appropriate errors
- [ ] Network errors are handled gracefully
- [ ] Permission requests work correctly

### Performance
- [ ] App starts quickly (< 3 seconds)
- [ ] UI remains responsive
- [ ] Memory usage is reasonable
- [ ] Battery impact is minimal

### Issues Found:
1. ________________
2. ________________
3. ________________

### Recommendations:
1. ________________
2. ________________
3. ________________
```

## 🚀 Next Steps

1. **Run through the manual test scenarios**
2. **Document any issues found**
3. **Test on different devices/Android versions**
4. **Verify real-world usage with actual store Wi-Fi**
5. **Monitor performance over extended periods**

## 📝 Notes

- The unit tests we created verify the core logic works
- Manual testing is essential for UI/UX and real-world scenarios
- Focus on testing the actual user experience
- Document any bugs or performance issues found
- Test edge cases and error conditions thoroughly 