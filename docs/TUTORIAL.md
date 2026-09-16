# ForkKnight Tutorial: Common Workflows

This tutorial walks through common Git workflows using ForkKnight's knightly vocabulary interface. Each step shows both the ForkKnight action and the underlying Git concept it represents.

## Tutorial Setup

Before beginning, ensure you have:
1. ForkKnight installed and running (`./gradlew run`)
2. A test directory initialized as a Git repository (or use an existing one)
3. Basic familiarity with Git concepts (helpful but not required)

Let's imagine we're working on a simple web project called "realm-website".

## Workflow 1: Starting New Work (Opening a Repository)

### Scenario: You want to start tracking a new project with version control.

**In ForkKnight:**
1. Launch ForkKnight - the last realm reopens automatically if it still exists
2. Click "Seek..." (or Realm -> Seek Realm..., Ctrl+O)
3. Navigate to and select your project directory
4. ForkKnight verifies it is a realm and loads the chronicle

**Underlying Git:** repository discovery via `git rev-parse`
**Knightly Term:** Seeking a realm to serve

**Note:** ForkKnight opens existing Git repositories; it does not
initialize new ones. If the directory is not yet a repository,
initialize it externally first (`git init`), then seek it.

**What happens:**
- ForkKnight verifies the directory belongs to the realm
- The Scroll fills with the woven trail; banners, sigils and allies load
- The realm path is remembered for the next launch

## Workflow 2: Basic Daily Work (Enlist, Seal, Emissary)

### Scenario: You've made changes to your website and want to save them.

**Step 1: Check Realm Status (Muster)**
**In ForkKnight:**
- Open The Field tab (Ctrl+R jumps there and musters)
- See which paths are modified, staged, or untracked

**Underlying Git:** `git status`
**Knightly Term:** Mustering the troops to see the realm's state

**Step 2: Prepare Changes (Enlist)**
**In ForkKnight:**
- In The Field, each row is a dispatch: Post (Vanguard/Field), Word
  (Reforged/Conscripted/Fallen/Unscouted), Path
- Select the rows you want and click "Enlist"
- Or "Enlist All" for everything

**Underlying Git:** `git add <paths>`
**Knightly Term:** Enlisting specific files for the upcoming feat

**Step 3: Create the Feat (Seal)**
**In ForkKnight:**
- Click "Seal..." (or Ctrl+N)
- In the sealing dialog:
  - Summary: "Add responsive navigation menu" (required - OK stays disabled without it)
  - Tale: Optional detailed description of what changed and why
- Click OK to seal the feat

**Underlying Git:** `git commit -m "..."`
**Knightly Term:** Sealing the pact with your summary and optional detailed tale

**Step 4: Share with Realm (Emissary)**
**In ForkKnight:**
- Choose the ally in the Allies combo and click "Emissary"
- The raised banner's newest feats are carried to the ally

**Underlying Git:** `git push <remote> <branch>`
**Knightly Term:** Sending an emissary to share your sealed feats with the wider realm

## Workflow 3: Working with Banners (Branches)

### Scenario: You want to add a new feature without disturbing the main banner of development.

**Step 1: View Available Banners**
**In ForkKnight:**
- The Banner combo lists every banner; the raised one is selected
- The banner row offers Raise / March / Fuse / Fell

**Step 2: Create New Banner (Raise Banner)**
**In ForkKnight:**
- Click "Raise..." next to the banner combo
- Enter name: "feature-dark-mode"
- Click OK - the banner is created at the frontier; you stay on the raised one

**Underlying Git:** `git branch feature-dark-mode`
**Knightly Term:** Raising a new banner for your dark mode feature campaign

**Step 3: March to New Banner**
**In ForkKnight:**
- Choose "feature-dark-mode" in the combo and click "March"
- The chronicle re-surveys that banner's trail

**Underlying Git:** `git switch feature-dark-mode`
**Knightly Term:** Marching to your new campaign front

**Step 4: Work on Feature**
- Make changes to implement dark mode (modify CSS, maybe add JS toggle)
- The Field: Enlist changes, Seal feat: "Add CSS variables for dark theme toggle"
- Repeat as needed for additional commits

**Step 5: Fuse Back into Main**
**In ForkKnight:**
- March back to the raised main banner
- Choose "feature-dark-mode" and click "Fuse" - a --no-ff fusion feat is sealed

