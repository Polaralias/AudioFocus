# AudioFocus Build Session Prompt

You are ChatGPT (gpt-5-codex), operating in a fresh coding session for the AudioFocus project. Your goal is to implement the Android overlay controller described in `AGENTS.md`. Follow the guidance below to plan and execute the work.

## Context Summary
- AudioFocus provides an attention-enhancing overlay for the official YouTube (`com.google.android.youtube`) and YouTube Music (`com.google.android.apps.youtube.music`) apps.
- Overlay behaviors, system architecture, and heuristics are defined in the repository root `AGENTS.md` file. Read and abide by that document fully.
- The experience must feel "slick and stable," with fade animations, low battery impact, and minimal permissions beyond those listed in `AGENTS.md`.

## Session Expectations
1. **Initial Planning**
   - Re-read `AGENTS.md` and outline the implementation plan using the task-stub format if required by the active instructions.
   - Identify core modules to create: foreground service, accessibility service, notification listener, overlay manager, UI/permission flow.
   - Clarify assumptions and external dependencies (e.g., Jetpack libraries, compile SDK level).

2. **Implementation Priorities**
   - Scaffold the Android project (Gradle settings, modules, AndroidManifest, baseline app skeleton).
   - Implement the foreground service hosting overlay views with fade-in/out animations.
   - Implement the accessibility service to detect YouTube/YouTube Music window states, with debounced state machine transitions.
   - Implement the notification listener and media session integration to determine playback state and heuristics for video vs audio.
   - Combine signals within an overlay manager that decides which overlay layout to show.
   - Provide UI for onboarding and permission requests, including persistent notification controls.

3. **Quality & Testing**
   - Add unit tests for the overlay state machine and heuristics.
   - Document manual testing procedures in the PR description after verification.
   - Ensure code adheres to Android best practices (coroutines or WorkManager as appropriate, lifecycle-aware components where possible).

4. **Operational Notes**
   - Respect repository-wide instructions regarding citations, testing reports, and PR preparation.
   - Avoid unnecessary permissions or scope creep outside YouTube/YouTube Music.
   - Keep the overlay views lightweight and release resources when hidden to conserve battery.

5. **Deliverables**
   - Functional Android project implementing the overlay behavior.
   - Documentation updates as needed to explain build/run steps.
   - Comprehensive PR message summarizing changes and tests.

Use this prompt to kick off the dedicated build session, ensuring all work aligns with the AudioFocus vision.
