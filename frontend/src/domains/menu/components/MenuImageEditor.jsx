import { useEffect, useState } from 'react';
import { presentDish } from '../model/menuModel';
import { saveMenuImage } from '../api/menuApi';

export default function MenuImageEditor({ item, authorization, csrf, onSaved, onBusy, disabled }) {
  const original = presentDish(item);
  const [file, setFile] = useState(null);
  const [url, setUrl] = useState(original.image);
  const [alt, setAlt] = useState(item.image?.alt || item.name);
  const [x, setX] = useState(item.image?.focusX ?? 50);
  const [y, setY] = useState(item.image?.focusY ?? 50);
  const [zoom, setZoom] = useState(item.image?.zoom ?? 1);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
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
      body.append('alt', alt); body.append('focusX', x); body.append('focusY', y); body.append('zoom', zoom);
      await saveMenuImage(item.id, body, authorization, csrf);
      await onSaved();
    } catch (failure) { setError(failure.message); }
    finally { setBusy(false); onBusy(false); }
  }
  return <fieldset className="staff-wide" disabled={disabled || busy}>
    <legend>Menu photograph</legend>
    <p>Choose a JPEG or PNG up to 8 MB and 16 megapixels. Focus controls adjust the card crop.</p>
    {error && <p role="alert">{error}</p>}
    <label>Add or replace photo<input type="file" accept="image/jpeg,image/png" onChange={(event) => {
      const selected = event.target.files[0];
      if (!selected) return;
      if (!['image/jpeg', 'image/png'].includes(selected.type) || selected.size > 8 * 1024 * 1024) {
        setError('Choose a JPEG or PNG no larger than 8 MB.'); return;
      }
      setError(''); setFile(selected); setX(50); setY(50); setZoom(1);
    }} /></label>
    {url && <div className="menu-focus-preview"><img src={url} alt={alt} style={{ objectPosition: `${x}% ${y}%`, transform: `scale(${zoom})`, transformOrigin: `${x}% ${y}%` }} /></div>}
    <label>Photo description<input maxLength={255} value={alt} onChange={(e) => setAlt(e.target.value)} /></label>
    <label>Horizontal focus: {x}%<input type="range" min="0" max="100" value={x} onChange={(e) => setX(Number(e.target.value))} /></label>
    <label>Vertical focus: {y}%<input type="range" min="0" max="100" value={y} onChange={(e) => setY(Number(e.target.value))} /></label>
    <label>Zoom: {zoom.toFixed(2)}×<input type="range" min="1" max="3" step="0.05" value={zoom} onChange={(e) => setZoom(Number(e.target.value))} /></label>
    <button type="button" disabled={!url || !alt.trim()} onClick={save}>{busy ? 'Saving photo…' : 'Save photo and focus'}</button>
    <p>Photo changes save separately. Save any dish text changes first.</p>
  </fieldset>;
}
