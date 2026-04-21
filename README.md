# 📱 Shared Device Authentication App

A professional enterprise authentication solution for shared Android devices in warehouse and logistics environments, featuring role-based access control, shift management, and seamless integration with Zebra MDM and Esper Device Management.

[![Version](https://img.shields.io/badge/version-1.0.20-blue.svg)](https://github.com/SDPSHETTY/shared-device/releases)
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

## ⚙️ Setup & Configuration

### 🏢 Esper Console Setup (3-Step Process)

#### **Step 1: Create Device Groups**
Create 3 device groups in Esper Console:
- **Home Group** - Default/logout state (shared device mode)
- **Manager Group** - Manager role workspace
- **Team Lead Group** - Team Lead role workspace

#### **Step 2: Create Blueprints**
Create 3 blueprints with appropriate app assignments:
- **Home Blueprint** - Authentication app only (assigned to Home Group)
- **Manager Blueprint** - Manager tools + permissions (assigned to Manager Group)
- **Team Lead Blueprint** - Team Lead tools + permissions (assigned to Team Lead Group)

#### **Step 3: Configure Managed Settings**
In Esper Console, configure the authentication app with managed configuration:

```json
{
  "api_key": "your_esper_api_key_here",
  "role_one_label": "Manager",
  "role_two_label": "Team Lead",
  "role_one_group_id": "manager-group-uuid-here",
  "role_one_password": "manager_password",
  "role_two_group_id": "teamlead-group-uuid-here",
  "role_two_password": "teamlead_password"
}
```

**📝 Configuration Notes:**
- **API Key**: Get from Esper Console → Settings → API Keys
- **Group IDs**: Copy from Device Groups → Group Details → UUID
- **Base URL**: Automatically detected (no longer required)
- **Passwords**: Set secure passwords for each role

### 🔄 How Role Switching Works

1. **Device starts** → Assigned to Home Group (auth app only)
2. **User selects role** → Enters password → **App moves device to role group**
3. **Role-specific blueprint activates** → Appropriate apps/permissions load
4. **Switch User/Logout** → Device returns to Home Group

### 🚀 Deployment Checklist

- [ ] Upload auth app to Esper Console
- [ ] Create 3 device groups (Home, Manager, Team Lead)
- [ ] Create 3 blueprints with correct app assignments
- [ ] Configure managed settings with API key and group IDs
- [ ] Assign devices to Home Group initially
- [ ] Test role switching functionality
- [ ] Deploy to production devices

## 🎮 Usage

### 👥 User Workflow

1. **Device Launch**: Shows login interface with role dropdown
2. **Role Selection**: User selects "Manager" or "Team Lead" from dropdown
3. **Authentication**: Enters role-specific password
4. **Device Group Switch**: App automatically moves device to appropriate group
5. **Role Workspace Loads**: Blueprint activates with role-specific apps/permissions
6. **Active Session**: Dashboard shows shift timer, user info, and session controls
7. **Role Switching**: "Switch User" button transitions to different role seamlessly
8. **Session End**: "Logout" returns device to Home Group (shared state)

### 🔧 Administrator Workflow

1. **Initial Setup**: Create groups (Home, Manager, Team Lead) and blueprints
2. **App Deployment**: Upload auth app and configure managed settings
3. **Device Assignment**: Assign devices to Home Group initially
4. **Testing**: Verify role switching moves devices between groups correctly
5. **Monitoring**: Track device group changes and user sessions in Esper Console

### 📱 Device States

| State | Group | Blueprint | Apps Available |
|-------|--------|-----------|----------------|
| **Shared/Logout** | Home Group | Home Blueprint | Auth app only |
| **Manager Session** | Manager Group | Manager Blueprint | Auth app + Manager tools |
| **Team Lead Session** | Team Lead Group | Team Lead Blueprint | Auth app + Team Lead tools |

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

### Production Deployment via Esper Console

#### **Quick Setup for New Users:**
1. **Upload APK**
   - Build release APK: `./gradlew assembleRelease`
   - Upload `app-release.apk` to Esper Console → Apps

2. **Create Infrastructure**
   ```
   Device Groups: Home → Manager → Team Lead
   Blueprints: Home BP → Manager BP → Team Lead BP
   App Assignments: Auth app to all groups + role-specific apps
   ```

3. **Configure Managed Settings**
   - Navigate to Apps → Shared device → Managed Config
   - Paste your configuration JSON (see Configuration section above)

4. **Deploy & Test**
   - Assign test device to Home Group
   - Verify role switching works between groups
   - Deploy to production devices

#### **Configuration Example:**
```json
{
  "api_key": "97ekzQ6mr71a1C7Yazzjin2dV1F9kQ",
  "role_one_label": "Manager",
  "role_two_label": "Team Lead",
  "role_one_group_id": "2264336c-2ea1-46bb-8396-5593ebe4c27b",
  "role_one_password": "sup123",
  "role_two_group_id": "8ba0219c-ff8d-4fff-940e-fdc35b5d8a6b",
  "role_two_password": "ass123"
}
```

### Development Installation
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## 📋 Changelog

### v1.0.20 (2026-04-21) - **Latest**
- 🔧 **Fixed keyboard and password field issues** - Major UX improvements for Zebra devices
- ✅ **Added NestedScrollView wrapper** - Form scrolls when keyboard appears, password field stays visible
- ✅ **Enhanced password field handling** - Auto-clear on failed login, immediate error feedback
- ✅ **Improved keyboard interaction** - Press "Done" key to submit login, live error clearing
- ✅ **Better state management** - Clean transitions during role switching and error recovery
- ✅ **Zebra device optimization** - Changed to `adjustPan` window mode for better compatibility
- 🧪 **End-to-end tested** - Comprehensive validation on Zebra TC27 devices

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

## 🔧 Troubleshooting

### Common Setup Issues

**❌ "Role switching not working"**
- ✅ Verify API key has device management permissions
- ✅ Check group IDs are correct UUIDs from Esper Console
- ✅ Ensure device has internet connectivity
- ✅ Confirm blueprints are assigned to correct groups

**❌ "App not launching after role switch"**
- ✅ Verify role-specific apps are assigned to blueprints
- ✅ Check blueprint is properly assigned to device group
- ✅ Allow time for blueprint activation (30-60 seconds)

**❌ "Password authentication failing"**
- ✅ Check managed config JSON syntax is valid
- ✅ Verify passwords match configured values exactly
- ✅ Ensure app has received managed configuration

**❌ "Device stuck in loading state"**
- ✅ Check device internet connectivity
- ✅ Verify Esper API endpoint is accessible
- ✅ Check device logs for API errors
- ✅ Restart app if timeout occurs (30 second limit)

### Debug Information
- **App Logs**: `adb logcat | grep AuthApp`
- **Device Group**: Check Esper Console → Devices → [Device] → Group
- **Config Status**: Look for managed configuration in app settings

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
