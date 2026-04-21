# 📱 Shared Device Authentication App

A professional enterprise authentication solution for shared Android devices in warehouse and logistics environments, featuring role-based access control, shift management, and seamless integration with Zebra MDM and Esper Device Management.

[![Version](https://img.shields.io/badge/version-1.0.18-blue.svg)](https://github.com/SDPSHETTY/shared-device/releases)
[![Android](https://img.shields.io/badge/platform-Android-green.svg)](https://android.com)
[![Kotlin](https://img.shields.io/badge/language-Kotlin-orange.svg)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/license-Private-red.svg)](#)

## 🎯 Overview

This application transforms Android devices into secure, shared workstations where multiple users can authenticate with their roles, track work shifts, and access personalized workspaces. Perfect for warehouse operations, retail environments, and logistics centers.

### ✨ Key Features

- **🔐 Role-Based Authentication**: Manager and Team Lead access levels with password protection
- **⏱️ Shift Management**: Real-time shift timer with session tracking and analytics
- **🎨 Professional UI**: Material Design 3 interface optimized for enterprise environments
- **🔄 Session Management**: Seamless role switching with proper logout workflows
- **📱 Dual UI Modes**: Login state and authenticated state with context-aware interfaces
- **🏢 Enterprise Integration**: Zebra MDM and Esper Device Management API integration
- **🛡️ Security**: Managed configuration support with encrypted session data

## 🏗️ Architecture

### Multi-Module Structure
```
shared-device/
├── app/                    # Main authentication application
├── supervisorpulse/        # Supervisor pulse check module
├── supervisorwalk/         # Supervisor walkthrough module
├── associatepick/          # Associate picking module
└── associatescan/          # Associate scanning module
```

### Technology Stack
- **Language**: Kotlin 100%
- **UI Framework**: Android Views with Material Design 3
- **Architecture**: MVVM with Android Architecture Components
- **Async**: Kotlin Coroutines
- **Storage**: SharedPreferences for session management
- **Network**: OkHttp for API communication
- **Device Management**: Esper Device SDK

## 🚀 Quick Start

### Prerequisites
- Android Studio Arctic Fox (2020.3.1) or later
- Android SDK API level 26+ (Android 8.0)
- Kotlin 1.8+
- Gradle 8.0+

### Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/SDPSHETTY/shared-device.git
   cd shared-device
   ```

2. **Open in Android Studio**
   - File → Open → Select project directory
   - Wait for Gradle sync to complete

3. **Configure build**
   ```bash
   ./gradlew assembleRelease
   ```

4. **Install on device**
   ```bash
   adb install app/build/outputs/apk/release/app-release.apk
   ```

## ⚙️ Configuration

### Managed Configuration (Recommended)
The app supports Android Enterprise managed configuration for deployment via Esper Console:

```xml
<restrictions xmlns:android="http://schemas.android.com/apk/res/android">
    <restriction android:key="api_key"
                android:restrictionType="string"
                android:title="Esper API Key" />
    <restriction android:key="base_url"
                android:restrictionType="string"
                android:title="Esper Base URL"
                android:defaultValue="https://espersalesdemo.esper.cloud" />
    <!-- Additional configuration options available -->
</restrictions>
```

### Manual Configuration
For testing environments, configuration can be set locally through the UI.

## 🎮 Usage

### User Workflow

1. **Device Launch**: App detects existing session or shows login interface
2. **Role Selection**: User selects Manager or Team Lead role
3. **Authentication**: Password verification with role-specific access
4. **Active Session**: Professional dashboard with shift timer and session controls
5. **Role Switching**: Seamless transition between roles without device reset
6. **Session End**: Secure logout returns device to shared state

### Session Management

```kotlin
// Example: Starting a user session
AppConfig.startUserSession(
    role = "Manager",
    userId = "user123",
    userName = "John Doe",
    groupId = "manager-group-id"
)

// Example: Checking session status
if (AppConfig.isSessionActive()) {
    val duration = AppConfig.getSessionDurationFormatted() // "2h 45m"
    val role = AppConfig.getCurrentUserRole() // "Manager"
}
```

## 🏢 Enterprise Features

### Shift Analytics
- Real-time shift duration tracking
- Session start/end timestamps
- User activity logging
- Role-based usage analytics

### Device Management Integration
- **Zebra MDM**: Identity Guardian integration for lockscreen detection
- **Esper API**: Automated device group management and app deployment
- **Group Switching**: Dynamic workspace preparation based on user role

### Security Features
- Encrypted session storage
- Role-based access control
- Managed configuration enforcement
- Secure logout with workspace cleanup

## 📱 Screenshots

| Login Interface | Authenticated State | Role Switching |
|----------------|-------------------|----------------|
| Professional shared device access | Active session with shift timer | Seamless role transition dialog |

## 🧪 Testing

### Comprehensive Test Coverage
- ✅ Authentication flows (login/logout/role switching)
- ✅ Session management and persistence
- ✅ UI state transitions and error handling
- ✅ Device lifecycle and memory management
- ✅ Enterprise integration scenarios

### Running Tests
```bash
# Unit tests
./gradlew test

# Integration tests
./gradlew connectedAndroidTest

# Build verification
./gradlew assembleRelease
```

## 🚀 Deployment

### Esper Console Deployment (Recommended)
1. Build release APK: `./gradlew assembleRelease`
2. Upload to Esper Console
3. Configure managed settings
4. Deploy to device groups

### Manual Installation
```bash
adb install app/build/outputs/apk/release/app-release.apk
```

## 📋 Changelog

### v1.0.19 (2026-04-20)
- ✅ **Refined sign-in flow** - Role selection now uses a managed dropdown with a single login action
- ✅ **Polished active session UI** - Clearer session summary, switch user workflow, and logout experience
- ✅ **Validated on Zebra TC27** - Connected-device end-to-end login, switch user, and logout coverage

### v1.0.18 (2026-04-19)
- 🚀 **Initial Production Release**
- ✅ **Fixed all ANR crashes** - Enhanced findViewById safety with null checks
- ✅ **Implemented authenticated state UI** - Professional session management interface
- ✅ **Added shift timer functionality** - Real-time duration tracking
- ✅ **Enhanced role switching** - Seamless dialog-based role transitions
- ✅ **Comprehensive testing** - End-to-end functionality verification
- ✅ **Enterprise UX polish** - Material Design 3 with warehouse/logistics focus

### Previous Versions
- v1.0.17: Core authentication and session management
- v1.0.16: Zebra MDM integration and UI improvements
- Earlier versions: Foundation development and testing

## 🤝 Contributing

This is a private enterprise project. For internal development:

1. Create feature branch: `git checkout -b feature/amazing-feature`
2. Commit changes: `git commit -m 'Add amazing feature'`
3. Push branch: `git push origin feature/amazing-feature`
4. Create Pull Request

### Development Guidelines
- Follow Kotlin coding standards
- Maintain Material Design 3 consistency
- Add comprehensive logging for debugging
- Test on multiple Android API levels (26+)

## 📞 Support

### Technical Support
- **Enterprise Issues**: Contact IT administrator
- **Device Management**: Esper Console documentation
- **Zebra Integration**: Zebra Technologies support

### Development Support
- **Architecture Questions**: Review codebase documentation
- **API Integration**: Esper Device SDK documentation
- **Build Issues**: Check Gradle configuration

## 🔒 Security & Privacy

- **Session Encryption**: All user session data encrypted locally
- **API Security**: HTTPS-only communication with certificate pinning
- **Data Retention**: Session data cleared on logout
- **Compliance**: Designed for enterprise security requirements

## 📄 License

Private Enterprise Software - All Rights Reserved

This software is proprietary and confidential. Unauthorized copying, distribution, or use is strictly prohibited.

---

**Built with ❤️ for Enterprise Mobility**

*Transforming shared devices into secure, personalized workstations for the modern workforce.*
