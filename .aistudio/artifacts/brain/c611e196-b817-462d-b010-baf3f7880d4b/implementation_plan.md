# Implementation Plan - Android Home Screen Widgets & Floating Countdown Overlay Window

This plan outlines the implementation for home screen widgets and a floating live timer window for FocusShield.

## User Preferences
- **Widget Features**: Combined Focus Timer launcher, today's stats (minutes & streak), and daily study plan preview.
- **Widget Sizes**: Both Compact 2x2 and Detailed 4x2 Android App Widgets.
- **Floating Timer**: Draggable system overlay window displaying live countdown over other apps, with tap-to-open app intent.

---

## Architecture & Features

### 1. Home Screen App Widgets
- **Compact Widget (`FocusWidgetCompactProvider`) - 2x2**:
  - Displays quick launch button for a 25m Focus Session.
  - Shows today's focus minutes and study streak count.
  - Live status indicator showing if a focus session is active.
- **Detailed Widget (`FocusWidgetDetailedProvider`) - 4x2**:
  - Displays active countdown timer or quick session launcher.
  - Today's stats: Total focus hours, current streak, completed sessions.
  - Next daily study plan item / task with quick check-off or start action.
  - Instant action buttons: Quick Start Focus, Pause/Resume, and Open App.
- **Widget State Manager (`FocusWidgetManager`)**:
  - Automatically pushes real-time updates to all widget instances whenever a focus session starts, ticks, pauses, or finishes.

### 2. Floating Countdown Overlay Window (`FloatingTimerService`)
- **System Overlay Service**:
  - Uses `SYSTEM_ALERT_WINDOW` and `WindowManager` to render a small, floating overlay window on top of other apps during an active session.
  - Draggable touch listener so the user can position the countdown window anywhere on their screen.
- **Floating Controls & Navigation**:
  - Displays session subject, active countdown timer (MM:SS), and mini progress bar.
  - Quick Pause/Resume button and Stop button.
  - Tapping the floating window triggers an `Intent` to bring FocusShield back to the foreground.
- **Permission & In-App Toggle**:
  - In-app permission request flow for `SYSTEM_ALERT_WINDOW` (Display over other apps) with clear fallback.
  - Toggle in App Settings and Active Focus Session screen to auto-enable/disable floating overlay when minimizing the app.

---

## Proposed Changes & Files

### Android Manifest
- Update `/app/src/main/AndroidManifest.xml`:
  - Add `<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW"/>`
  - Add `<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>`
  - Declare `FocusWidgetCompactProvider` receiver with `APPWIDGET_UPDATE` intent filter.
  - Declare `FocusWidgetDetailedProvider` receiver with `APPWIDGET_UPDATE` intent filter.
  - Declare `FloatingTimerService` foreground service.

### Widget Resources & Providers
- `/app/src/main/res/xml/widget_info_compact.xml`: 2x2 App Widget configuration metadata.
- `/app/src/main/res/xml/widget_info_detailed.xml`: 4x2 App Widget configuration metadata.
- `/app/src/main/res/layout/widget_focus_compact.xml`: Compact widget layout XML.
- `/app/src/main/res/layout/widget_focus_detailed.xml`: Detailed widget layout XML with study plan & stats.
- `/app/src/main/java/com/example/widget/FocusWidgetCompactProvider.kt`: 2x2 widget update logic.
- `/app/src/main/java/com/example/widget/FocusWidgetDetailedProvider.kt`: 4x2 detailed widget update logic.
- `/app/src/main/java/com/example/widget/FocusWidgetManager.kt`: Helper for broadcasting widget updates.

### Floating Countdown Overlay Service
- `/app/src/main/java/com/example/service/FloatingTimerService.kt`: Draggable window manager overlay service with Compose/View layout, live timer updates, and tap-to-open activity launch.

### UI & Settings Integration
- `/app/src/main/java/com/example/feature/timer/FloatingOverlayPermissionDialog.kt`: UI dialog explaining and requesting overlay permission.
- Integrate floating window controls into Settings and Active Session view models.

---

## Verification Plan
1. **Compilation Check**: Run `compile_applet` to ensure clean build with new receivers, layouts, and service.
2. **Widget Receiver Validation**: Verify widget layout XML rendering and AppWidgetProvider intent handlers.
3. **Floating Service Logic**: Verify permission handling, lifecycle bounds, and intent forwarding to `MainActivity`.
