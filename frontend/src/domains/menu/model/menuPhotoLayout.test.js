import test, { before, after } from 'node:test';
import assert from 'node:assert/strict';
import { MENU_PHOTO_ASPECT_RATIO, menuPhotoLayout, menuPhotoPresentation } from './menuPhotoLayout.js';

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


let server, MenuItemCard, FeaturedDishCard, presentDish;
before(async () => {
  const { createServer } = await import('vite');
  server = await createServer({ server: { middlewareMode: true, hmr: false, ws: false, watch: null }, appType: 'custom', logLevel: 'error' });
  MenuItemCard = (await server.ssrLoadModule('/src/domains/menu/components/MenuItemCard.jsx')).default;
  ({ FeaturedDishCard } = await server.ssrLoadModule('/src/website/components/SignatureDishes.jsx'));
  ({ presentDish } = await server.ssrLoadModule('/src/domains/menu/model/menuModel.js'));
});
after(async () => { await server?.close(); });

for (const [name, values] of Object.entries({
  default: {}, horizontal: { focusX: 20 }, vertical: { focusY: 80 }, zoom: { zoom: 1.6 },
  rotation: { rotation: 90 }, 'position and zoom': { focusX: 30, focusY: 70, zoom: 1.2 },
  combined: { focusX: 45, focusY: 55, zoom: 1.6, rotation: 45 },
})) {
  test(`homepage thumbnail, preview and Menu card share ${name} presentation`, async () => {
    const { createElement } = await import('react');
    const { renderToStaticMarkup } = await import('react-dom/server');
    const item = { id: 'photo-test', name: 'Test dish', available: true, variations: [], optionGroups: [], image: { url: '/test-photo.jpg', ...values } };
    const dish = presentDish(item);
    const photo = menuPhotoPresentation(dish);
    assert.deepEqual(photo, { focusX: values.focusX ?? 50, focusY: values.focusY ?? 50,
      zoom: values.zoom ?? 1, rotation: values.rotation ?? 0,
      origin: `${values.focusX ?? 50}% ${values.focusY ?? 50}%` });
    const menu = renderToStaticMarkup(createElement(MenuItemCard, { item, collection: { availability: { available: true } }, enabled: true, cart: [] }));
    const homepage = renderToStaticMarkup(createElement(FeaturedDishCard, { dish, open: true }));
    const styles = html => Array.from(html.matchAll(/class="menu-photo-layer" style="([^"]+)"/g), match => match[1]);
    assert.deepEqual(styles(homepage), [styles(menu)[0], styles(menu)[0]]);
    assert.ok(styles(menu)[0].includes(`scale(${photo.zoom}) rotate(${photo.rotation}deg)`));
    assert.ok(styles(menu)[0].includes(`transform-origin:${photo.origin}`));
    assert.equal((homepage.match(/menu-photo-frame/g) || []).length, 2);
  });
}

test('combined rotation retains source corners that frame-sized object-fit would discard', () => {
  const W = 290, H = W / MENU_PHOTO_ASPECT_RATIO;
  const focusX = 45, focusY = 55, zoom = 1.6, angle = Math.PI / 4;
  const style = menuPhotoLayout(0.4, focusX, focusY);
  const width = W * parseFloat(style.width) / 100, height = H * parseFloat(style.height) / 100;
  const left = W * parseFloat(style.left) / 100, top = H * parseFloat(style.top) / 100;
  const px = W * focusX / 100, py = H * focusY / 100;
  let discardedCorner = false;
  for (const [x,y] of [[0,0],[W,0],[0,H],[W,H]]) {
    // Invert rotate then scale around the persisted origin to locate source pixels.
    const dx=(x-px)/zoom, dy=(y-py)/zoom;
    const sourceX=px+dx*Math.cos(angle)+dy*Math.sin(angle);
    const sourceY=py-dx*Math.sin(angle)+dy*Math.cos(angle);
    assert.ok(sourceX>=left && sourceX<=left+width && sourceY>=top && sourceY<=top+height);
    if (sourceY<0 || sourceY>H) discardedCorner=true;
  }
  assert.ok(discardedCorner, 'old frame-sized raster has missing corners despite sufficient full-source coverage');
});
