# Manual QA - Overlay Fill Modes

## Solid color overlay
1. Launch AudioFocus and grant all required permissions so the overlay can start.
2. Open **Settings → Overlay appearance** and tap **Use default color**.
3. Start media playback (e.g., on YouTube) and confirm the overlay mask displays the default dim color with no image visible.
4. Return to settings, choose **Pick custom color**, select a noticeably different hue, and apply it.
5. Verify the overlay updates in-place to the newly selected color without restarting the service.

## Image overlay
1. From **Settings → Overlay appearance**, tap **Choose image** and select a landscape photo from device storage.
2. Confirm the app requests and retains storage access, then observe the overlay mask updates to the chosen image with a center-cropped fill.
3. Pause and resume playback to ensure the image reloads quickly without flashing.
4. Tap **Remove image** and check the overlay immediately falls back to the previously configured solid color without leaving residual image artifacts.
