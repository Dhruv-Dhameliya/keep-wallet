# Keep Wallet – feature checklist

Derived from the three reference boards in `kacharesource/`:

- **01 / DAILY** – `Daily expense tracker UI board-2.png` (teal, monthly view)
- **04 / POCKETS** – `Pockets expense tracker UI board-4.png` (envelope budgeting, pastel tiles)
- **06 / RHYTHM** – `Rhythm weekly expense tracker-3.png` (terracotta, weekly view)

and refined by the owner's later feedback (balance card, minimal home, new categories, settings, backup, month browsing, bills, goals, and the Material 3 redesign).

`[x]` = implemented. Behaviour is covered by the automated tests and reviewed in rendered screenshots, except items marked † (receipt photo picking, the camera, system file pickers and chart tap-to-inspect), which can't run in the test environment and were only checked by reading the code.

## 1. Identity and look

- [x] **New logo**: a flat 2D wallet with a "K" cut out of it and a coin clasped on its corner, as an adaptive icon with a one-colour version for themed icons; the same mark is used in the app and in notifications
- [x] **First launch only**: an animated logo card (light or dark to match the theme) and a short welcome to pick a palette, currency and first account; every later start opens straight away
- [x] **Material 3 design** (the liquid glass look is gone): tonal surfaces, soft shadows, a colourful second highlight colour, a Material-style bottom bar with the add button in the middle, quick spring and fade animations, a count-up on the balance
- [x] Tighter, more compact layout and type; the balance card is larger
- [x] **Ten ready-made palettes** (Forest, Ocean, Midnight, Sunset, Berry, Meadow, Ember, Aurora, Candy, Noir) applied with one tap, plus 8 accent colours, a **custom colour slider**, and Light / Dark / Pure black / Pure white / System backgrounds, all mixable

## 2. Home

- [x] Default: **balance card → monthly plan → spending graph → recent activity → your pockets** (everything else off); drag to rearrange in Customize home
- [x] **Balance card**: bank balance (or the total of all accounts, a setting), the month's income and expense and an eye to hide the amounts; the month is chosen from the dropdown in the top bar (or by holding the card)
- [x] **Hold the card (or tap the month) to choose a month.** The whole app follows it: Home sections, Balance, Activity, Insights and Plan. A past month shows the balance as it stood at the end of that month, with a "Back to now" button
- [x] **Every section opens**: the graph (daily average, busiest day, top categories), pockets (spent, limit, per-day allowance, latest entries, link to Activity), the monthly budget (daily average, forecast, left per day), recent activity (show more), income and remaining (jump to Activity / Plan)
- [x] Spending graph with a period dropdown; bars, line or donut chosen in Settings
- [x] New sections, off by default and added from Customize home: **Highlights**, **Upcoming bills**, **Savings goals**
- [x] Existing installs keep their layout; the new sections arrive switched off

## 3. Highlights, bills and goals (new)

- [x] **Highlights**: daily average, month-end forecast against the budget, no-spend days, biggest expense, change against the same stretch of last month, share of income saved. Tap a figure for a plain-words explanation
- [x] **Bills & subscriptions** (Plan → Bills): weekly, monthly or yearly, expense or income, with category and account. Mark paid, skip, pause, edit; or switch on "add automatically" and due entries are booked when the app opens, including any missed dates. Monthly commitments and what is due in 30 days are summed; overdue bills are flagged
- [x] **Savings goals** (Plan → Goals): target and saved so far, quick +10 / +50 / +100, any amount in or out, edit and delete, progress and a total; kept apart from account balances
- [x] **Quick fill** in the entry form: the merchants you use most, one tap to fill name, category, account and amount
- [x] **Export to spreadsheet** (CSV) next to the full backup

## 4. List and grid views

- [x] A small list / grid switch where it helps: Plan budgets, Plan goals, Balance accounts, Insights categories and the Categories manager; each screen remembers its choice. (Not on Home, as asked.)

## 5. Balance page

- [x] Back arrow; **Bank accounts** and **Wallets & cash** with balances and the total; the month's income, expense and net with a month selector (shared with the rest of the app)
- [x] Tap an account to see its latest entries and reach "Edit account or balance"; add accounts; delete empty ones
- [x] Balances always equal the opening amount plus income minus expenses

## 6. Add / edit entry and details

- [x] Full-page form in three steps (**Amount → Category → Details**), quick +20 / +50 / +100 / +500 chips, the keypad right under the amount, a **category grid**, accounts Bank / Wallet / Cash, **Enter moves to the next field**, one-line note, receipt from the **camera or gallery** †
- [x] "$152 left after saving" card; editing says **Update expense / income**
- [x] Entry details as a simple bill with **Edit** and **Delete** (with Undo)

## 7. Activity, Insights, Plan

- [x] Activity: **All / Expenses / Income tabs with counts**, category and account filters, day strip, search, sort, **days that fold away**, per-day totals
- [x] Insights: donut, **category rows that open to their latest entries** (or tiles), treemap, ranked top categories, week / month, spending / income side by side
- [x] Plan: Budgets, Goals and Bills in one place; budgets open to a per-day allowance, latest entries and edit; Essentials / Flexible, week / month, move money
- [x] Percentages always add up to exactly 100

## 8. Settings

- [x] A summary header, **search**, and groups that fold away, each in its own colour (the list keeps its scroll position when you come back from an option): Appearance (live **preview**, accent colours, background tiles, palettes, custom colour, currency), Home, Money (accounts, categories, bills, goals), Smart capture, Backup & sync, Data, About
- [x] **Hide balances** switch; **export / import** backup, **spreadsheet export**; Google sync is shown as *coming soon*

## 9. Notification capture

