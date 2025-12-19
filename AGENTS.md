# Project Status

## Phase 1: Project Setup & Infrastructure
- [x] Project Initialization
- [x] Dependencies
- [x] Project Structure
- [x] Core Data Models

## Phase 2: Service & Permissions
- [x] Service Implementation (Foreground, Accessibility, Notification)
- [x] Manifest Configuration
- [x] Permission Management

## Phase 3: Monitors
- [x] Monitor Architecture
- [x] Accessibility Monitor
- [x] Media Session Monitor
- [x] Notification Monitor
- [x] Foreground App Detector
- [x] Integration

## Phase 4: Logic Engine
- [x] Playback State Engine
- [x] Decision Logic
- [x] Service Integration & Verification

## Phase 5: Overlay UI
- [x] System Alert Window
- [x] Media Controls
- [x] Customization

## Phase 6: App UI & Settings
- [x] Onboarding Flow
- [x] Settings Screen
- [x] Persistence

## Phase 7: QA & Polish
- [x] Verification (Code & Lint)
- [x] Performance Optimization
- [x] Edge Cases
- [x] CI/CD Pipeline

## QA Evaluation (Current)
- Validated Phases 1-5 implementation.
- Completed Phase 6 (App UI & Settings):
    - Implemented `OnboardingScreen` for permission management.
    - Implemented `HomeScreen` for app selection and theme customization (Solid Color/Image, Blur).
    - Integrated `SettingsRepository` with `OverlayManager` and `OverlayScreen`.
    - Refactored `OverlayScreen` to support app-specific themes and custom images using Coil.
- Completed Phase 7 (QA & Polish):
    - **Performance Optimization**: Optimized `AccessibilityMonitor` to break traversal loop early when video surface is found, reducing CPU usage during window content changes.
    - **Edge Cases**: Fixed `AudioFocusService` lifecycle management to correctly cancel and restart monitoring coroutines when toggled or when the service stops, preventing duplicate collectors and potential memory leaks.
    - **Verification**: Verified logic via static analysis and existing test suite.
- Established CI/CD:
    - Created GitHub Action workflow `.github/workflows/android-debug.yml` for automatic debug APK builds, unit tests, and linting.
- Next Steps: Prepare for release candidates and user feedback.

# Agents skill triage

Agents using this repository should consult the skill documents stored locally in `./skills/<skill-id>/SKILL.md`.

Before producing work, the agent should fully understand the referenced skill document relevant to the task and always prioritise direct user instruction when a prompt specifies a particular skill.

## Android delivery

- **`android-product-shaping`** – This skill is used to turn Android app ideas into small, well-bounded product slices with clear value, ready for UX and implementation.
- **`android-ux-flows`** – This skill is used to design Android user flows and screen structures that match the existing app patterns and keep forms, lists and navigation clear.
- **`android-dev-standards`** – Standards, architecture patterns, and best practices for Android app development with Kotlin, Jetpack Compose, and Android Jetpack libraries using clean architecture and MVVM. Use for any Android coding, review, refactor, or design task, especially when acting as an AI coding agent that must follow established project conventions.
- **`android-engineering-core`** – This skill is used to implement Android features within the existing Kotlin, Compose, Room, Hilt and Navigation architecture, including data, navigation and background work.
- **`android-ui-compose`** – This skill is used to implement Android UI in Jetpack Compose based on an existing UX flow, focusing on clear hierarchy, list vs form separation and discoverable navigation.
- **`android-qa-verification`** – This skill is used to verify Android features against acceptance criteria, catch regressions and define tests that reflect real device behaviour.

## Design & UX

- **`brand-guidelines`** – Applies Anthropic's official brand colors and typography to any sort of artifact that may benefit from having Anthropic's look-and-feel. Use it when brand colors or style guidelines, visual formatting, or company design standards apply.
- **`frontend-design`** – Create distinctive, production-grade frontend interfaces with high design quality. Use this skill when the user asks to build web components, pages, or applications. Generates creative, polished code that avoids generic AI aesthetics.

## Writing & comms

- **`internal-comms`** – A set of resources to help me write all kinds of internal communications, using the formats that my company likes to use. Claude should use this skill whenever asked to write some sort of internal communications (status reports, leadership updates, 3P updates, company newsletters, FAQs, incident reports, project updates, etc.).

## Skills meta

- **`mcp-builder`** – Guide for creating high-quality MCP (Model Context Protocol) servers that enable LLMs to interact with external services through well-designed tools. Use when building MCP servers to integrate external APIs or services, whether in Python (FastMCP) or Node/TypeScript (MCP SDK).
- **`skill-creator`** – Guide for creating effective skills. This skill should be used when users want to create a new skill (or update an existing skill) that extends Claude's capabilities with specialized knowledge, workflows, or tool integrations.
- **`template-skill`** – Replace with description of the skill and when Claude should use it.
