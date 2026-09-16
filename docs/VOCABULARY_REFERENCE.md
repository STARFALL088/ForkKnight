# ForkKnight Vocabulary Reference

This document provides a complete reference of ForkKnight's knightly vocabulary and their corresponding Git terminology. The project uses this creative mapping to teach Git concepts through memorable medieval/anime metaphors.

## Complete Vocabulary Mapping

| ForkKnight Term | Git Equivalent | Description |
|-----------------|----------------|-------------|
| Realm | repository | The collection of code being managed |
| Feat | commit | A sealed victory in the realm's chronicle |
| Banner | branch | A dispatch on a campaign front - one of the roads a knight may travel |
| Sigil | tag | A sigil placed on a feat so it can be referenced forever |
| Chronicle | git history | The realm's chronicle keeper that speaks ONLY in knightly vocabulary |
| Survey the trail | git log | Viewing the history of commits |
| Enlist | git add (stage) | Staging changes for commit |
| Release | git reset (unstage) | Unstaging changes |
| Seal | git commit | Creating a new feat (commit) |
| Re-seal | git commit --amend | Folding the vanguard into the newest feat and rewriting its words |
| Muster | git status | Checking the current state of the realm |
| Dispatch | file change entry | Individual file changes in a feat |
| Toll of a feat | diff of a commit | The changes made in a specific commit |
| Recount | show (full diff) | Detailed view of a specific feat with all changes |
| Raise / Fell a banner | create / delete a branch | Creating or deleting campaign fronts |
| March to a banner | checkout a banner | Moving to work on a different banner |
| Fusion | merge | Combining two banners (branches) |
| Disputed fusion | merge conflict | A fusion blocked by opposing tales (UU/AA/DD states) |
| Withdraw from fusion | merge --abort | Abandoning a disputed fusion cleanly |
| Press / Melt a sigil | create / delete a tag | Creating or deleting permanent references to feats |
| Kamui (vanish / summon) | git stash push / pop | Temporarily removing and restoring changes |
| Ally / the roll | remote / remote -v | An allied realm on the roll of allies |
| Rally | git fetch --prune | Refreshing knowledge of allied banners |
| Recall | git pull --ff-only | Fast-forwarding the raised banner from an ally |
| Emissary | git push | Carrying the raised banner's feats to an ally |
| Scry | search | Finding specific content in the realm's history |
| The Council | repository statistics | Leader boards of heroes, days and path heat |
| Knight's Memory | settings persistence | Facade over the ledger (settings, notes, realm bookmarks) |
| The Ledger | SQLite database | ~/.forkknight/forkknight.db - the realm's tables |
| Night/Day Sight | dark/light theme | Switching between UI themes |
| Hero | author | The person who created a feat |
| Banish | discard changes | Removing uncommitted changes |
| Vanquish | delete untracked file | Removing files not tracked by the realm |

## Detailed CODEX Mappings from Source Code

The following mappings are implemented in the `Chronicle` class's CODEX map, showing how each knightly command maps to its Git counterpart:

### Core Operations
| Knightly Command | Git Command | Purpose |
|------------------|-------------|---------|
| survey | log | View commit history |
| muster | status | Check repository status |
| recount | show | Display commit details and changes |
| mark | rev-parse | Resolve references to specific commits |
| banners | for-each-ref | List all branches/tags |
| toll | diff-tree | Show changes between commits |
| kamui | stash | Stash and apply changes |
| seal | commit | Create a new commit |
| enlist | add | Stage files for commit |
| retreat | reset | Unstage or reset commits |
| restore | checkout/switch | Check out branches or files |
| march | switch | Change current branch |
| banner | branch | Create, list, or delete branches |
| sigil | tag | Create, list, or delete tags |
| lineage | rev-list | List commits in reverse chronological order |
| fusion | merge | Merge branches together |
| rally | fetch | Download objects from remote repository |
| emissary | push | Upload local commits to remote repository |
| allies | remote | Manage remote repositories |
| recall | pull | Fetch and merge from remote repository |

### Environmental Settings
| Knightly Term | Git Equivalent | Purpose |
|---------------|----------------|---------|
| LC_ALL=C | Environment setting | Ensures consistent output parsing |

## Usage Examples

### Basic Workflow
1. **Start working**: `muster` (git status) to see current state
2. **Prepare changes**: `enlist <file>` (git add) to stage changes
3. **Seal the feat**: `seal` (git commit) to create a new commit
4. **Share with realm**: `emissary` (git push) to share your feats

### Branching Workflow
1. **See available banners**: `banners` (git for-each-ref) to list branches
2. **Create new banner**: `raise banner <name>` (git branch) to create branch
3. **March to banner**: `march <banner>` (git checkout) to switch branches
4. **Merge banners**: `fusion <banner>` (git merge) to combine work

### Tagging Workflow
1. **Mark important feat**: `press sigil <name> <feat>` (git tag) to create tag
2. **List all sigils**: `sigils` (git tag) to see all tags
3. **Remove sigil**: `melt sigil <name>` (git tag -d) to delete tag

### Stashing Workflow
1. **Hide changes temporarily**: `kamui` (git stash push) to stash changes
2. **Retrieve changes**: `kamui` (git stash pop) to apply stashed changes
3. **View stashed changes**: `toll kamui` (git stash show) to see what's stashed

## Educational Value

This vocabulary mapping serves several educational purposes:

1. **Memory Aid**: Medieval/anime terms are more memorable than technical Git commands for beginners
2. **Conceptual Understanding**: Each metaphor reinforces the underlying Git concept:
   - Feat = commit (a sealed achievement)
   - Banner = branch (a campaign front to pursue)
   - Sigil = tag (a permanent mark of importance)
   - Kamui = stash (temporary vanishing of changes)

3. **Reduced Intimidation**: Friendly terminology lowers the barrier to entry for version control
4. **Consistent Metaphor**: The entire application uses this vocabulary consistently, reinforcing learning

## Implementation Notes

The vocabulary mapping is implemented in the `Chronicle` class through the CODEX pattern:
- Public API methods use knightly terms (survey(), muster(), seal(), etc.)
- Internal implementation maps these to actual Git commands
- This keeps the raw Git terminology confined to a single class, preventing leakage to UI or other layers

## Extending the Vocabulary

To add new Git operations to ForkKnight's vocabulary:
1. Choose an appropriate knightly/anime metaphor
2. Add the mapping to the CODEX map in Chronicle.java
3. Implement corresponding public methods in the Chronicle class
4. Add UI elements in App.java or relevant dialogs
5. Update documentation to reflect the new term

This reference was compiled from the original README.md and source code analysis without modifying any files in the ForkKnight project.