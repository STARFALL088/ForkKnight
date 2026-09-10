package forkknight.git;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Thin wrapper around the git CLI. All methods are synchronous and blocking;
 * callers are responsible for running them off the UI thread.
 */
public class GitService {

    private final File repoDir;

    public GitService(File repoDir) {
        this.repoDir = repoDir;
    }

    public File getRepoDir() {
        return repoDir;
    }

    /** Throws if the directory is not a git work tree. */
    public void validateRepository() throws IOException, InterruptedException {
        run("rev-parse", "--is-inside-work-tree");
    }

    /** Returns the full commit history of the current HEAD. */
    public List<Commit> log() throws IOException, InterruptedException {
        return log(null, Integer.MAX_VALUE);
    }

    public List<Commit> log(int maxCount) throws IOException, InterruptedException {
        return log(null, maxCount);
    }

    /**
     * Commit history of the given branch (or the current HEAD when branch
     * is null), newest first.
     */
    public List<Commit> log(String branch, int maxCount)
            throws IOException, InterruptedException {
        String sep = "\u001e";
        String format = "%H%x09%aE%x09%an%x09%ad%x09%s%x09%b" + sep;
        List<String> args = new ArrayList<>();
        args.add("log");
        args.add("--date=short");
        args.add("--max-count=" + maxCount);
        if (branch != null && !branch.isBlank()) {
            args.add(branch);
        }
        args.add("--pretty=format:" + format);
        String out = run(args.toArray(new String[0]));
        List<Commit> commits = new ArrayList<>();
        for (String entry : out.split(sep)) {
            // Keep the entry as-is: trim() would strip the trailing tab that
            // terminates an empty body field and break field counts.
            if (entry.startsWith("\n")) {
                entry = entry.substring(1);
            }
            if (entry.isEmpty()) {
                continue;
            }
            String[] p = entry.split("\t", 6);
            if (p.length < 6) {
                continue;
            }
            LocalDate date = LocalDate.parse(p[3], DateTimeFormatter.ISO_LOCAL_DATE);
            commits.add(new Commit(p[0], p[2], p[1], date, p[4], p[5].trim()));
        }
        return commits;
    }

    /** Names of all local branches; the checked-out one first. */
    public List<String> branches() throws IOException, InterruptedException {
        String out = run("for-each-ref", "--format=%(HEAD) %(refname:short)",
                "refs/heads");
        List<String> result = new ArrayList<>();
        List<String> current = new ArrayList<>();
        for (String line : out.split("\n")) {
            String name = line.strip();
            if (name.isEmpty()) {
                continue;
            }
            if (name.startsWith("*")) {
                current.add(name.substring(1).strip());
            } else {
                result.add(name);
            }
        }
        List<String> all = new ArrayList<>(current);
        all.addAll(result);
        return all;
    }

    /** The currently checked-out branch name. */
    public String currentBranch() throws IOException, InterruptedException {
        return run("rev-parse", "--abbrev-ref", "HEAD").trim();
    }

