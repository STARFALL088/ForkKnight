# ForkKnight Algorithms and Data Structures

This document details the specialized algorithms and data structures implemented in ForkKnight that go beyond standard Git operations to provide enhanced performance and features.

## Scryer - Enhanced Search System

The Scryer class implements a sophisticated search index that provides fast querying of commit messages and metadata.

### Two-Structure Approach

As described in the source comments, Scryer uses two complementary data structures:

#### 1. 26-way Lowercase Trie
- **Purpose**: Efficient prefix-based token search
- **Structure**: A trie (prefix tree) where each node represents a character
- **Alphabet**: a-z plus digits and '-', '_', '/', '.' (36-way actually, despite comment)
- **Performance**: O(L) prefix search where L is length of search term
- **Implementation Details**:
  - Sparse children array for memory efficiency
  - Each token from feat summaries and bodies is inserted
  - Postings lists at leaf nodes indicate which feats contain the token

#### 2. Bigram Inverted Index
- **Purpose**: Efficient filtering for multi-word queries
- **Structure**: Hash map from tokens to sets of feat IDs containing that token
- **Query Processing**:
  - For multi-word query, get posting sets for each term
  - Intersect these sets to get candidate feats
  - Only perform expensive substring verification on this small candidate set
- **Performance**: Reduces O(N * text) scan to O(L + posting sizes) where N is number of feats

### Key Features
- **Index Isolation**: Maintains its own Feat copies to prevent index pollution when switching banners
- **Case Insensitive**: Converts all tokens to lowercase for searching
- **Bounded DFS**: Prefix harvest caps traversal depth at 32 defensively (token depth is naturally bounded)
- **Scoping Lenses**: `scryRecent(days)` (cutoff-based recency) and
  `scryByHero(name)` (exact author match) compose with the text lens
  via predicate AND-ing in the UI; `heroes()` exposes the roster in
  first-seen order (LinkedHashSet)

### Algorithmic Complexity
- **Index Build**: O(T * L) where T is total tokens, L is average token length
- **Prefix Search**: O(L + K) where L is prefix length, K is number of matching tokens
- **Multi-word Query**: O(M * L + I) where M is number of terms, L avg term length, I intersection size
- **Space Complexity**: O(T * L) for the trie + O(T) for the inverted index

## Weave - Commit Graph Layout

The Weave class implements an algorithm to layout commit DAG (Directed Acyclic Graph) onto numbered lanes for visualization without line crossings.

### Single-Pass Algorithm

Processes commits in newest-first order (as they appear from git log):

#### Lane Assignment Process
1. **Parent Analysis**: For each commit, examine its parent commits that are currently visible
2. **Lane Inheritance**: If visible parents exist, commit continues the highest-numbered parent's lane
3. **New Lane Allocation**: If no visible parents, allocate fresh lane from free lane pool
4. **Lane Recycling**: When a commit's lineage is fully rendered, return its lane to the free pool

### Data Structures
- **FREE-LANE POOL**: TreeSet of available lane numbers providing O(log L) allocation/deallocation
- **Lane Tracking**: Each commit records:
  - Its assigned lane number
  - Which parent lanes to draw CURVES from (for merge visualization)

### Key Properties
- **Crossing Prevention**: By construction, no two commit lines cross in the same lane
- **Lane Efficiency**: Lanes are recycled like "barracks bunks" rather than permanently allocated
- **Merge Visualization**: Merge commits show curves from parent lanes to current lane
- **Compact Layout**: Minimizes total number of lanes used through intelligent reuse

### Algorithmic Complexity
- **Time Complexity**: O(N log L) where N is number of commits, L is maximum number of lanes
  - O(log L) per commit for lane allocation/deallocation from TreeSet
  - O(P) per commit to examine P parents (typically small)
- **Space Complexity**: O(N + L) for storing commit assignments and lane pool

## Chronicle - Enhanced Git Parsing

The Chronicle class implements specialized parsing techniques for robust and efficient Git command output processing.

### NUL-Separated Porcelain Parsing
- **Approach**: Uses Git's `--null` option (e.g., `git log --null`) to separate records with ASCII NUL (0) characters
- **Benefit**: Handles pathnames containing newlines, quotes, and other special characters safely
- **Implementation**: Custom parsing that splits on NUL characters rather than newlines

