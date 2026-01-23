# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

FindMe is an Android location-sharing app that allows users to track and share their real-time location with friends and groups.

**Tech Stack:** Kotlin, Android SDK (API 24-36), Volley (HTTP), Google Maps/Location Services, Google Sign-In, PHP backend with Azure MySQL.

**Language:** Portuguese (UI strings, comments).

## Build Commands

```bash
./gradlew build                    # Full build
./gradlew assembleDebug            # Debug APK
./gradlew test                     # Unit tests
./gradlew installDebug             # Install to connected device
./gradlew clean                    # Clean build artifacts
```

## Architecture

### Android App (`app/src/main/java/pt/ipt/projetodam_findme/`)

**Activities:**

| Activity | Purpose |
|----------|---------|
| `MainActivity` | Main map view, starts LocationService, bottom navbar |
| `LoginActivity` | Email/password login + Google Sign-In |
| `RegisterActivity` | User registration with backend validation |
| `ProfileActivity` | User profile with satellite map view |
| `GroupsActivity` | List user's groups, create new groups |
| `GroupDetailsActivity` | View group members, add/remove members, see member locations |
| `CreateGroupActivity` | Create a new group |
| `AddFriendActivity` | Send friend requests by email |
| `AddMemberActivity` | Add friends to a group |

**Services:**

| Service | Purpose |
|---------|---------|
| `LocationService` | Foreground service for continuous GPS tracking, sends updates to backend |
| `NotificationService` | Handles notification channels and alerts |

**Adapters:** `FriendsAdapter`, `GroupsAdapter`, `RequestsAdapter` - RecyclerView adapters for lists.

### Backend (`/backend`)

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `login.php` | POST | Auth with email/password, returns 64-char token (1-hour expiry) |
| `google_auth.php` | POST | Google Sign-In token verification, creates user if new |
| `register.php` | POST | User registration with bcrypt password hashing |
| `logout.php` | POST | Token invalidation via Authorization header |
| `update_location.php` | POST | Location updates (user_id, latitude, longitude) |
| `get_users_locations.php` | POST | Get locations of user's friends |
| `add_friend.php` | POST | Send friend request by email |
| `get_pending_requests.php` | POST | Get pending friend requests |
| `accept_request.php` | POST | Accept/decline friend request |
| `create_group.php` | POST | Create a new group |
| `get_my_groups.php` | POST | Get user's groups |
| `get_group_locations.php` | POST | Get locations of group members |
| `add_group_member.php` | POST | Add member to group |
| `remove_group_member.php` | POST | Remove member from group |
| `db.php` | - | PDO connection to Azure MySQL |
| `config.php` | - | SMTP config for email notifications (not in git) |

### Session Management

SharedPreferences key `"SessaoUsuario"` stores:
- `logado` (boolean) - authentication status
- `id_user`, `nome_user`, `email_user`, `token`

### API Communication Pattern

```kotlin
// POST with JSON body
val jsonBody = JSONObject().apply {
    put("email", email)
    put("password", password)
}
val request = JsonObjectRequest(Request.Method.POST, url, jsonBody,
    { response -> /* handle success */ },
    { error -> /* handle error */ }
)
Volley.newRequestQueue(this).add(request)
```

**API Base URL:** `https://findmyandroid-e0cdh2ehcubgczac.francecentral-01.azurewebsites.net/backend/`

### Location Tracking

`LocationService` runs as a foreground service:
- Uses `FusedLocationProviderClient` for GPS
- Receives `USER_ID` via Intent extra when started
- Runs continuously until explicitly stopped
- Shows persistent notification while tracking

### Database Tables

- `users` - id_user, name, email, password_hash (or 'GOOGLE_AUTH' for Google users)
- `user_tokens` - token (64-char), id_user, expires_at
- `locations` - id_user, latitude, longitude, last_update
- `friends` - friendship relationships and pending requests
- `groups` - group id, name, creator
- `group_members` - group membership mapping
