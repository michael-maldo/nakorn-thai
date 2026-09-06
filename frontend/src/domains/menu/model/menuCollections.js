// Availability comes from the backend. These labels do not evaluate schedules.
export function collectionAvailability(collection) {
  if (collection?.availability?.available === true) return '';
  const messages = {
    NOT_STARTED: 'This menu is not available to order yet.',
    ENDED: 'Ordering from this menu has ended.',
    OUTSIDE_SCHEDULE: 'This menu is currently outside its ordering hours.',
    INACTIVE: 'Ordering from this menu is temporarily unavailable.',
  };
  return messages[collection?.availability?.reason] || 'This menu is currently unavailable to order.';
}

export function selectCollection(collections, selectedId) {
  return collections.find((collection) => collection.id === selectedId)
    ?? collections.find((collection) => collection.availability?.available === true)
    ?? collections[0] ?? null;
}

export function menuSections(menu, search = '') {
  const query = search.trim().toLowerCase();
  return menu.categories.map((category) => ({
    ...category,
    items: menu.items.filter((item) => item.category?.id === category.id
      && `${item.name} ${item.description ?? ''}`.toLowerCase().includes(query))
      .sort((a, b) => a.displayOrder - b.displayOrder || a.id.localeCompare(b.id)),
  }));
}
