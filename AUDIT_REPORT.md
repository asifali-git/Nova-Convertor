# Nova Converter — Combined Audit Report

**Date:** 2026-09-28
**Source:** Parent session *"APK features, settings aur 10 languages policy build"* (`ses_f1891cb3effe`) ke 2 explore subagents
(*Audit converter tools status* + *Audit app shell and settings*) ke partial findings, **dobara verify karke** source + shipped release APK par.

> **TL;DR (Hinglish):** 15 me se 2 tools seedhe toote hue hain (Text Watermark, Image→WEBP) kyunki bundled FFmpeg me `drawtext` aur `libwebp` hi nahi hai. Localization **bilkul zero** hai (aapka "arbi/hindi/urdu already hai" wala assumption galat hai — koi `values-xx` folder nahi, code me `R.string` tak use nahi hota). Play Store ke liye 4 hard blockers hain: targetSdk 34 (API 36 required since 31 Aug 2026), debug signing, GPL license files APK se shrink ho chuki hain, aur 32-bit ARM devices unsupported. Baaki sab medium/low issues hain.
>
> **✅ UPDATE (2026-09-29):** Sab P0/P1/P2 fixes implement + release build verify ho chuke hain (targetSdk 36, release signing, 10 languages, watermark/WEBP/save-crash sab fixed) — detail **§12** me. Sirf 32-bit ARM deferred hai (§12).

---

## 0. Pending task status

| Item | Status |
|---|---|
| Parent session | `ses_f1891cb3effe` — "APK features, settings aur 10 languages policy build" |
| Last user msg | `continue` |
| Subagent 1 `ses_f1890b9b9` (Tools audit) | **STALLED** — last message ~10:09 (its clock), 25 msgs, final report kabhi nahi likha |
| Subagent 2 `ses_f1890a09a` (Shell audit) | **STALLED** — last message ~10:10, 34 msgs, "ready to write the report" tak pahuncha tha, report nahi likha |
| Parent ke `task` tools | Dono abhi bhi `status = running` → result kabhi deliver nahi hua |
| Pehle wali ek aur same pair (09:29) | `AbortError` se cancel ho chuki thi — current pair uska retry hai |

Is report me dono ka kaam merge karke poora kar diya gaya hai.

---

## 1. 🔴 BLOCKER — Text Watermark tool toota hua hai (drawtext missing)

**Evidence (shipped release APK par verify kiya, sirf source par nahi):**

```
app/build/outputs/apk/release/app-release.apk
  lib/arm64-v8a/libavfilter.so  drawtext=False  boxborderw=False  drawbox=True
  lib/arm64-v8a/libavcodec.so   libwebp=False
```

`drawtext` string **kisi bhi** `.so` me nahi mili (scan: AAR + APK dono, arm64 + x86_64).
`drawbox`, `scale`, `atempo`, `fps`, `setpts`, `concat`, `anullsrc`, `subtitles` sab present hain → scan method sahi hai.

FFmpeg ka configure string `--enable-libfreetype --enable-libfontconfig --enable-libfribidi` dikhata hai, phir bhi `vf_drawtext` compile hi nahi hua (ffmpeg-kit 7.1.6 build me).

**Impact:**

- `Tools.kt:388-411` → `watermark` tool → `Commands.kt:172-205` → `-vf "drawtext=..."` → FFmpeg `No such filter: 'drawtext'` → **job hamesha FAILED**.
- App me is tool ka koi fallback nahi hai.

**Fix options:**
1. ffmpeg-kit ka koi aisa variant lo jisme drawtext ho (verify karo: `ffmpeg -filters | grep drawtext`), ya
2. Tool ko hata do / disable karo, ya
3. Watermark ko Media3/Canvas overlay se implement karo (software path).

---

## 2. 🔴 BLOCKER — Image Converter → WEBP format toota hua hai

- `Tools.kt:346` — `image_converter` me `webp` option user ko diya gaya hai.
- `Commands.kt:53-56` — image branch sirf `-y -i in -q:v N out.webp` deti hai, koi encoder specify nahi.
- FFmpeg `.webp` output ke liye `libwebp` encoder chahta hai → **`libwebp` build me nahi hai** (binary scan: 0 matches).
- Result: `Unknown encoder 'libwebp'` → job FAILED.

`jpg`, `png`, `bmp` native encoders hain → wo chalenge. **Sirf `webp` output toota hai** (decode webp native decoder se chalta hai).

**Fix:** `Commands.kt` image branch me explicit codec mapping add karo, ya UI se `webp` hata do, ya libwebp wala FFmpeg build lo.

---

## 3. 🔴 BLOCKER — Google Play submission ready nahi hai

| # | Issue | Evidence |
|---|---|---|
| 3.1 | **targetSdk = 34** — Play requires **API 36** since **31 Aug 2026** (new apps + updates). Aaj 28 Sep 2026. Extension form se 1 Nov 2026 tak mil sakta hai. | `app/build.gradle.kts:9,14` (`compileSdk = 34`, `targetSdk = 34`) |
| 3.2 | **Release debug key se sign hai** | `app/build.gradle.kts:29-30` — `// TODO: real release ke liye apna keystore lagao` + `signingConfig = signingConfigs.getByName("debug")` |
| 3.3 | **GPL license notices APK me hi nahi hain** — ffmpeg-kit-full-gpl (x264/x265 GPL components) ke `res/raw/license*.txt` resource shrinker ne delete kar diye (app koi `R.raw.*` reference hi nahi karta). | AAR me 30+ files (`res/raw/license.txt`, `license_lame.txt`, …); release APK me **0** license/notice entries (zip scan) |
| 3.4 | **32-bit ARM support nahi hai** — `abiFilters` sirf `arm64-v8a` + `x86_64`. AAR me bhi wahi 2 ABIs hain. Armeabi-v8a wale budget devices par install hi nahi hoga. | `app/build.gradle.kts:18`, AAR `jni/` listing |

**Bonus (compliance risk):** GPL distribute kar rahe ho → source-availability obligation bhi hai. Kam se kam license text to APK me hona hi chahiye.

**Fix order:** keystore banao → `targetSdk 36` (+ `compileSdk 36`) → license resources ko shrinker se protect karo (`res/raw` keep rules ya in-app About screen me display) → ABI decision lo (armeabi-v8a add ya consciously drop + Play listing me mention).

---

## 4. 🟠 HIGH — Android 8/9 (API 26–28) par Save button crash kar sakta hai

- `AndroidManifest.xml:13-15` → `WRITE_EXTERNAL_STORAGE` declared with `maxSdkVersion="28"`.
- **Runtime permission kabhi request nahi hoti.** Sirf `POST_NOTIFICATIONS` maanga jaata hai (`MainActivity.kt:58-60, 87-93`).
- `Engine.kt:350` → `context.contentResolver.insert(collection, values)` **try block ke bahar** hai (try `351` se shuru hota hai) → `SecurityException` seedha bahar.
- `Engine.kt:264` wala auto-save call try/catch me hai → wahan **silent failure** (file Gallery me save hi nahi hogi, user ko pata bhi nahi chalega).
- ❌ `ToolDetailScreen.kt:1286` — `Outputs.save(...)` **unwrapped** (Done card ka "Save" button).
- ❌ `QueueScreen.kt:325` — `Outputs.save(...)` **unwrapped** (Queue row ka "Save" button).

**Impact:** API 26–28 devices par "Save" tap → unhandled `SecurityException` → **app crash**. API 29+ par theek (scoped storage, permission nahi chahiye).

**Fix:** `Outputs.save` ke andar hi try/catch lagao (insert + openOutputStream dono), aur API ≤28 ke liye runtime permission request karo — ya minSdk 29 kar do.

---

## 5. 🟠 HIGH — Localization: 0% (ye parent task ka core finding hai)

| Check | Result |
|---|---|
| `res/values-*` directories | **NONE** — sirf `values/` hai |
| `R.string.` / `stringResource` usage in code | **ZERO** (sirf manifest/shortcuts ke liye `strings.xml` use hota hai) |
| Devanagari / Arabic characters in `.kt` | **NONE** (regex scan `[\u0900-\u097F\u0600-\u06FF]` → 0) |
| `strings.xml` contents | 9 strings: `app_name` + 8 shortcut labels — sab English |
| Language setting | Koi nahi (`Settings.kt` me language field hi nahi) |
| UI strings kahan hain | Hardcoded English literals — `Tools.kt`, `HomeScreen.kt`, `SettingsScreen.kt`, `ToolDetailScreen.kt` etc. me |

**Conclusion:** App **100% English-only** hai. "Arabic / Hindi / Urdu / English already there" wala assumption **galat** hai. Agar 10-language policy chahiye to pehle poora UI string extraction (`strings.xml` + Compose `stringResource`) karna padega — ye ab tak hua hi nahi.

**Scale estimate:** ~7000 lines Kotlin, saari user-facing strings inline. Ye ek bada refactor hai, chhota fix nahi.

---

## 6. 🟠 HIGH — Legal / About / Privacy content bilkul missing

`app/src` tree me grep: `privacy | terms | license | eula | open source | credits | attribution | disclaimer` → **sirf 2 matches**, dono khud "About" heading ke (`SettingsScreen.kt:197-198`).

- `SettingsScreen.kt:198-214` — About section me sirf: app name, `"FFmpeg + Media3 engine • 15 tools"`, output folder note. **No version number, no license, no privacy, no credits.**
- `versionName = "1.0"` / `versionCode = 1` (`build.gradle.kts:15-16`) define hai par **kahin display nahi hota** (`BuildConfig`/`PackageInfo` ka koi usage nahi).
- FFmpeg GPL + LAME + x264 attribution **kahin nahi** (aur APK se license files shrink bhi ho chuki hain — §3.3).

**Play Store ke liye:** privacy policy URL mandatory hai (app INTERNET permission nahi rakhta to policy simple hogi: "no data collected"), phir bhi chahiye. Open-source licenses screen (OSS licenses) bhi expected hai jab GPL components bundle kar rahe ho.

---

## 7. MEDIUM — Converter tools audit (15/15 tools verified)

**Count claim ✅ sahi:** `SettingsScreen.kt:205` `"FFmpeg + Media3 engine • 15 tools"` vs `Tools.kt` me exactly **15** `NovaTool(...)` definitions (lines 110, 129, 145, 162, 180, 207, 225, 247, 265, 283, 302, 317, 336, 355, 388).

**HomeScreen sections** (`HomeScreen.kt:66-70, 127, 137, 147, 157`): `HERO` (bina title, top cards) → `VIDEO` → `AUDIO` → `IMAGE` → `MORE TOOLS`.

