# Nook — guide for anyone editing this repo (people, Claude Code, Google AI Studio)

Nook is a private chat app for a small friend group. Native Android (Kotlin + Jetpack Compose),
Firebase on the **free Spark plan** (no Cloud Functions, no Firebase Storage), Cloudinary for media,
a Cloudflare Worker for push/LiveKit/cleanup, LiveKit for calls. Keep it a **standard single-module
Android Studio project** so Google AI Studio can import it.

## Hard rules
- No secrets in git. Keys come from `local.properties` → `BuildConfig` (see `app/build.gradle.kts`)
  and `app/google-services.json` (git-ignored). Never hard-code real values.
- Spark plan: no Cloud Functions, no Firebase Storage, no Firestore TTL. Server work lives in `/worker`.
- Push payloads and logs **never** contain message content — only "Ali sent you a photo" style text.
- Avatars are always read from `users/{uid}` (`UserRepository.observeUser`), never copied into messages.
- Koin for DI (no Hilt/kapt). Navigation Compose with `@Serializable` routes. ViewModel + StateFlow.
- No custom Gradle scripts, NDK or exotic plugins. Fonts/assets are bundled in `res/`.

## Folder structure
```
app/src/main/java/com/nook/app/
  NookApplication.kt      Koin, Firestore offline cache, notification channels, Coil (GIF) loader
  MainActivity.kt         FragmentActivity (BiometricPrompt), edge-to-edge, FLAG_SECURE, deep links
  AppConfig.kt            BuildConfig keys + "is X configured" checks
  di/AppModule.kt         every singleton + ViewModel
  designsystem/
    theme/                Color.kt (tokens), Type.kt, Dimens.kt (spacing/shapes), Motion.kt, Theme.kt
    components/           Buttons, Surfaces (cards, dashed add card, badges), Header, SegmentedControl,
                          Avatar, States (empty/error/skeleton/offline), Inputs, Overlays (sheets/dialogs),
                          NookLogo, Modifiers (pressScale, glow, breathingGlow, shimmer)
  data/
    model/Models.kt       domain models (hand-mapped, no reflection)
    firebase/             snapshot→Flow adapters, Firestore mappers + collection names
    prefs/SettingsStore   DataStore: onboarding flag, theme, lock hash, biometric, FCM token
    security/             PasswordHasher (PBKDF2), AppLockManager (lock state machine)
    remote/               CloudinaryApi, WorkerApi, GiphyApi (OkHttp)
    media/                ImageCompressor, VoiceRecorder (MediaRecorder), VoicePlayer (Media3)
    repo/                 Auth, User, Chat, Message, Realtime (presence/typing), Media, Outbox,
                          Call, Sticker, People, Connectivity, Cache
  session/                SessionManager (foreground/background → lock + presence + FCM token),
                          SignOutUseCase, DeepLinkBus, ActiveChat
  navigation/             Routes.kt, NookNavHost.kt
  notifications/          Notifier (all notifications), NookMessagingService, CallActionReceiver
  feature/
    root/ splash/ onboarding/ auth/ lock/ shell/ chats/ chat/ friends/ stickers/ calls/ account/
firebase/                 firestore.rules, database.rules.json, indexes, Kotlin rules tests (standalone Gradle build)
worker/                   Cloudflare Worker in Kotlin/JS (standalone Gradle build): /notify /call /livekit-token
                          /media/delete + cron. commonMain = pure logic (tested on JVM), jsMain = runtime glue.
```

## Data model (Firestore) — keep in sync with `data/firebase/Mappers.kt` and `firebase/firestore.rules`
- `users/{uid}` uid, username (permanent), displayName, photoUrl, photoPublicId, createdAt
- `users/{uid}/private/settings` blocked[], mutedChats[], notificationsEnabled, disappearingDefault, fcmTokens[] (owner-only)
- `usernames/{username}` { uid } — claimed in the same transaction as the profile
- `chats/{chatId}` type direct|group, members[], admins[], name, photoUrl, createdBy, lastMessage{id,senderId,type,preview},
  lastMessageAt, lastRead{uid: ts}, disappearing off|24h|7d. DM id = `dm_{uidA}_{uidB}` (sorted).
- `chats/{chatId}/messages/{id}` senderId, type, text, media{url,publicId,resourceType,…}, replyTo{…}, reactions{uid: emoji},
  hiddenFor[], createdAt (server), clientCreatedAt, expireAt|null, forwarded
