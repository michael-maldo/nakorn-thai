import test from 'node:test';
import assert from 'node:assert/strict';
import { MENU_PHOTO_ASPECT_RATIO, menuPhotoLayout } from './menuPhotoLayout.js';

const near = (actual, expected) => assert.ok(Math.abs(actual - expected) < 1e-9, `${actual} ≈ ${expected}`);

test('full photo retains its natural aspect ratio and covers the frame for portrait, square and landscape sources', () => {
  for (const ratio of [0.4, 1, 1.45, 3]) {
    const style = menuPhotoLayout(ratio);
    const width = parseFloat(style.width), height = parseFloat(style.height);
    near(width * MENU_PHOTO_ASPECT_RATIO / height, ratio);
    assert.ok(width >= 100 && height >= 100);
    assert.ok(width === 100 || height === 100);
  }
});

test('focus preserves the existing unrotated cover crop at both edges and intermediate positions', () => {
  for (const ratio of [0.4, 3]) {
    for (const focus of [0, 25, 50, 100]) {
      const style = menuPhotoLayout(ratio, focus, focus);
      near(parseFloat(style.left), (100 - parseFloat(style.width)) * focus / 100);
      near(parseFloat(style.top), (100 - parseFloat(style.height)) * focus / 100);
    }
  }
});

test('portrait pixels outside the initial frame remain available after a quarter turn', () => {
  const frameWidth = 360, frameHeight = frameWidth / MENU_PHOTO_ASPECT_RATIO;
  const style = menuPhotoLayout(0.4);
  const photoWidth = parseFloat(style.width) / 100 * frameWidth;
  const photoHeight = parseFloat(style.height) / 100 * frameHeight;
  // A centred 90° turn swaps extents: the entire portrait can fill all frame corners.
  assert.ok(photoHeight >= frameWidth);
  assert.ok(photoWidth >= frameHeight);
  // Rotating the previous frame-sized object-fit element would instead leave side gaps.
  assert.ok(frameHeight < frameWidth);
});