| # | id | Section | Backend | Status |
|---|---|---|---|---|
| 1 | `video_to_audio` | HERO | `Commands.extractAudio` | 🟡 see §7.1 (mp3 bitrate) |
| 2 | `audio_cutter` | HERO | `Commands.trimAudio` | ✅ |
| 3 | `video_cutter` | HERO | `Commands.trimVideo` | ✅ |
| 4 | `video_converter` | VIDEO | `Commands.convert` | ✅ (mp4/mkv/webm/avi/mov/3gp/gif sab supported) |
| 5 | `video_compressor` | VIDEO | Media3 `FastCompressor` | 🟡 FFmpeg fallback **dead code** — §7.2 |
| 6 | `video_speed` | VIDEO | `Commands.speed` | ✅ |
| 7 | `video_merger` | VIDEO | `Commands.merge` | ✅ (filter_complex + silence pad) |
| 8 | `video_to_gif` | VIDEO | `Commands.gif` | ✅ |
| 9 | `frame_capture` | VIDEO | `Commands.frame` | ✅ |
| 10 | `audio_converter` | AUDIO | `Commands.extractAudio` | 🟡 §7.1 |
| 11 | `audio_merger` | AUDIO | `Commands.audioMerge` | ✅ (b!! null-safe via `runTool` guard) |
| 12 | `audio_speed` | AUDIO | `Commands.speed` | ✅ (dead `-filter:v` — §7.3) |
| 13 | `image_converter` | IMAGE | `Commands.convert` | 🔴 **webp output broken** (§2); jpg/png/bmp ✅ |
| 14 | `metadata` | MORE | `Commands.metadata` | ✅ |
| 15 | `watermark` | MORE | `Commands.watermark` | 🔴 **drawtext missing** (§1) |

**No stubs/TODOs in tools:** `TODO|FIXME|placeholder|stub` grep across `app/src` → 0 real matches (PowerShell ka `toDouble` → `TODO` case-insensitive false-positive tha; asli ek hi TODO hai: `build.gradle.kts:29`).

**No UI-wired-but-engineless or engine-but-not-in-UI tools** — har tool ka `build {}` lambda implement hai, aur `verbFor` bhi 15/15 covered hai (notification verbs).

### 7.1 🟡 `extractAudio` — `-b:a` aur `-q:a` saath me (`Commands.kt:208-211`)

```kotlin
return "-y -i $i -vn -c:a $codec $br-q:a 0 ${q(output)}"
```

`br = "-b:a 192k "` (bitrate chips 128/192/256/320 se) + `-q:a 0` dono jaate hain.

- libmp3lame ke liye `-q:a` VBR-quality option hai → **bitrate chip ignore ho sakta hai** (VBR quality 0 ≈ ~245 kbps).
- `wav`/`flac` ke liye `-q:a 0` meaningless hai (warning).
- `convert()` ke audio branch (`Commands.kt:57-58`) me bhi same combo.

**Confidence: medium.** Yahan runtime verification chahiye tha (is machine par ffmpeg binary nahi hai) — device pe ek mp3 convert karke `ffprobe` se bitrate check kar lo.

**Fix:** codec ke hisaab se quality flag select karo — CBR chahiye to `-b:a` do aur `-q:a` hatao; VBR chahiye to sirf `-q:a`.

### 7.2 🟡 `compressCustom` — dead code

- `ToolDetailScreen.kt:328-329`:
  ```kotlin
  val engine = params["engine"] as? String
  val useHardware = tool.hardware && (engine == null || engine.startsWith("Fast"))
  ```
- `video_compressor` ke `fields` me koi `engine` field hi nahi (`Tools.kt:191-194`), aur `params["engine"]` kahin set nahi hota → `engine == null` hamesha true → **`useHardware` hamesha true**.
- Matlab `Tools.kt:204` wala `Commands.compressCustom(...)` kabhi call hi nahi hota. Grep confirm: `compressCustom` ki sirf 1 usage (`Tools.kt:204`), definition `Commands.kt:66`.

**Impact:** koi user-facing breakage nahi (hardware path kaam karta hai), bas dead code + ek **missing fallback** — agar Media3 compress fail ho jaye to FFmpeg path already available hai par wired nahi.

### 7.3 🔵 `speed()` — audio-only input pe useless `-filter:v`

`Commands.kt:106`:
```kotlin
"-y -i $in -filter:v setpts=PTS/$factor -filter:a atempo=$atempo $out"
```

`audio_speed` isko mp3 par chalata hai → video stream hi nahi hai. FFmpeg simple filterchain sirf existing streams parse karta hai → **harmless no-op, error nahi** (subagent ne SuperUser/AOSP sources se verify kiya). Dead weight hai, hata sakte ho.

### 7.4 🔵 `convert()` ka audio branch unreachable

`Commands.kt:57-58` — `target in AUDIO_TARGETS` branch. Koi bhi tool `convert()` ko audio target nahi deta (`video_to_audio`/`audio_converter` → `extractAudio` jaate hain; `video_converter` ke options me audio formats nahi hain). **Dead branch.**

### 7.5 🔵 `metadata` tool `*/*` accept karta hai

`Tools.kt:362` `mimes = anyMime` + `Commands.kt:165` `-c copy`. Image file do to bhi chalega (copy), par extension `input.substringAfterLast(".")` se aayega — koi crash nahi, sirf UX oddity.

---

## 8. MEDIUM — App shell / navigation

**Structure** (`MainActivity.kt`):

- Bottom tabs (4): `home`, `history`, `queue`, `settings` (`MainActivity.kt:137-142`)
- Routes: `home` (:256), `tool/{toolId}?path={path}` (:269), `player` (:287), `history` (:288), `settings` (:289), `queue` (:292)
- Share-to-Nova (`ACTION_SEND` / `SEND_MULTIPLE`) → mime ke hisaab se tool kholta hai (:192-215)
- App icon long-press shortcuts → `nova://home|history|queue|settings` (:118-128), `shortcuts.xml` me 4 shortcuts ✅
- Player = **overlay** via `PlayerController.current` (:307-324), navigation se nahi

### 8.1 🔵 Dead route: `"player"`

`MainActivity.kt:287` `composable("player") { PlayerScreen(snackbar) }` — poore codebase me `navigate("player")` **kahin nahi** (grep: 0 matches). Actual player overlay :307-324 se render hota hai. → Dead code, hata do.

### 8.2 🟡 NovaService `exported=true` + `permission=DUMP`

`AndroidManifest.xml:63-67`:
```xml
<service android:name=".core.NovaService"
    android:exported="true"
    android:permission="android.permission.DUMP"
    android:foregroundServiceType="dataSync" />
```

- **Self-start safe hai** — AOSP `ActiveServices` me `checkComponentPermission(..., owningUid = r.appInfo.uid, ...)` same-app ko GRANT karta hai. Isliye app apni service start kar sakta hai (ye verify kiya gaya hai, guess nahi).
- **Matlab:** sirf system/shell (DUMP holder) apps bahar se service start kar sakti hain — third-party nahi. Koi data leak nahi.
- **Par galat design hai:** app service kisi ko expose karta hi nahi → `android:exported="false"` hona chahiye. `DUMP` (signature|privileged permission) ka yahan koi legitimate use nahi.
- FGS type `dataSync` + `FOREGROUND_SERVICE_DATA_SYNC` permission declared ✅ (`AndroidManifest.xml:9`).

### 8.3 🟡 `allowBackup="true"` bina backup rules ke

`AndroidManifest.xml:18` — `android:allowBackup="true"`, par **na** `android:fullBackupContent`, **na** `android:dataExtractionRules` (API 31+). Auto Backup saara data (history JSON + settings prefs) cloud me bhejega. Privacy-focused app ke liye history ko exclude karna chahiye.

---

## 9. Settings — current inventory

**Settings screen me (4 sections):**

| Section | Items | Line |
|---|---|---|
| Appearance | Theme dropdown (10 themes + live swatch preview), "Follow system dark mode" switch, "AMOLED pure black" switch | `SettingsScreen.kt:81-157` |
| Notifications | "Completion sound + vibration" switch | `:160-179` |
| Storage | Auto-save note + "Clear history records" button (confirm dialog) | `:182-195` |
| About | Static text: name / "FFmpeg + Media3 engine • 15 tools" / output folders | `:198-214` |

**Player ke andar reh gaye (Settings screen me nahi):** audio theme (`AudioPlayerX.kt:348`), EQ preset (`:395`), subtitle size/color/sync (`VideoPlayerX.kt:634-685`), first-run tips dismiss (`HomeScreen.kt:121`).

**Missing (requested features se match):** Language selector, default output quality/codec, engine chooser (FFmpeg vs Media3), cache clear, version/build info, privacy/licenses.

`Settings.kt` = 94 lines, 10 prefs, SharedPreferences `nova_settings` — clean, koi issue nahi.

---

## 10. Distribution / build checklist

| Item | Value | Note |
|---|---|---|
| `versionCode` / `versionName` | `1` / `"1.0"` | `build.gradle.kts:15-16` — display kahin nahi hota |
| `compileSdk` / `targetSdk` | 34 / 34 | 🔴 Play API 36 required |
| `minSdk` | 26 | §4 crash risk |
| Release minify | `isMinifyEnabled = true`, `isShrinkResources = true` | `:26-27` |
| Release signing | **debug keystore** | 🔴 `:30` |
| ProGuard | `-keep class com.nova.converter.** { *; }` | 🟡 `proguard-rules.pro:3` — app code ka shrinking/obfuscation **poora defeat** ho gaya (sirf libs shrink hoti hain). Isko targeted rules me badlo. |
| ABIs | `arm64-v8a`, `x86_64` | 🔴 32-bit ARM nahi |
| Permissions | READ_MEDIA_*, POST_NOTIFICATIONS, FGS + FGS_DATA_SYNC, READ/WRITE_EXTERNAL (maxSdk 32/28) | ✅ **INTERNET nahi hai** |
| Merged extra permission | `ACCESS_NETWORK_STATE` (media3-common se) | harmless |
| Crash reporting / analytics | **NONE** (koi lib, koi network call nahi) | ✅ privacy-friendly |
| Lint vital | PASSED | `build/intermediates/lint_vital_*` |
| TODO/FIXME in repo | **exactly 1** — `app/build.gradle.kts:29` (keystore) | ✅ clean tree |
| Packaging excludes | `/META-INF/{AL2.0,LGPL2.1}` | `build.gradle.kts:49` — AL2.0/LGPL2.1 notices bhi exclude kiye hain (GPL ke saath odd) |

**Privacy story (policy ke liye ready-made points):** INTERNET permission nahi · koi network call nahi · koi analytics/crash SDK nahi · saara processing on-device (FFmpeg + Media3) · output sirf device storage me · history sirf local JSON.

---

## 11. Recommended fix order

**P0 — ship se pehle (broken functionality):**
1. Watermark tool: drawtext available build lo **ya** tool disable karo (§1)
2. Image → WEBP: codec mapping / option hatao (§2)
3. `Outputs.save` ko crash-safe bao + API ≤28 permission (§4)

