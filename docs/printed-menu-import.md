# Printed restaurant menu import

Migration `V14__import_printed_restaurant_menu.sql` imports the two supplied menu photos.
It adds 58 regular-menu entries, 10 lunch specials and 5 drink groups. Existing
Chef’s Specials and homepage signature dishes remain unchanged. Regular green and
yellow curries are separate à la carte items because the original signature descriptions
include rice; no existing prices or descriptions are overwritten.

Prices below are AUD. Protein options use the printed base price for beef, chicken,
or vegetables & tofu; prawns +$6, seafood +$8, crispy pork +$5, fish +$6. Fixed
protein dishes keep their printed protein. Lunch fried rice lists chicken or beef only.
Fish dishes have three sauce choices; drink choices and water sizes are variations.

Lunch is visible but unavailable for online ordering until the 2:30 PM cutoff can
be enforced by the backend. Do not enable lunch items for unrestricted ordering.
Item 29 remains unavailable without a variation because its price is obscured by glare.
Descriptions obscured by glare are shortened to readable content. Pineapple juice
and eggplant spelling are normalized. Printed GF/VG/V labels are source text only;
dietary/allergen review remains NOT_REVIEWED, with no inferred verified declarations.
No dish photos are manufactured from the menu photograph.

Slugs deliberately fail on conflicts instead of overwriting staff edits. Flyway applies
the import once. Select Restaurant Menu, Lunch Specials or Drinks on the public menu
page; staff can edit all imported items in the existing menu dashboard.

| Printed entry | Item | Base price |
|---|---|---|
| 1 | Prawn Crackers with Chilli Jam | $11.90 |
| 2 | Roti (2 pieces) | $11.90 |
| 3 | Chicken Spring Rolls (4 pieces) | $11.90 |
| 4 | Vegetable Spring Rolls (4 pieces) | $11.90 |
| 5 | Vegetable Curry Puffs (4 pieces) | $11.90 |
| 6 | Fish Cakes (4 pieces) | $11.90 |
| 7 | Salt & Pepper Calamari | $17.90 |
| 8 | Calamari Rings | $17.90 |
| 9 | Tempura Prawns | $17.90 |
| 10 | Satay Chicken (4 skewers) | $13.90 |
| 11 | Vegetable Tempura | $17.90 |
| 12 | Chives Pancake | $15.90 |
| 13 | Mix Plate | $16.90 |
| 14 | Whole Barramundi Fish | $45.90 |
| 14A | Fish Fillet | $28.90 |
| 15 | Green Curry (À la carte) | $23.90 |
| 16 | Red Curry | $23.90 |
| 17 | Yellow Curry (À la carte) | $23.90 |
| 18 | Panang Curry | $23.90 |
| 19 | Massaman Beef Curry | $25.90 |
| 20 | Jungle Curry | $23.90 |
| 21 | Cashew Nuts with Chilli Jam | $23.90 |
| 22 | Sweet and Sour (Pad Priew Wang) | $23.90 |
| 23 | Fresh Ginger (Pad Khing) | $23.90 |
| 24 | Pad Kra Pao | $23.90 |
| 25 | Garlic and Pepper | $23.90 |
| 26 | Satay Stir-Fried | $23.90 |
| 28 | Stir-Fried Basil with Deep-Fried Eggplant | $23.90 |
| 29 | Sizzling Hot Plate | Awaiting confirmation |
| 30 | Grilled Marinated Chicken | $24.90 |
| 31 | Grilled Marinated Pork | $25.90 |
| 32 | Grilled Marinated Beef (350g) | $28.90 |
| 33 | Pad Thai | $20.90 |
| 34 | Pad See Ew | $20.90 |
| 35 | Pad Kee Mao | $20.90 |
| 36 | Egg Noodles with BBQ Chicken | $21.90 |
| 37 | Fried Rice | $20.90 |
| 38 | Chilli Fried Rice | $20.90 |
| 39 | Tom Yum Fried Rice | $20.90 |
| 40 | Pineapple Fried Rice | $20.90 |
| 41 | Chicken Noodle Soup | $20.90 |
| 42 | Tom Yum Seafood Noodles Soup | $25.90 |
| 43 | Tom Yum Soup | $20.90 |
| 44 | Tom Kha Kai | $20.90 |
| 45 | Chicken / Beef Salad | $21.90 |
| 46 | Deep-Fried Tofu Salad | $18.90 |
| 47 | Larb Salad | $21.90 |
| 48 | Coconut Rice | $7.50 |
| 49 | Steamed Rice | $6.50 |
| 50 | Roti | $3.80 |
| 51 | Home Made Peanut Sauce | $3.80 |
| 52 | Steamed Mixed Vegetables | $7.90 |
| 53 | Banana Dumplings | $12.90 |
| 54 | Kanom Tuay | $12.90 |
| 55 | Banana Fritter | $12.90 |
| 56 | Taro Dumpling | $12.90 |
| 57 | Sticky Date Pudding | $12.90 |
| 58 | Mixed Berries Crepes | $12.90 |
| L1 | Pad Thai | $14.90 |
| L2 | Pad See Ew | $14.90 |
| L3 | Pad Kee Mao | $14.90 |
| L4 | Fried Rice Chicken / Beef | $14.90 |
| L5 | Green Curry with Rice | $14.90 |
| L6 | Red Curry with Rice | $14.90 |
| L7 | Yellow Curry with Rice | $14.90 |
| L8 | Pad Kra Pao with Rice | $14.90 |
| L9 | Cashew Nut with Chilli Jam | $14.90 |
| L10 | Ginger Stir Fried with Rice | $14.90 |
| D1 | Thai Tea & Coffee | $9.99 |
| D2 | Juices | $4.99 |
| D3 | Soft Drinks | $3.99 |
| D4 | Sparkling Water | $3.90 |
| D5 | Coconut Water | $7.50 |