**Underlying Git:** `git merge --no-ff feature-dark-mode`
**Knightly Term:** Fusing the campaign's spoils into the main trail

## Workflow 4: Marking Important Points (Sigils/Tags)

### Scenario: You've reached a stable release point and want to mark it permanently.

**In ForkKnight:**
1. Select the feat that represents your release state in the Scroll (or none, to sigil the frontier)
2. Click "Press..." next to the sigil combo
3. Enter name: "v1.0.0" or "release-summit"
4. Click OK

**Underlying Git:** `git tag v1.0.0` or `git tag release-summit <hash>`
**Knightly Term:** Pressing a sigil onto the feat to create a permanent reference

**To View Sigils:**
- The sigil combo lists all sigils, the newest feat first
- Choosing one jumps the chronicle to the marked feat if it is on the
  current trail (otherwise the status bar notes where it points)

**To Remove Sigil (if needed):**
1. Choose the sigil in the combo
2. Click "Melt"
3. Confirm removal

**Underlying Git:** `git tag -d v1.0.0`
**Knightly Term:** Melting the sigil to remove it

## Workflow 5: Collaboration (Working with Others' Changes)

### Scenario: Your teammate has pushed changes and you want to incorporate them.

**Step 1: Fetch Changes (Rally)**
**In ForkKnight:**
- Choose the ally in the Allies combo and click "Rally" (or F5, which
  also re-surveys the trail)
- This downloads changes from the remote without merging them yet

**Underlying Git:** `git fetch --prune`
**Knightly Term:** Rallying scouts to bring back news from distant realms

**Step 2: Review Remote Changes**
**In ForkKnight:**
- After rallying, the banners can be re-checked via the banner combo
- Survey the trail to see where the raised banner now stands

**Step 3: Integrate Changes (Recall)**
**In ForkKnight:**
- Ensure the banner you want to update is raised
- Click "Recall" and confirm
- The raised banner fast-forwards to the ally's state

**Underlying Git:** `git pull --ff-only`
**Knightly Term:** Recalling allied wisdom into the realm

**Alternative: Manual Fusion**
If the trails have diverged (Recall refuses rather than rewrite history):
1. Rally to make sure you know where the ally stands
2. Fuse the relevant banner from the banner row
3. Resolve any dispute, or Withdraw from the fusion

## Workflow 6: Temporary Changes (Kamui/Stash)

### Scenario: You're working on a feature but need to quickly fix a bug on main banner.

**Step 1: Vanish Changes (Kamui)**
**In ForkKnight:**
- Make sure you have uncommitted changes you want to set aside
- In The Field, click "Kamui" and confirm
- Your working copy is cleaned, changes are stored (unscouted files too)

**Underlying Git:** `git stash push --include-untracked`
**Knightly Term:** Using Kamui to temporarily vanish your works

**Step 2: Switch to Main Banner**
**In ForkKnight:**
- March to your main banner - possible now that the field is clear
- Verify with a muster (Ctrl+R)

**Step 3: Fix Bug**
- Make your bug fix changes
- Enlist appropriate files
- Seal feat: "Fix critical login validation bug"
- Emissary to share the fix

**Step 4: Return to Feature Work**
**In ForkKnight:**
- March back to your feature banner
- Click "Summon" (enabled only while Kamui holds something)
- Your changes are restored and you can continue

**Underlying Git:** `git stash pop`
**Knightly Term:** Using Kamui to summon back your vanished works

**Note:** Kamui operates as a stack - vanish repeatedly and summons
return the most recent first (LIFO).

## Workflow 7: Finding History (Scry/Search)

### Scenario: You want to find when a particular feature was added or a bug introduced.

**Basic Search:**
**In ForkKnight:**
- Click in the scry box (Ctrl+F focuses and selects it)
- Type your search term (e.g., "login", "navigation", "bug fix")
- The chronicle filters live as you type; the status bar counts the matches

**Underlying Git:** like `git log --grep="login"` - but served from
the in-memory trie + inverted index, no subprocess needed
**Knightly Term:** Scrying the chronicle for hidden knowledge

