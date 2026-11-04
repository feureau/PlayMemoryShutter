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
- **Configurable Failsafe Delay:** To prevent race conditions with native firmware and avoid bootloops, the auto-start feature includes a user-configurable delayed launch.

---

### 2. System Architecture & Framework Strategy

The OpenMemories Framework uses a **dual-implementation strategy** to abstract platform differences between standard Android and Sony's proprietary hardware APIs. 

#### 2.1 Component Breakdown
1. **`MainActivity`:** A minimal UI allowing the user to configure button behaviors, enable auto-start, and set the failsafe delay. Designed to be navigable via the Sony D-pad by wrapping content in a ScrollView. Exits cleanly on MENU or DELETE keys.
2. **`BootReceiver`:** A `BroadcastReceiver` listening for `android.intent.action.BOOT_COMPLETED`. It schedules the background service to start after the configured delay via `AlarmManager`.
3. **`ShutterInterceptService`:** An Android `Service` that runs in the background to manage the overlay and camera hooks.
4. **`CameraController`:** A wrapper class using an `ExecutorService` and `Instrumentation` to dispatch key events back to the native camera app securely.
5. **`InputHandler`:** An overlay-based listener using `TYPE_SYSTEM_ALERT` that dynamically toggles its `FLAG_NOT_FOCUSABLE` state so it can capture keys without permanently stealing focus from the native camera.

---

### 3. Technical Implementation Details

#### 3.1 Hardware Input Interception (`InputHandler`)
Because a standard `Service` cannot globally intercept keys, `InputHandler` creates a transparent 0x0 overlay window. 
- **Focus Management:** By default, it holds focus. When a non-remapped key is pressed (like MENU or Shutter Half-press), it immediately drops focus (`FLAG_NOT_FOCUSABLE`), injects the event so the native camera receives it, and then restores focus after `ACTION_UP`.
- **Duplicate Prevention:** Checks `CameraController.isInjecting` to avoid intercepting its own synthetic key presses.

#### 3.2 Camera Control & Injection (`CameraController`)
Instead of directly instantiating Sony `CameraEx` classes and risking native hardware locks, the app relies on Android's native `Instrumentation.sendKeySync()`.
- **Video Record:** Injects `ISV_KEY_STASTOP` (Value: 515).
- **Still Capture:** Injects `ISV_KEY_S2` (Value: 518).
- **Sequential Execution:** All injections are pushed to a `SingleThreadExecutor` to ensure down/up events remain ordered.

#### 3.3 Mode Dial State Detection
To support the "Movie Mode Only" configuration, the service queries `ui.scene.mode` via `com.sony.scalar.sysutil.ScalarProperties`. If the result matches `2085` (`INTVAL_UI_SCENE_MODE_MOVIE`), the camera is in Movie Mode. If reflection fails, it defaults to `false` to prevent unwanted overrides in other modes.

#### 3.4 Auto-Start & Failsafe Mechanism
- **The Bootloop Risk:** If the service crashes the camera upon launch, starting it instantly at boot causes a permanent bootloop.
- **The Solution:** `BootReceiver` uses `AlarmManager` to wait *N* seconds. 
- **The Escape Hatch:** If a crash occurs, the user can quickly press `Menu` to open the app. `MainActivity.onResume()` automatically kills any running intercept service, and unchecking Auto-Start or pressing "Stop Service" actively cancels any pending `BootReceiver` alarms.

---

### 4. Data Flow & Lifecycle

1. **Boot/Launch:** 
   - `BootReceiver` waits *N* seconds and launches `ShutterInterceptService`.
   - Alternatively, user opens `MainActivity`. Service stops immediately. User clicks "Save & Start", app exits cleanly to camera UI.
2. **Listening:** `InputHandler` overlay adds itself and takes focus.
3. **Event Trigger (Key Pressed):**
   - **If mapped (e.g. Shutter -> Video):** Drops focus, injects video trigger key down/up, restores focus.
   - **If not mapped (e.g. D-pad, Menu):** Drops focus, passes exact key down to native firmware, restores focus on up.
4. **Cleanup:** When the service stops, the overlay is removed.