**P1 — Play Store ke liye:**
4. Release keystore banao (`build.gradle.kts:30`) (§3.2)
5. `targetSdk/compileSdk = 36` (§3.1)
6. GPL license notices APK me wapas laao + About me version/licenses screen (§3.3, §6)
7. ABI decision: `armeabi-v8a` add karo ya consciously drop karo (§3.4)

**P2 — product completeness:**
8. Localization pipeline (§5) — pehle strings extract karo, phir 10 languages
9. Privacy policy + OSS licenses screen (§6)
10. `extractAudio` bitrate verify/fix (§7.1)
11. `compressCustom` fallback wire karo ya delete karo (§7.2)
12. `exported=false` for NovaService (§8.2), backup rules (§8.3), dead `player` route hatao (§8.1)
13. ProGuard rules targeted banao (§10)

---

## 12. Resolution status (2026-09-29)

> Poora fix cycle implement + verify ho chuka hai. Verification logs: `build24.log` (debug compile + resources, **BUILD SUCCESSFUL**), `build25.log` (assembleRelease, **BUILD SUCCESSFUL**).

### Fixed ✅

| § | Item | Kya hua | Kaise verify |
|---|---|---|---|
| 1 | Watermark → drawtext missing | Tool ab FFmpeg **`overlay` filter** se image-overlay karta hai (`Commands.kt fun watermark` → `-filter_complex "[0:v][1:v]overlay=$x:$y"`) — drawtext ki zaroorat hi nahi | source read + green release build |
| 2 | Image → WEBP broken | `webp` option UI se hata diya (`Tools.kt` me "webp" = 0 matches); jpg/png/bmp native encoders chalte hain | grep |
| 4 | Save crash API 26–28 | `Outputs.save` (Engine.kt) poora guarded: MediaStore try → **app-specific folder fallback** → null sirf dono fail ho to; callers crash nahi kar sakte | source read |
| 3.1 | targetSdk 34 | `compileSdk = 36`, `targetSdk = 36` | aapt2 badging: `targetSdkVersion:'36'`, `compileSdkVersion='36'` |
| 3.2 | Debug-key signing | release keystore wired (`keystore.properties` + debug fallback) | apksigner: `CN=Nova Converter` (debug cert nahi) |
| 3.3 | GPL notices shrink | **In-app** Legal/About screen: OSS licenses + ffmpeg-kit/GPL source-availability notice (`LegalScreen`, `licenses_intro`) — res/raw path apne aap drop (in-app path authoritative) | strings + screen present |
| 6 | Legal/About/Privacy missing | `LegalScreen` (privacy, terms, OSS licenses) + About me version display (`about_version`) | SettingsScreen me 2 refs |
| 5 | Localization 0% | Poora UI string extraction (Home, ToolDetail, Queue, History, Settings, both Players, Notifications, Common, Tools + `optionLabels`) → base `values/strings.xml` **330 entries / 319 translatable**; **10 languages**: en (base) + **hi, ur, ar, bn, ta, te, es, pt, id** — har locale **314 keys** (317 − 3 brand keys jo intentionally English fallback hain); `privacy_body`/`terms_body` legal text English me hi (decision); in-app language picker (`system` default) + `attachBaseContext` locale apply + `Activity.recreate()` | validator: all 9 locales **PASSED** (0 missing/dup/format-err, 0 raw `&`, 0 apostrophes); release APK `resources.arsc` me `(hi)(ur)(ar)(bn)(ta)(te)(es)(pt)(id)` translated values |
| 7.1 | extractAudio `-b:a`+`-q:a` clash | ab codec-aware: lossless (wav/flac) pe bitrate skip, baaki sirf `-b:a ${bitrateK}k` — `-q:a` hata diya | `Commands.kt extractAudio` |
| 7.2 | compressCustom dead code | `video_compressor` ka build lambda ab seedha `Commands.compressCustom(a, outQ(...), h, kbps, true)` call karta hai → FFmpeg fallback wired | `Tools.kt` |
| 7.3 | speed() ka useless `-filter:v` | `speed(input, output, factor, hasVideo)` — video filter sirf video input par | `Commands.kt` |
| 8.1 | dead `player` route | `composable("player")` aur `navigate("player")` dono hate (0 matches) | MainActivity.kt grep |
| 8.2 | NovaService exported=true+DUMP | ab `android:exported="false"`, DUMP permission hata di (comment ke saath) | AndroidManifest |
| 8.3 | backup rules missing | `res/xml/backup_rules.xml` + `data_extraction_rules.xml` added (history/settings exclude) | res/xml listing |
| 10 | ProGuard blanket keep | `-keep com.nova.converter.**` hata ke targeted rules: ffmpeg-kit JNI + media3 transformer + readable stack traces | `proguard-rules.pro` |
| — | Build tooling | AGP 8.11.1, Gradle 8.13, Kotlin 2.0.21, R8 minify + resource shrink | wrapper + build |

### Deferred ⏸️ (decision chahiye ya low-value)

| § | Item | Reason |
|---|---|---|
| 3.4 | **32-bit ARM (armeabi-v8a)** | abiFilters abhi bhi sirf `arm64-v8a` + `x86_64`. ffmpeg-kit-full-gpl AAR khud sirf yahi 2 ABIs deta hai → armeabi-v8a ke liye alag variant chahiye (ya AAR apna banana padega). Play listing me consciously mention karo ya variant lo. |
| 8.3 | `allowBackup="true"` | rules ke saath ab controlled; full disable optional |
| — | Residual hardcoded literals | `LegalScreen` (~39), `Engine` (12), `FastCompressor` (36), `MediaUtil` (30), `Theme` (24), `History` (21), `NovaService` (4), `Files`/`Watermark` — mostly internal/technical (log tags, handler names); poora user-facing UI localized hai |
| 7.4 | `convert()` audio branch | unchanged — harmless dead branch |
| 7.5 | metadata `*/*` mimes | unchanged — minor UX oddity |

### Final artifacts

- Debug: `build24.log` → BUILD SUCCESSFUL
- Release: `build25.log` → BUILD SUCCESSFUL → `app/build/outputs/apk/release/app-release.apk` (**84.9 MB**)
- APK verified: `targetSdkVersion 36` · signed `CN=Nova Converter` · 9 locales translated values in `resources.arsc` · arm64 ffmpeg `.so` intact · zero raw apostrophes/ampersands across all locale files

---

---

## 13. Field-report fixes - "video compressor / image converter / audio merger / video merger kam nahi kar rha"

User report ke baad deep static debugging (ffmpeg-kit ka `parseArguments` source verify kiya: quotes **strip** karte hain - double-quote ka asli nuksaan Java-side ext-sniffing aur space-paths me tha). Root causes + fixes:

| # | Symptom | Root cause | Fix (file) |
|---|---------|-----------|------------|
| 1 | **audio_merger** fails har run (audio_cutter bhi) | `Tools.outQ()` output path **double-quote** karta tha (`""path""`). `Commands.audioMerge`/`trimAudio` ka ext sniff `substringAfterLast('.')` -> `mp3"` -> `aCodec()` else-branch -> **`aac`** -> aac-into-mp3 tag/mux error -> FAIL. Space wale path bhi `""my file.mp3""` me parse hote waqt toot jate the | `outQ` ab RAW path deta hai (har `Commands.*` builder pe `q()` ek hi baar lagta hai) - `Tools.kt` |
| 2 | **video_merger** output wrong (hamesha 720p, B-clip ki silence length galat); watermark size bhi 1280x720 fix | `runTool` `params["_w"]/_h` **Int** aur `_dB` **Float** store karta hai, par `Map.i()/f()` **sirf `String`** padhte the -> hamesha defaults | `i()`/`f()` ab Int/Long/Float/Double/String sab coerce karte hain - `Tools.kt` |
| 3 | **video_merger** kabhi-kabhi FAIL (ya audio gayab) | `probeAll` ka `METADATA_KEY_HAS_AUDIO`: unsupported codec pe `null` -> `false` -> asli audio ki jagah silence; MMR exception pe initial `true` -> bina-audio file pe `[0:a]` matches no stream -> FFmpeg error | Audio presence ab **ffprobe** (`streams[].type`) se confirm, MMR fallback - `ToolDetailScreen.kt` `probeAll` |
| 4 | **video_compressor** crash / hang / cancel-to-naya-job | (a) `runTool` aur `FastCompressor.compress()` me **try/catch nahi** tha -> Media3 sync exception = app crash. (b) Media3 stuck ho to koi timeout nahi -> hamesha "compressing". (c) **Cancel bug**: `cancel()` ke baad `Transformer.onError` -> `onDone(false)` -> **FFmpeg fallback job start** ho jata tha (cancel karne pe bhi naya job chalu!) | (a) dono guarded + fallback ab shared `submitFFmpeg()` fun se; (b) **90s progress-stall watchdog** -> auto-cancel -> FFmpeg fallback; (c) `AtomicBoolean` once-guard - user cancel pe callback suppress - `FastCompressor.kt`, `ToolDetailScreen.kt` |
| 5 | **image_converter** fail (gif/animated webp input) + bmp gallery save odd | IMAGE branch me `-frames:v 1` nahi tha -> multi-frame input pe image2 muxer single filename pe multiple frame nahi likh pata; `guessMime("bmp")` missing -> `application/octet-stream` | `-frames:v 1` added (`Commands.kt`); `guessMime` me `bmp`+`webp` (`FastCompressor.kt`) |
| 6 | Errors visible nahi | blank-cmd sirf transient snackbar dikhata tha | `failMsg` -> persistent `ErrorCard`; build/submit exception bhi `failMsg` me - `ToolDetailScreen.kt` |

Encoder capability bhi binary-se verify ki (`libavcodec.so` scan): `libx264`, `libmp3lame`, `mjpeg`, `png`, `bmp`, `gif`, `libvorbis`, `libopus` sab present (full-gpl AAR) - image/audio encoder kami nahi thi.

Verification: `build26.log` -> compileDebugKotlin **BUILD SUCCESSFUL**; `build27.log` -> assembleRelease **BUILD SUCCESSFUL**.

## 14. Field round 2 - save flow / cancel dialog / UI polish / legal localization

Reported: video compressor / image converter / audio merger / video merger failing; save flow, cancel dialog, and UI polish requests.

### Root causes fixed (round 2)