### Dual-Stream Subprocess Draining
- **Problem**: Git commands can fill stdout or stderr buffers, causing deadlock if not read properly
- **Solution**: Simultaneously read both stdout and stderr streams using separate threads or asynchronous I/O
- **Benefit**: Prevents subprocess hanging due to full buffers

### Compile-Time CODEX Guard
- **Approach**: The CODEX map is built at class initialization time, making it final and thread-safe
- **Benefit**: Ensures vocabulary mapping is consistent and available immediately

### Timeout Handling
- **Implementation**: Git subprocess calls include configurable timeouts to prevent indefinite blocking
- **Fallback Behavior**: Graceful handling of timed-out operations with appropriate error reporting

## Vault - LRU Cache Implementation

The Vault class implements a generic Least Recently Used (LRU) cache with O(1) operations.

### Access-Ordered LinkedHashMap
- **Core Mechanism**: Uses Java's LinkedHashMap with access ordering enabled
- **LRU Implementation**: Override `removeEldestEntry()` to evict when size exceeds capacity
- **Operations**:
  - **get(K)**: O(1) hash lookup, moves entry to most-recently-used position
  - **put(K,V)**: O(1) hash insertion, may trigger eviction
  - **Eviction**: Automatic when capacity exceeded, O(1) due to linked list maintenance

### Thread Safety Considerations
- All public methods (`fetch`, `acquire`, `purge`, `clear`, `size`) are
  `synchronized` on the Vault itself
- The `acquire` loader runs OUTSIDE the lock (so a slow subprocess
  diff does not block readers), then re-enters to insert
- Used in `TalePane` where access spans both the FX thread (cache hits)
  and worker threads (loads)

### Performance Characteristics
- **Hit/Miss**: O(1) average case
- **Eviction**: O(1) when triggered by insertion
- **Memory**: Bounded by capacity parameter
- **Overhead**: Minimal - only maintains insertion/access order links

## Chronicler - Top-K Selection via Bounded Min-Heap

The Chronicler computes realm statistics (hero standings, busiest days,
path heat) without ever sorting the full data set.

### Bounded Min-Heap Technique
- **Structure**: PriorityQueue of size at most k, ordered ASCENDING -
  the root is always the weakest of the current leaders
- **Insertion**: if the heap holds fewer than k entries, offer; else if
  a newcomer beats the root, poll the root and offer the newcomer
  (O(log k) per element)
- **Harvest**: draining the heap yields the k largest, re-sorted
  descending for display
- **Complexity**: O(n log k) time, O(k) extra space - for leader boards
  with small k (the Council uses 8), this beats the O(n log n) full sort

### Aggregate Queries
- **Hero standings**: single hash-map pass merging author counts, then top-k
- **Busiest days**: counts per day, then sorted by count DESC with
  recency as the tiebreaker
- **Path heat**: churn counted across per-feat tolls (hash map), then top-k
- **Fusion count / campaign span**: single linear scans

## KnightMemory - Atomic Tiny-Config Persistence

A key=value vault at `~/.forkknight/memory` with deliberately minimal
machinery:

- **Escape discipline**: only `\n` and `\\` are escaped (a line-based
  format needs nothing else), keeping reads and writes trivially
  auditable
- **Atomic saves**: writes go to a staging file first, then
  `Files.move(..., ATOMIC_MOVE)` swaps it in - a crash mid-save can
  never leave a half-written memory
- **Corruption tolerance**: unreadable files read as empty; scribbled
  lines (no `=`, empty keys, comments) are skipped rather than fatal

## Performance Summary

| Component | Purpose | Key Algorithm | Time Complexity | Space Complexity |
|-----------|---------|---------------|-----------------|------------------|
| **Scryer** | Commit search | Trie + Inverted Index | O(L + K) search | O(T*L) index |
| **Weave** | Graph layout | Single-pass lane assignment | O(N log L) layout | O(N + L) storage |
| **Chronicle** | Git parsing | NUL-separated + dual-stream | O(L) per command | O(output size) |
| **Vault** | Diff caching | LRU cache | O(1) get/put | O(capacity) |
| **Chronicler** | Statistics | Bounded min-heap top-K | O(n log k) leaders | O(k) heap |
| **KnightMemory** | Settings | Atomic swap persistence | O(entries) read/write | O(entries) |

These algorithms demonstrate sophisticated computer science techniques applied to enhance the Git client experience while maintaining the educational knightly vocabulary theme.