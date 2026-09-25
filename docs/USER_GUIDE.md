# ForkKnight User Guide

This guide explains how to use the ForkKnight application to perform Git operations using the knightly vocabulary interface.

## Getting Started

### Launching the Application

```bash
./gradlew run
```

Upon first launch, you'll be prompted to select a Git repository (Realm) to work with.

## Main Interface Overview

The ForkKnight interface consists of several key areas:

1. **Realm row** - the realm picker (every open realm, one combo) with
   the active realm's path, and a "Seek..." button
2. **Banner row** - banner selector with Raise / March / Fuse / Fell,
   sigil selector with Press / Melt, and the Herald (allies combo with
   Rally / Recall / Emissary)
3. **Scroll tab** - the woven chronicle: the Weave graph column plus
   Mark / Hero / Day / Feat columns, with the scrying row above
   (text box, scope, Days lens, hero lens, Still button) and the Tale
   Pane below (dispatches + recount)
4. **The Field tab** - working changes: Muster / Enlist / Release /
   Enlist All / Seal / Re-seal / Banish / Kamui / Summon
5. **Status bar** - state and helpful messages

On launch, ForkKnight reopens every realm you had open (standing in the
one you left, if it still exists); otherwise use Realm -> Seek Realm...
(Ctrl+O) to choose one.

## Core Operations

### Selecting a Realm (Repository)

1. Click "Seek..." or use Realm -> Seek Realm... (Ctrl+O)
2. Navigate to and select the directory containing your Git repository
3. The application will load the Chronicle (history) of that Realm
4. Every realm you had open is remembered and reopened automatically
   next launch (each knight remembers his own set)

### Keeping Several Realms Open

- Opening a realm you already have open simply switches to it
- The realm picker at the head of the toolbar lists every open realm;
  choosing one repaints the whole workspace for it - banner, trail,
  scrying and selection all return exactly as you left them
- Switching is instant (drawn from memory), then the trail quietly
  re-surveys in the background so outside changes are never missed
- Realm -> Close This Realm shuts the active one; the picker offers the
  neighbours, and closing the last one empties the workspace

### Surveying the Trail (Viewing Commit History)

- The Scroll tab displays Feats (commits), newest first
- The Weave column draws the commit graph: lanes with colored lines,
  fusion feats ringed in red
- Each row shows the Mark (short hash), Hero, Day, and the feat's
  summary (fusions are suffixed with a crossed-swords fusion mark)

### Examining a Feat (Commit Details)

1. Click on any row in the chronicle
2. The Tale Pane below shows:
   - The feat's header (short hash + summary)
   - Dispatches (changed files) on the left, with status badges:
     Conscripted (added), Fallen (deleted), Reforged (modified),
     Renamed (old -> new)
   - The recount (diff) of the selected path on the right, cached in
     the Vault for instant re-selection

### Searching the Realm (Scrying)

1. Click in the scry box (or Ctrl+F to jump there)
2. Combine up to three lenses, ANDed together:
   - Text: prefix and multi-word AND queries, case-insensitive
   - Scope: Summary (summary + body), Author, or Hash
   - Days: All / last 7 / 30 / 90 days
   - Hero: exact match from the trail's roster
3. "Still" clears all three lenses at once
4. The status bar shows how many feats answer the scry

## Working with Feats (Commits)

All vanguard work happens in **The Field** tab (Ctrl+R jumps there and
refreshes).

### Creating a New Feat (Committing Changes)

1. Make changes to files in your repository using your preferred editor
2. Open The Field tab - the dispatches list what changed (Post column:
   Vanguard = staged, Field = unstaged)
3. Enlist the files you want (select rows, then Enlist; or Enlist All)
4. Click "Seal..." (or Ctrl+N)
5. In the sealing dialog:
   - Enter a summary line (required - OK stays disabled without it)
   - Optionally add a detailed tale
   - Click OK to seal the feat
6. The new feat appears at the top of the Scroll

### Re-sealing (Amending)

To fold new work or reworded tales into the newest feat:

1. Enlist the additional changes in The Field
2. Click "Re-seal..." - the dialog opens pre-filled with the current
   summary and tale
3. Adjust the words and confirm; the feat's hash changes accordingly

### Releasing (Unstaging)

Select staged rows in The Field and click "Release" to pull them back
out of the vanguard.

### Banishing (Discarding)

Select rows and click "Banish..." - after confirmation, staged entries
are released, tracked files restored to their sworn state, and
unscouted files vanquished. This cannot be undone.

### Kamui (Stashing)

- "Kamui" vanishes all current changes (including unscouted files)
  into Kamui's dimension, leaving the field clear
- "Summon" restores the most recent vanished changes (the button is
  enabled only while Kamui holds something)
- Marching to banners refuses while the field is dirty - vanish first

## Working with Banners (Branches)

All banner actions live in the Banner row of the toolbar.

### Creating a Banner

- "Raise..." creates a new banner at the current frontier; you stay on
  the raised banner

### Switching (Marching)

1. Choose the target banner in the combo box
2. Click "March" and confirm - the host marches there and the chronicle
   re-surveys that banner's trail
3. Marching refuses while the field is dirty

### Fusing (Merging)

1. Choose another banner and click "Fuse" - it is joined into the
   raised banner as a --no-ff fusion feat
2. If both sides touched the same paths, a dispute dialog appears:
   choose Withdraw (clean abort) or resolve the conflicts externally

