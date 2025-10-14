# File Descriptions

This document provides a detailed breakdown of the files in the MadeMeDance project, explaining the purpose of each file and how it fits into the overall architecture.

## Root Directory

### .gitignore
Specifies files and directories that should be ignored by Git. This includes build artifacts, local configuration files, and IDE-specific files.

### AGENTS.md
Provides guidance for AI agents working on the project, outlining the architecture, development conventions, and other important information.

### LICENSE
Contains the Unlicense, which dedicates the software to the public domain.

### README.md
The main documentation for the project. It provides an overview of the application's functionality, instructions for building and running the app, and a to-do list for future development.

### ShazamKit-Android-doc-2.1.1.zip
A zip archive containing the documentation for the ShazamKit Android SDK, which is a key component of the application.

### build.gradle.kts
The top-level Gradle build file. It configures the Android and Kotlin plugins for the project.

### gradle.properties
Contains project-wide Gradle settings, such as JVM arguments for the Gradle daemon.

### gradlew & gradlew.bat
Gradle wrapper scripts for Unix-like systems and Windows, respectively. They allow the project to be built without requiring a local Gradle installation.

### settings.gradle.kts
Declares the project's modules. In this case, it includes the `app` module.

## `app` Module

### app/.gitignore
Specifies that the `build` directory within the `app` module should be ignored by Git.

### app/build.gradle.kts
The build script for the `app` module. It configures the Android application, including the SDK versions, application ID, and build types. It also declares the dependencies for the application, such as Jetpack Compose for the UI, Apache Commons Math for FFT calculations, and testing libraries.

### app/proguard-rules.pro
Contains rules for ProGuard, which is used to shrink, obfuscate, and optimize the application's code for release builds.

### app/src/androidTest/java/com/hereliesaz/mademedance/ExampleInstrumentedTest.kt
An instrumented test that runs on an Android device to verify that the application's context is set up correctly.

### app/src/main/AndroidManifest.xml
The manifest file for the Android application. It declares the necessary permissions (body sensors, storage, and record audio), the main activity, and other essential information about the application.

### app/src/main/java/com/hereliesaz/mademedance/AudioBpmDetector.kt
This class is responsible for detecting the BPM of the surrounding audio. It uses the device's microphone to record audio, and then processes the audio data using a Fast Fourier Transform (FFT) to identify the dominant frequency, which is then converted to BPM. It also includes functionality to save a snippet of the recorded audio.

### app/src/main/java/com/hereliesaz/mademedance/MainActivity.kt
The main entry point of the application. This activity sets up the UI using Jetpack Compose, manages the gyroscope sensor for movement detection, and coordinates the `AudioBpmDetector` and `RhythmDetector`. It also handles the logic for checking for a match between the movement and audio BPMs, and for saving audio snippets when a match is found.

### app/src/main/java/com/hereliesaz/mademedance/RhythmDetector.kt
This class is responsible for detecting the user's movement BPM. It uses the device's gyroscope sensor to capture movement data, and then processes that data using a Fast Fourier Transform (FFT) to identify the dominant frequency of the movement, which is then converted to BPM.

### app/src/main/java/com/hereliesaz/mademedance/ui/ClipListScreen.kt
A Jetpack Compose screen that displays a list of the audio clips that have been saved. The user can select a clip to either play it back or to open a Google search to identify the song.

### app/src/main/java/com/hereliesaz/mademedance/ui/MainScreen.kt
The main UI of the application, built with Jetpack Compose. It displays the real-time movement and audio BPMs, the system status (e.g., "Listening...", "BPM Match Found!"), and provides buttons for the user to grant microphone permissions and to navigate to the `ClipListScreen`.

### app/src/main/java/com/hereliesaz/mademedance/ui/theme/Color.kt
Defines the color palette used in the application's theme.

### app/src/main/java/com/hereliesaz/mademedance/ui/theme/Theme.kt
Defines the overall theme for the application using Jetpack Compose's `MaterialTheme`.

### app/src/main/res/
This directory contains all of the application's resources.
-   **drawable/**: Contains XML drawables for the launcher icon.
-   **mipmap-**\***/**: Contains the launcher icons in various densities.
-   **values/**: Contains XML files for colors, strings, and themes.
-   **xml/**: Contains XML files for backup rules and data extraction rules.

### app/src/test/java/com/hereliesaz/mademedance/ExampleUnitTest.kt
A local unit test that runs on the development machine. It includes a simple test to ensure that the testing framework is set up correctly.
