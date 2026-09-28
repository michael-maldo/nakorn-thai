import { useState } from 'react';
import { MENU_PHOTO_ASPECT_RATIO, menuPhotoLayout } from '../model/menuPhotoLayout';

function PhotoLayer({ src, alt, focusX, focusY, zoom, rotation, origin, loading }) {
  const [aspectRatio, setAspectRatio] = useState(MENU_PHOTO_ASPECT_RATIO);
  return <div className="menu-photo-layer" style={{ transform: `scale(${zoom}) rotate(${rotation}deg)`, transformOrigin: origin }}>
    <img src={src} alt={alt} loading={loading} draggable={false}
      style={menuPhotoLayout(aspectRatio, focusX, focusY)}
      onLoad={event => {
        const photo = event.currentTarget;
        if (photo.naturalWidth && photo.naturalHeight) setAspectRatio(photo.naturalWidth / photo.naturalHeight);
      }} />
  </div>;
}

export default function MenuPhoto({ src, alt, className, focusX = 50, focusY = 50, zoom = 1, rotation = 0,
  origin = `${focusX}% ${focusY}%`, loading }) {
  return <div className={`${className} menu-photo-frame`} style={{ aspectRatio: MENU_PHOTO_ASPECT_RATIO }}>
    <PhotoLayer key={src} {...{ src, alt, focusX, focusY, zoom, rotation, origin, loading }} />
  </div>;
}
