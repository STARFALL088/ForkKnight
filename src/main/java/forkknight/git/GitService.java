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
        return runCommand(false, args);
    }

    /** Like {@link #run}, but preserves NUL separators in the output. */
    private String runNul(String... args) throws IOException, InterruptedException {
        return runCommand(true, args);
    }

    private String runCommand(boolean keepNul, String... args)
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
            throw new IOException("git " + args[0] + " failed (exit "
                    + process.exitValue() + "): " + err.toString().trim());
        }
        String result = out.toString();
        return keepNul ? result : result.replace("\0", "");
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