| # | Symptom | Root cause | Fix |
|---|---------|-----------|-----|
| 1 | audio_merger + audio_cutter fail with mux error | `outQ()` returned a pre-quoted path → Java-side ext sniff saw `mp3"` → picked `aac`; spaces broke parseArguments | `outQ` returns raw path (single-quote layer lives in Commands builders) |
| 2 | video_merger stuck 720p, silence duration wrong | `i()`/`f()` param helpers only accepted String; stored params were Int/Float → fell back to defaults | helpers coerce Int/Long/Float/Double/String |
| 3 | merger audio flags wrong (muting/failed second input) | HAS_AUDIO from MediaMetadataRetriever flaky/null | `probeAll` confirms audio streams via FFprobeKit `getMediaInformation` |
| 4 | video_compressor crash / hang / cancel spawns fallback job | Media3 setup unguarded, no stall timeout, `cancel()` triggered `onError` → FFmpeg fallback | try/catch + 90s stall watchdog + AtomicBoolean once-guard; cancel suppresses callback |
| 5 | image_converter fail on animated inputs | image2 muxer cannot write multi-frame to one file; `guessMime("bmp")` missing | `-frames:v 1` in IMAGE branch; bmp/webp mime mappings |
| 6 | failures gave only a transient snackbar | build/submit exceptions unreported | `submitFFmpeg` try/catch → persistent ErrorCard with `failMsg` |

### UI / UX changes (round 2)

- **ConfirmDialog rewritten** (cancel-job + history delete + clear-history): stacked, full-width confirm/dismiss buttons inside a custom `Dialog`. Field symptom was the dismiss ("No") button not rendering in the M3 AlertDialog button row (overflow/label-length dependent); both buttons are now layout-guaranteed in every language and font scale.
- **Manual Save button removed from ResultCard** — auto-save on completion is the single path; card shows a `Saved → Gallery / NovaConverter` hint plus Share / Open / Delete.
- **Auto-open after completion**: foreground-completed video/audio jobs open the in-app player automatically; images open with the system viewer (FileProvider + `ACTION_VIEW`). Background/queued completions keep notification-tap flow (no auto-open while user is elsewhere).
- **Home header**: top Settings and Queue shortcut icons removed (bottom navigation + mini jobs bar remain).
- **Settings reworked**: theme = tappable 10-swatch chip grid (FlowRow, no dropdown), language = button + scrollable picker dialog capped at 360.dp (~7 rows visible), About privacy/terms side-by-side, **Open-source licenses button removed** (route retained; license texts remain bundled), SectionCard tightened for density.
- **Localization**: `privacy_body` + `terms_body` translated into all 9 locales (316 keys/locale, XML valid, l10n validator PASSED; about_* were already localized).

### Verification status

- `build28` (feature batch) and `build29` (Outputs.save diagnostics): **BUILD SUCCESSFUL**; `app-release.apk` installed on device (update install, data kept).
- Device-verified: audio + video auto-save to `Music|Movies/NovaConverter` (MediaStore rows confirmed), completion notifications posted (channel `nova_done`, importance HIGH, POST_NOTIFICATIONS granted), home header + Settings rework live.
- **Open item — image auto-save to `Pictures/NovaConverter`**: MediaStore accepts `image/bmp|png` inserts on this device (proven via `adb content insert`), yet no image row existed after the field run. `Outputs.save` now logs every branch under log tag `NovaOut`; background `adb logcat -s NovaOut` capture armed for the next image-conversion test.

## 15. Round 3 — image auto-save hardening (open item §14 ka fix)

§14 ka open item (image conversion ke baad `Pictures/NovaConverter` me row nahi)
device-par verify hona baaki hai, par code me 2 load-bearing gaps mile jo Dono
is symptom ko explain karte hain — dono fix kar diye:

| # | Gap | Fix (file) |
|---|---|---|
| 1 | `saveViaMediaStore` me **IS_PENDING protocol nahi tha**. Row insert hote hi Gallery ko visible ho jaati thi jabki bytes abhi likhe bhi nahi gaye the — kuch devices (khaas taur **Images** collection) aisi adhuri row hide kar dete hain → "insert to hua, row nahi dikhi". Saath me `SIZE`/`DATE_ADDED` columns bhi missing the, aur extension↔mime mismatch possible tha (e.g. `.bmp` pe galat mime → kuch OEM Gallery row filter kar dete hain) | `Engine.kt saveViaMediaStore`: API 29+ pe `IS_PENDING=1` ke saath insert → bytes copy → `IS_PENDING=0` update; `SIZE` + `DATE_ADDED`; extension se canonical image mime (`jpg→image/jpeg`, `png`, `bmp`, `gif`, `webp`); insert ab try/catch me (throw pe null → app-files fallback, crash nahi) |
| 2 | Ordering bug: `Queue.update` DONE path (aur hardware path `ToolDetailScreen`) me `History.recordSuccess(...)` **unguarded** tha aur `Outputs.save(...)` se **pehle** call hota tha. Prefs/JSON kabhi throw kare to MediaStore copy skip ho jaati (notification dikhta, Gallery me file nahi) | Dono jagah `History.recordSuccess` apne try/catch me — History fail ho to bhi save hota hai (`Engine.kt`, `ToolDetailScreen.kt`) |

Verification: `:app:compileDebugKotlin` (BUILD SUCCESSFUL — `compile30.log`).
Device par ab bhi confirm karna hai: image convert → `adb logcat -s NovaOut`
me `saved <name> mime=image/... → content://media/.../images/...` aur
Gallery me `Pictures/NovaConverter` row. Agar phir row na bane to log me
`insert null/ threw / copy failed` wali line exact reason degi.

## 16. Round 4 — Real-ESRGAN wiring, speed tool 0.5x–100x, compressor warning

User ke 3 field reports: (a) Real-ESRGAN binary ban gayi thi par app se linked
nahi thi, (b) speed tool 3x/4x pe error, (c) compressor ke dauran "1 warning"
info aati hai + speed range sirf 0.5x–4x thi.

### 16.1 Speed tool 3x/4x — **do alag bugs**, dono confirmed

| # | Bug | Kyun | Fix |
|---|---|---|---|
| 1 | `Commands.speed` atempo chain **filter naam repeat nahi karta tha**: `atempo=2.0,1.500` banta tha | FFmpeg ka filtergraph parser `,` se chain todta hai aur har piece ko alag filter samajhta hai — piece #2 `"1.500"` ek **bogus filter name** hai → `No such filter: '1.500'`. Isliye **sirf >2x** fail hota tha; 0.5/0.75/1.5/2 ek single instance the isliye "theek" the | Har link par naam repeat: `atempo=2.0000,atempo=1.5000` (`Commands.atempoChain`) |
| 2 | `"%.3f".format(f)` **default locale** leta hai, aur `MainActivity.applyLocale` (`MainActivity.kt:78`) `Locale.setDefault()` karta hai | `ar`/`ur`/`hi` locale me Java Arabic-Indic digits deta hai (`٣٫٠`) → `PTS/٣٫٠` FFmpeg parse hi nahi karta → **koi bhi speed** fail. Yani 3x/4x ka field report sirf ek symptom tha | Saare numbers `String.format(Locale.US, ...)` se (`Commands.n4`/`n6`) |

Chain verify kiya (0.5 … 100, har value ka product exactly factor, har instance
(0.5, 2.0] me):

```
3x    -> atempo=2.0000,atempo=1.5000                        product=3.0000
4x    -> atempo=2.0000,atempo=2.0000                        product=4.0000
25x   -> atempo=2.0000 x4,atempo=1.5625                     product=25.0000
100x  -> atempo=2.0000 x6,atempo=1.5625                     product=100.0000
```

Do aur cheezein `speed()` me fix/saaf ki gayi:
- **Codecs ab explicit** (`-c:v libx264 … -c:a aac`); pehle container default par depend the.
- **`-fps_mode passthrough`** — `-r`/`-vsync cfr` lagane se frames duplicate/drop hote (speed-up ka matlab hi frame nikalna hai). `-vsync` FFmpeg 7 me deprecated hai, isliye use nahi kiya.
- **`hasAudio` flag**: `_aA` pehle **sirf** `twoInputs` tools me set hota tha, to single-input speed tool par default `true` chala jaata tha → silent video par FFmpeg ko `atempo` filter mil jata tha (stream match nahi karta). Ab `_aA` har tool ke liye set hota hai (`ToolDetailScreen.submitFFmpeg`).

### 16.2 Speed range 0.5x – 100x + curves + custom

Naya `TField.Speed` + `SpeedScale` (`Tools.kt`), UI `SpeedEditor` (`ToolDetailScreen.kt`):

- **Slider** piecewise scale (`toSlider`/`fromSlider`): pehla half travel 0.5x→2x linear, doosra 2x→100x log. Pure-log map me 0.5–2 poore slider ka sirf ~13% leta aur normal speeds (1x/1.5x/2x) jaldi khatam ho jate; piecewise se slow-motion side fine control *aur* 100x dono milte hain.
- **Presets (curve)**: 0.5 / 0.75 / 1 / 1.25 / 1.5 / 1.75 / 2 / 3 / 4 / 6 / 8 / 10 / 16 / 25 / 50 / 100.
- **`±` step buttons** (`SpeedScale.stepFor`): step value ke hisaab se chhota→bada (0.05x / 0.1x / 0.5x / 1x). Button apna next value dikhata hai, to user ko pehle se pata rehta hai.
- **Numeric field**: bilkul exact value (1.37x). Typing ke dauraan text slider se overwrite nahi hota (`typing` flag + `set`/`setQuiet` alag); out-of-range pe red error + message, value clamp.
- **Live readout**: bada value upar, aur neeche **estimated output length** (`duration ÷ factor`) — `R.string.speed_out_hint`.
- Value params map me raw `Float` (string nahi) — string round-trip `1.37` ko `1.369999885` bana deta tha.

`video_speed` aur `audio_speed` dono chips → `TField.Speed`. Subtitle `subt_video_speed` 0.5x–4x → 0.5x–100x (sabhi 10 locales).

### 16.3 Real-ESRGAN wiring (binary → app)

`realesrgan-ncnn-vulkan` NDK se ban chuki thi (dono ABI) par **dead code** thi:
`jniLibs` khali, koi call nahi.

| Step | Detail |
|---|---|
| Packaging | `app/src/main/assets/ai/<abi>/realesrgan-ncnn-vulkan` — **assets, jniLibs nahi** (ye ELF *executable* hai, shared library nahi; jniLibs ka merger sirf `.so` expect karta hai). Default compress: APK me sirf **+13.2 MB** (4.67 + 8.53), khaali 39 MB nahi |
| Runtime install | `RealEsrgan.install()` — asset → `filesDir/bin/` copy + `chmod 700`, pehli run me hi |
| Exec | `ProcessBuilder` + `pb.directory(modelDir)` (model `.bin`/`.param` CWD se relative resolve hote hain), stderr se progress regex `= N.NN%`, `ProgressBus` + `NovaService` se live %, notification + History + auto-save usi tarah jaise hardware path |
| Preflight | `RealEsrgan.ready()` pehle hi bata deta hai: no-binary / no-vulkan / no-model — user ko error card pehle dikhta hai, process start nahi hota |
| Cancel | `RealEsrgan.cancel()` → `Process.destroy()` (FFmpeg Queue se alag, native process hai) |
| UI | `image_converter` me naya `AI (Real-ESRGAN)` chips field: Off / 2x / 4x / 4x-anime + TTA toggle |

