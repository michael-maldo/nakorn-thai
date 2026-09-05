import yellowCurryImage from '../../../assets/images/signature-yellow-curry.jpg';
import crispyPorkVegetablesImage from '../../../assets/images/signature-crispy-pork-vegetables.jpg';
import greenCurryImage from '../../../assets/images/signature-green-curry.jpg';
import crispyPorkBroccoliImage from '../../../assets/images/signature-crispy-pork-broccoli.jpg';


// Presentation assets only: names, descriptions and visibility come from the API.
const photos = {
  '20000000-0000-0000-0000-000000000001': { image: yellowCurryImage, imagePosition: '42% 2%', imageScale: 1.09, imageRotation: '-0.8deg' },
  '20000000-0000-0000-0000-000000000002': { image: crispyPorkVegetablesImage, imagePosition: '68% 18%', imageScale: 1.13, imageRotation: '1.2deg' },
  '20000000-0000-0000-0000-000000000003': { image: greenCurryImage, imagePosition: '31% 13%', imageScale: 1.4, imageRotation: '-1.1deg' },
  '20000000-0000-0000-0000-000000000004': { image: crispyPorkBroccoliImage, imagePosition: '56% 41%', imageScale: 1.06, imageRotation: '0.7deg' },
};
export function presentDish(item) {
  return { ...item, ...(photos[item.id] || {}),
    image: item.image?.url || photos[item.id]?.image,
    imageAlt: item.image?.alt || item.name,
    ...(item.image ? { imagePosition: `${item.image.focusX ?? 50}% ${item.image.focusY ?? 50}%`, imageOrigin: `${item.image.focusX ?? 50}% ${item.image.focusY ?? 50}%`, imageScale: item.image.zoom ?? 1, imageRotation: '0deg' } : {}),
  };
}
