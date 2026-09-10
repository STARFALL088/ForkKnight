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

**Test suite: 70 green**

---

## Planned Next (in rough order)

1. **Settings persistence** - remember last realm, theme, window size
   (a small JSON vault in ~/.forkknight)

---

*Last updated: scrying lenses forged; the trail bends to three lenses.*