### Felling (Deleting)

- "Fell..." removes the selected banner; the raised one and unmerged
  ones are refused

## Working with Sigils (Tags)

### Pressing (Creating)

1. Select a feat in the Scroll (or the sigil lands on the frontier if
   nothing is selected)
2. Click "Press...", name the sigil, confirm

### Viewing

- The sigil combo lists all sigils, newest feat first
- Choosing one jumps the chronicle to the marked feat if it is on the
   current trail

### Melting (Deleting)

- Choose a sigil, click "Melt", confirm

## The Herald (Remote Operations)

The Allies combo lists allied realms (remotes); three buttons act on
the selected ally:

- **Rally** - refresh knowledge of allied banners (fetch --prune);
  harmless with no allies
- **Recall** - fast-forward the raised banner from the ally
  (pull --ff-only); refuses when trails have diverged instead of
  rewriting history
- **Emissary** - carry the raised banner's feats to the ally (push)

## The Council (Statistics)

Realm -> Summon the Council (Ctrl+I) opens the chronicler's reading:
feat count, fusion count, campaign span, heroes' standing, busiest
days, and the hottest paths (sampled from the newest 200 feats). All
gathering happens on a worker thread.

## Realm Bookmarks

- Realm -> Bookmark Current Realm... names the open realm (defaults to
  the folder name) and stores it on the roll
- Realm -> Bookmarked Realms... lists every named realm; select one and
  press Open (or double-click) to march there
- Bookmarks live in the ledger (SQLite), so they survive restarts

## The Order of Knights (local accounts)

Accounts are local profiles kept in the ledger - signing in is optional
and nothing here ever touches a network. Ride as the Wanderer (the guest
seat) and the git viewer works exactly as it always did.

Realm -> Knights... opens **ForkKnight - The Order of Knights**, headed by
"Who rides now: <seat>"; the Realm menu carries the same readout as
"Riding as: <knight>".

- **Join the order** - name (3-24 characters: letters, digits, dot, dash
  or underscore; `guest` is reserved for the Wanderer), an optional
  "known as" display name, then a password of
  8+ characters entered twice; the new knight is signed in straight away
- **Sign in / switch knights** - the sign-in form; a password is always
  required to take another knight's seat
- **Claim a locked ledger** - an account that predates passwords (the
  `keeper`) holds a *locked* ledger: sign-in refuses until you use
  "Set Password..." to give it its first password. For a locked ledger no
  current password is asked - setting one *is* claiming it
- **Change password** - "Set Password..." on a normal account asks for the
  current password first; a refusal keeps the dialog open so you can fix
  it in place
- **Sign Out (to the Wanderer)** - back to the passwordless guest seat
- **Dismiss...** - delete the current account behind a confirmation alert;
  his settings, notes and bookmarks fall with him

Closing the dialog with a changed seat swaps the whole workspace: the
outgoing knight's open realms are written down first, then the incoming
knight's sight, window bounds and remembered realms come back. The seat
survives restarts - ForkKnight reopens as whoever rode last.

## Theme and Interface

- Sight -> Night Sight / Day Sight (Ctrl+Shift+D) toggles the theme;
  the choice is remembered per knight
- Window bounds, the open realms, notes, bookmarks and who signed in all
  persist across sessions in the SQLite ledger (~/.forkknight/forkknight.db)

## Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| Ctrl+O | Seek Realm |
| Ctrl+R | Muster the Field (jump to The Field + refresh) |
| Ctrl+N | Seal the Vanguard (commit dialog) |
| Ctrl+B | Banners Roll (ahead/behind of every banner) |
| F5 | Rally the Allies + re-survey the trail |
| Ctrl+F | Peer into the Scryer (focus + select search box) |
| Ctrl+Shift+D | Day Sight / Night Sight toggle |
| Ctrl+I | Summon the Council (statistics) |
| Ctrl+Q | Depart |

These nine shortcuts are fixed for good (requirement R1): the menus show
them as read-only reminders of what the keys already do, and ForkKnight
never offers a way to rebind them.

## Tips for Effective Use

### Learning Git Concepts
- Remember that each knightly term corresponds to a Git concept:
  - Think of Feats as achievements in your project's history
  - Banners are different paths your project can take
  - Sigils are permanent markers of important moments
  - Kamui is like hitting pause on your work

### Best Practices
- Seal feats frequently with descriptive summaries
- Keep feats focused on a single logical change
- Use banners for feature development or experimentation
- Mark important releases with sigils
- Regularly muster to stay aware of your realm's state

### Troubleshooting
- If the application seems unresponsive, check the status bar for the running operation
- Recall refuses on diverged trails by design - fuse instead, or resolve first
- For persistent issues, restart the application - your Git data is always safe
- ForkKnight never modifies your repository without an explicit action

## Educational Notes

As you use ForkKnight, pay attention to how the knightly vocabulary helps you understand Git concepts:

- **Fealty (Commits)**: Each feat represents a sworn allegiance to a particular state of your project
- **Campaigns (Branches)**: Different banners allow you to pursue different campaigns simultaneously
- **Heraldry (Tags)**: Sigils serve as the heraldic devices marking significant victories
- **Scouting (Search)**: The scryer lets you quickly reconnaissance through your chronicle
- **Magic (Stash)**: Kamui represents the mystical ability to vanish and later summon your works

This metaphorical approach helps build intuition for version control that transfers to understanding standard Git terminology.