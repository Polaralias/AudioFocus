# Manual QA: Overlay fill modes

## Solid color fill
1. Launch AudioFocus and open the Settings screen.
2. In **Overlay appearance**, tap **Use default overlay color**.
3. Verify the overlay immediately updates to the Material scrim color when the service is running.
4. Tap **Choose custom color**, adjust sliders to a vivid tint, and confirm.
5. Confirm the overlay switches to the selected color and the custom value is persisted after closing/reopening the app.

## Image fill
1. On the Settings screen, tap **Select background image** and choose a local image.
2. Grant storage access if prompted; ensure the overlay now displays the selected image with a centered crop.
3. Return to the Settings screen and tap **Remove background image**; verify the overlay reverts to the last chosen color.
4. Re-open the image picker and pick a different image; confirm the overlay refreshes and previous selections are cleared.