**Advanced Search:**
**In ForkKnight:**
- Scope lens: Summary (summary + body), Author, or Hash
- Multi-word: "dark mode" finds feats containing BOTH terms
- Prefix: "nav" finds "navigation", "navigator", "navigate", etc.
- Case insensitive: "LOGIN" matches "login", "Login", "lOgIn", etc.
- Days lens: All / last 7 / 30 / 90 days
- Hero lens: exact author from the trail's roster

**To Clear Search:**
- Click "Still" to clear every lens at once

## Workflow 8: Undoing Changes

### Scenario: You made a mistake and want to undo recent changes.

**Option 1: Unstaging Changes (Release)**
**In ForkKnight:**
- In The Field, select the staged rows and click "Release"

**Underlying Git:** `git restore --staged <file>` or `git reset HEAD <file>`
**Knightly Term:** Releasing enlisted changes from your impending feat

**Option 2: Discarding Uncommitted Changes (Banish)**
**In ForkKnight:**
- In The Field, select rows and click "Banish..."
- Confirm when prompted
- WARNING: This permanently deletes uncommitted changes!

**Underlying Git:** `git restore <file>` or `git checkout -- <file>`
**Knightly Term:** Banishing unwanted changes from the realm

**Option 3: Modifying Last Feat (Re-seal)**
**In ForkKnight:**
- If you just sealed a feat and realize you forgot something or made a typo in summary:
- Make your additional changes
- Enlist them in The Field
- Click "Re-seal..." - the dialog opens pre-filled with the current words
- Update summary/tale as needed and confirm

**Underlying Git:** `git commit --amend`
**Knightly Term:** Re-sealing the pact with corrections

**Note:** ForkKnight deliberately offers no hard reset - the realm's
history is never rewritten backwards, only amended at the tip.

## Workflow 9: Resolving Conflicts

### Scenario: You attempted to fuse banners but there are conflicting changes.

**When Conflict Occurs:**
**In ForkKnight:**
- The fusion attempt leaves the realm in a disputed state; ForkKnight
  shows a dispute dialog
- Choose "Withdraw" for a clean abort back to the pre-fusion state
- Or choose "Keep" and resolve externally, then return

**Basic Conflict Resolution Process:**
1. Identify conflicted files (muster shows them in the dispatch list)
2. Open each conflicted file in your editor
3. Look for conflict markers: `<<<<<<<`, `=======`, `>>>>>>>`
4. Choose which changes to keep, edit to resolve
5. Save the file
6. Return to ForkKnight, open The Field
7. Enlist the resolved files and seal to complete the fusion

**Underlying Git:** Manual conflict resolution process
**Knightly Term:** Reconciling opposing tales in the chronicle

**After Resolution:**
- Continue with your intended operation (seal feat, fuse banners, etc.)
- The conflict is now resolved in your local realm
- Remember to emissary your resolved state

## Workflow 10: Viewing History and Details

### Scenario: You want to understand what happened in a particular feat.

**Examining a Feat:**
**In ForkKnight:**
1. Click on any feat row in the Scroll
2. The Tale Pane shows:
   - Header: short hash + summary
   - Dispatches: changed files with status badges (Conscripted,
     Fallen, Reforged, Renamed old -> new)
   - Toll: the recount (diff) of whichever dispatch is selected,
     served from the Vault LRU cache on repeat visits

**Navigating History:**
- Arrow keys move through the chronicle rows
- The Weave column draws the DAG: lane colors distinguish lineages,
  red rings mark fusions
- Summon the Council (Ctrl+I) for the realm-wide view: heroes,
  busiest days, hottest paths

**Underlying Git:** `git show <feat-hash>` / `git log -p`
**Knightly Term:** Requesting a full recount of the feat from the chronicle

## Workflow 10: Bookmarking Realms (and What Persists)

### Scenario: You serve several repositories and want one-click access to each.

**In ForkKnight:**
1. Open the realm you want to keep (Realm -> Seek Realm...)
2. Realm -> Bookmark Current Realm... - name it (defaults to the folder name)
3. Later: Realm -> Bookmarked Realms... lists the roll; pick one (or
   double-click) to open it

**What persists (the Ledger, SQLite at ~/.forkknight/forkknight.db):**
- the last realm, window bounds and the favored sight
- knight's notes on feats ("Annotate..." in the tale pane)
- realm bookmarks

