# Nook

A private, good-looking chat app for you and your friends. Native Android (Kotlin + Jetpack Compose),
Firebase **Spark (free) plan**, Cloudinary for media, a tiny Cloudflare Worker for push notifications,
and LiveKit for voice/video calls. **100% Kotlin** — the app, the Worker (Kotlin/JS) and the security-rules tests.

> ⚠️ **Privacy note:** messages are stored in Firebase and media on Cloudinary. Both encrypt data in transit
> and at rest, but Nook is **not end-to-end encrypted** — whoever administers those accounts could read it.

## What's inside

- **Animated first run**: logo-drawing splash → 4-slide onboarding with original Compose illustrations
  (parallax, word-by-word reveals, morphing page pill, shifting pastel glow, haptic ticks) → animated login.
- **Google sign-in only** (Credential Manager), a permanent unique `@username`, a permission primer that
  explains each permission before Android asks, and your own **app lock** (PBKDF2-hashed password/PIN,
  optional fingerprint, locks on launch and after 30 s away, blanks the app in Recents).
- **Chats**: DMs and groups, add people by username, text, replies (swipe to reply), reactions, photos,
  files (10 MB), voice notes, GIFs, stickers, forward, unsend, delete-for-me, seen receipts, typing dots,
  online presence, disappearing messages (off / 24 h / 7 d), optimistic sending with retry.
- **Signature long-press menu**: background blurs, the bubble lifts from its exact position, reactions pop
  in above it, an action card slides in below, emojis fly to the bubble.
- **Friends**, **Stickers** (Giphy GIFs/stickers + custom group sticker packs made from photos),
  **Calls** (history + full-screen call UI + full-screen incoming calls even when the app is closed),
  **Account** (change photo with square→circle crop, display name, theme, cache, privacy settings,
  sign out, delete account).
- Push notifications that only ever say *who* — "Ali sent you a photo", "New message in The Boys".

## Repo layout

```
app/          Android app (single module). See CLAUDE.md for architecture & conventions.
firebase/     Firestore + Realtime Database security rules, indexes, and Kotlin rules tests (own Gradle build).
worker/       Cloudflare Worker in Kotlin/JS (own Gradle build): push, call ringing, LiveKit tokens, Cloudinary cleanup.
CLAUDE.md     Architecture, folder structure, design tokens, animation rules, coding conventions.
```

---

## Setup

You'll create five free accounts. Nothing secret is committed — every key goes in a git-ignored file.

### 0. Prerequisites
- Android Studio (latest stable) with Android SDK 36, JDK 17+.
- Node.js 20+ — only to run the `firebase` and `wrangler` command-line tools via `npx` (no JavaScript lives in this repo).
  Java 21 for the Firebase emulators (rules tests only).

### 1. Firebase (Spark plan — no billing card needed)
1. Go to <https://console.firebase.google.com> → **Add project** (you can disable Google Analytics).
2. **Add app → Android**. Package name: `com.nook.app`. Add your debug **SHA-1**
   (run `./gradlew signingReport` in the project, copy the SHA1 of the `debug` variant). Add your release SHA-1 later too.
3. **Build → Authentication → Get started → Sign-in method → Google → Enable**. Pick a support email. Save.
4. **Build → Firestore Database → Create database** → production mode → pick a region close to you.
5. **Build → Realtime Database → Create database** → locked mode → same region if possible.
6. **Project settings → Your apps → download `google-services.json`** (download it *after* steps 3–5 so it
   contains the OAuth client and the database URL) and put it at **`app/google-services.json`**.
7. Deploy the security rules and indexes (from the repo root):
   ```bash
   npx firebase-tools login
   npx firebase-tools deploy --only firestore:rules,firestore:indexes,database --project YOUR_FIREBASE_PROJECT_ID
   ```
8. For the Worker you'll need a **service account key**: Project settings → **Service accounts** →
   **Generate new private key**. Keep that JSON file somewhere safe and **never commit it**.

