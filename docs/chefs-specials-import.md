# Chef’s Special Recommendations import

Source: two user-supplied menu photographs (main specials and entrées).
V10 imports 20 menu listings: 19 new items and one reused signature dish,
with 23 priced variations and a published `chefs-special-recommendations` collection.
The existing `signature-dishes` collection retains its four memberships.
Prices below are AUD. `Standard` is the internal variation label for a single price.
Lamb Shank has four equally priced choices; no default curry is inferred.

| Printed dish | Category | Price AUD |
|---|---|---:|
| Crispy Fish Balls | entrees | $11.90 |
| Golden Crispy Chicken Maryland with Skin | entrees | $11.90 |
| Golden Bags | entrees | $11.90 |
| Grilled Pork Skewers with Sticky Rice | entrees | $15.90 |
| Signature Wings | entrees | $11.90 |
| Crispy Egg Pops | entrees | $11.90 |
| Soft Shell Crab | entrees | $17.90 |
| Prawns Spring Rolls | entrees | $17.90 |
| Crab Fried Rice | rice | $27.90 |
| Soft Shell Crab Curry Stir-Fry | stir-fries | $27.90 |
| Red Duck Curry | curries | $28.90 |
| Roast Duck with Chinese Broccoli | stir-fries | $28.90 |
| Lamb Shank with Curry | curries | $31.90 |
| Salmon Green Curry | curries | $28.90 |
| Asian Green Stir-Fry | stir-fries | $23.90 |
| Thai Crispy King Prawns | seafood | $29.90 |
| Crispy Pork Chinese Broccoli | stir-fries | $24.90 |
| Thai Basil Pork Mince | stir-fries | $23.90 |
| Salmon Salad | salads | $25.90 |
| Crispy Pork Salad | salads | $25.90 |

Crispy Pork Chinese Broccoli is mapped to the original seeded Crispy Pork & Broccoli
(ID `20000000-0000-0000-0000-000000000004`). Its existing name, description,
photo, publication and availability are preserved; the printed $24.90 price is
added as a Standard variation. This avoids duplicating the same dish.

The Main Chef’s Specials heading is represented by food categories (Curries,
Stir-fries, Rice, Salads, Seafood); Entrée items use Entrées. All 20 belong to the Chef’s
Special Recommendations collection. Thai Crispy King Prawns uses Seafood because the source does not specify a
preparation method beyond “crispy”.
Minor spelling/capitalization/punctuation is normalized (for example “ang” to
“and” and “green been” to “green beans”). The Crab Fried Rice description ends
with “seasoned” in the source; no additional seasoning ingredients are invented.
No photos, dietary badges, verified allergens or availability schedules are
inferred. Food profiles start NOT_REVIEWED, including every curry choice.

Deploy the updated backend normally. Flyway applies V10 once on startup. No
production database was accessed while preparing this import. Back up the database
before deploying. Do not run individual statements manually or edit applied SQL.
A conflicting item/collection slug intentionally fails the transaction instead of
overwriting existing staff edits. If the reused pork dish already has variations,
reconcile its prices before deploying this migration.

The items appear in the staff dashboard after migration. Public JSON is available
at `/api/menu/collections/chefs-special-recommendations/items`. The homepage still
requests Signature Dishes; this import does not replace that selection. The current
initial dashboard does not yet provide a price/variation editor.