**Underlying Git:** none - bookmarks are ForkKnight's own ledger, not
part of the repository
**Knightly Term:** Writing a realm's name into the ledger so it can be
summoned again

## Best Practices for Using ForkKnight

### For Effective Learning
1. **Notice the Metaphors**: Pay attention to how each knightly term maps to a Git concept
2. **Use Consistent Vocabulary**: Try to think in terms of feats, banners, sigils when discussing Git
3. **Explore the Mapping**: When you learn a new Git concept, think about what knightly term would represent it
4. **Use the Tutorial Features**: If tooltips or help are available, use them to reinforce learning

### For Productive Work
1. **Seal Frequently**: Make small, focused feats rather than infrequent giant ones
2. **Descriptive Summaries**: Write clear summary lines that explain WHY, not just WHAT
3. **Banner Hygiene**: Clean up old banners after they're merged (fell banner when no longer needed)
4. **Sigil Releases**: Use sigils for important milestones (releases, versions)
5. **Regular Mustering**: Check your realm status frequently to avoid surprises
6. **Backup via Emissary**: Regularly send emissaries to remote realms to backup your work

### Common Patterns to Internalize
- **Enlist → Seal**: The basic workflow for saving changes
- **Muster**: Always check status before and after operations
- **Banner per Feature**: Work on features in isolated banners
- **Sigil for Releases**: Mark stable points with permanent tags
- **Kamui for Interruptions**: Use stash when you need to context-switch quickly
- **Scry Before Acting**: Search to understand context before making changes

## Troubleshooting Common Issues

### "Repository Not Found"
**Solution:** Ensure you've opened the correct directory as a realm. Use Realm -> Seek Realm... (Ctrl+O) to navigate to your Git repository's root directory. ForkKnight opens existing repositories; initialize new ones externally first.

### "No Changes to Seal"
**Solution:** Make sure you've enlisted (staged) changes in The Field. Remember that you must enlist changes before you can seal them.

### "Operation Timed Out"
**Solution:** Check your network connection if rallying/recalling/emissaries. Large realms may take time. Git subprocesses time out after 60 seconds by design.

### "Merge Conflict" (Disputed Fusion)
**Solution:** Follow the conflict resolution workflow above - withdraw for a clean abort, or resolve externally and seal. Conflicts are normal and expected in collaborative work.

### "Recall faltered" (diverged trails)
**Solution:** Recall uses --ff-only and refuses to rewrite history. Fuse the ally's banner instead, or resolve the divergence first.

### "Application Unresponsive"
**Solution:** Check the status bar for the running operation. If truly stuck, restart - your Git data is always safe.

## Connecting to Standard Git Terminology

As you become comfortable with ForkKnight's vocabulary, practice translating between the two:

| ForkKnight Concept | Standard Git Term | When to Use Each |
|-------------------|-------------------|------------------|
| Realm | Repository | Use "realm" when thinking about the project as a whole |
| Feat | Commit | Use "feat" when focusing on a specific saved state |
| Banner | Branch | Use "banner" when thinking about parallel lines of development |
| Sigil | Tag | Use "sigil" when marking permanent reference points |
| Enlist | git add | Use "enlist" when preparing changes for commitment |
| Seal | git commit | Use "seal" when creating a new saved state |
| Emissary | git push | Use "emissary" when sharing your work with others |
| Rally | git fetch | Use "rally" when getting updates without merging |
| Recall | git pull | Use "recall" when getting and integrating updates |
| Kamui | git stash | Use "kamui" when temporarily saving work-in-progress |
| Scry | git search/log | Use "scry" when exploring project history |
| Muster | git status | Use "muster" when checking current state |
| Banish | git restore/clean | Use "banish" when removing unwanted changes |
| Fell Banner | git branch -d | Use "fell banner" when removing unnecessary banners |
| Fuse Banner | git merge | Use "fuse banner" when combining work from different banners |

## Conclusion

By working through these tutorials, you should gain practical experience with both ForkKnight's interface and the underlying Git concepts it teaches. Remember that the knightly vocabulary isn't just whimsical - each metaphor was carefully chosen to help you understand and remember what each Git operation does.

As you continue using ForkKnight, try to internalize these mappings so that when you eventually encounter standard Git terminology, you'll instantly understand what it means through the lens of the knightly metaphors you've learned here.

Happy questing in your realm!