### 2. Cloudinary (photos, voice notes, files, stickers, avatars)
1. Sign up at <https://cloudinary.com> (free plan). Note your **Cloud name** (Dashboard).
2. Settings → **Upload** → **Upload presets** → **Add upload preset**:
   - **Signing mode: Unsigned**. Name it e.g. `nook_unsigned` — that's `CLOUDINARY_UPLOAD_PRESET`.
   - Leave **Folder / Asset folder empty** (the app chooses `nook/chats/<chatId>` or `nook/avatars/<uid>`).
   - If your account uses *dynamic folders*, turn on **"Use asset folder as public ID prefix"**
     (the Worker only deletes assets whose public ID starts with those folders).
   - **Allowed formats**: `jpg,jpeg,png,webp,gif,heic,m4a,mp4,aac,mp3,pdf,txt,zip,doc,docx,xls,xlsx,ppt,pptx`.
   - **Max file size**: 10 MB (10485760). Unique filename: on. Overwrite: off.
3. Settings → **API Keys**: note the **API Key** and **API Secret** — these go to the Worker only (never the app).

> Unsigned presets can be used by anyone who knows the preset name. The format/size limits above keep abuse small,
> and only the Worker (with the secret) can delete.

### 3. Giphy (GIFs and stickers)
1. <https://developers.giphy.com/dashboard/> → **Create an App** → choose **API** (not SDK).
2. Copy the **API key** → `GIPHY_API_KEY`.

### 4. LiveKit (calls)
1. <https://cloud.livekit.io> → create a project.
2. Settings → Keys: note the **WebSocket URL** (`wss://….livekit.cloud`) → `LIVEKIT_URL` (app),
   and the **API key / secret** → Worker secrets.

### 5. Cloudflare Worker (push notifications, call ringing, LiveKit tokens, media cleanup)
The Worker is written in Kotlin and compiled to JavaScript by Kotlin/JS; wrangler uploads the compiled file.
1. Sign up at <https://dash.cloudflare.com> (free plan).
2. Build it and log in:
   ```bash
   ./gradlew -p worker bundle          # → worker/build/worker/nook-worker.mjs
   cd worker
   npx wrangler login
   ```
3. Edit `worker/wrangler.toml` `[vars]`: set `FIREBASE_PROJECT_ID` and `CLOUDINARY_CLOUD_NAME` (not secret).
4. Add the secrets (each command prompts you to paste the value):
   ```bash
   npx wrangler secret put FIREBASE_SERVICE_ACCOUNT   # paste the WHOLE service-account JSON on one line
   npx wrangler secret put CLOUDINARY_API_KEY
   npx wrangler secret put CLOUDINARY_API_SECRET
   npx wrangler secret put LIVEKIT_API_KEY
   npx wrangler secret put LIVEKIT_API_SECRET
   npx wrangler deploy
   ```
   (Re-run `./gradlew -p worker bundle` before every `npx wrangler deploy`.)
5. Copy the printed URL (`https://nook-worker.<you>.workers.dev`) → `NOOK_WORKER_URL`.
   Check it: opening that URL in a browser should show `{"ok":true,"service":"nook-worker"}`.
   A cron trigger (every 30 min) deletes expired disappearing messages and their media.

### 6. App keys → `local.properties`
Create/edit `local.properties` in the project root (Android Studio already created it with `sdk.dir`; it is git-ignored):
```properties
NOOK_WORKER_URL=https://nook-worker.<you>.workers.dev
CLOUDINARY_CLOUD_NAME=your-cloud-name
CLOUDINARY_UPLOAD_PRESET=nook_unsigned
GIPHY_API_KEY=your-giphy-key
LIVEKIT_URL=wss://your-project.livekit.cloud
# Optional: only if google-services.json has no web OAuth client
GOOGLE_WEB_CLIENT_ID=1234567890-xxxx.apps.googleusercontent.com
```
| Key | Where it comes from | Used for |
|---|---|---|
| `app/google-services.json` | Firebase console | Auth, Firestore, RTDB, FCM |
| `NOOK_WORKER_URL` | `npx wrangler deploy` in `worker/` | push, call ringing, LiveKit tokens, media delete |
| `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_UPLOAD_PRESET` | Cloudinary | uploads |
| `GIPHY_API_KEY` | Giphy | GIF / sticker search |
| `LIVEKIT_URL` | LiveKit | calls |
| `GOOGLE_WEB_CLIENT_ID` (optional) | Google Cloud → Credentials (Web client) | Google sign-in fallback |

Missing keys don't break the build: the matching feature shows a friendly "not set up yet" message.

