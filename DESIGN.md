### 📝 PlayMemoryShutter: Engineering Design Document

| Field | Details |
| :--- | :--- |
| **Project Name** | PlayMemoryShutter |
| **Target Environment** | Sony a7S (Original) running PlayMemories Camera Apps (PMCA) subsystem (Android API 10 / 15) |
| **Core Objective** | Intercept physical shutter (`ISV_KEY_S2`) and video record (`ISV_KEY_STASTOP`) buttons to remap their functionalities based on user configuration and camera mode dial state. |
| **Primary APIs** | OpenMemories Framework, Sony Scalar API (`CameraEx`, `ScalarInput`) |
| **Language** | Java (Android SDK) |
| **License** | GNU GPL v3.0 |

---

### 1. Overview & Goals

The Sony a7S lacks native menu options to fully remap hardware buttons, specifically the shutter and the hard-to-reach movie record button. PlayMemoryShutter acts as a lightweight background utility that intercepts these hardware input signals and programmatically dispatches alternative actions.

**Core Features:**
- **Shutter Button Remapping:** Map the physical shutter button to toggle video recording.
- **Context-Aware Overrides:** Configure the shutter remapping to apply *only* when the mode dial is in "Movie" mode, or universally across *all* shooting modes.
- **Video Button Remapping:** Map the dedicated video record button to act as a standard shutter button for taking still photos.
- **Auto-Start on Boot:** Automatically start the background service when the camera powers on.
- **Configurable Failsafe Delay:** To prevent race conditions with native firmware and avoid bootloops, the auto-start feature includes a user-configurable delayed launch (e.g., wait 5, 10, or 15 seconds after boot).

**Success Criteria:**
- App installs and launches via PMCA-RE without crashing.
- Remapping configurations are reliably saved and persist across camera reboots.
- Input interception respects the user's mode-dial preferences without interfering with standard camera functions when disabled.
- Auto-start safely initializes the service *after* the native camera software is fully booted.

---

### 2. System Architecture & Framework Strategy

The OpenMemories Framework uses a **dual-implementation strategy** to abstract platform differences between standard Android and Sony's proprietary hardware APIs. 

- **Development Target:** Built using standard legacy Android APIs for UI layout and `SharedPreferences`, enabling testing on standard Android emulators.
- **Deployment Target:** At runtime, the framework routes hardware control calls to the Sony-specific `CameraEx` API and the proprietary `ScalarInput` system.

#### 2.1 Component Breakdown
1. **`MainActivity`:** A minimal UI allowing the user to configure button behaviors, enable auto-start, and set the failsafe delay. Settings are persisted using `SharedPreferences`.
2. **`BootReceiver`:** A `BroadcastReceiver` listening for `android.intent.action.BOOT_COMPLETED`. It schedules the background service to start after the configured delay.
3. **`ShutterInterceptService`:** An Android `Service` that runs in the background to listen for hardware input events.
4. **`CameraController`:** A wrapper class around `com.sony.scalar.hardware.CameraEx` responsible for handling recording modes, checking the mode dial state, and triggering capture actions.
5. **`InputHandler`:** A listener class that monitors `ScalarInput` events and translates key presses (`ISV_KEY_S2`, `ISV_KEY_STASTOP`) into appropriate actions via the `CameraController`.

---

### 3. Technical Implementation Details

#### 3.1 Hardware Input Interception (`ScalarInput`)
According to the Sony API stubs, the `ScalarInput` class defines the constants for hardware buttons:
- **`ISV_KEY_S2` (Value: 518):** Shutter full-press.
- **`ISV_KEY_STASTOP` (Value: 515):** Video record start/stop.
- **`ISV_KEY_S1_1` (Value: 516):** Shutter half-press (Autofocus/Meter).

*Design Choice:* The app will intercept these raw keycodes before they reach the native firmware, consulting the user's `SharedPreferences` to determine the mapped action.

#### 3.2 Camera Control (`CameraEx`)
The `CameraEx` class is the primary hardware abstraction interface for Sony cameras. 
- **Initialization:** The camera is opened via `CameraEx.open()` and must be released with `CameraEx.release()` when the service is destroyed to prevent locking the device.
- **Executing Video Record:** Set mode to `RECORDING_MODE_MOVIE` and invoke the appropriate recording start/stop method.
- **Executing Still Capture:** Set mode to `RECORDING_MODE_STILL` and invoke `takePicture()` (or dispatch the shutter key event programmatically).

