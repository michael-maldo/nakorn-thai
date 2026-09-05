Use this reusable prompt:
Add the attached food images to the [SECTION OR MENU CATEGORY] section of the existing React/Vite application.

For each image:

1. Inspect the image and identify the main dish.
2. Copy it into `frontend/src/assets/images/` with a descriptive filename.
3. Import it through the appropriate content/data module—do not hardcode image paths inside presentation components.
4. Add or update the placeholder dish name and description so they match the pictured food.
5. Display the image using the existing reusable menu card component.
6. Crop each image individually so the main dish is the focal point.
7. For dishes served with rice, focus primarily on the main dish and show only a small hint of rice.
8. Give similar images varied compositions using different horizontal and vertical focal positions.
9. Apply controlled variation through subtle differences in zoom and rotation. Do not generate random values on every render.
10. Keep the food sharp while softly fading the image edges into the page background using the existing mask and warm inset vignette.
11. Ensure images remain clipped inside their frames and do not overflow.
12. Preserve responsive behavior on desktop, tablet, and mobile.
13. Add accurate, concise alt text for accessibility.
14. Reuse the existing image configuration fields:
    - `image`
    - `imagePosition`
    - `imageScale`
    - `imageRotation`
15. Run `npm run build` and fix all compilation errors.

Use this data shape for each menu item:

{
  name: '[DISH NAME]',
  description: '[SHORT DISH DESCRIPTION]',
  image: importedImage,
  imagePosition: '[HORIZONTAL]% [VERTICAL]%',
  imageScale: 1.0,
  imageRotation: '0deg'
}

Cropping guidance:

- Start with `imagePosition: '50% 50%'`.
- Reduce the vertical percentage to move the subject visually downward.
- Increase the vertical percentage to move the subject visually upward.
- Adjust the horizontal percentage to vary left/right composition.
- Use `imageScale` between `1.05` and `1.4` for closer crops.
- Keep `imageRotation` subtle, normally between `-1.2deg` and `1.2deg`.
- Choose settings by inspecting each image rather than applying identical values.
- Keep results stable between renders.
Attach the relevant images and replace [SECTION OR MENU CATEGORY] with values such as Entrées, Curries, Noodles, Rice Dishes, or Desserts.