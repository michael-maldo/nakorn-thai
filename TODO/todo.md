
TODO
[Done] Checkout rechecks all collections shared with the Menu page.


Main header
-- make main header menu font a little bigger and stick to across pages -- always available when scrolling

ordering online / menu
-- make the menu selector sticky -- below the header when user starts scrolling
-- create category header menu below menu selector also sticky  -- which scrolls horizontally as the user scrolls down the menu
-- category header menu - responsive design - create left and right buttons at the end
-- also make the header menu touch sensitive - for horizontal scrolling
-- align prices to the right -- beside the "add to order button"
-- implement staff user defined menu catalog variants( ex. size s, m, l, -- options pork, chicken, pork --- with corresponding setting of price for the variant+ ) - that will available in the dashboard to manage menus and display these options in the menu ordering popup
-- also add features to add badges for allergens, dietary preferences in the dashboard and display in the customer menu pages / menu ordering popup

the sticky behavior is meant to address main header, menu selector, category headers accessible even when out of focus when use scrolls down on the menu

home page --
-- specials section popup make order online trigger add to cart as its action
-- view menu button -- target menu page

dashboard
-- add timezone,
-- define store hours -- days and time,
-- feature to select for dates that store is closed
-- to disable ordering and booking

restaurant menu
dashboard to control menu collection by days, date and time
-- availability by days and hours (like lunch specials)
-- holiday special menus -- made for specific holidays -- (like father's day, Christmas, Valentines Day..)

design promotions and rewards -- like discount coupons, birthday celebrant promos, etc...
-- ask to implement best practices in the restaurant industries
-- utilize dashboard -- for defining promos


PHASE 1 — Customer menu UX
1. Sticky site header
2. Sticky menu selector
3. Sticky category navigation
4. Horizontal/touch category navigation
5. Price/button alignment
6. Homepage menu/order CTAs

PHASE 2 — Catalog model
7. Menu variants/options
8. Variant pricing
9. Dietary badges
10. Allergens

PHASE 3 — Restaurant scheduling
11. Restaurant timezone
12. Store opening hours
13. Closed dates
14. Disable ordering when closed
15. Disable booking when closed

PHASE 4 — Menu availability
16. Menu availability by weekday/time
17. Date-specific menus
18. Holiday/special-event menus

PHASE 5 — Promotions
19. Promotion domain design
20. Coupon MVP
21. Scheduled promotions
22. Birthday/customer promotions
23. Rewards/loyalty later


For **Phase 1 — Customer Menu UX**, use this workflow every time. The goal is to make Codex efficient without losing your application's structural boundaries.

### Phase 1 — Customer Menu UX

Your Phase 1 scope is:

1. Sticky main header
2. Sticky menu selector
3. Sticky category navigation
4. Horizontal/touch category navigation
5. Price/button alignment
6. Homepage menu/order CTAs

Before starting, make sure your repository contains a root-level `AGENTS.md` describing the architecture, folder ownership, shared-code rules, and the instruction that **existing repository structure wins over generic best practices**. Codex should not need a long-running chat to remember these rules.

Then follow this checklist for each task:

* Start a **fresh Codex thread** for one bounded change.
* Tell Codex to follow `AGENTS.md`.
* Give it the **feature entry point or owning domain**, not the whole repository.
* Tell it to inspect the relevant directory structure first.
* Allow it to follow **direct dependencies only**.
* Explicitly prohibit unrelated refactoring and new architectural patterns.
* Give it measurable requirements.
* Give it a build/test stopping condition.
* Review the diff.
* Commit the completed task.
* Start a new Codex thread for the next task.

For example, your first Phase 1 task should be the sticky navigation stack. I would send Codex this:

```text
PHASE 1 — CUSTOMER MENU UX
TASK 1 — STICKY NAVIGATION

Follow the architecture, folder structure, and boundaries defined in AGENTS.md.

Existing repository structure takes precedence over generic best practices.

GOAL

Improve navigation on the customer restaurant menu so the important navigation controls remain accessible while the user scrolls.

REQUIREMENTS

1. Main site header
   - slightly increase the navigation font size
   - make the header sticky
   - it must remain available across relevant customer-facing pages while scrolling

2. Menu selector
   - make it sticky below the main header
   - it must remain accessible while scrolling through menu items

3. Category navigation
   - place it below the menu selector
   - make it sticky below the menu selector
   - make it horizontally scrollable
   - support touch/swipe horizontal scrolling
   - provide left/right navigation controls when appropriate on smaller widths

The intended sticky stack is:

Main Header
↓
Menu Selector
↓
Category Navigation
↓
Scrollable Menu Content

SCOPE

Frontend only.

Start from the existing customer menu feature/page.

Before modifying code:

1. Identify the owning frontend domain/feature.
2. Show the relevant existing directory tree only.
3. Identify the existing:
   - main header component
   - menu page/layout
   - menu selector
   - category navigation, if one already exists
   - relevant styling files/components
4. State which files need to change and why.

Do not scan the entire repository.

Follow only direct dependencies required to understand this UI flow.

ARCHITECTURAL CONSTRAINTS

- Preserve the existing folder structure.
- Follow neighboring component and styling patterns.
- Do not introduce a parallel architecture.
- Do not create new top-level directories.
- Do not move unrelated files.
- Do not introduce a new state-management library.
- Reuse existing components/hooks/utilities where appropriate.
- Do not refactor unrelated code.
- If code belongs in a shared location, verify that it is genuinely shared before placing it there.

IMPLEMENTATION

After identifying the existing structure:

1. Implement the minimum changes required.
2. Ensure sticky offsets work correctly so the three sticky elements do not overlap.
3. Preserve responsive behavior.
4. Check desktop, tablet, and mobile behavior.
5. Preserve accessibility and keyboard interaction where relevant.

VALIDATION

Run the relevant frontend tests/build.

At completion report:

- files modified
- files created
- why each file belongs where it does
- any architectural boundary crossed
- build/test result

Stop after this task.
Do not continue with other Phase 1 TODOs.
```

That last sentence is important: **“Stop after this task.”** It prevents Codex from seeing your broader TODO list and deciding to “helpfully” continue.

Once Task 1 is clean and committed, start a fresh thread for price alignment:

```text
PHASE 1 — CUSTOMER MENU UX
TASK 2 — MENU ITEM PRICE / CTA ALIGNMENT

Follow AGENTS.md.

GOAL

On the customer ordering menu, align each item's price to the right beside the existing "Add to order" control.

SCOPE

Frontend customer menu item/card component only.

Before modifying:

1. Locate the existing menu item/card component.
2. Show the relevant directory location.
3. Identify its existing styles/layout.
4. Confirm whether any shared component is involved.

CONSTRAINTS

- Preserve existing folder structure.
- Reuse the current design system/layout approach.
- Do not change ordering logic.
- Do not modify backend/API/database code.
- Do not refactor unrelated components.
- Preserve responsive/mobile layout.

IMPLEMENT

Make the minimum layout/styling change.

VALIDATE

Run the relevant frontend build/tests.

Report changed files and stop.
```

Then do the homepage CTAs as another task:

```text
PHASE 1 — CUSTOMER MENU UX
TASK 3 — HOMEPAGE CTAS

Follow AGENTS.md.

GOAL

Correct the homepage calls-to-action.

REQUIREMENTS

1. Specials section popup:
   "Order Online" should trigger the application's existing add-to-cart/order flow for that special.

2. "View Menu":
   navigate to the existing customer menu page.

SCOPE

Start with the homepage/specials feature only.

Follow direct dependencies only when necessary to locate:
- existing cart/order action
- existing menu route

Before modifying:

1. Show the relevant existing homepage directory structure.
2. Identify the existing specials component.
3. Identify the existing cart/order hook/service/action that should be reused.
4. Identify the existing menu route.

CONSTRAINTS

- Reuse existing cart/order logic.
- Do not implement duplicate ordering logic inside the homepage.
- Reuse existing routing.
- Do not modify the backend unless the existing flow genuinely requires it.
- Preserve existing application structure.
- No unrelated refactoring.

VALIDATE

Run relevant frontend tests/build.

Report modified files, explain reuse of existing ordering/routing logic, then stop.
```

After these three commits, I would do a small **Phase 1 integration review**. This is one of the few times a slightly broader prompt is justified, but still don't ask it to analyze the whole repository:

```text
PHASE 1 — CUSTOMER MENU UX
INTEGRATION REVIEW ONLY

Do not modify code initially.

Follow AGENTS.md.

Review only the customer-facing frontend areas changed during Phase 1:

- main header
- customer menu navigation
- menu item layout
- homepage specials/order CTA
- menu routing

Check for:

- inconsistent sticky offsets
- responsive issues
- duplicated logic
- component boundary violations
- accessibility regressions
- routing inconsistencies
- styling regressions
- code placed outside the correct feature/domain
- unnecessary new shared abstractions

Do not inspect unrelated application domains.

First report findings.

If there are clear defects introduced by Phase 1, make only the minimum corrective changes.

Run the frontend tests/build afterward.
```

Your Git workflow can mirror this neatly:

```text
feature/customer-menu-ux
    │
    ├── commit 1
    │   sticky header/menu/category navigation
    │
    ├── commit 2
    │   menu price/button alignment
    │
    ├── commit 3
    │   homepage CTA behavior
    │
    └── commit 4
        Phase 1 integration fixes, if required
```

And this is the compact checklist I'd keep beside you while using Codex:

```text
CODEX TASK CHECKLIST

[ ] One clear outcome
[ ] Fresh thread for a new task
[ ] "Follow AGENTS.md"
[ ] Existing structure wins over generic best practices
[ ] Give an entry point/domain
[ ] Inspect relevant directory tree first
[ ] Do not scan entire repository
[ ] Follow direct dependencies only
[ ] Identify files before editing
[ ] Preserve folder/package/component boundaries
[ ] No unrelated refactoring
[ ] No unnecessary new abstractions/dependencies
[ ] Reuse existing application mechanisms
[ ] Define expected behavior
[ ] Define validation/build/test
[ ] Tell Codex when to stop
[ ] Review git diff yourself
[ ] Commit working change
[ ] New thread for next bounded task
```

The most important workflow change for you is this:

```text
Repository
   │
   ├── AGENTS.md          ← persistent architectural memory
   ├── architecture docs
   │
   └── source code
          │
          ▼
     Fresh Codex task
          │
          ▼
     bounded inspection
          │
          ▼
       one change
          │
          ▼
         test
          │
          ▼
       git diff
          │
          ▼
        commit
```

That lets you stop depending on one giant Codex conversation to protect your architecture. Your **repository becomes the source of architectural truth**, while each Codex thread becomes a disposable implementation session.


Yes. In fact, this is one of the few times I would deliberately allow Codex to inspect the repository **fairly broadly**. Creating `AGENTS.md` is an architectural discovery task, and you want it grounded in the structure that actually exists rather than an architecture Codex invents.

I would make this a **read/analyze first, write second** task. Paste this into Codex from the repository root:

```text
TASK
Create a root-level AGENTS.md for this repository.

PURPOSE

AGENTS.md will be the persistent architectural and implementation guide
for future Codex sessions.

I want future Codex tasks to be able to start in fresh conversations while
still respecting the existing application architecture, directory structure,
domain boundaries, naming conventions, and implementation patterns.

IMPORTANT

Do not redesign, refactor, reorganize, or modify the application.

This task is documentation only.

The existing repository is the source of truth.

Existing structure and established project patterns take precedence over
generic best practices.

Do not invent an ideal architecture that differs from the repository.

DISCOVERY

Inspect the repository sufficiently to understand its actual structure.

Determine:

1. Top-level repository structure.
2. Frontend application structure.
3. Backend application structure.
4. Database/migration structure.
5. Domain/feature boundaries.
6. Shared/common infrastructure.
7. Frontend component organization.
8. Frontend API/service organization.
9. Backend controller/service/repository organization.
10. DTO/entity/model conventions.
11. Configuration structure.
12. Testing structure.
13. Existing naming conventions.
14. Build tools and important validation commands.
15. How cross-layer features normally flow through the application.

Pay particular attention to whether the application follows
domain-oriented / vertical-slice organization.

Do not assume this architecture merely because I described it.
Verify it against the repository.

ARCHITECTURAL MAPPING

For representative existing features, trace enough code to understand the
normal application flow, for example:

Frontend page/component
→ frontend hook/service/API client
→ HTTP API
→ backend controller
→ service/domain logic
→ repository
→ PostgreSQL/database

You do NOT need to deeply analyze every implementation.

The goal is to discover structural conventions and boundaries, not to
understand every line of code.

EFFICIENCY

Avoid unnecessary deep inspection of:

- node_modules
- build/
- target/
- dist/
- generated files
- IDE metadata
- binary assets
- dependency caches
- logs

Prefer directory structure, configuration files, representative source
files, and representative vertical slices.

If several domains follow the same pattern, inspect enough examples to
establish the pattern rather than exhaustively reading every domain.

CREATE

After discovery, create:

./AGENTS.md

The document should be concise enough to serve as useful context for
future Codex sessions.

Include sections for:

# Project Overview

Briefly describe the application's purpose and technology stack based on
what actually exists in the repository.

# Repository Structure

Document the important top-level directories and their responsibilities.

# Architecture

Describe the architectural style actually used by the application.

Explain domain/feature/vertical-slice boundaries where applicable.

# Frontend Structure

Document:

- where pages live
- where feature components live
- where shared components live
- hooks
- API clients/services
- routing
- providers/context/state
- styling conventions

Only document patterns supported by the repository.

# Backend Structure

Document:

- package/domain organization
- controllers
- services
- repositories
- entities/models
- DTOs
- configuration
- security
- cross-cutting infrastructure

# Database

Document:

- PostgreSQL organization
- Flyway migration location
- migration naming/versioning convention
- important schema/domain conventions

Explicitly state that already-applied Flyway migrations must not be
modified and schema changes require new migrations, if that matches the
existing project.

# Architectural Boundaries

Document rules future agents should follow, including:

- determine the owning domain before adding code
- preserve existing folder/package structure
- follow neighboring implementation patterns
- avoid creating parallel architectures
- avoid unnecessary new top-level directories
- keep feature-specific code within its owning domain
- use shared/common areas only for genuinely shared concerns
- avoid unrelated refactoring

# Cross-Layer Changes

Explain how future Codex sessions should approach changes spanning:

frontend
→ API
→ backend
→ database

Instruct future agents to start from the owning feature and follow only
the dependencies necessary for that change rather than scanning the
entire repository.

# Existing Patterns Win

Explicitly state:

Existing repository structure and established patterns take precedence
over generic or textbook best practices.

Future agents must not reorganize the application merely because another
architecture would also be valid.

If an architectural change appears necessary, it should be identified
and explained rather than silently introduced as part of an unrelated
feature.

# Testing and Validation

Document actual commands available in this repository for:

- frontend tests
- frontend build
- backend tests
- backend build
- any linting/static analysis that actually exists

Do not invent commands.

# Codex Working Rules

Include instructions for future Codex sessions:

1. Read this AGENTS.md before making changes.
2. Identify the owning domain/feature.
3. Inspect the relevant local structure first.
4. Follow direct dependencies only as necessary.
5. Identify files that need modification before editing.
6. Preserve architectural boundaries.
7. Reuse existing components/services/patterns.
8. Avoid unrelated refactoring.
9. Do not introduce dependencies without justification.
10. Make the smallest coherent change.
11. Run relevant validation.
12. Report files changed and why.
13. Stop when the requested task is complete.

# Repository-Specific Findings

Include any important conventions discovered during inspection that future
Codex sessions need to know but that do not fit naturally into the
sections above.

ACCURACY REQUIREMENT

Every repository-specific statement in AGENTS.md must be supported by
something actually observed in the repository.

If something cannot be determined confidently, either omit it or mark it
as needing confirmation.

Do not invent conventions.

FINAL REPORT

After creating AGENTS.md, do not modify any other file.

Report:

1. AGENTS.md created
2. directories/features inspected
3. architectural pattern discovered
4. important conventions captured
5. anything that could not be determined confidently
6. git diff --stat
7. confirmation that no application source files were modified

Stop after creating and reviewing AGENTS.md.
```

### Why I'd allow broader inspection this time

Normally we're trying to achieve:

```text
Task
  ↓
one domain
  ↓
direct dependencies
  ↓
change
```

But `AGENTS.md` is different:

```text
             Repository
                  │
       ┌──────────┼──────────┐
       ▼          ▼          ▼
    Frontend    Backend      DB
       │          │          │
       └──────────┼──────────┘
                  ▼
           Architecture map
                  │
                  ▼
             AGENTS.md
```

You're effectively spending some Codex allowance **once** to reduce context consumption on many future tasks.

There is still no reason for Codex to read every `.java`, `.jsx`, `.css`, test, or migration. That's why the prompt says to inspect **representative vertical slices**.

For example, if your menu domain establishes:

```text
frontend
  menu page
     ↓
  menu API

backend
  MenuController
     ↓
  MenuService
     ↓
  MenuRepository
     ↓
PostgreSQL
```

and orders follow essentially the same convention, Codex doesn't need a detailed line-by-line examination of both simply to write the architectural instructions.

### After Codex creates it

**Don't immediately trust the generated `AGENTS.md`.**

Run:

```bash
git diff -- AGENTS.md
```

Read it yourself. You're specifically looking for Codex inventing rules that aren't actually part of your architecture.

Watch for statements such as:

```text
All business logic MUST use domain services.
```

when your application doesn't actually do that, or:

```text
Every feature must contain:
components/
hooks/
api/
models/
utils/
```

when that's just a pattern Codex decided would be nice.

The document should describe **your application**, not Codex's preferred application.

Once you're satisfied, commit it separately:

```bash
git add AGENTS.md
git commit -m "docs: add Codex repository guidance"
```

Then start a **new Codex thread** for Phase 1.

Your first message becomes something much smaller:

```text
Read and follow AGENTS.md.

Phase 1 — Customer Menu UX
Task 1 — Sticky navigation.

Start from the existing customer menu feature.

Before editing:
1. identify the owning feature
2. inspect its relevant directory structure
3. identify the header, menu selector and category navigation components
4. list the files that need modification

Do not scan unrelated domains.

[requirements...]
```

At that point you can stop maintaining one enormous Codex conversation simply to preserve architectural context.

**Spend context once creating a trustworthy `AGENTS.md`; then spend your future Codex allowance on the feature being changed rather than repeatedly rediscovering the application.**



there is this odd behavior in my UI in my restaurant app codex has designed -- not sure if you are familiar with this --- on desktop i cannot use keyboard numbers -- i have to use the up/down control on the input field --- if i use the keyboard i am not able to actually change it -- values are intermittent -- when i put more that 3 digits it always result to 20

=====================

it is worse in the mobile --- since it does not have hover -- the controls does not show-- and i can remove 1 and replace to example 7..  if i put more numbers -- it give just alway 20
are you familiar with this issue?


Fix the quantity input UX across desktop and mobile.

Current problem:

* Keyboard editing of the number input behaves incorrectly.
* Clearing the existing value is difficult/impossible because validation appears to run on every keystroke.
* Entering values above the configured maximum immediately changes the value to 20 while typing.
* Desktop currently relies too much on native number-input spinner controls.
* On mobile, those controls are unavailable or difficult to use.

Requirements:

1. Do not clamp or normalize the quantity on every keyboard keystroke.
2. Allow the input to temporarily contain an empty string while the user is editing.
3. Keep the editable input state as a string while typing.
4. Validate and clamp the final numeric value on blur, Enter, or when committing the quantity.
5. Respect the existing minimum and maximum business rules.
6. Add explicit touch-friendly decrement and increment buttons:
   [-] [quantity input] [+]
7. The decrement/increment buttons must work on desktop and touch devices.
8. Use `inputMode="numeric"` for mobile numeric keyboard support.
9. Do not depend on browser-native number input spinner controls.
10. Preserve the existing application architecture, component boundaries, state-management conventions, styling system, and domain structure.
11. Do not introduce unrelated refactors.

Expected behavior:

* If quantity is 1, the user can select it or delete it and type 7.
* Typing multi-digit numbers must not be interrupted while typing.
* If the allowed maximum is 20 and the user types 75, allow the user to finish entering 75, then normalize to 20 when the value is committed.
* Empty/invalid values should normalize safely to the configured minimum.
* Both keyboard and touch interaction must work reliably.

Before making changes, inspect the existing quantity component and identify exactly where per-keystroke clamping or normalization currently occurs. Briefly report the cause, then implement the smallest architecture-consistent fix.
