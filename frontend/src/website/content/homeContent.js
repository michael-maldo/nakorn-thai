import yellowCurryImage from '../../assets/images/signature-yellow-curry.jpg';
import crispyPorkVegetablesImage from '../../assets/images/signature-crispy-pork-vegetables.jpg';
import greenCurryImage from '../../assets/images/signature-green-curry.jpg';
import crispyPorkBroccoliImage from '../../assets/images/signature-crispy-pork-broccoli.jpg';

export const navigation = ['Home', 'Menu', 'Reservations', 'Functions', 'Gallery', 'About', 'Contact'];

export const dishes = [
  {
    name: 'Yellow Curry',
    description: 'A fragrant, gently spiced curry served with steamed jasmine rice.',
    image: yellowCurryImage,
    imagePosition: '42% 2%',
    imageScale: 1.09,
    imageRotation: '-0.8deg',
  },
  {
    name: 'Crispy Pork Stir-Fry',
    description: 'Crispy pork tossed with fresh seasonal vegetables and Thai sauce.',
    image: crispyPorkVegetablesImage,
    imagePosition: '68% 18%',
    imageScale: 1.13,
    imageRotation: '1.2deg',
  },
  {
    name: 'Green Curry',
    description: 'Classic Thai green curry with bamboo shoots, vegetables and jasmine rice.',
    image: greenCurryImage,
    imagePosition: '31% 13%',
    imageScale: 1.4,
    imageRotation: '-1.1deg',
  },
  {
    name: 'Crispy Pork & Broccoli',
    description: 'Crispy pork served over Chinese broccoli with a savoury garlic sauce.',
    image: crispyPorkBroccoliImage,
    imagePosition: '56% 41%',
    imageScale: 1.06,
    imageRotation: '0.7deg',
  },
];

export const features = [
  { icon: '❧', title: 'Fresh Ingredients', text: 'Locally sourced produce for the best quality.' },
  { icon: '♨', title: 'Authentic Recipes', text: 'Traditional Thai flavours passed down through generations.' },
  { icon: '♛', title: 'Experienced Chefs', text: 'Passionate chefs bringing authentic Thai cuisine to your table.' },
  { icon: '♧', title: 'Licensed Bar', text: 'Fine selection of wines, beers and handcrafted cocktails.' },
  { icon: '♕', title: 'Serving Since 2005', text: 'Proudly serving Hawthorn and surrounding areas for over 18 years.' },
];

export const reviews = [
  { quote: 'The best Thai food in Melbourne! Amazing flavours, beautiful ambience and friendly staff.', name: 'Sarah L.' },
  { quote: 'Incredible food and service. We come back here every time we’re in Hawthorn.', name: 'Michael T.' },
  { quote: 'Authentic, fresh and delicious. The Massaman curry is simply outstanding!', name: 'Jessica W.' },
];

export const contactDetails = [
  { icon: '◷', label: 'Opening hours', lines: ['Mon – Sun', '11:30 AM – 9:30 PM', '(Kitchen closes 9:00 PM)'] },
  { icon: '☎', label: 'Phone', lines: ['(03) 9819 4044'] },
  { icon: '⌖', label: 'Address', lines: ['233 Glenferrie Rd,', 'Malvern VIC 3144'] },
  { icon: '▱', label: 'Parking', lines: ['Street parking available', 'on Glenferrie Rd and nearby.'] },
];

export const footerGroups = [
  { title: 'Menu', links: ['Entrées', 'Mains', 'Desserts', 'Drinks'] },
  { title: 'Reservations', links: ['Book a Table', 'Functions', 'Private Dining'] },
  { title: 'About', links: ['Our Story', 'Our Chefs', 'Gallery'] },
  { title: 'Contact', links: ['Location', 'Contact Us', 'Careers'] },
];
