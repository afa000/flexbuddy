# 1. The native app gets its own repository, and its API is off in production until switched on

Date: November 11, 2026. Status: accepted by the owner. Details: [native-app-plan.md](../native-app-plan.md).

## Context

The Trusted Web Activity in `android/` is being replaced by a native Kotlin and Compose app. The owner relies on the
live website for personal use and internship demos, so it must stay exactly as it is until the native app is done, and
afterwards. Every push to `main` redeploys production, and there was no staging environment.

## Decision

1. **The app is built in a separate repository**, `flexbuddy-android`. App commits can't reach this repository, its CI
   or Render, and agents working on Kotlin load only the app's own rules.
2. **The server's API for the app lives under `/api/v1/**`, behind `FLEXBUDDY_API_ENABLED`**, off by default. While it
   is off, production runs today's code paths and `/api/v1` answers 404. The owner turns it on in Render when internal
   testing starts; turning it off is the server's rollback.
3. **A free test server** (Render free tier with a Neon database, its own keys, made-up data only) runs with the API
   on, so app builds have a real server before production does.
4. **The TWA stays the Play app until the native app reaches parity**, then the native app ships as an update to the
   same listing.

## Consequences

- Most features become two tasks: the endpoint here first, then the screen in the app repository. FB numbers are one
  sequence across both. The contract between them is [api-v1.md](../api-v1.md).
- The only change production sees before the switch is empty tables from the token migration.
- Rejected: one repository with an `android-app/` folder (app commits would share CI and deploys, needing a Render
  build filter); rewriting the server in Kotlin first (no gain for drivers, risk in sign-in and earnings code); moving
  production to another host now (the TWA and asset links are tied to `flexbuddy.onrender.com`).