#### 3.3 Mode Dial State Detection
To support the "Movie Mode Only" configuration, the service must query the camera's current mode dial position via `CameraEx` before deciding whether to intercept the `ISV_KEY_S2` press.

#### 3.4 Auto-Start & Failsafe Mechanism
- **The Race Condition:** The Sony firmware demands exclusive early access to hardware. Starting a custom app instantly at boot will likely freeze the camera.
- **The Bootloop Risk:** If the service crashes the camera upon launch, starting it instantly at boot causes a permanent bootloop, soft-bricking the camera until connected to a PC.
- **The Solution:** `BootReceiver` uses Android's `Handler.postDelayed()` or `AlarmManager` to wait *N* seconds (configured by the user in `MainActivity`, defaults to 10s). 
- **The Escape Hatch:** If a crash occurs, the delay guarantees the user has *N* seconds of normal camera operation to press `Menu`, open the app, and disable Auto-Start before the crash loops.

---

### 4. Data Flow & Lifecycle

1. **Boot/Launch:** User powers on the camera. 
2. **Auto-Start Flow:**
   - Android fires `BOOT_COMPLETED`.
   - `BootReceiver` wakes up, checks `SharedPreferences`.
   - If Auto-Start is ON, it waits the configured delay (e.g., 10 seconds).
   - `BootReceiver` launches `ShutterInterceptService`.
3. **Initialization:** Service reads `SharedPreferences` and calls `CameraEx.open()` to gain hardware control.
4. **Listening:** `InputHandler` registers a listener for `ScalarInput` events.
5. **Event Trigger (Shutter Press):** User presses `ISV_KEY_S2`.
   - Check Settings: Is Shutter mapped to Video? 
   - Check State: If "Movie Mode Only", read mode dial. Pass through if not in Movie mode.
   - Action: Block native event, call `CameraController.startVideo()`.
6. **Event Trigger (Video Button Press):** User presses `ISV_KEY_STASTOP`.
   - Action: If mapped to Photo, block native event and call `CameraController.takePicture()`.
7. **Cleanup:** When the service stops, `CameraEx.release()` returns hardware control.

---

### 5. Build & Deployment Pipeline

#### 5.1 Project Setup (Gradle)
The `build.gradle` file must include the OpenMemories dependencies:
```gradle
repositories {
    jcenter()
    maven { url "https://jitpack.io" }
}
dependencies {
    implementation 'com.github.ma1co.OpenMemories-Framework:framework:-SNAPSHOT'
    compileOnly 'com.github.ma1co.OpenMemories-Framework:stubs:-SNAPSHOT'
}
```

#### 5.2 Packaging & Installation
- **Build:** Compile the app to an `.apk` using Android Studio.
- **Deploy:** Use the **PMCA-RE** tool to sideload the APK onto the Sony a7S via USB (Mass Storage mode).

---

### 6. Risks, Constraints & Mitigation

| Risk | Impact | Mitigation |
| :--- | :--- | :--- |
| **Bootloop via Auto-Start** | Critical | Implemented the delayed start failsafe. Allows user a time window to open the app and disable the setting before the service starts. |
| **Camera Crash / Freeze** | High | Implement strict `try/catch` blocks around all `CameraEx` calls. Ensure `release()` is always called in `onDestroy()`. |
| **Dial State Inaccuracy** | Medium | Fallback: intercept strictly based on UI toggle if dial state is unreadable. |

---

### 7. Next Steps for Development

1. **Scaffold the Android Project:** Create the project structure, configure Gradle with OpenMemories dependencies, and target legacy Android API levels (API 10/15).
2. **UI & Preferences:** Build the `MainActivity` with settings layout (Checkboxes/Spinners/Delay Slider) and wire up `SharedPreferences`.
3. **Verify the API Stubs:** Inspect the `com.sony.scalar.hardware.CameraEx` stubs.
4. **Implement Auto-Start:** Create the `BootReceiver` and wire up the delayed launch logic.
5. **Implement Interception Service:** Create the `Service` and test hardware button interception.