**Do honest limitations (jo pehle report me nahi thi):**

1. **Models app me nahi hain.** ncnn binary ko `realesrgan-x4plus.bin` + `.param` chahiye (~64 MB), jo NDK build par bhi nahi the. App inhe `<filesDir>/ai/` me dhoondta hai — model na ho to crash nahi, clean error (`err_ai_no_model`). **Binary ke bina model ke kuch nahi kar sakta.**
2. **Video AI upscale nahi kiya.** `realesrgan-ncnn-vulkan` sirf **image / image-folder** leta hai, video nahi. Video ke liye frame-by-frame extract + reassemble chahiye, jo on-device itna slow hai ki practical nahi — isliye `video_enhancer` FFmpeg par hi hai aur AI mode `image_converter` me hai. Ye pichle session ka framing ("Video Enhancer FFmpeg se replace karo") galat tha: binary video nahi chala sakta.

### 16.4 Compressor "1 warning" — app side hataya, exact text confirm nahi

Jo confirm kiya ja sakta tha woh hataya:

- `err_hw_fallback` snackbar **dono** fallback paths se remove (hardware `onError` aur sync-setup exception). Ye toast padhne me "conversion fail ho gaya" lagta hai jabki actually FFmpeg fallback chalu tha aur job **success** hoti hai — yahi "confusing warning" ka best candidate hai. Ab silent fallback + `Log.i("NovaHw", …)`.
- Comment bhi likha ki future me wapas na aaye.

**Jo confirm nahi:** exact "1 warning" text ka source. App ka koi bhi string/notification "warning" nahi bolta (grep: 0 matches), Toast app me zero hai, Media3 1.4.1 Transformer toast nahi deta. Isliye wo ya to system-level message hai ya MediaCodec/encoder ka log. Device se pakka karne ka tareeka: compress chala kar `adb logcat | findstr /i "warn nova"` — text milte hi batayein, exact fix kar dunga.

### 16.5 Verification

- `:app:compileDebugKotlin` — BUILD SUCCESSFUL (compile loop 3 baar: pehle 2 error fix hue — `FEATURE_VULKAN_HARDWARE_ACCELERATED` unavailable, `$abi()` string template me `Function invocation expected`).
- **`:app:assembleRelease` — BUILD SUCCESSFUL**, `app-release.apk` 94.2 MB, built 2026-09-29 16:18:29.
- APK byte-scan: `classes.dex` me `atempo=` ✅, `fps_mode passthrough` ✅, `realesrgan-ncnn-vulkan` ✅; `resources.arsc` me saare 15 naye string keys ✅; `assets/ai/{arm64-v8a,x86_64}/realesrgan-ncnn-vulkan` ✅.
- atempo chain math: PowerShell se 17 factors (0.5 … 100 + 1.37) verify — product exactly factor, har instance (0.5, 2.0].
- **l10n**: 16 naye keys × 10 locales + `subt_video_speed` update, sab valid; base 357 keys, har locale 343, `extra=0`, `missing=14` = sirf intentional brand/lang keys (`app_name`, `*_brand`, `lang_*`), BOM nahi, CRLF nahi.
- **Device par test nahi hua** — `adb devices` khaali hai (koi phone/tablet connected nahi). Speed slider/AI upscale/compressor warning teeno device test baaki hain.

---

## 17. Video Enhancer hataaya + "sab app ke andar" + audio speed ka real bug

### 17.1 `video_enhancer` tool remove
- `Tools.kt` se poora `NovaTool(id = "video_enhancer")` block hataya + `Icons.Filled.AutoFixHigh` import.
- `Commands.enhance()` (Lanczos upscale + hqdn3d/unsharp/eq + mediacodec-vs-libx264) poora hataya - ab koi caller nahi.
- `ToolDetailScreen.kt` ka "ENHANCER INFO" info-box (source->output dims + factor) hataya.
- **14 dead strings 10 files se delete**: `tool_video_enhancer`, `subt_video_enhancer`, `field_enhance`, `field_encoder`, `opt_pres_clean`, `opt_pres_clarity`, `opt_pres_smooth`, `opt_pres_none`, `opt_eng_fast`, `opt_eng_best`, `opt_8k_warn`, plus `saved_gallery`, `open_with`, `notif_open_file` (teeno is round me use hona band ho gaye).
- Base ab 348 keys, har locale 334, dupes=0, sab XML valid, base UTF-8 no-BOM (3 bare LF the, normalize kar diye).

### 17.2 Audio speed: "har speed pe error" - ASLI root cause
Pichle round me **do** speed bugs fix hue the (atempo chain + locale digits). Field report phir bhi "har speed pe error" bola. Is baar asli wajah alag thi:

```kotlin
// BUGGY
val audio = "-filter:a $atempo -c:a libmp3lame " + audioBitrateFor(ext, "Medium")
return "-y -i $i -vn $audio$o"      // <-- $audio ka trailing space nahi tha
```

`audioBitrateFor()` `"-b:a 160k"` (space ke bina) return karta hai, isliye command ban rahi thi:

```
-y -i "song.mp3" -vn -filter:a atempo=1.5000 -c:a libmp3lame -b:a 160k"out.mp3"
                                                            ^^^^^^^^^^^^^^^^^^
```

ffmpeg-kit ka `parseArguments` quotes sambhalta hai, to yeh **ek hi argument** ban jata hai: `160k/data/cache/nova/song.mp3`. FFmpeg uspe output path samajhta hai -> har speed pe fail.

**Ye bug sirf audio branch me tha** - video branch me `$audio` ke baad `"-fps_mode passthrough "` (apne trailing space ke saath) aata tha, isliye video speed chalti thi. Isiliye user ko "sirf audio speed toot rahi hai" laga.

**Fix**: `speed()` ab token-list se build karta hai (`args.joinToString(" ")`), to string interpolation se chipke hue args ka pura class hi khatam:

```kotlin
val args = mutableListOf("-y", "-i", q(input))
if (hasVideo) args += listOf("-filter:v", "setpts=PTS/${n6(f)}") else args += "-vn"
if (hasAudio) {
    args += listOf("-filter:a", atempo, "-c:a", if (hasVideo) vAudioCodec(ext) else aCodec(ext))
    val bitrate = if (hasVideo) "-b:a 160k" else audioBitrateFor(ext, "Medium")
    if (bitrate.isNotBlank()) args += bitrate
} else args += "-an"
if (hasVideo) args += listOf("-fps_mode", "passthrough", "-c:v", vCodec(ext), ...)
args += q(output)
return args.joinToString(" ")
```

Saare `Commands.*` builders scan kiye - yehi glue pattern kahin aur nahi hai (`extractAudio` / `convert` / `metadata` me explicit spaces hain).

**Verification**: PowerShell me equivalent logic chala kar 6 commands banaye aur har opening-quote ki boundary check ki - sab clean, aur **purana buggy line detect hua** (`GLUED=True`). 0.5x / 0.75x / 1x / 1.5x / 2x / 3x / 4x / 7.5x / 25x / 100x sab chain sahi.

### 17.3 "Koi bhi file app ke bahar na jaye" - 4 jagah band
| Raasta | Pehle | Ab |
|---|---|---|
| Tool result auto-open (image) | `openImageExternal` -> external gallery chooser | `PlayerController.open` (built-in) |
| Queue row "Open" | external `ACTION_VIEW` + chooser | `PlayerController.open` |
| Notification tap | external chooser intent | `MainActivity` + `EXTRA_OPEN_MEDIA` / `EXTRA_OPEN_MEDIA_MIME` |
| History "Open" (image) | external | in-app viewer (sab types ke liye "Play/View") |

- `MainActivity.handleOpenMedia()` - `onCreate` **aur** `onNewIntent` dono me; file missing ho to silently no-op (crash nahi).
- `Notifications.viewIntent()` ab `MainActivity` launch karta hai; `FileProvider` import hata diya.
- `openImageExternal()` poori tarah hatayi.

### 17.4 "Open directory" + file path (History + result card)
Naya `core/FileActions.kt`:

- `savedFolder(mime)` -> `Pictures/NovaConverter` / `Movies/...` / `Music/...` (yehi folder `Outputs.saveViaMediaStore` use karta hai - user ko wahi dikhta hai; cache path useless hai).
- `openFolder()` - `DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:<rel>")` + `ACTION_VIEW` + `MIME_TYPE_DIR` + `INITIAL_URI` extra. **`file://` nahi** - Android 7+ `FileUriExposedException` maarta hai aur FileProvider folder serve hi nahi kar sakta. `mkdirs()` try/catch me (pehli baar folder na bana ho).
- `locationOf()` + `copy()` fallback.

UI:

- **History row**: file-name ke neeche ek tappable location line; buttons row me `Play`/`View` + `Open folder`; Share/fav/delete alag row me (Hindi/Urdu/Arabic me labels lambe hote hain, ek row me truncate ho rahe the).
- **Result card**: naya `Open folder` button + `saved_gallery` ki jagah live `Saved in Pictures/NovaConverter`.
- **Queue row**: `Open folder` button bhi.
- File manager na mile -> `err_no_file_manager` snackbar (3 jagah).

### 17.5 GIF ab animate hota hai
`BitmapFactory` GIF ka sirf pehla frame decode karta hai -> Video->GIF ka output app me "static photo" dikhta tha. Naya `GifView()` `android.graphics.Movie` se frame-by-frame chalta hai:

- frame counter **sirf draw phase** me padha jaata hai (`Canvas { }` -> `Spacer(drawBehind)`), isliye 60fps pe sirf redraw hota hai, poori composition recompose nahi.
- scale/offset `nativeCanvas` transform se (`Movie.draw` sirf `(canvas, x, y)` leta hai; scaled w/h overload API 36 stub me nahi hai).
- zoom gesture wahi `graphicsLayer` se chalta hai.
- `MediaUtil.isImageFile("gif")` pehle se true tha, isliye mode `"image"` hi rehta hai.

### 17.6 Chhoti lekin asli cheezein
- Image viewer ke hard-coded English `"Back"`, `"Next"`, `"Queue"` -> `cd_back`, `btn_next`, `queue_title` (Hindi/Arabic UI me player English dikh raha tha).

