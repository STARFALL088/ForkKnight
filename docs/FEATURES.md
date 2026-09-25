# ForkKnight - Feature Chronicle

A living ledger of what stands in the realm, what is being forged right
now, and what the blacksmiths plan next. Updated as features land.

---

## Completed Features

### The Foundations
| # | Feature | Commit | Notes |
|---|---------|--------|-------|
| 1 | Repository browser + commit log table | `1646e7b` | Directory chooser, log loader on background threads |
| 2 | Commit details pane | `345db48` | Changed files with status badges + per-file diffs |
| 3 | Branch selector | `bb7a1a6` | List branches, per-branch history |
| 4 | Working changes tab | `b95bcb1` | Stage / unstage / commit / discard |
| 5 | History search | `cbe0bff` | Message / author / hash scopes, live filtering |
| 6 | Branch management UI | `c92dfad` | Raise (create), march (checkout), fell (delete) |
| 7 | Tags + stash | `4d4b517` | Press/melt sigils; Kamui vanish/summon |
| 8 | Dark theme (Night Sight) | `dab01ed`+ | Menu toggle, CSS overlay |

### The Great Rebrand - "Speak of the Realm" (`ef78f2e`)
All git vocabulary replaced with knight/anime codenames. The raw git
tongue is confined to a private CODEX map inside `Chronicle.java` and
never leaks to the UI.

| ForkKnight | Common tongue |
|------------|---------------|
| Realm / Feat / Banner / Sigil | repository / commit / branch / tag |
| Survey / Muster / Seal | log / status / commit |
| Enlist / Release / Restore | add / reset / checkout |
| Raise / March / Fell banner | create / switch / delete branch |
| Press / Melt sigil | create / delete tag |
| Kamui vanish / summon | stash push / pop |
| Fusion | merge |
| Scry | search |
| Night / Day Sight | dark / light theme |

### Algorithms & Data Structures delivered
- **Scryer** (`core/Scryer.java`) - 43-slot sparse trie over commit
  tokens for O(L) prefix search + bigram inverted index with posting-set
  intersection for multi-word AND queries
- **Weave** (`core/Weave.java`) - DAG lane assignment (first-fit with
  TreeSet free-lane pool + lane recycling, O(n log n)) powering the
  commit graph column; iterative stack-safe bloodline walks (50k depth
  tested)
- **Vault** (`core/Vault.java`) - O(1) LRU diff cache on access-ordered
  LinkedHashMap
- **Chronicle** (`core/Chronicle.java`) - NUL-separated porcelain
  parsing (rename-aware), compile-time codex guard, dual-stream
  subprocess draining with timeouts

### Fusion (`8c3ba1c`)
Merge support: `fuseBanner` seals a --no-ff fusion, `fusionInDispute`
detects conflicted fusions (UU/AA/DD), `abandonDisputedFusion` withdraws
cleanly. UI: Fuse button + dispute dialog with Withdraw option.

### The Herald (remote operations)
Allied realms (remotes) themed as allies on the roll:

| ForkKnight | Common tongue |
|------------|---------------|
| Allies / the roll | `git remote -v` |
| Rally | `git fetch --prune` |
| Recall | `git pull --ff-only` |
| Emissary | `git push <ally> <banner>` |

Core (`Chronicle.allies/rally/recall/sendEmissary`) + UI (allies combo
with Rally / Recall / Emissary buttons; allies load on realm open) both
complete. Recall refuses when trails have diverged (no history rewrites);
rally with no allies is a harmless no-op.

**Test suite: 54 green**

### Keyboard Shorts (the knight's quick orders)
- Ctrl+O - Seek Realm
- Ctrl+R - Muster the Field (jump to The Field tab + refresh)
- Ctrl+N - Seal the Vanguard (commit dialog)
- F5 - Rally the Allies + re-survey the trail
- Ctrl+F - Peer into the Scryer (focus + select search box)
- Ctrl+Shift+D - Day Sight / Night Sight toggle
- Ctrl+Q - Depart

### Re-seal (amend)
`reSeal` folds the vanguard into the newest feat and rewrites its
words (git commit --amend); `newestFeat` exposes HEAD for pre-filling
the dialog. Also fixed: tolls of root feats now resolve via diff-tree
--root (the very first feat previously showed an empty toll).

### The Council (realm statistics)
`Chronicler` computes the realm's tale with bounded min-heap TOP-K
selection (O(n log k)): heroes' standings, busiest days, fusion count,
campaign span and path heat. The CouncilDialog (Realm menu, Ctrl+I)
reads them aloud; path heat samples the newest 200 feats on a worker
thread so big realms never stall the UI.

### Scrying Lenses (range + hero filters)
The Scryer grows three combinable lenses, ANDed over the woven trail:
- text (trie + inverted index, as before)
- recency: All / last 7 / 30 / 90 days (`scryRecent`)
- hero: exact match from the trail's roster (`scryByHero`,
  `heroes()` in first-seen order)

The "Still" button clears all three at once. Hero options refresh with
every survey; the previous choice is re-selected when it still exists.

### The Knight's Memory (settings persistence)
`KnightMemory` - now a thin facade over the Ledger (SQLite). Remembers:
- the realms you had open and the active one (the knight's own set,
  restored on launch)
- the favored sight (night/day theme, saved on switch)
- window bounds (restored within sane minimums)
- feat annotations (local notes per hash)

### Chronicle Annotations (Knight's Notes)
Knights can attach persistent local annotations to any feat in the realm without touching git objects or remote history. Stored in `KnightMemory` under `note.<hash>`, exposed with an "Annotate..." dialog in `TalePane`, and marked with a scroll badge (`📜`) in the chronicle table.

