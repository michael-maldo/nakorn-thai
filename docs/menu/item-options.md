# Reusable item choices and extras

Open **Staff → Menu → an item → Options & extras**. Creating a new item opens
this section after saving its basic details; the item starts as a draft.

- **Create option group** defines a reusable group and choices while setting
  prices for this item. Required choices select exactly one option (for example,
  Pork or Beef). Optional extras allow zero selections and configurable quantities.
- **Reuse existing group** attaches an existing group's choices. Enter each
  additional price for this dish; prices are never copied from another item.
- **Edit item prices & rules** changes this item's surcharges and selection limits.
  Enter `0` for a free/included choice; leave a price blank to exclude that choice.
- **Edit shared choices** creates or edits reusable choice names, codes and active
  status. These definition changes apply everywhere the group is assigned; prices
  do not change. New choices require explicit item prices before they are selectable.
- **Remove from item** deletes the assignment and its prices only. The standalone
  group and choices remain available for other items to reuse.

Each item can use multiple required groups, such as Protein and Spice, as well as
optional extras. Public menu selectors and checkout enforce selection rules and
calculate prices from the item's assignment. Orders retain their original price
and option-name snapshots even after configuration changes.

The V23 migration preserves existing prices per assigned item. Start the updated
backend to apply it through Flyway; do not manually edit earlier migrations.
See [API contract](backend-menu-v2-api.md#item-specific-option-pricing-v23).