### 17.7 Verification
- `:app:compileDebugKotlin` SUCCESSFUL, `:app:assembleRelease` SUCCESSFUL (94.2 MB, 17:24:50), `:app:assembleDebug` SUCCESSFUL (113.9 MB, 17:27:50).
- **APK byte-scan**: `classes.dex` me `atempo=`, `fps_mode` + `passthrough`, `libmp3lame`, `com.android.externalstorage.documents`, `vnd.android.document/directory`, `com.nova.converter.OPEN_MEDIA` (x2), `Movie`, `setpts=PTS/`, `-filter:a`, `nova_in` **present**; `video_enhancer`, `AutoFixHigh`, `openImageExternal`, `minterpolate`, `hqdn3d`, `Lanczos` **absent** (0 hits - dead code sach me gaya).
- `resources.arsc`: naye 5 keys present, 13 hataye gaye keys **0** (resource shrink ne bhi confirm kiya).
- `assets/ai/{arm64-v8a,x86_64}/realesrgan-ncnn-vulkan` 13.9 MB + 23.6 MB present.
- **Device par phir bhi nahi test hua** - `adb devices` khaali. Audio speed 0.5x / 2x / 100x, GIF animation, aur `Open folder` (DocumentsUI intent OEM-dependent hai) device pe confirm karne hain.

## 18. Round 5 — Isolate Voice, output folders, home icons, legal rewrite

> Status: code complete + `compileDebugKotlin` + `assembleRelease` pass. Jahan "device verified"
> likha hai wahan `uiautomator dump` se pixel bounds confirm hue. Jahan "pending" likha hai
> wahan device test nahi hua (USB debugging baar-baar drop ho raha tha).

### 18.1 "Open folder" — asli root cause aur 5-step chain

Pehle `FileActions.openFolder()` ek hi intent bhejta tha: `ACTION_VIEW` +
`content://com.android.externalstorage.documents/document/primary%3AMusic%2FNovaConverter`.

**Ye normal app se kabhi nahi chalta.** DocumentsUI ka `ExternalStorageProvider` aisi directory
URI par app ke liye grant nahi banata, aur logcat me saaf aata hai:

```
NovaFiles: SecurityException for content://com.android.externalstorage.documents/
           document/primary%3AMusic%2FNovaConverter grant=true:
           Permission Denial: opening provider com.android.externalstorage.documents
           from ProcessRecord{...} (uid=10933) that is not exported from UID 10933
```

`uid=10933` = Nova app. Yahi URI **shell (uid 2000)** se chalti hai — isliye `adb shell am start`
se test karne par folder khulta dikhta tha aur code "theek" lagta tha. Ye classic false-positive tha.

Ab chain 5 steps ki hai (`FileActions.kt:143-181`), har step apne try/catch me:

| # | Route | MIME | Grant flag | Result |
|---|---|---|---|---|
| 1 | exact folder (`primary:Music/NovaConverter`) | `vnd.android.document/directory` | yes | `OPENED` |
| 2 | wahi URI, grant flag ke **bina** | `vnd.android.document/directory` | no | `OPENED` |
| 3 | parent public folder (`primary:Music`) | `vnd.android.document/directory` | yes | `OPENED` |
| 4 | storage root | `vnd.android.document/root` | yes | `OPENED` |
| 5 | `ACTION_OPEN_DOCUMENT_TREE` + `EXTRA_INITIAL_URI` | — | read+write | `PICKER` |
| — | sab fail → path clipboard me | — | — | `COPIED` |

Step 2 isliye ki kuch OEM builds grant flag wali "foreign authority" ko hi SecurityException dete
hain; step 5 **har** Android device par guaranteed hai (system DocumentsUI), bas user ko "Use this
folder" dabana hota hai. Return type `FolderOpen = OPENED | PICKER | COPIED` — caller sirf `COPIED`
par snackbar dikhata hai, warna user ko bekaar ka message milta tha.

**Device proof (Infinix X6833B, Android 14):** Settings → Audio row par "Open" tap →
`mCurrentFocus=com.google.android.documentsui/...FilesActivity`, breadcrumb
`asu > Music > NovaConverter`, aur asli files dikhi (`lv_0_20260930142324.mp3` 1.24 MB,
`lv_0_20261001163412__1_.mp3` 724 kB). Log me step 1 ka SecurityException aur baad ka successful
fallback dono dikhe — matlab chain kaam kar rahi hai, sirf step badla.

### 18.2 Home icons — do bade icons ek saath (3 iterations)

**(a) Title bahar.** `ToolGridCard` pehle icon + title **ek hi rounded box** me rakhta tha, isliye 104dp tile me sab kuch thusa hua tha. Ab `Column` → 84dp icon tile + 8dp gap + title bahar. Poora `Column` clickable hai (title par tap bhi card kholta hai). "Video Compressor" ka title 2 lines leta hai → wo card 124dp lamba hota hai, baaki 108dp — **jaan-boojh kar accept kiya**, clipping nahi.

**(b) Iteration 1 → "ek bada op icon + kind badge" (DISCARD).** Operation wale tools ko ek bada op icon + corner me chhota kind badge diya tha. User ne isko reject kiya:

> *"icons ka size bahut chota hai… jaise video converter icon hai style me waise hi sabka karna hai, har kisi ka style same ho lakin icon cards jitna bada ho"* + ek example image (ek music note + ek film reel, ek hi composition me)

Matlab: (1) **saare cards me ek hi style** — jaise `video_converter` ka `[🎬][⇄]`, (2) do icon **hi** hone chahiye (media-kind + operation), dono **barabar bade**.

**(c) Iteration 2 (FINAL) — `[media-kind] + [operation]`, dono bade, diagonal.**

`ToolGlyph.kt` naya: `Box(fillMaxSize)` ke andar do `Icon`, dono `Alignment.Center` par offset karke:

```
   left  = flow[0] ka glyph   (media kind, ya conversion ka SOURCE)
   right = flow[1] ka glyph   (conversion ka TARGET)  |  tool.icon (operation)
   beech = chhota arrow, sirf conversion tools me
```

**Diagonal kyun?** Do icons side-by-side width se limited hote hain: 104dp tile me do icons + gap = ~96dp, yaani har icon max ~46dp — tile ki 84dp height ka sirf 55%. Left icon ko thoda upar, right icon thoda neeche rakhte hain (offset x = -0.32×size / +0.30×size, y = -0.14×size / +0.13×size), jisse composition ~96×70dp ho jaata hai aur **tile ka 80%+ bhar jaata hai**. Ye wahi feel hai jo example image me note aur reel ek doosre ke upar rakhe hain.

| Card | plate | left icon | right icon | title |
|---|---|---|---|---|
| grid tile | 104 × 78dp, `tool.tint` gradient 0.22→0.06 + tint border | **44dp** | **46dp** | bahar |
| small card | fillMaxWidth × 44dp, tint plate 0.14 | **28dp** | **30dp** | andar |
| hero | 76 × 76dp, white plate | **44dp** | **46dp** | andar |

Tile ka background ab khud icon ka "plate" hai (`Brush.verticalGradient(listOf(tint@0.22, tint@0.06))` + 1dp tint border @0.30) — icon area card ke borders tak pahunchta hai.

Grid geometry (device par confirm, pixel/3 = dp): columns x=14/128/242, 10dp gaps, tile 104×84dp.

#### Operation icon ka rule

Card pe media-kind already alag se bada dikh raha hai, isliye operation icon **duplicate hona sahi** hai — actually desirable hai, jaise dono cutters dono kenchi:

| tool | op icon | card pe kya dikhta hai |
|---|---|---|
| audio_cutter / **video_cutter** | `ContentCut` | `[♪][✂]` aur `[🎬][✂]` — **dono par kenchi** |
| video_converter / audio_converter / image_converter | `SwapHoriz` | `[🎬][⇄]` `[♪][⇄]` `[🖼][⇄]` — **teeno par left-right arrow** |
| video_speed / audio_speed | `Speed` | `[🎬][⏱]` `[♪][⏱]` |
| video_merger / audio_merger | `CallMerge` | `[🎬][merge]` `[♪][merge]` |
| video_compressor | `ArrowDownward` | `[🎬][↓]` |
| metadata | `Label` | `[📄][🏷]` |
| watermark | `BrandingWatermark` | `[🎬][💧]` |
| video_to_audio / video_to_gif / frame_capture | (2-stage) | `[🎬]→[♪]` / `[🎬]→[GIF]` / `[🎬]→[🖼]` |

`Tools.kt` me se 5 icon badle gaye + imports 21→15 kiye gaye:

| tool | pehle | ab | kyun |
|---|---|---|---|
| video_cutter | `MovieFilter` (ya `Movie`) | `ContentCut` | sirf film icon dikhta tha, **kenchi thi hi nahi** |
| audio_speed | `AvTimer` / `Forward` | `Speed` | "speed" ka seedha icon |
| image_converter | `Transform` / `Image` | `SwapHoriz` | **left-right arrow** chahiye tha |
| audio_converter | `SyncAlt` / `Audiotrack` | `SwapHoriz` | teeno converters ka style same |
| audio_merger | `Merge` / `CallMerge` | `CallMerge` | video_merger jaisa |
| video_compressor | `Compress` | `ArrowDownward` | purana wala pasand tha |

Saath me 6 dead imports (`Audiotrack`, `AvTimer`, `Compress`, `Merge`, `MovieFilter`, `Transform`) hata diye — verify: `Tools.kt` me use hone wale **saare 14 icon import** hue hain, 0 missing.

> ⚠️ **Pending (device):** naya diagonal composition visually dekhna baaki hai. Check karna hai ki (1) 52/54dp icons 104×84 tile me clip toh nahi ho rahe, (2) 12dp ka horizontal overlap dono icons ko saaf dikhne se nahi rok raha, (3) conversion wale 3 cards ka beech ka arrow theek lag raha hai.

> ℹ️ **Note:** ab glyph me koi **badge** nahi hai. Pehle wala corner badge 46dp icon ke saath overlap kar raha tha (square plate me corner badge ka koi natural jagah nahi), aur waise bhi card ka title already media-kind bata deta hai.

### 18.3 Isolate Voice — naya tool + 3 rounds of fixes

`isolate_voice` AUDIO section me add hua (ab **16 tools**). Approach: mid/side center-channel, **koi AI model nahi** — app offline hai aur Demucs ~80 MB hota. **[REMOVED 2026-10-27]— ye section sirf history ke liye hai, dekho §18.12.**

```
mid  = (L + R) / 2   → vocals + jo bhi beech me hai
side = (L - R) / 2   → do-taraf wale instruments
```

**Bug 1 — "Remove voice pe error".** Filter graph me `stereotools=mlev=0.008:slev=1.0` tha. `stereotools` shipped `libavfilter.so` me **present** hai (byte-scan: `mlev`, `slev` dono null-terminated option strings maujood), phir bhi command exit code 1 deti thi aur UI `Error: failed(1): unknown error` dikhata tha — kyunki app ke paas FFmpeg log callback nahi hai, stderr kahin capture nahi hota. Fix: `stereotools` **poora hata diya**, wahi `pan` use kiya jo `keep` branch me pehle se chal raha tha (proven syntax), sirf `+` → `-`:

