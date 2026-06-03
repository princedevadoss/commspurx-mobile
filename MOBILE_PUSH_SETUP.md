# Mobile push notifications (FCM) setup

Commspurx mobile uses **Firebase Cloud Messaging** for reliable alerts when the app is closed. Polling still runs as a fallback.

## 1. Create a Firebase project

1. Open [Firebase Console](https://console.firebase.google.com/).
2. Create a project (or use an existing one).
3. **Add app** → **Android**.
4. Package name: `com.commspurx.mobile` (must match exactly).
5. Download **`google-services.json`** and place it at:

   ```
   commspurx-mobile/app/google-services.json
   ```

6. Rebuild the Android app (Gradle applies the Google Services plugin when that file exists).

### Alternative: `local.properties` (no JSON file)

If you prefer not to commit `google-services.json`, add these keys to `commspurx-mobile/local.properties` (from the Firebase Android app settings / downloaded JSON):

```properties
FIREBASE_API_KEY=AIza...
FIREBASE_APP_ID=1:123456789:android:abcdef
FIREBASE_PROJECT_ID=your-firebase-project-id
```

## 2. Backend service account

1. Firebase Console → **Project settings** → **Service accounts**.
2. **Generate new private key** → save JSON securely.
3. On the Commspurx API server, set **one** of:

   ```bash
   # .env (path on server)
   FIREBASE_SERVICE_ACCOUNT_PATH=/secure/path/commspurx-firebase-adminsdk.json
   ```

   ```bash
   # Or inline (containers; keep secret)
   FIREBASE_SERVICE_ACCOUNT_JSON={"type":"service_account",...}
   ```

4. Restart the API. Logs should show `fcm.initialized` (not `fcm.disabled`).

## 3. Database migration

```bash
cd commspurx
npm run db:migrate
```

This creates the `mobile_fcm_tokens` table.

## 4. Test end-to-end

1. Install the app, sign in as admin/manager, allow **Notifications**.
2. When prompted, allow **battery optimization** exemption (recommended).
3. Confirm the API logs no FCM errors after login (token registered).
4. Close the app completely (swipe from recents).
5. Trigger a notification on the server (e.g. approval or bulk import).
6. You should receive a system notification within seconds.

### Troubleshooting

| Symptom | Check |
|--------|--------|
| `fcm.disabled` in API logs | `FIREBASE_SERVICE_ACCOUNT_*` not set |
| No push, polling works when app opens | `google-services.json` missing or wrong package name |
| Push works on Wi‑Fi only | Device battery / OEM autostart settings |
| Token never registered | Logged in? `POST /api/mobile/fcm-token` returns 204? |

## 5. Production checklist

- [ ] `google-services.json` for release build (or `local.properties` in CI secrets)
- [ ] `FIREBASE_SERVICE_ACCOUNT_PATH` on production server
- [ ] `npm run db:migrate` on production DB
- [ ] HTTPS API URL in release `build.gradle.kts` (`API_BASE_URL`)
