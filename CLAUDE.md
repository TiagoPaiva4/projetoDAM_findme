# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

FindMe is an Android location-sharing app that allows users to share real-time locations with friends and groups, create geofenced areas with boundary notifications, and manage friend requests.

**Tech Stack:** Kotlin, Android SDK (API 24-36), Volley (HTTP), Google Maps/Location Services, PHP backend with Azure MySQL.

## Build Commands

```bash
./gradlew build                    # Full build
./gradlew assembleDebug            # Debug APK
./gradlew test                     # Unit tests
./gradlew connectedAndroidTest     # Instrumented tests
./gradlew installDebug             # Install to connected device
./gradlew clean                    # Clean build artifacts
```

## Architecture

### Android App Structure

Traditional Activity-based architecture without MVVM. Activities handle UI, business logic, and API calls directly via Volley.

**Main Activities:**
- `MainActivity` - Map view with friends list, tab navigation, real-time location markers
- `LoginActivity`/`RegisterActivity` - Authentication
- `GroupsActivity`/`GroupDetailsActivity` - Group management and member tracking
- `ProfileActivity` - User settings, location sharing toggle, pending requests
- `ZonesActivity`/`MapsActivity` - Geofenced area management (partially implemented)

**Services:**
- `LocationService` - Foreground service for continuous location tracking (5s intervals, 15m min distance)
- `NotificationService` - Background service for friend request notifications

**Data Models:** `Friend`, `Group`, `FriendRequest`, `Zone` (in respective Adapter and model files)

**Session:** SharedPreferences key `"SessaoUsuario"` stores user id, name, email, token, login state, location sharing preference.

### Backend Structure (`/backend`)

PHP APIs with Azure MySQL database. Key endpoints:
- Auth: `login.php`, `register.php`
- Location: `update_location.php`, `get_users_locations.php`
- Friends: `add_friend.php`, `get_pending_requests.php`, `accept_request.php`, `reject_request.php`
- Groups: `create_group.php`, `get_my_groups.php`, `add_group_member.php`, `remove_group_member.php`
- Areas: `create_area.php`, `delete_area.php`, `get_user_areas.php`

API Base URL: `https://findmyandroid-e0cdh2ehcubgczac.francecentral-01.azurewebsites.net/backend/`

### Data Flow

1. Activities make HTTP requests via Volley to backend PHP APIs
2. Backend processes against Azure MySQL, returns JSON
3. Responses parsed into Kotlin data classes, UI updated via adapters

## Key Configuration

- **Google Maps API Key:** In `AndroidManifest.xml`
- **Cleartext Traffic:** Enabled (`usesCleartextTraffic="true"`)
- **Permissions:** Internet, fine/coarse location, foreground service, notifications

## Current TODOs

- `ZonesActivity.kt`: Zone creation UI and fetching existing zones from backend
- `update_location.php`: Push notification implementation for geofence triggers
