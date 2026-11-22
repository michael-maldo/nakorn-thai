import MenuPhoto from './MenuPhoto';
import { menuPhotoPresentation } from '../model/menuPhotoLayout';
import { presentDish } from '../model/menuModel';
import MenuItemOrder from './MenuItemOrder';

export default function MenuItemCard({ item, collection, enabled, cart, dispatch, onAdded }) {
  const dish = presentDish(item);
  return <article className="restaurant-menu-item">
    {dish.image && <MenuPhoto src={dish.image} alt={dish.imageAlt} className="restaurant-menu-photo" loading="lazy"
      {...menuPhotoPresentation(dish)} />}
    <div className="restaurant-menu-item-content">
      <h3>{dish.name}</h3><p>{dish.description}</p>
      {collection.availability.available && !item.available && <p className="dish-unavailable">Currently unavailable</p>}
      <MenuItemOrder {...{ item, collection, enabled, cart, dispatch, onAdded }} />
    </div>
  </article>;
}
