# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

FindMe is an Android location-sharing app that allows users to share real-time locations with friends and groups, create geofenced polygon areas with boundary monitoring, and manage friend requests.

**Tech Stack:** Kotlin, Android SDK (API 24-36), Volley (HTTP), Google Maps/Location Services, PHP backend with Azure MySQL.

**Language:** Portuguese (UI strings, variable names, comments).

## Build Commands

```bash
./gradlew build                    # Full build
./gradlew assembleDebug            # Debug APK
./gradlew test                     # Unit tests
./gradlew test --tests "ClassName" # Run single test class
./gradlew connectedAndroidTest     # Instrumented tests (requires device/emulator)
./gradlew installDebug             # Install to connected device
./gradlew clean                    # Clean build artifacts
```

## Architecture

### Android App Structure

Traditional Activity-based architecture without MVVM. Activities handle UI, business logic, and API calls directly via Volley.

**Main Activities:**
- `MainActivity` - Map view with friends list, tab navigation, real-time location markers
- `LoginActivity`/`RegisterActivity` - Authentication (email/password + Google Sign-In)
- `GroupsActivity`/`GroupDetailsActivity` - Group management and member tracking
- `ProfileActivity` - User settings, location sharing toggle, pending requests
- `ZonesActivity` - Lists user's geofenced zones with CRUD operations (long-press for options menu)
- `MapsActivity` - Multi-mode map: CREATE_ZONE (draw polygon), VIEW_ZONE (real-time tracking with color feedback), EDIT_ZONE (modify existing polygon)

**Services:**
- `LocationService` - Foreground service for continuous location tracking (5s intervals, 15m min distance)
- `NotificationService` - Background service for friend request notifications

**Data Models:** `Friend`, `Group`, `FriendRequest`, `Zone`, `LatLng` (custom, in `Zone.kt`) - uses `@Parcelize` for intent passing.

**Session:** SharedPreferences key `"SessaoUsuario"` stores user id, name, email, token, login state, location sharing preference.

### Zones Feature

Full CRUD for polygon geofences:
- Users draw polygons by tapping map points (minimum 3 points)
- Zones can monitor self or friends' locations
- VIEW_ZONE mode shows real-time location with color feedback: green (inside zone), red (outside zone)
- Uses ray casting algorithm for point-in-polygon detection
- Zones can be activated/deactivated via `toggle_area_status.php`

### Backend Structure (`/backend`)

PHP APIs with Azure MySQL database. Key endpoints:
- Auth: `login.php`, `register.php`, `logout.php`, `google_auth.php` (Google Sign-In)
- Location: `update_location.php`, `get_users_locations.php`, `get_group_locations.php`
- Friends: `add_friend.php`, `get_pending_requests.php`, `accept_request.php`
- Groups: `create_group.php`, `get_my_groups.php`, `add_group_member.php`, `remove_group_member.php`
- Areas: `create_area.php`, `get_user_areas.php`, `update_area.php`, `delete_area.php`, `toggle_area_status.php`

API Base URL: `https://findmyandroid-e0cdh2ehcubgczac.francecentral-01.azurewebsites.net/backend/`

### Data Flow

1. Activities make HTTP requests via Volley to backend PHP APIs
2. Backend processes against Azure MySQL, returns JSON
3. Responses parsed into Kotlin data classes, UI updated via adapters

## Key Configuration

- **Google Sign-In:** Requires Web Application OAuth client ID in `LoginActivity.kt` (configured in Google Cloud Console)
- **Google Maps API Key:** In `AndroidManifest.xml`
- **Cleartext Traffic:** Enabled (`usesCleartextTraffic="true"`)
- **Permissions:** Internet, fine/coarse location, foreground service, notifications
- **Database config:** `backend/db.php` (Azure MySQL connection)

## Key File Locations

- **Activities:** `app/src/main/java/pt/ipt/projetodam_findme/`
- **Services:** `app/src/main/java/pt/ipt/projetodam_findme/services/`
- **Data Models:** `app/src/main/java/pt/ipt/projetodam_findme/Zone.kt`
- **Layouts:** `app/src/main/res/layout/`
- **Backend APIs:** `backend/*.php`
- **Backend Services:** `backend/services/` (EmailService, NotificationHelper)
- **SQL Migrations:** `backend/sql/` (database schema changes)
- **Backend Config:** `backend/config.php` (SMTP credentials - not in Git)

## Email Notifications

Geofence email notifications are fully implemented:
- `EmailService.php` - PHPMailer wrapper for sending HTML emails via Gmail SMTP
- `NotificationHelper.php` - Rate-limited notification dispatch with database logging
- `update_location.php` - Server-side boundary detection triggers notifications on enter/leave events
- Rate limit: 8 emails per hour per zone (configurable in `config.php`)
- Requires `email_notifications` table (see `backend/sql/create_email_notifications.sql`)