```
pan=stereo|c0=0.5*c0-0.5*c1|c1=0.5*c1-0.5*c0
```

**Bug 2 — "Keep voice me instruments bhi aa rahe the".** Ye bug nahi, approach ki limit thi: `(L+R)/2` sirf **center** nikalta hai, aur pop mix me bass, kick, snare, lead synth sab bhi center hote hain. Pehla fix: mid ke baad vocal-band chain.

**Bug 3 — "vocal alag nahi ho raha, music bhi nahi hat raha" (FINAL tuning).** Do changes:

1. **Sirf naya build phone par nahi tha.** Verify kiya: installed APK ka MD5 == disk APK ka MD5 == **purane build (21:28:54)** ka hash. Yaani user purane build par test kar raha tha jisme `stereotools` wali broken graph thi. Naya APK (md5 `4EB8D3D5…`) install karke confirm kiya.
2. **Vocal band ko aur tight kiya** (pehle: highpass 170 / lowpass 7000 / 250 Hz −5 dB / 3 kHz +4 dB):

| stage | pehle | ab | kyun |
|---|---|---|---|
| highpass | 170 Hz | **150 Hz** | bass/kick/tabla ki ghusli |
| low-mid cut | 250 Hz −5 dB | **180 Hz −8 dB** + **380 Hz −6 dB** | guitar/snare ka body ("mud") 2 alag bands me dabaya |
| presence | 3 kHz +4 dB | **2.4 kHz +6 dB** | vocal ki main clarity band |
| consonants | — | **5.2 kHz +3 dB** | "s", "t", "ch" ki saafai |
| lowpass | 7 kHz | **9 kHz** | thodi si air bachi, cymbal khud hat jaata hai |
| volume | 2.6 | **3.2** | zyada cut ke baad level wapas |
| remove: extra | — | **2.6 kHz −4 dB** | wide/dual-mono vocal ka bacha presence bhi dabane ke liye |

Sab filters **core** hain (`pan`, `highpass`, `lowpass`, `equalizer`, `volume`, `alimiter`) — sab byte-scan me present confirm. `afftdn` available hai par music par artifacts banata hai isliye nahi lagaya.

**Honest limit (UI me bhi likhi hai):** ye true AI separation nahi hai. Mono file par kuch nahi hota (`L−R = 0` → remove mode me silence), aur stereo me bhi center instruments (bass, kick, snare, lead) poori tarah gayab nahi hote — sirf daba jaate hain. `subt_isolate_voice` 10 locales me badla: `"Separate the voice from the music (best on stereo songs)"`.
### 18.4 Settings — "Where files are saved" (naya section)

`section_output_folders` — 4 highlighted rows, har row tinted rounded background + colour-coded icon
(`Movie`/`MusicNote`/`Image`/`Folder`) + actual path + right side "Open" `OutlinedButton`:

| Row | Icon | Path (device par confirm) |
|---|---|---|
| Videos | `Movie` | `Movies/NovaConverter` |
| Audio | `MusicNote` | `Music/NovaConverter` |
| Images | `Image` | `Pictures/NovaConverter` |
| Other files | `Folder` | `Download/NovaConverter` |

Path seedha `FileActions.savedFolder(mime)` se aata hai — yaani settings me jo dikhta hai wahi save code
use karta hai, drift **impossible**. `openOutputFolder` lambda `FileActions.openFolder()` call karta hai
aur sirf `FolderOpen.COPIED` par `folder_path_copied` snackbar dikhata hai.

### 18.5 About trim + legal rewrite

- **About** me ab **sirf version** (`Version 1.0 (1)`) dikhta hai. `about_engine`, `about_outputs`,
  `about_no_internet` teeno keys **saare 10 locales se delete** (verify: 0 leftover, 0 dupes).
- **Privacy Policy + Terms of Use buttons jaan-boojh kar rakhe** — inke bina legal screens unreachable
  ho jatin aur Play listing reject hoti. User ne "baaki text hata do" kaha tha; ye exception batayi gayi.
- **Bodies 10 locales me rewrite** — human tone, plain language, sections ke saath.
- Claims **manifest + code se verify karke** likhe: manifest me `android.permission.INTERNET`
  **bilkul nahi** hai (isliye "kuch bhi internet par nahi jata" claim 100% verifiable hai), aur original
  file kabhi delete/overwrite nahi hoti (sirf `FastCompressor.kt:60` aur `RealEsrgan.kt:152` output path
  par `.delete()`).
- **AAPT2 gotcha** jo baar-baar maara: `<string>` ke andar literal CR/LF allowed nahi
  (`Invalid unicode escape sequence in string`). Saare bodies single-line + `\n` + `\'` escapes me
  normalize kiye (`normalize_bodies.ps1`).
- Bengali / Indonesian / Tamil `terms_body` me cross-language leak (Hindi/Chinese/Kannada text) mila —
  teeno poore dobara likhe. Verify: 0 `?` corruption, 0 double-apostrophe, 0 U+FFFD, koi BOM nahi.

### 18.6 Verification

| Check | Natija |
|---|---|
| `:app:compileDebugKotlin` | ✅ BUILD SUCCESSFUL (sirf purane auto-mirrored deprecation warnings) |
| `:app:assembleRelease` | ✅ 3 baar BUILD SUCCESSFUL (icon build + DSP build) |
| **Naya APK sach me phone par?** | ✅ **haan** — MD5 match se pakda: `pm path` ka md5 == disk APK ka md5. User pehle **purane build** (21:28:54) par test kar raha tha, isliye Isolate Voice ki complaints purani broken graph ki thi |
| Naya build install + launch | ✅ `install -r -d` Success, process alive (pid 18924), crash buffer khaali |
| Naye DSP ka static proof | ✅ APK `classes.dex` me `equalizer=f=180:…-8`, `f=5200:…+3`, `lowpass=f=9000`, `f=2600:…-4` PRESENT; purana `stereotools` aur `highpass=f=170` **absent** |
| Icons ke imports | ✅ `Tools.kt` me use hone wale saare 14 icon import, 0 missing, 6 dead imports hataaye |
| Grid geometry (device) | ✅ 16 tools, 3 columns x=14/128/242, tile 104×84dp |
| Settings output folders (device) | ✅ 4 rows + sahi paths + "Open" |
| Open folder (device) | ✅ DocumentsUI `asu > Music > NovaConverter`, asli files |
| Privacy Policy screen (device) | ✅ poora text, saare sections |
| Hero overflow bug | ✅ **pakda aur theek kiya** — device measurement se pata chala content `234dp` tha `190dp` box me, jis se "Start now" CTA card ke bahar clip ho raha tha. Plate 100→(84) dp, padding 18→14, spacers 14/4/14→10/2/10 |
| **Naya diagonal 2-icon design** (device) | ✅ **installed + render verified** — home par 16/16 tools render, koi crash nahi (pid 18723, crash buffer khaali) |
| **Hero overflow fix** | ✅ **device par confirm** — "Start now" CTA ab dump me dikhta hai (y=905..953); pehle poora absent tha |
| **Cards chhote** (tile 84→78dp) | ✅ **device par confirm** — 'Video Converter' title y=1487..1535 → y=1469..1517, exactly 18px = 6dp up |
| Notification rate limit fix | ✅ code me 3 changes (foreground skip + fixed IDs + counter); quota **khud** device par nahi test kiya | — phone ka notification shade ek OEM bug (`mCurrentFocus=NotificationShade`, har tap/swatch/`statusbar collapse` ignore) me atak gaya; `uiautomator dump` sirf shade ke nodes deta hai |
| **Isolate Voice ka actual conversion** | ⚠️ **pending** — naya DSP APK bana hai (md5 `4EB8D3D5` family) par USB disconnect ho gaya, install nahi hua |
| Terms of Use screen (device) | ⚠️ pending |
| Launcher adaptive icon | ⚠️ pending (pichle rounds se) |
| Audio speed 0.5x / 2x / 100x | ⚠️ pending (pichle rounds se) |

**Device notes (is round):**
- `adb install` ke waqt `com.hoffnung` (Infinix ka scanner) upar aa jaata hai aur ek OEM dialog *"Nova Converter requests to display an overlay on the lock screen"* dikhata hai jo **kisi bhi tap se nahi hata**. App `SYSTEM_ALERT_WINDOW` declare hi nahi karta — ye purely OEM behaviour hai. Fix jo kaam aaya: `pm grant com.nova.converter android.permission.POST_NOTIFICATIONS` + `am force-stop com.hoffnung`.
- Uske baad notification shade **stuck** ho gaya: `mCurrentFocus=Window{u0 NotificationShade}`, aur `input keyevent 3/4`, `input swipe`, `cmd statusbar collapse`, `am force-stop com.android.systemui`, `pkill systemui` — kuch nahi chala. `pkill` pe "Operation not permitted". App process zinda tha aur uska window stack me tha (`Window #15`), bas shade upar atki thi. Fix: phone reboot ya shade manually band karna.
- `pidof` se confirm kiya app crash nahi hua — crash buffer khaali tha.

### 18.7 Ek tooling bug jo mila (dhyan rakhne layak)

Is round me **do baar** source file ek `edit` ke baad **trailing NUL bytes** ke saath likhi gayi:
`HomeScreen.kt` (+42 NUL) aur `AUDIT_REPORT.md` (+10598 NUL). Kotlin compiler ne
`Expecting a top level declaration` diya aur `assembleRelease` fail hui; report me likha hua §18
bhi isi wajah se gum ho gaya. Pehchan aasan hai:

```powershell
$b=[IO.File]::ReadAllBytes($f); $b[$b.Length-1] -eq 0   # trailing NUL = corrupt
```

Fix: trailing NULs strip karke file dobara likho (aur uske baad har file pe NUL-scan chalao).
Bade text blocks edit karne ke baad ye check zaroor karna.

### 18.8 Khuli decisions

**Real-ESRGAN models APK me nahi hain.** `realesrgan-x4plus.bin` + `.param` (~64 MB) missing hain —
binaries (`assets/ai/{arm64-v8a,x86_64}/realesrgan-ncnn-vulkan`, 13.9 + 23.6 MB) present hain par models
nahi. User ko chunna hai:
(a) APK me bundle karo (size ~94 MB → ~158 MB), ya
(b) pehli baar use par on-demand download `<filesDir>/ai/` me.

### 18.9 "5 notifications per day" — ye system limit hai, app nahi hata sakti

Phone par aaya notification: **"File completed — 5 notifications per day"** aur **"Task failed — 3 notifications per day"**.

**Ye app ka nahi, Android ka system-level rate limit hai** (per-app daily notification quota / cooldown).
Koi bhi app ise apni taraf se disable ya badha nahi sakti — na `NotificationChannel` se, na
`POST_NOTIFICATIONS` permission se. Ise sirf **kam notification bhejke** avoid kiya ja sakta hai.

