# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

FindMe is an Android location-sharing app that allows users to track and share their real-time location.

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

| Activity | Purpose |
|----------|---------|
| `MainActivity` | Main map view with real-time GPS tracking, sends location updates to backend when user moves >= 30m |
| `LoginActivity` | Email/password login + Google Sign-In (in progress) |
| `RegisterActivity` | User registration with backend validation |
| `ProfileActivity` | User profile with satellite map view (18x zoom) |
| `MapsActivity` | Basic map display (placeholder for future features) |

### Backend (`/backend`)

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `login.php` | POST | Auth with email/password, returns 64-char token (1-hour expiry) |
| `register.php` | POST | User registration with bcrypt password hashing |
| `logout.php` | POST | Token invalidation via Authorization header |
| `update_location.php` | POST | Location updates (user_id, latitude, longitude) |
| `db.php` | - | PDO connection to Azure MySQL (`findme.mysql.database.azure.com`) |

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

// POST with form data
val request = object : StringRequest(Request.Method.POST, url, ...) {
    override fun getParams() = mutableMapOf("key" to "value")
}
```

**API Base URL:** `https://findmyandroid-e0cdh2ehcubgczac.francecentral-01.azurewebsites.net/backend/`

### Location Tracking (MainActivity)

- Uses `FusedLocationProviderClient` for GPS
- Updates every 10 seconds, minimum 5 seconds, 10-meter displacement
- Only sends to backend when moved >= 30 meters (battery optimization)

### Database Tables

- `users` - id_user, name, email, password_hash
- `user_tokens` - token (64-char), id_user, expires_at
- `locations` - id_user, latitude, longitude, last_update

## Current Branch: `auth_check`

Implements Google Sign-In in `LoginActivity.kt`:
- Uses `play-services-auth:20.7.0`
- Client ID: `1050226007080-ch5u5u0vv2n1sde2psbkbk2ooi9plq3v.apps.googleusercontent.com`
- **Incomplete:** Captures ID token but doesn't send to backend for verification/user creation
