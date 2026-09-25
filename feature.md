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
- the last realm (auto-reopened on launch when it still exists)
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

**Test suite: 113 green**

---

## Requirements (recorded before any code)

Hard requirements, not ideas. They constrain what may be built.

### R1 - Shortcuts are fixed and must never become editable
Every keyboard shortcut the app ships is **immutable**: no rebinding
UI, no settings panel for keys, no per-user shortcut overrides - now
or later. Menus display accelerators as a read-only reminder of what
the keys already do. If a shortcut is wrong it changes in the source,
never at runtime.

*Standing:* accelerators are set in code (`App.buildMenuBar`) and no
rebinding surface exists. The requirement is to keep it that way - any
future settings surface must treat shortcuts as display-only.

### R2 - Support multiple realms (git repositories) at once
The knight must be able to hold **several realms** and work in all of
them. Every feature already built (survey, muster, seal, banners,
sigils, fusion, herald, scrying, notes...) must operate on whichever
realm is *active*, and moving between realms must not cost a full
re-open from disk.

*Current standing:* the app holds exactly one `Chronicle` behind a
single realm path field, so this is a real change - a realm must
become a first-class, selectable session rather than a global.

*Acceptance sketch:*
- several realms open at once, switched cheaply (no disk re-open)
- each realm keeps its own raised banner, trail and view state
- notes and bookmarks stay scoped per realm (they already key on path)
- the open set of realms is restored on the next launch

---

## Planned Next (ideas, in rough order)

1. **Undo/redo of realm actions** - an action history with inverse ops
2. **Weave polish** - hover tooltips with feat summaries, clickable curve selection
3. **Distribution** - jpackage a self-contained launcher

---

*Last updated: the Ledger landed - SQLite persistence and realm bookmarks.*