### 7. Run
Open the folder in Android Studio → let Gradle sync → run on a device/emulator with Google Play services.
Command line: `./gradlew installDebug`.

---

## Open in Google AI Studio
The repo is a plain single-module Android Studio project (Gradle Kotlin DSL + version catalog, Koin, no kapt/NDK/custom plugins),
so it imports as-is:
1. Push this repo to GitHub.
2. In Google AI Studio's Android builder, choose **Import from GitHub** and pick the repo/branch.
3. It builds **without** `google-services.json` — the app then opens a "Almost there" setup screen instead of crashing.
   To run it for real, add `app/google-services.json` and the `local.properties` keys above in AI Studio's file editor
   (or build locally). Keep secrets out of commits.
4. Ask AI Studio to follow `CLAUDE.md` when making edits so the design system and architecture stay consistent.

---

## Tests
```bash
./gradlew testDebugUnitTest lint     # Android unit tests (lock state machine, hashing, SetPasswordViewModel, rules/models)
./gradlew -p worker jvmTest          # Worker logic tests (run on the JVM, no Node needed)
# 22 security-rules tests, run inside the Firestore + Realtime Database emulators (needs Java 21):
npx firebase-tools emulators:exec --only firestore,database --project demo-nook "./gradlew -p firebase test"
```

## Spark plan budget (≈ 6 friends)
Free daily quota: **50K reads, 20K writes, 20K deletes, 1 GiB stored**; Realtime Database: 100 simultaneous connections.
Assumptions: ~900 messages/day across the group (≈150 each), a couple of groups, each person opens the app ~20×/day.

| Activity | Writes/day | Reads/day |
|---|---|---|
| Sending (1 batch = message + chat preview) | ~1,800 | ~900 (rule membership check) |
| Delivering to open listeners (chat list + open chat) | – | ~4,000 |
| Opening chats (newest 30, older pages on demand, offline cache) | – | ~5,000 |
| Chat list + profile listeners on app open | – | ~2,400 |
| Read receipts (one `lastRead` per chat, only when it changes) | ~400 | – |
| Reactions, settings, misc | ~300 | ~300 |
| Worker `/notify` (chat + sender + 1 settings doc per recipient) | – | ~3,600 |
| **Total** | **≈ 2.5K (13%)** | **≈ 16K (32%)** |

- Deletes: disappearing messages + unsends, typically < 1K/day.
- Storage: ~1 KB per text message → ~330 MB/year of text; media lives on Cloudinary, not Firestore.
- Presence and typing use the Realtime Database, and the app disconnects whenever it's in the background,
  so 6 friends use at most ~6 of the 100 connections.
- Cloudflare Workers free tier (100K requests/day) is far above ~1K notify calls/day.
- Check the current free limits for Cloudinary (credits/month) and LiveKit Cloud (participant minutes) on their pricing pages.

## Security model
- Firestore rules: only chat members can read/write a chat and its messages; senders can't be spoofed; you can only
  react with your own key, hide messages for yourself, unsend your own messages; any member may sweep *expired*
  disappearing messages; usernames are claimed atomically with the profile, can't be changed, and one account gets one;
  you can only edit your own profile; private settings (blocked list, muted chats, device tokens) are owner-only.
- Realtime Database rules: presence is per-user; typing is readable/writable only by chat members via a membership mirror.
- The Worker verifies the Firebase ID token and chat/call membership on every request, sends **data-only** pushes with
  content-free text, never logs content, and only deletes Cloudinary assets inside the caller's chat/avatar folder.
- App lock: salted PBKDF2-HMAC-SHA256 (120k iterations) stored only on the device; Recents preview blanked (FLAG_SECURE);
  backups disabled. Forgot password = sign out and sign back in.

## Troubleshooting
- **Google sign-in fails / "no credentials"**: the SHA-1 isn't registered in Firebase, or `google-services.json` was
  downloaded before enabling Google sign-in. Re-download it.
- **"The query requires an index"**: re-run the `firebase-tools deploy` command from setup step 1.7 (it deploys `firestore.indexes.json`).
- **No notifications**: check `NOOK_WORKER_URL`, the Worker secrets, notification permission, and that the device is online.
- **Incoming calls don't go full-screen on Android 14+**: Account → Notifications → Full-screen calls.

## Licenses
Instrument Serif and DM Sans are bundled under the SIL Open Font License (see `licenses/`).
