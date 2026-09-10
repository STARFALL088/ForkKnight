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

---

## Planned Next (in rough order)

1. **Keyboard shorts** - Ctrl+N seal, Ctrl+F scry focus, Ctrl+R muster,
   F5 rally + survey refresh
2. **Re-seal (amend)** - fold new work into the last feat
3. **Realm statistics panel** - lane counts per banner, feats per hero
   (a nice histogram), busiest paths
4. **Chronicle range scrying** - "last 7 days" / "by hero X" quick
   filters using the Scryer index
5. **Settings persistence** - remember last realm, theme, window size
   (a small JSON vault in ~/.forkknight)

---

*Last updated: the Herald is forged and standing.*
