import { useEffect, useRef, useState } from 'react';
import { useMenuNavigationGuard } from '../hooks/useMenuAdminForm';
import { presentDish } from '../model/menuModel';
import { saveMenuImage } from '../api/menuApi';

export default function MenuImageEditor({ item, authorization, csrf, onSaved, onBusy, disabled }) {
  const original = presentDish(item);
  const fileInput = useRef(null);
  const [file, setFile] = useState(null);
  const [url, setUrl] = useState(original.image);
  const [alt, setAlt] = useState(item.image?.alt || item.name);
  const [x, setX] = useState(item.image?.focusX ?? 50);
  const [y, setY] = useState(item.image?.focusY ?? 50);
  const [zoom, setZoom] = useState(item.image?.zoom ?? 1);
  const [rotation, setRotation] = useState(item.image?.rotation ?? 0);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [saved, setSaved] = useState(false);
  const dirty = !saved && (file !== null || alt !== (item.image?.alt || item.name) || x !== (item.image?.focusX ?? 50) || y !== (item.image?.focusY ?? 50) || zoom !== (item.image?.zoom ?? 1) || rotation !== (item.image?.rotation ?? 0));
  const guard = useMenuNavigationGuard(dirty, busy);

  useEffect(() => {
    if (!file) return;
    const objectUrl = URL.createObjectURL(file);
    setUrl(objectUrl);
    return () => URL.revokeObjectURL(objectUrl);
  }, [file]);
  async function save() {
    setBusy(true); onBusy(true); setError('');
    try {
      const body = new FormData();
      let upload = file;
      if (!upload && !item.image && original.image) {
        const response = await fetch(original.image);
        if (!response.ok) throw new Error('Could not load the original photograph. Choose a file instead.');
        upload = await response.blob();
      }
      if (upload) body.append('file', upload, 'photo');
      body.append('version', item.version);
      body.append('alt', alt); body.append('focusX', x); body.append('focusY', y); body.append('zoom', zoom); body.append('rotation', rotation);
      await saveMenuImage(item.id, body, authorization, csrf);
      guard.current.dirty = false; setSaved(true);
      await onSaved();
    } catch (failure) { setError(failure.message); }
    finally { setBusy(false); onBusy(false); }
  }
  return <fieldset className="staff-wide" disabled={disabled || busy || saved}>
    <legend>Menu photograph</legend>
    <p>Choose a JPEG or PNG up to 8 MB and 16 megapixels. Use the preview and controls to position the picture inside the menu card. Zoom in to allow movement in both directions.</p>
    {error && <p role="alert">{error}</p>}
    <label>Add or replace photo<input ref={fileInput} type="file" accept="image/jpeg,image/png" onChange={(event) => {
      const selected = event.target.files[0];
      if (!selected) return;
      if (!['image/jpeg', 'image/png'].includes(selected.type) || selected.size > 8 * 1024 * 1024) {
        setError('Choose a JPEG or PNG no larger than 8 MB.'); event.target.value = ''; return;
      }
      setError(''); setFile(selected); setX(50); setY(50); setZoom(1); setRotation(0);
    }} /></label>
    {url && <div className="menu-focus-preview"><img src={url} alt={alt} style={{ objectPosition: `${x}% ${y}%`, transform: `scale(${zoom}) rotate(${rotation}deg)`, transformOrigin: `${x}% ${y}%` }} /></div>}
    <label>Photo description<input maxLength={255} value={alt} onChange={(e) => setAlt(e.target.value)} /></label>
    <fieldset className="menu-image-controls" disabled={!url}>
      <legend>Position, zoom and rotation</legend>
      <label>Horizontal focus: {x}%<input type="range" min="0" max="100" value={x} onChange={(e) => setX(Number(e.target.value))} /></label>
      <div className="menu-image-control-buttons" role="group" aria-label="Move picture horizontally">
        <button type="button" disabled={x === 100} onClick={() => setX(Math.min(100, x + 5))}>← Move left</button>
        <button type="button" disabled={x === 0} onClick={() => setX(Math.max(0, x - 5))}>Move right →</button>
      </div>
      <label>Vertical focus: {y}%<input type="range" min="0" max="100" value={y} onChange={(e) => setY(Number(e.target.value))} /></label>
      <div className="menu-image-control-buttons" role="group" aria-label="Move picture vertically">
        <button type="button" disabled={y === 100} onClick={() => setY(Math.min(100, y + 5))}>↑ Move up</button>
        <button type="button" disabled={y === 0} onClick={() => setY(Math.max(0, y - 5))}>Move down ↓</button>
      </div>
      <label>Zoom: {zoom.toFixed(2)}×<input type="range" min="1" max="3" step="0.05" value={zoom} onChange={(e) => setZoom(Number(e.target.value))} /></label>
      <div className="menu-image-control-buttons" role="group" aria-label="Picture zoom">
        <button type="button" disabled={zoom <= 1} onClick={() => setZoom(Math.max(1, Number((zoom - 0.05).toFixed(2))))}>− Zoom out</button>
        <button type="button" disabled={zoom >= 3} onClick={() => setZoom(Math.min(3, Number((zoom + 0.05).toFixed(2))))}>+ Zoom in</button>
        <button type="button" onClick={() => { setX(50); setY(50); setZoom(1); setRotation(0); }}>Reset position, zoom and rotation</button>
      </div>
      <label>Rotation: {rotation}°<input type="range" min="-180" max="180" step="1" value={rotation} onChange={(e) => setRotation(Number(e.target.value))} /></label>
      <div className="menu-image-control-buttons" role="group" aria-label="Picture rotation">
        <button type="button" onClick={() => setRotation(value => value - 90 < -180 ? value + 270 : value - 90)}>↶ Rotate left 90°</button>
        <button type="button" onClick={() => setRotation(value => value + 90 > 180 ? value - 270 : value + 90)}>↷ Rotate right 90°</button>
        <button type="button" disabled={rotation === 0} onClick={() => setRotation(0)}>Reset rotation</button>
      </div>
      <p>Zoom in if rotating the picture exposes the frame background.</p>
      <p>The preview uses the same frame and crop as the public menu.</p>
    </fieldset>
    <button type="button" disabled={!url || !alt.trim()} onClick={save}>{busy ? 'Saving photo…' : 'Save photo and focus'}</button>
    <button type="button" disabled={!dirty} onClick={() => {
      if (fileInput.current) fileInput.current.value = '';
      setFile(null); setUrl(original.image); setAlt(item.image?.alt || item.name);
      setX(item.image?.focusX ?? 50); setY(item.image?.focusY ?? 50); setZoom(item.image?.zoom ?? 1); setRotation(item.image?.rotation ?? 0); setError('');
    }}>Cancel photo changes</button>
    {saved && <p role="status">Photo saved. Refresh data if the updated preview has not loaded.</p>}
    <p>Photo changes save separately from item details.</p>
  </fieldset>;
}
