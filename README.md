# ClipCutter

Pick a long video, mark multiple clip ranges on a real timeline, and batch-export them
all at once (max 720p, real hardware H.264, ready to manually upload to Instagram/YouTube).

- Repo: https://github.com/tranquilizer014/ClipCutter
- GitHub profile: https://github.com/tranquilizer014
- Telegram channel: https://t.me/tranquilizer014
- Reddit profile: https://www.reddit.com/u/tranquilizer014
- Subreddit: https://www.reddit.com/r/tranquilizer/

## Features

- Pick a video from Files or your Gallery (mp4, mov, mkv, 3gp, and more).
- Real video preview (ExoPlayer) with a thumbnail filmstrip above the seek bar.
- Playback speed control (0.5x–2x) and ±5s / ±10s nudge buttons.
- Mark a clip's start/end by scrubbing and tapping buttons, **or** type the times
  directly as `mm:ss`.
- Tap any clip in the list to preview just that range; tap **Edit** to adjust it.
- Batch export all marked clips at once, named `{your name} clip 1.mp4`,
  `{your name} clip 2.mp4`, etc.
- Cancel an export mid-batch.
- Your clip list is saved automatically and restored if the app is closed or
  backgrounded (see the caveat about Gallery-picked videos below).
- In-app **Info** screen with usage instructions and links.
- No captions, no auto-upload — deliberately out of scope for this version; export,
  then share/upload manually.

## Getting the APK (CI build)

1. Fork or clone this repo, or push it as-is to your own GitHub repo.
2. Go to the **Actions** tab — the workflow (`Build ClipCutter APK`) runs automatically
   on push, or trigger it manually via "Run workflow".
3. **This first run can take 1-3 hours** — it compiles FFmpeg from source (not just the
   app) because that's the only free way to get real hardware H.264 encoding; the free
   prebuilt library only ships VP9/AV1.
4. Download the artifact from the finished run:
   - `ClipCutter-debug-apk` — the installable APK, if the build succeeded.
   - `ffmpeg-kit-build-logs` — only appears on failure; useful for debugging.
5. Install on your phone (you'll need to allow "install from unknown sources" since
   it's unsigned).

### Known risk points if the CI build fails

Compiling FFmpeg natively is fragile. Likely culprits, in order:
1. **Disk space** on the free GitHub runner (~14GB usable) — the workflow already
   cleans up unused preinstalled tooling and restricts the build to arm64-v8a only.
2. **`android.sh` not found at the assumed path** — the upstream repo's structure has
   shifted before; the workflow now searches the whole cloned tree for `android.sh`
   rather than assuming a fixed location.
3. **The output `.aar` isn't where expected** — same fix, the workflow searches for
   any produced `.aar` rather than assuming a path.
4. **A missing apt package** the native build script needs — the failure log will
   name it directly.

## Building it yourself, locally (instead of relying on CI)

If you'd rather build on your own machine (faster iteration, easier debugging than
waiting on a multi-hour CI run):

**Requirements:**
- Linux or macOS (the FFmpeg build scripts don't support native Windows — use WSL2 if
  you're on Windows).
- Android Studio (latest stable) with SDK Platform 35 and Android NDK `26.2.11394342`
  installed via SDK Manager.
- ~15GB free disk space for the native FFmpeg build.
- JDK 17.

**Steps:**

1. Clone this app repo:
   ```
   git clone https://github.com/tranquilizer014/ClipCutter.git
   cd ClipCutter
   ```

2. Clone and build FFmpegKit from source, with hardware H.264 enabled:
   ```
   git clone --recurse-submodules https://github.com/ffmpegkit-maintained/ffmpeg.git ffmpeg-kit-src
   cd ffmpeg-kit-src
   find . -iname "android.sh"   # confirm where it landed
   ```
   `cd` into whichever directory that `find` reports, then run:
   ```
   export ANDROID_NDK_ROOT=<path to your installed NDK 26.2.11394342>
   chmod +x android.sh
   ./android.sh \
     --enable-android-media-codec \
     --enable-android-zlib \
     --disable-arm-v7a \
     --disable-arm-v7a-neon \
     --disable-x86 \
     --disable-x86-64
   ```
   This step alone can take well over an hour depending on your machine.

3. Find the resulting `.aar` file:
   ```
   find . -iname "*.aar"
   ```
   Copy it into `ClipCutter/app/libs/ffmpeg-kit-release.aar` (create the `libs` folder
   if it doesn't already exist).

4. Open the `ClipCutter` folder in Android Studio, let it sync Gradle, then
   **Build > Build Bundle(s) / APK(s) > Build APK(s)**.

5. The debug APK will be under `app/build/outputs/apk/debug/`.

### If the native build won't cooperate on your machine

Two fallbacks, both far simpler than compiling from source:
- Swap the dependency in `app/build.gradle` to
  `dev.ffmpegkit-maintained:ffmpeg:8.1.7` (free, Maven Central, no build step) —
  but it only supports VP9/AV1 encoding, not H.264, so exports come out as `.webm`.
  YouTube accepts this fine; Instagram may not.
  If you use this route, also change the `-c:v h264_mediacodec` flag in
  `MainActivity.kt`'s export command to `-c:v libvpx-vp9`, and change the output
  file extension from `.mp4` to `.webm`.
- Buy the maintainer's one-time $24 Basic tier AAR, which includes real H.264
  hardware encoding as a direct drop-in dependency — no source compile needed at all.