### Trailing Banners & Campaign Divergence ("Banners Roll")
`tallyDivergence` measures ahead/behind feat counts relative to the raised banner (`git rev-list --left-right --count`). The "Banners Roll..." dialog (Ctrl+B / "Roll..." button) displays all banners, their frontier marks, and standing vs the active banner (`+N / -M`), allowing one-click marching.

### The Ledger (SQLite persistence + realm bookmarks)
`KnightDatabase` moves the knight's memory onto an embedded SQLite
database (`~/.forkknight/forkknight.db`, driver `org.xerial:sqlite-jdbc`
via JDBC; prepared statements, `ON CONFLICT ... DO UPDATE` upserts,
schema created on first connection). Tables: `settings`, `notes`,
`realm_bookmarks`. New Realm-menu orders: "Bookmark Current Realm..."
and "Bookmarked Realms..." - the dialog lists bookmarked realms and
opens the one chosen (double-click works too).

**Test suite: 80 green**

### The Ledger, hardened (versioned migrations) (`67017e2`)
`KnightDatabase` stops creating its schema ad hoc: every change is now an
entry in a `MIGRATIONS` list, recorded with `PRAGMA user_version` and
applied once, in order, inside one transaction (`schemaVersion()` is
exposed for tests). v1 = the legacy three tables, v2 = accounts (below),
v3 = `app_state`. The connection is opened once and reused - WAL journal,
`busy_timeout=5000`, `foreign_keys=ON`, `synchronous=NORMAL` - with all
access serialized on an internal lock, and every failure is wrapped in a
`KnightDbException` with a message meant for the UI.

### Accounts & passwords - the Order of Knights (`ba16ec3`, `ae83921`)
A `users` table joins the ledger (`id`, case-insensitive unique
`username`, `display_name`, `password_hash`, `is_guest`, `created_at`);
in migration v2 every legacy settings/notes/bookmark row is adopted by
the auto-created `keeper` account, so nothing is lost to the upgrade.
Passwords are hashed by `PasswordHasher`: PBKDF2-HMAC-SHA256, 600,000
iterations, stored as `pbkdf2-sha256$iters$salt$hash`, compared in
constant time with `MessageDigest.isEqual`; `matches()` answers `false`
for anything it does not recognise and never throws. `AccountService`
offers sign up, sign in/switch, sign out, change password and delete
account, refusing with an `AuthException` whose message is written for
the knight to read. `guest` is reserved for the passwordless Wanderer,
`AccountService.locked(account)` spots the keeper's `locked$...` sentinel
hash, and tests inject a fast 10,000-iteration hasher through a
package-private constructor so the suite stays quick.

### The remembered seat (`09ce669`)
The new global `app_state` table holds `account.current` - the id of the
knight who was signed in when the app last left. `KnightMemory` reads it
back on launch (falling back to `keeper`, then the Wanderer; a dismissed
knight does not haunt the launch) and rewrites it on every switch or sign
out, so the seat follows the rider across restarts.

### R1 - One immutable shortcut catalogue (`56f00a2`)
Every shortcut ForkKnight ships lives in the `forkknight.Shortcut` enum -
built once at class init, all fields final, no setter of any kind:

| Shortcut | Keys | Order |
|---|---|---|
| `SEEK` | Ctrl+O | Seek a realm |
| `MUSTER` | Ctrl+R | Muster the field |
| `SEAL` | Ctrl+N | Seal the vanguard |
| `RALLY` | F5 | Rally the allies |
| `COUNCIL` | Ctrl+I | Summon the council |
| `ROLL` | Ctrl+B | Banners roll |
| `DEPART` | Ctrl+Q | Depart |
| `DAY_SIGHT` | Ctrl+Shift+D | Day Sight toggle |
| `SCRY` | Ctrl+F | Peer into the scryer |

Requirement: shortcuts are **display-only forever** - no rebinding UI, no
per-user overrides, now or later; menus show the accelerators as a
read-only reminder. `ShortcutTest` pins the catalogue and fails if anyone
adds a rebinding surface.

### R2 - Several realms at once (`fb82029`)
A realm is now a first-class `RealmSession` (chronicle + banner + trail +
view state per open realm). The toolbar grows a realm picker listing every
open realm; each keeps its own raised banner, trail, scrying index and
selected feat, so switching paints from memory - no git, no disk re-open -
and then quietly re-surveys in the background (silent, selection kept).
The open set and the active realm are persisted per knight in the
settings keys `realms` (one path per line) and `realm` and restored on
launch; a realm that vanished meanwhile simply stays closed.

### The Knights Dialog (the seat, and who holds it) (`16566f3`)
Realm -> "Knights..." (with a "Riding as: <knight>" readout beside it)
opens **ForkKnight - The Order of Knights**: a "Who rides now: <seat>"
line, a sign-in/switch form (a password is required to take another
knight's seat), a join form (name 3-24 chars, optional "known as",
password 8+ with repeat), "Sign Out (to the Wanderer)", "Set Password..."
(which claims a locked ledger or changes the current password - failures
keep the dialog open so the knight can correct it right there) and
"Dismiss..." (delete the account behind a confirmation alert). Closing
the dialog with a changed seat swaps the whole workspace: the outgoing
knight's open realms are written down first, then the incoming knight's
sight, window bounds and remembered realms are restored. The Wanderer
rides without ever signing in, so the git viewer is never blocked.

**Test suite: 127 green**

---

## Planned Next (ideas, in rough order)

1. **Undo/redo of realm actions** - an action history with inverse ops
2. **Weave polish** - hover tooltips with feat summaries, clickable curve selection
3. **Distribution** - jpackage a self-contained launcher

---

*Last updated: The Order of Knights - local seats, passwords, multi-realm sessions and an immutable shortcut catalogue.*
