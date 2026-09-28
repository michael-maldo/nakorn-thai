export const MENU_PHOTO_ASPECT_RATIO = 1.45;

// Size the entire photo as cover would, without discarding pixels outside the frame.
// Percentages keep the layout responsive; only the outer frame clips the result.
export function menuPhotoLayout(imageAspectRatio, focusX = 50, focusY = 50) {
  const width = Math.max(1, imageAspectRatio / MENU_PHOTO_ASPECT_RATIO);
  const height = Math.max(1, MENU_PHOTO_ASPECT_RATIO / imageAspectRatio);
  return {
    width: `${width * 100}%`,
    height: `${height * 100}%`,
    left: `${(1 - width) * focusX}%`,
    top: `${(1 - height) * focusY}%`,
  };
}