- [x] `NotificationListenerService`, off by default, with an explanation, status, denial handling and a deep link to the system screen
- [x] Parser for bank / UPI / card text; ignores OTPs, failed payments, requests, promotions, bills and balances
- [x] High-priority alert with **Add** and **Dismiss**; prefilled form; nothing saved without confirmation; only parsed fields are stored

## 10. Round 2: privacy, money moves and rules

- [x] App lock: 4–6 digit PIN (salted PBKDF2, never stored as typed), optional fingerprint with PIN fallback, "ask every time / 1 min / 5 min", lock now, change PIN, turning it off needs the PIN, wrong-PIN cooldown after 5 tries
- [x] "Hide in recent apps" blanks the preview and blocks screenshots while the lock is on
- [x] Transfers between accounts: two linked entries, one line in Activity, balances move, no income/expense distortion, deleting removes both halves
- [x] Split a bill across categories; parts must add up exactly; each part links to the others
- [x] Tags in notes (`#trip`): tag chips in Activity, `#tag` search, tag suggestions in the form
- [x] Rules for detected payments (match text → category, account, name) with a "remember this?" prompt after filing by hand
- [x] Budget rollover per category: unspent amount carries forward (up to 12 months, never negative) and shows in Plan, Home and the entry form
- [x] Duplicate-entry warning (same amount, category and account within 10 minutes) with Add anyway / Cancel
- [x] Saved Home layouts: Minimal, Detailed, Bills focus, and your own named layouts
- [x] Backup version 3 carries transfers, splits, rollover and rules; half a transfer is refused on import; database moves to version 4 automatically

## 11. Round 3: capture, shortcuts and celebration

- [x] Home-screen widget: this month's spending against the budget with a button that opens the add form; shows no amounts while the app lock is on
- [x] Long-press app-icon shortcuts: Add expense, Add income, Scan receipt, Speak an entry, plus "add again" shortcuts for the entries you repeat most (hidden while the app lock is on)
- [x] Currency converter (Insights tab and Settings, never on Home): works offline with built-in approximate rates; the "Update rates" button is the one optional network call
- [x] Receipt scanning on the phone (Google ML Kit text recognition, bundled model): total, shop name and date are read from the photo, the photo stays attached, and everything is checked by you before saving
- [x] Voice entry with the system speech recogniser (offline when the language pack is installed): "spent 250 on lunch at Subway" fills amount, type, category and name; a typed sentence works the same when no recogniser exists
- [x] Custom categories with an emoji or a photo instead of the icon; photos are cropped small and travel in backups
- [x] Seasonal accent packs (Winter, Holi, Spring, Summer, Monsoon, Diwali, Autumn, Halloween, Christmas) with the pack for the current month suggested first
- [x] Haptic feedback and confetti when a savings goal is fully funded and when a finished month ends inside the budget

## 12. Material 3 rebuild

- [x] Colour schemes generated with Material Color Utilities from the accent (Balanced, Vibrant, Faithful and Neutral styles) or taken from the wallpaper (Material You, Android 12+)
- [x] Standard Material 3 structure: top app bars on every page (medium collapsing bar in Settings), navigation bar with the indicator pill, extended floating "Add" button that shrinks when scrolling, segmented buttons, list items, filter and input chips, elevated cards
- [x] Material motion: tabs fade through, pages slide a short way; the home screen's detected-payment card scrolls into view
- [x] Settings remember which groups are folded and where the page was scrolled
- [x] Tags stay on screen when chosen (filled, ticked, bold), and you can make your own tags
- [x] Built as an optimised release APK (R8, baseline profile) signed with the same local key, so it updates the installed debug build in place

## 13. Round 4: reminders, review, other currencies and polish

- [x] **Notifications**: a bell on Home with a count, and a page listing payments waiting, bills due in three days or overdue, and budgets at 80% or over. Optional bill reminders, budget alerts and an evening nudge (all off by default; text is masked while the app lock is on; each situation is reminded once)
- [x] **Review**: monthly and yearly figures, share of income kept, top categories, month by month with best and hardest month, net worth over the last 12 month-ends, a forecast for the month under way (pace plus bills still due), and PDF export
- [x] **Paid in another currency**: pick a currency, type what was paid, the home amount is worked out with the converter's rates, and the original amount is kept with the entry, in backups and on its details page
- [x] **Black and white by default** (existing installs are moved to it once); Mono is the first palette, with all other palettes, seasonal packs, colour styles and wallpaper colours still a tap away in Settings
- [x] **Hide balance on open**: the card starts hidden each time the app opens; the eye shows it until the app is left; "Hide balance on open" and "Hide balances" are both in Settings
- [x] **Activity**: search moved into the header (replacing the plus), compact month control in the header, slimmer day strip, tighter rows. The tag feature was removed (notes are still searchable text)
- [x] **Settings**: sections are plain names above their settings (no folding), compact rows, and a new Notifications section
- [x] **Motion**: one set of timings and curves; pages push and pop as a matched pair, tabs slide in the direction you move; lists animate their changes
- [x] **First-run welcome** redesigned: live preview card, appearance choice, larger palette tiles, currency pills, hide-balance switch
- [x] New logo, 95% interface size, near-neutral surfaces with hairline outlines, Inter typeface, arm64-v8a release build

## 14. Quality

- [x] Upgrading keeps all data: the database moves through versions 2 → 3 → 4 automatically and old backups still import
- [x] Money is exact integer arithmetic; period boundaries are right across time zones and daylight saving; recurring dates stay on their day through short months and leap years
- [x] Layout checked on a 320 dp screen at 1.5× font; text contrast is tested across background modes, accents, palettes and custom colours
- [x] Offline by design: no cloud and no analytics. The only network permission is for the converter's optional "Update rates" button

## Not built

- Google sync (needs a Google Cloud project; the row is a placeholder), reminders as system notifications, SMS reading