    /**
     * Working-copy changes. Index (staged) and worktree (unstaged) entries
     * are parsed from machine-readable NUL-separated porcelain output:
     * entries are "XY path\0", except renames/copies which carry the old
     * path in an extra trailing token: "XY newPath\0oldPath\0".
     */
    public List<WorkDirChange> status() throws IOException, InterruptedException {
        String out = runNul("status", "--porcelain", "-z");
        List<WorkDirChange> changes = new ArrayList<>();
        String[] tokens = out.split("\0", -1);
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i];
            if (token.length() < 4) {
                continue;
            }
            String x = token.substring(0, 1);   // staged (index) status
            String y = token.substring(1, 2);   // unstaged (worktree) status
            String path = token.substring(3);
            if (path.isBlank()) {
                continue;
            }
            // Untracked/ignored: one entry, never staged, no rename payload.
            if (x.equals("?") || x.equals("!")) {
                changes.add(new WorkDirChange(path, path, x, false));
                continue;
            }
            String oldPath = null;
            if (x.equals("R") || x.equals("C")) {
                if (i + 1 < tokens.length && !tokens[i + 1].isBlank()) {
                    oldPath = tokens[i + 1];
                    i++;
                }
            }
            String effectiveOld = oldPath != null ? oldPath : path;
            if (!x.equals(" ") && !x.isEmpty()) {
                changes.add(new WorkDirChange(path, effectiveOld, x, true));
            }
            if (!y.equals(" ") && !y.isEmpty()) {
                changes.add(new WorkDirChange(path, effectiveOld, y, false));
            }
        }
        return changes;
    }

    /** Stages the given file (git add --). */
    public void stage(String path) throws IOException, InterruptedException {
        run("add", "--", path);
    }

    /** Stages every change (git add -A). */
    public void stageAll() throws IOException, InterruptedException {
        run("add", "-A");
    }

    /** Unstages the given file (git reset --). */
    public void unstage(String path) throws IOException, InterruptedException {
        run("reset", "--", path);
    }

    /** Restores the given file in the worktree from the index (checkout --). */
    public void discard(String path) throws IOException, InterruptedException {
        run("checkout", "--", path);
    }

    /** Deletes an untracked file (worktree only, never a committed file). */
    public void deleteUntracked(String path) throws IOException, InterruptedException {
        File file = new File(repoDir, path);
        if (file.isFile()) {
            if (!file.delete()) {
                throw new IOException("Could not delete " + path);
            }
        }
    }

    /** Creates a new branch pointing at HEAD, without checking it out. */
    public void createBranch(String name) throws IOException, InterruptedException {
        run("branch", name);
    }

    /**
     * Creates a new branch at HEAD and checks it out. Refuses when the
     * worktree is dirty so uncommitted changes never ride along.
     */
    public void switchToNewBranch(String name) throws IOException, InterruptedException {
        requireCleanWorktree();
        run("switch", "--create", name);
    }

    /** Checks out an existing local branch. Refuses on a dirty worktree. */
    public void switchBranch(String name) throws IOException, InterruptedException {
        requireCleanWorktree();
        run("switch", name);
    }

    /**
     * Deletes a local branch. Refuses to delete the current branch or one
     * with unmerged changes unless {@code force} is set.
     */
    public void deleteBranch(String name, boolean force) throws IOException, InterruptedException {
        if (name.equals(currentBranch())) {
            throw new IOException("Cannot delete the current branch: " + name);
        }
        if (force) {
            run("branch", "-D", name);
        } else {
            run("branch", "-d", name);
        }
    }

    /**
     * Tags in the repository, ordered by the recency of the commit they
     * point at (newest first). Lightweight tags carry no creation date, so
     * commit position is used; tags off-branch history sort last.
     */
    public List<String> tags() throws IOException, InterruptedException {
        List<String> names = linesOf(run("for-each-ref",
                "--format=%(refname:short)", "refs/tags"));
        if (names.size() <= 1) {
            return names;
        }
        // Map each commit hash to its position in HEAD's history (0 = newest).
        java.util.Map<String, Integer> position = new java.util.HashMap<>();
        List<Commit> history = log();
        for (int i = 0; i < history.size(); i++) {
            position.put(history.get(i).hash(), i);
        }
        record TagInfo(String name, int index) {}
        List<TagInfo> infos = new ArrayList<>();
        for (String name : names) {
            String hash = tryRun("rev-list", "-n", "1", name);
            Integer idx = hash == null || hash.isBlank()
                    ? null : position.get(hash.strip());
            infos.add(new TagInfo(name, idx == null ? Integer.MAX_VALUE : idx));
        }
        infos.sort((a, b) -> {
            int byIndex = Integer.compare(a.index(), b.index());
            return byIndex != 0 ? byIndex : a.name().compareTo(b.name());
        });
        return infos.stream().map(TagInfo::name).toList();
    }

    /** Creates a lightweight tag at the given commit (HEAD if null). */
    public void createTag(String name, String hash) throws IOException, InterruptedException {
        List<String> args = new ArrayList<>();
        args.add("tag");
        args.add(name);
        if (hash != null && !hash.isBlank()) {
            args.add(hash);
        }
        run(args.toArray(new String[0]));
    }

    /** Deletes a local tag. */
    public void deleteTag(String name) throws IOException, InterruptedException {
        run("tag", "-d", name);
    }

    /** Commit the named tag points at. */
    public Commit tagCommit(String name) throws IOException, InterruptedException {
        String hash = run("rev-list", "-n", "1", name).strip();
        List<Commit> commits = log(hash, 1);
        if (commits.isEmpty()) {
            throw new IOException("Tag " + name + " does not point at a commit");
        }
        return commits.get(0);
    }

    /** Throws when the worktree has staged or unstaged changes. */
    public void requireCleanWorktree() throws IOException, InterruptedException {
        List<WorkDirChange> changes = status();
        if (!changes.isEmpty()) {
            throw new IOException("Uncommitted changes in " + changes.size()
                    + " file(s); commit or discard them first.");
        }
    }

    /** Stashes staged and unstaged changes; throws when nothing to stash. */
    public String stash() throws IOException, InterruptedException {
        requireDirtyWorktree("Nothing to stash.");
        run("stash", "push", "--include-untracked", "-m", "ForkKnight stash");
        String ref = tryRun("rev-parse", "-q", "--verify", "refs/stash");
        return ref == null ? "" : ref.strip();
    }

    /** Throws when the worktree is completely clean. */
    private void requireDirtyWorktree(String message)
            throws IOException, InterruptedException {
        if (status().isEmpty()) {
            throw new IOException(message);
        }
    }

    /** True when at least one stash entry exists. */
    public boolean hasStash() throws IOException, InterruptedException {
        String out = tryRun("rev-parse", "-q", "--verify", "refs/stash");
        return out != null && !out.isBlank();
    }

    /** Applies the most recent stash, keeping it in the stash list. */
    public void stashPop() throws IOException, InterruptedException {
        run("stash", "pop");
    }

    /** Commits the staged changes with the given message. */
    public Commit commit(String message) throws IOException, InterruptedException {
        run("commit", "-m", message);
        String hash = run("rev-parse", "HEAD").trim();
        return new Commit(hash, null, null, LocalDate.now(), message, "");
    }

    /** Throws if there is nothing to commit (no staged changes). */
    public void requireStaged() throws IOException, InterruptedException {
        List<WorkDirChange> staged = status().stream()
                .filter(WorkDirChange::staged).toList();
        if (staged.isEmpty()) {
            throw new IOException("Nothing to commit: no staged changes.");
        }
    }

    /** Unified diff of the given commit against its first parent. */
    public String showDiff(String hash) throws IOException, InterruptedException {
        return run("show", "--format=fuller", "--no-ext-diff", hash, "--");
    }

    /** Unified diff of one file in the given commit (empty string if none). */
    public String showFileDiff(String hash, String path)
            throws IOException, InterruptedException {
        return run("show", "--format=", "--no-ext-diff", hash, "--", path);
    }

    /**
     * Files changed in the given commit. For merge commits, the diff is
     * taken against the first parent. Status and path are separated by a
     * NUL, as are entries, so that renames parse reliably.
     */
    public List<FileChange> changedFiles(String hash)
            throws IOException, InterruptedException {
        String out = runNul("diff-tree", "--no-commit-id", "--name-status", "-r",
                "-m", "--first-parent", "-M", "-z", hash);
        String[] tokens = out.split("\0");
        List<FileChange> files = new ArrayList<>();
        for (int i = 0; i < tokens.length; i++) {
            String status = tokens[i].trim();
            if (status.isEmpty()) {
                continue;
            }
            if (i + 1 >= tokens.length) {
                break;
            }
            // -z format: entries are NUL-separated as "status\0path".
            // Renames and copies carry a similarity score and two paths in
            // the order "R100\0oldPath\0newPath".
            if ((status.startsWith("R") || status.startsWith("C"))
                    && i + 2 < tokens.length && !tokens[i + 1].isEmpty()) {
                String oldPath = tokens[i + 1].trim();
                String newPath = tokens[i + 2].trim();
                if (!oldPath.isEmpty() && !newPath.isEmpty()) {
                    files.add(new FileChange(oldPath, newPath, status));
                    i += 2;
                }
            } else {
                String path = tokens[i + 1].trim();
                if (!path.isEmpty()) {
                    files.add(new FileChange(path, path, status));
                    i += 1;
                }
            }
        }
        return files;
    }

    // ------------------------------------------------------------------

    private String run(String... args) throws IOException, InterruptedException {
        return runCommand(false, false, args);
    }

    /** Like {@link #run}, but preserves NUL separators in the output. */
    private String runNul(String... args) throws IOException, InterruptedException {
        return runCommand(true, false, args);
    }

    /** Like {@link #run}, but returns null instead of throwing on failure. */
    private String tryRun(String... args) throws IOException, InterruptedException {
        return runCommand(false, true, args);
    }

    private String runCommand(boolean keepNul, boolean tolerateFailure, String... args)
            throws IOException, InterruptedException {
        String[] cmd = new String[args.length + 3];
        cmd[0] = "git";
        cmd[1] = "-C";
        cmd[2] = repoDir.getAbsolutePath();
        System.arraycopy(args, 0, cmd, 3, args.length);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.environment().put("LC_ALL", "C");
        pb.redirectErrorStream(false);
        Process process = pb.start();

        StringBuilder out = new StringBuilder();
        StringBuilder err = new StringBuilder();
        Thread outThread = drain(process, process.getInputStream(), out);
        Thread errThread = drain(process, process.getErrorStream(), err);

        boolean finished = process.waitFor(60, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("git " + args[0] + " timed out after 60s");
        }
        outThread.join(5000);
        errThread.join(5000);

        if (process.exitValue() != 0) {
            if (tolerateFailure) {
                return null;
            }
            throw new IOException("git " + args[0] + " failed (exit "
                    + process.exitValue() + "): " + err.toString().trim());
        }
        String result = out.toString();
        return keepNul ? result : result.replace("\0", "");
    }

    /** Splits raw output into non-blank trimmed lines. */
    private static List<String> linesOf(String out) {
        List<String> lines = new ArrayList<>();
        if (out == null) {
            return lines;
        }
        for (String line : out.split("\n")) {
            if (!line.isBlank()) {
                lines.add(line.strip());
            }
        }
        return lines;
    }

    private static Thread drain(Process process, java.io.InputStream stream, StringBuilder into) {
        Thread t = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                char[] buf = new char[8192];
                int n;
                while ((n = reader.read(buf)) != -1) {
                    into.append(buf, 0, n);
                }
            } catch (IOException ignored) {
                // stream closed on process teardown
            }
        });
        t.setDaemon(true);
        t.start();
        return t;
    }
}
