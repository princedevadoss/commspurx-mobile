# Commspurx Mobile

Native Android app for **admins** and **managers** to receive Commspurx notifications and act on approvals on the go.

## Features

- Secure login with the same credentials as the web app (account code, email, password)
- JWT access tokens with encrypted refresh-token storage and automatic token refresh
- **Admin:** pending approvals with approve/reject actions, plus activity notifications
- **Manager:** activity notifications (including bulk import results)
- Bulk import notifications open a detail sheet with total / added / rejected counts and rejected row reasons
- Pull-to-refresh and background polling every 30 seconds

## Requirements

- Android Studio with SDK 36
- Commspurx backend running (default dev: `http://localhost:3000`)

## API URL

Debug builds use the Android emulator host alias:

```
http://10.0.2.2:3000/api
```

Change `API_BASE_URL` in `app/build.gradle.kts` for physical devices (use your machine LAN IP) or production.

## Run

1. Start the Commspurx API: `npm run dev` in the main `commspurx` project
2. Open this folder in Android Studio and run on an emulator or device
3. Sign in with an admin or manager account (traders are not supported in this app)

## Backend mobile auth

The app sends `X-Commspurx-Client: mobile` on requests. Login returns a refresh token in the JSON body, and token rotation uses `POST /api/auth/mobile/refresh`.
