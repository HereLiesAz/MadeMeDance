# MadeMeDance

An Android app that identifies songs by matching your body's rhythm to the music's beat. Dance to a song, and when your movement BPM matches the music's BPM, the app automatically saves a 15-second audio clip you can use to identify the song.

## How It Works

1. The app detects your **movement BPM** via the accelerometer (so it works with the phone in your pocket)
2. Simultaneously detects the **music BPM** via the microphone
3. When both BPMs match (within 5 BPM tolerance), a 15-second audio snippet is saved
4. You can review saved clips and identify songs via Google Search

It runs as a background **foreground service**, so you can pocket the phone and keep dancing — no need to keep the app open. The cheap accelerometer runs continuously, but the **microphone only switches on once dancing is detected** and switches off again a few seconds after you stop — saving battery and keeping the mic off while you're still.

### Tuning sensitivity

- A **sensitivity knob** on the main screen sets how much movement counts as dancing (lower for vigorous dancing, higher for subtle motion).
- Rating a saved clip **"False alarm"** nudges sensitivity down automatically (one-way — the app never raises it on its own; you do that with the knob).
- The service watches its own **battery drain** and, when consumption is high, enters a power-saving mode that reduces sensitivity and stretches the audio-processing interval.

## Architecture

```
mademedance/
├── MainActivity.kt              # Thin shell: permissions, lifecycle, Compose UI
├── MainViewModel.kt             # Business logic, state management, coordination
├── AudioBpmDetector.kt          # FFT-based beat detection on microphone audio
├── RhythmDetector.kt            # FFT-based movement BPM from accelerometer
├── data/
│   └── ClipRepository.kt       # File-based clip management (list, delete)
├── sensor/
│   └── MovementTracker.kt      # Accelerometer sensor wrapper, exposes BPM StateFlow
└── ui/
    ├── MainScreen.kt            # Pulse ring animation, match proximity bar
    ├── ClipListScreen.kt        # Clip list with playback, deletion, identification
    └── theme/
        ├── Theme.kt             # Material 3 with dynamic colors
        └── Color.kt
```

## Tech Stack

- **Kotlin** + **Jetpack Compose** (Material 3)
- **AndroidX ViewModel** + **StateFlow** for reactive state
- **Apache Commons Math3** for FFT (Fast Fourier Transform)
- **Jetpack Navigation Compose** for screen routing
- minSdk 24 (Android 7.0) / targetSdk 35

## Building

Requires Android Studio and the Android SDK.

```bash
./gradlew assembleDebug     # Build debug APK
./gradlew testDebugUnitTest # Run unit tests
```

## Permissions

- `RECORD_AUDIO` — microphone access for music BPM detection

## License

[Unlicense](LICENSE) — Public Domain
