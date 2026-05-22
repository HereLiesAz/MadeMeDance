# MadeMeDance

An Android app that identifies songs by matching your body's rhythm to the music's beat. Dance to a song, and when your movement BPM matches the music's BPM, the app automatically saves a 15-second audio clip you can use to identify the song.

## How It Works

1. The app detects your **movement BPM** via the gyroscope sensor
2. Simultaneously detects the **music BPM** via the microphone
3. When both BPMs match (within 5 BPM tolerance), a 15-second audio snippet is saved
4. You can review saved clips and identify songs via Google Search

## Architecture

```
mademedance/
├── MainActivity.kt              # Thin shell: permissions, lifecycle, Compose UI
├── MainViewModel.kt             # Business logic, state management, coordination
├── AudioBpmDetector.kt          # FFT-based beat detection on microphone audio
├── RhythmDetector.kt            # FFT-based movement BPM from gyroscope
├── data/
│   └── ClipRepository.kt       # File-based clip management (list, delete)
├── sensor/
│   └── MovementTracker.kt      # Gyroscope sensor wrapper, exposes BPM StateFlow
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
