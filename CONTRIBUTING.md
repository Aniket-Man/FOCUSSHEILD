# Contributing to FocusShield

Thanks for taking the time to contribute. This document is short on purpose: it lists only the
things a change has to do to be mergeable.

## 1. Set up

```bash
git clone https://github.com/Aniket-Man/FOCUSSHEILD.git
cd FOCUSSHEILD
./gradlew assembleDebug
```

You need JDK 17 and an Android SDK with platform 35. No keystore, `.env` or `google-services.json`
is required for a debug build — see the [README](README.md#getting-started).

## 2. Branches and commits

* Branch from `main`, one branch per issue: `fix/issue-42`, `feat/session-templates`, …
* Reference the issue in the PR title and body (`Fix #42: …`) so it closes on merge.
* Keep a commit focused. A commit message explains **why**, not just what.

## 3. What a change must not do

These are the rules the existing code is written against; a PR that breaks one will be asked to
change:

1. **Never invent a value.** If input cannot be parsed (a clock time, a day list, a cloud row),
   report it and skip — do not substitute a plausible default. Silent substitution is how this
   project produced reminders at times the user never chose.
2. **Never swallow an enforcement failure.** A block, sync or alarm that could not be applied must
   be visible in the UI or, at minimum, logged at `WARN`/`ERROR` with the reason.
3. **Re-arm alarms before fallible work**, or in a `finally`. One database error must never be able
   to kill every future reminder.
4. **No blocking work on the main thread.** No `runBlocking` in UI, services or receivers.
5. **Keep decision logic pure.** Put "should this happen?" logic in an Android-free object
   (`ScheduleAlarmPlanner`, `ScheduleValidation`, `NotificationBlockDecision`, `SingleFlightThrottle`)
   and unit-test it; leave only plumbing in the Android class.
6. **No secrets, keystores or build outputs in Git.** `.env`, `*.jks`, `*.keystore`, `*.apk` and
   `*.aab` are git-ignored; keep it that way.

## 4. Checks

Run locally before pushing — CI runs the same thing:

```bash
./gradlew assembleDebug
./gradlew test
```

If you touch Compose UI covered by a screenshot test, re-record and commit the golden:

```bash
./gradlew recordRoborazziDebug
./gradlew verifyRoborazziDebug
```

New behaviour needs a test. Pure logic goes in `app/src/test`; anything that genuinely needs a
device goes in `app/src/androidTest`. A test with no assertion does not count as a test.

## 5. Reporting bugs

Open an issue with: what you expected, what happened, the exact file/line if you have it, and the
device + Android version. Issues that quote the offending code path get fixed fastest — that is how
every issue in this repository has been written so far.