**Asli wajah:** `Notifications.completed()` har job ke liye **naya unique id** use kar rahi thi
(`manager(app).notify(nextId.getAndIncrement(), …)`). 10 file convert karne par 10 alag
notifications — jo batch me hoti hain wahi quota kha jaati thi.

**3 changes (is build me hain):**

| # | Change | Asar |
|---|---|---|
| 1 | **App foreground me ho to "File completed" post hi nahi** (`if (appOnScreen) return`) | User ko result screen par dikh hi raha hota hai. Batch me 20 conversions par sirf 1–2 notification bachte hain — sabse bada hissa |
| 2 | **Done/fail ke liye fixed id** — `ID_DONE = 9101`, `ID_FAIL = 9102` (pehle `nextId++`) | Notification update hoti rehti hai, shade bhar-dhar nahi hota, 10 failures = 10 alag notifications nahi |
| 3 | Notification par **run counter** (`x2`, `x3`…) | User ko pata chalta hai kitne files bane |

Foreground tracking ke liye `Notifications.appOnScreen` (`@Volatile var`) add kiya aur
`MainActivity.onResume()` / `onPause()` se set kiya — koi extra dependency nahi.

**Failures foreground me bhi post hoti hain** — sirf "done" wali skip hoti hai, kyunki error
user ko turant batana chahiye.

> ℹ️ Settings me "Notification without sound" pehle se maujood hai (silent channel) — agar user
> notifications hi nahi chahta to wo toggle off kar sakta hai.
### 18.10 Grid cards chhote + har icon par highlighted plate (FINAL design)

User feedback ke do parts:

> *"jo top per main cards hain usnko chod kar jo niche cards hain unko aur chota karo aur har card k icon ka card badge highlight karo, upar aur niche sab wale har theme me perfect lage"*

**Pehle** grid tile 84dp tha aur do **plain icons** (koi plate nahi) the — card ka koi apna colour block nahi tha, sirf do icons. Ab:

| Card | pehle | ab | plates | icon |
|---|---|---|---|---|
| grid tile | 104 × **84dp**, tint gradient | 104 × **68dp**, neutral surface | **44dp** | 30dp |
| small card | 101 × 50dp, tint plate | 101 × **44dp**, neutral | **28dp** | 19dp |
| hero | 100 × 76dp, white plate | **72dp**, white plates | **42dp** | 29dp |

Grid row gap 12→10dp, tile-title gap 8→6dp.

#### Highlighted plate

`ToolGlyph` me har icon ab ek **solid tint wale rounded-square plate** par hai — bilkul waise jaise
example app icon me note orange square par aur reel purple square par baithe hain:

```
+--------+ . +--------+
|  kind  |'..|   op   |
+--------+  +--------+
```

Plate `RoundedCornerShape(size * 0.30f)`, dono plates diagonal offsets (x ±0.46·size, y ±0.12·size)
me — composition dono corners use karta hai.

#### Har theme me kaise theek hai

Ye design **theme se independent** hai, isliye light + dark + AMOLED + har accent wale theme me
same behaviour:

1. **Plate ka colour = tool ka apna tint (solid)** — card ka background `colorScheme.surface`
   hai, to plate hamesha background se alag dikhta hai. Pehle tile khud bhi tint gradient tha,
   isliye same-hue plate blend ho jaati.
2. **Icon ka colour runtime pe chuna jaata hai** — `contrastOn(color)` WCAG relative luminance
   dekhta hai: luminance > 0.45 → **dark icon** (`0xFF10142B`), warna **white icon**.
   Hamare 8 hues me ye decide karta hai:
   - white icon → red, purple, blue, pink, violet (`0xFFFF5A5A`, `0xFF7C6CFF`, `0xFF4F8CFF`, `0xFFFF7AB6`, `0xFFA78BFA`)
   - **dark icon** → amber `0xFFFFB020`, green `0xFF36D399`, cyan `0xFF4FC3F7` (ye bright hain, white icon unpar weak contrast deta)
3. **Card border theme-adaptive** — pehle `Color.White.copy(alpha = 0.08f)` tha jo light theme me
   **invisible** ho jata tha. Ab `colorScheme.onSurface.copy(alpha = 0.10f)` — dark theme me light
   border, light theme me dark border.
4. **Hero** ka background khud tool ka gradient hai, isliye wahan plate **white translucent**
   (`alpha = 0.24f` + white `0.35f` border) + white icons — har hue par safe.

#### Verification (device)

| Check | Natija |
|---|---|
| Grid title position (78dp → 68dp build) | ✅ `y=1469..1535` → `y=1415..1481`, 54px = 18dp up (10dp tile + 2dp title gap + 2dp row gap + section spacer) |
| Saare 9 visible cards render | ✅ Video to Audio, Audio Cutter, Video Cutter, Video Converter, Video Compressor, Video Speed, Video Merger, Video to GIF, Frame Capture — sab present |
| "Start now" CTA | ✅ ab bhi render (hero overflow fix pichle build me apply hua tha) |
| Crash | ✅ crash buffer khaali, `mCurrentFocus=com.nova.converter/.MainActivity` |
| Install | ✅ `install -r -d` Success, MD5 `693887CA…` == disk MD5 |

> ⚠️ **Colour visually verify nahi hua** — is model me image input nahi hai, sirf `uiautomator`
> bounds milte hain. Contrast ka guarantee **structural** hai (solid plate + luminance-derived
> icon colour), computation app me hi hoti hai, isliye har theme me same rahegi.

### 18.11 Ek galti jo maine ki (aur kaise pakdi)

`ToolGlyph.kt` me `fun contrastOn(c: Color): Color =` **expression-body** function hai — iska
koi closing `}` nahi hota. Maine `read` output ka last line dekh kar use "truncated" samajh liya
aur ek extra `}` jod diya → `Expecting a top level declaration`.

Sabse badi sabaq: **truncation ka matlab "last line `}` nahi hai" nahi, balki "brace count
balanced nahi hai" hona chahiye.** Har edit ke baad ab ye 3 check chalane ka rule hai:

```powershell
$b=[IO.File]::ReadAllBytes($f); $n=0; foreach($x in $b){if($x -eq 0){$n++}}   # NUL = 0
$t=[IO.File]::ReadAllText($f); ([regex]::Matches($t,'\{')).Count -eq ([regex]::Matches($t,'\}')).Count  # braces
.\gradlew.bat :app:compileDebugKotlin --offline --rerun-tasks                # actual compile
```

Isi build me ek aur catch hua: pehla `:app:compileDebugKotlin` **BUILD SUCCESSFUL** dikha diya
**bina `--rerun-tasks` ke** — incremental ne use up-to-date samajh liya tha. Isliye ab
verification ke liye `--rerun-tasks` use hota hai (poora ~2.5 min, par sach bolta hai).
### 18.12 Isolate Voice feature + model hataya (2026-10-27)

User ne bola: **"voice isolate wala feature aur uska code aur model hata do"** — kyuki vocal isolation kaam nahi kar raha tha (na voice alag hota tha, na music).

User ne ye decision deliberately liya — ML vocal-separation ka research kiya gaya tha (MDX23C / ONNX model), par user ne woh raasta nahi chaha, feature ka khatma chuna.

**Jo kuch hataya:**

| Cheez | Pehle | Ab |
|---|---|---|
| Tool | `isolate_voice` (Tools.kt) | **gaya** |
| Kotlin code | `core/VocalSeparator.kt` (460 lines) | **file delete** |
| Engine wiring | `Engine.kt` — `AiEngine.cancel("vocal-sep")`, `runVocalSeparation()`, `sideName()`, `object FFmpegRunner` (49 lines, sirf vocal-sep use karta tha) | **gaya** |
| FFmpeg builder | `Commands.isolateVoice()` + private `bitrateFor()` | **gaya** |
| Model asset | `assets/mdx23c_int8.onnx` (**66,759,214 bytes** = ~64 MB) | **file delete** |
| Gradle deps | `com.microsoft.onnxruntime:onnxruntime-android:1.20.0`, `com.github.wendykierp:JTransforms:3.1` | **gaye** |
| Build config | `aaptOptions { noCompress += listOf("onnx") }` | **gaya** |
| ProGuard | ONNX + JTransforms keep rules (10 lines) | **gaye** |
| Strings | 7 keys — `tool_isolate_voice`, `subt_isolate_voice`, `field_voice_mode`, `voice_mode_vocal`, `voice_mode_accompaniment`, `voice_mode_keep`, `voice_mode_remove` | **gaye** (saari 11 locales me se) |
| ToolList | `ToolDetailScreen.kt` rec_audio branch se `"isolate_voice"` | **gaya** |
| Icon import | `Icons.Filled.RecordVoiceOver` | **gaya** |

**Jo JAAN-bujhkar rakha:**

- `RealEsrgan` + `assets/ai/<abi>/realesrgan-ncnn-vulkan` — ye image upscale ka alag feature hai (`image_converter` me kaam karta hai).
- equalizer ka `"vocal"` preset (`AudioPlayerX`, `Settings`) — woh audio EQ preset hai, voice isolation se unrelated.
- `Commands.q()`, `Commands.AUDIO_TARGETS`, `FlowKind`, `AiEngine`, `Outputs` — baaki tools use karte hain.

**Verify:**

- Signed release build: `gradlew assembleRelease` (dekho §0 pending table).
- APK ~64 MB chhota (model + onnxruntime/jtransforms native libs chale gaye).
- `assets/` me sirf `ai/` (Real-ESRGAN) bacha.
- Grep sweep `vocal-sep|VocalSeparator|FFmpegRunner|isolate_voice|isolateVoice|onnx|jtransforms` → **clean**.

---

## Appendix — audit method

- Source read: `Tools.kt`, `Commands.kt`, `Engine.kt`, `FastCompressor.kt`, `Settings.kt`, `ProgressBus.kt`, `MainActivity.kt`, `SettingsScreen.kt`, `HomeScreen.kt`, `ToolDetailScreen.kt` (partial), `AndroidManifest.xml`, `build.gradle.kts`, `proguard-rules.pro`, `strings.xml`, `shortcuts.xml`, `file_paths.xml`
- Binary verification: `ffmpeg-kit-full-gpl-7.1.6.aar` **and** shipped `app-release.apk` → byte-level ASCII scan of all `lib/*/libav*.so` for `drawtext`, `boxborderw`, `libwebp`, plus full `--enable-*` configure string extraction
- Gradle outputs: merged manifest, lint-vital report, APK zip entry listing (343 entries, no `res/raw`)
- Play policy: confirmed against `developer.android.com/google/play/requirements/target-sdk` (Aug 31 2026 → API 36)
- Subagent transcripts recovered from `~/.local/share/opencode/opencode.db` (both were stalled with `task` parts stuck at `status=running`)