- `calls/{id}` chatId, callerId, calleeId, members[caller, callee], type, status, createdAt, acceptedAt, endedAt
- `stickerPacks/{id}` chatId, name, createdBy, members[], stickers[{url, publicId}]
- RTDB: `presence/{uid}`, `typing/{chatId}/{uid}`, `chatMembers/{chatId}/{uid}` (membership mirror for RTDB rules)
- Cloudinary folders: `nook/chats/{chatId}/…`, `nook/avatars/{uid}/…` (the Worker only deletes inside these)

## Spark budget habits
- One shared chat-list listener (`ChatRepository.chatList`, `shareIn`). Message listener = newest 30; older pages are one-shot reads.
- Read receipts = one `lastRead.{uid}` timestamp per chat, written only when it would change.
- Presence/typing in RTDB; we call `goOffline()` whenever the app is backgrounded. Typing writes ≤ 1 per 3 s.
- Firestore offline persistence stays ON (100 MB). Never add counters or per-message receipts.

## Design tokens (designsystem/theme)
- Dark (default): background `#000000`, surface `#1E1B18`, border `#2E2A26`, text `#F5EFE6`, muted `#9A938A`,
  accent `#FF6A33`, amber `#E8B04B`, danger `#F26B5E`, pastels mint `#CDEFD9` sky `#D4E6FB` peach `#FBE3C0`
  lavender `#DDD0F5` rose `#F6C9C4`, navy `#1F3A52`. Light = same logic on cream `#F7F2EA`.
- Always use `NookTheme.colors.*`, `NookTheme.type.*`, `Spacing.*`, `NookShapes.*` — never raw hex or Material defaults.
- Type: Instrument Serif for titles (`display` 40sp, `hero`, `headline`, `title`), DM Sans for everything else.
- Shape: 24dp cards with 1dp border, pills for buttons/segmented controls, hairline dividers, spacing 16/20/24.
- Icons: outline (`Icons.Outlined`) by default, filled/rounded when active.
- Buttons: primary = cream→peach gradient pill with black text; secondary = charcoal; destructive = coral.
- Empty states: `EmptyState` (outline icon, serif headline, one muted sentence); first-run `DashedAddCard`.
- Screen headers: `NookHeader` (big serif title left, ≤2 icon buttons right, no hairline — spacing separates) or
  `NookTopBar` (with hairline) for pushed screens.
- Bottom bar: no pill/background behind the selected tab. Selected = filled icon + label in accent orange with a small
  bounce; unselected = outline icon in muted.
- Keep it clean: prefer filled surfaces over outlines (composer, segmented track, their bubbles in dark mode); use
  borders only on cards and where contrast needs it (light theme).

## Animation rules
- Springs from `NookMotion` (`standard`, `bouncy`, `gentle`), 200–400 ms feel. Animate via `graphicsLayer`
  (no layout thrash), keep 60 fps.
- Respect "Remove animations": read `NookTheme.reduceMotion` and fall back to short fades (`NookMotion.choose`).
- Every tappable surface uses `Modifier.pressScale(...)`. Haptics via `rememberHaptics()`:
  `tick` (tabs/pages/toggles), `longPress`, `confirm` (send/react), `reject` (errors).
- Messages: only genuinely new ones animate (`MessageUi.isNew`); initial load and older pages never do.
  LazyColumns use stable keys + `animateItem()`.

## Coding conventions
- **The repo is 100% Kotlin.** Don't add JavaScript/TypeScript; Node is only used to run the `firebase` and
  `wrangler` CLIs through `npx`.
- Kotlin official style, 4-space indent, trailing commas. One feature = Screen composable + ViewModel in `feature/<name>`.
- UI state: immutable data classes / sealed interfaces exposed as `StateFlow`; collect with `collectAsStateWithLifecycle()`.
- Every screen handles loading (skeleton), empty, error and offline (`OfflineBanner`).
- Process-death safety: route args via type-safe routes, filters/queries in `SavedStateHandle`, drafts in `rememberSaveable`.
  Never persist passwords anywhere except the PBKDF2 hash.
- Accessibility: `contentDescription` on icons that act, 48dp touch targets, merged semantics on rows.
- New Firestore fields → update Mappers.kt, firestore.rules AND `firebase/src/test` (Kotlin, emulator REST).
- New Worker behaviour → pure logic in `worker/src/commonMain` with a test in `worker/src/jvmTest`; runtime calls in
  `worker/src/jsMain`. Keep `dynamic`/`js()` usage inside `Js.kt`.

## Checks to run
```
./gradlew assembleDebug lint testDebugUnitTest      # Android
./gradlew -p worker jvmTest bundle                  # Worker tests + compiled Worker
npx firebase-tools emulators:exec --only firestore,database --project demo-nook "./gradlew -p firebase test"
```
