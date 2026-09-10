package forkknight.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * The realm's chronicle keeper. Speaks ONLY in knightly vocabulary at the
 * API level; the raw tongue of the dragon (git CLI verbs) is confined to
 * the private ORDERS codex below and never escapes this class.
 *
 * All methods are synchronous and blocking; callers run them off the UI
 * thread (see the War Room tasks in the UI).
 */
public class Chronicle {

    // ------------------------------------------------------------------
    // The codex: knightly order -> dragon tongue. Never exposed.
    // ------------------------------------------------------------------

    private static final String[] ORDERS = {
            "log", "status", "show", "rev-parse", "for-each-ref",
            "diff-tree", "stash", "commit", "add", "reset", "checkout",
            "switch", "branch", "tag", "rev-list", "merge", "fetch", "push",
            "remote", "pull"
    };

    private static final Map<String, String> CODEX = buildCodex();

    private static Map<String, String> buildCodex() {
        Map<String, String> m = new HashMap<>();
        // survey      -> log            (scout the campaign trail)
        m.put("survey", "log");
        // muster      -> status         (count the troops in the field)
        m.put("muster", "status");
        // recount     -> show           (retell one feat in full)
        m.put("recount", "show");
        // mark        -> rev-parse      (read the realm's marks)
        m.put("mark", "rev-parse");
        // banners     -> for-each-ref   (list every banner in the hall)
        m.put("banners", "for-each-ref");
        // toll        -> diff-tree      (count the cost of one feat)
        m.put("toll", "diff-tree");
        // kamui       -> stash          (Kakashi's dimension: vanish & return)
        m.put("kamui", "stash");
        // seal        -> commit         (bind the pact)
        m.put("seal", "commit");
        // enlist      -> add            (conscript files into the vanguard)
        m.put("enlist", "add");
        // retreat     -> reset          (pull troops back from the line)
        m.put("retreat", "reset");
        // restore     -> checkout       (bring a file back to its sworn state)
        m.put("restore", "checkout");
        // march       -> switch         (the army marches to a new banner)
        m.put("march", "switch");
        // banner      -> branch         (raise or fell a banner)
        m.put("banner", "branch");
        // sigil       -> tag            (press a sigil onto history)
        m.put("sigil", "tag");
        // lineage     -> rev-list       (trace a bloodline)
        m.put("lineage", "rev-list");
        // fusion      -> merge          (two banners become one)
        m.put("fusion", "merge");
        // rally       -> fetch          (call the allied hosts home)
        m.put("rally", "fetch");
        // emissary    -> push           (send word to allied lands)
        m.put("emissary", "push");
        // allies      -> remote         (the allied realms' roll)
        m.put("allies", "remote");
        // recall      -> pull           (bring allied wisdom here)
        m.put("recall", "pull");
        return Map.copyOf(m);
    }

    static {
        // Guard: every codex translation must hit a known dragon verb,
        // otherwise a typo would silently corrupt every command.
        for (String knightly : CODEX.keySet()) {
            String dragon = CODEX.get(knightly);
            if (Arrays.stream(ORDERS).noneMatch(dragon::equals)) {
                throw new ExceptionInInitializerError(
                        "Codex broken: " + knightly + " -> " + dragon);
            }
        }
    }

    private final File realmDir;

    public Chronicle(File realmDir) {
        this.realmDir = realmDir;
    }

    public File getRealmDir() {
        return realmDir;
    }

    // ------------------------------------------------------------------
    // Surveys: reading the chronicle
    // ------------------------------------------------------------------

    /** Throws unless the directory belongs to the realm (is a work tree). */
    public void validateRealm() throws IOException, InterruptedException {
        command("mark").flags("--is-inside-work-tree").run();
    }

    /** The full campaign trail of the current banner, newest first. */
    public List<Feat> surveyTrail() throws IOException, InterruptedException {
        return surveyTrail(null, Integer.MAX_VALUE);
    }

    public List<Feat> surveyTrail(int maxCount) throws IOException, InterruptedException {
        return surveyTrail(null, maxCount);
    }

    /**
     * Campaign trail of the given banner (or HEAD when null), newest
     * first. Parent hashes are captured so the Weave can draw the DAG.
     */
    public List<Feat> surveyTrail(String banner, int maxCount)
            throws IOException, InterruptedException {
        String sep = "\u001e";
        // %P = parent hashes, space separated (may be empty for roots).
        String format = "%H%x09%P%x09%aE%x09%an%x09%ad%x09%s%x09%b" + sep;
        Command cmd = command("survey")
                .flags("--date=short", "--max-count=" + maxCount,
                        "--pretty=format:" + format);
        if (banner != null && !banner.isBlank()) {
            cmd.target(banner);
        }
        String out = cmd.run();
        List<Feat> feats = new ArrayList<>();
        for (String entry : out.split(sep)) {
            // Never trim: trim() would eat the trailing tab that
            // terminates an empty body and break field counts.
            if (entry.startsWith("\n")) {
                entry = entry.substring(1);
            }
            if (entry.isEmpty()) {
                continue;
            }
            String[] p = entry.split("\t", 7);
            if (p.length < 7) {
                continue;
            }
            List<String> parents = new ArrayList<>();
            if (!p[1].isBlank()) {
                for (String parent : p[1].split(" ")) {
                    if (!parent.isBlank()) {
                        parents.add(parent);
                    }
                }
            }
            LocalDate date = LocalDate.parse(p[4], DateTimeFormatter.ISO_LOCAL_DATE);
            feats.add(new Feat(p[0], parents, p[3], p[2], date, p[5], p[6].trim()));
        }
        return feats;
    }

    /** Files changed in a feat; fusions are tolled against the first parent. */
    public List<Dispatch> tollOf(String hash) throws IOException, InterruptedException {
        String out = command("toll").flags("--no-commit-id", "--name-status", "-r",
                        "--root", "-m", "--first-parent", "-M", "-z")
                .target(hash).runRaw();
        String[] tokens = out.split("\0");
        List<Dispatch> dispatches = new ArrayList<>();
        for (int i = 0; i < tokens.length; i++) {
            String status = tokens[i].trim();
            if (status.isEmpty()) {
                continue;
            }
            if (i + 1 >= tokens.length) {
                break;
            }
            // -z: "status\0path", renames as "R100\0old\0new".
            if ((status.startsWith("R") || status.startsWith("C"))
                    && i + 2 < tokens.length && !tokens[i + 1].isEmpty()) {
                String oldPath = tokens[i + 1].trim();
                String newPath = tokens[i + 2].trim();
                if (!oldPath.isEmpty() && !newPath.isEmpty()) {
                    dispatches.add(new Dispatch(newPath, oldPath, status, false));
                    i += 2;
                }
            } else {
                String path = tokens[i + 1].trim();
                if (!path.isEmpty()) {
                    dispatches.add(new Dispatch(path, path, status, false));
                    i += 1;
                }
            }
        }
        return dispatches;
    }

    /** Full recount (header + unified toll) of one feat against its first parent. */
    public String recount(String hash) throws IOException, InterruptedException {
        return command("recount").flags("--format=fuller", "--no-ext-diff")
                .target(hash).flag("--").run();
    }

    /** Recount of a single path within a feat; empty string when untouched. */
    public String recountPath(String hash, String path)
            throws IOException, InterruptedException {
        return command("recount").flags("--format=", "--no-ext-diff")
                .target(hash).flag("--").target(path).run();
    }

    // ------------------------------------------------------------------
    // Banners (branches) and sigils (tags)
    // ------------------------------------------------------------------

    /** All banners in the hall; the one currently raised comes first. */
    public List<Banner> banners() throws IOException, InterruptedException {
        String out = command("banners")
                .flags("--format=%(HEAD)%00%(refname:short)%00%(objectname)", "refs/heads")
                .runRaw();
        List<Banner> raised = new ArrayList<>();
        List<Banner> others = new ArrayList<>();
        // Records end with newline; fields inside each record are NUL-separated.
        for (String record : out.split("\n")) {
            if (record.isBlank()) {
                continue;
            }
            String[] p = record.split("\0");
            if (p.length < 3) {
                continue;
            }
            boolean active = p[0].strip().equals("*");
            Banner banner = new Banner(p[1].strip(), active, p[2].strip());
            if (active) {
                raised.add(banner);
            } else {
                others.add(banner);
            }
        }
        List<Banner> all = new ArrayList<>(raised);
        all.addAll(others);
        return all;
    }

    /** The currently raised banner's name. */
    public String activeBanner() throws IOException, InterruptedException {
        return command("mark").flags("--abbrev-ref", "HEAD").run().trim();
    }

    /**
     * Sigils of the realm ordered by the recency of the feat they mark
     * (newest first). Feats off the current trail sort last, then by name.
     */
    public List<Sigil> sigils() throws IOException, InterruptedException {
        List<String> names = linesOf(command("banners")
                .flags("--format=%(refname:short)", "refs/tags").run());
        if (names.isEmpty()) {
            return new ArrayList<>();
        }
        // Rank sigils by their feat's position in HEAD's trail (0 = newest)
        // with a single-pass hash map, falling back to MAX_VALUE for feats
        // outside the current trail.
        Map<String, Integer> rank = new HashMap<>();
        List<Feat> trail = surveyTrail();
        for (int i = 0; i < trail.size(); i++) {
            rank.put(trail.get(i).hash(), i);
        }
        record SigilRank(Sigil sigil, int index) {}
        List<SigilRank> ranked = new ArrayList<>();
        for (String name : names) {
            String hash = tryCommand("lineage").flags("-n", "1").target(name).runOrNull();
            Feat feat = null;
            int index = Integer.MAX_VALUE;
            if (hash != null && !hash.isBlank()) {
                Integer found = rank.get(hash.strip());
                if (found != null) {
                    index = found;
                    feat = trail.get(found);
                }
            }
            ranked.add(new SigilRank(new Sigil(name, feat), index));
        }
        ranked.sort((a, b) -> {
            int byIndex = Integer.compare(a.index(), b.index());
            return byIndex != 0 ? byIndex : a.sigil().name().compareTo(b.sigil().name());
        });
        return ranked.stream().map(SigilRank::sigil).toList();
    }

    /** Presses a sigil onto a feat (HEAD when hash is null). */
    public void pressSigil(String name, String hash) throws IOException, InterruptedException {
        Command cmd = command("sigil").target(name);
        if (hash != null && !hash.isBlank()) {
            cmd.target(hash);
        }
        cmd.run();
    }

    /** Melts a sigil away. */
    public void meltSigil(String name) throws IOException, InterruptedException {
        command("sigil").flags("-d").target(name).run();
    }

    /** Raises a new banner at HEAD without marching to it. */
    public void raiseBanner(String name) throws IOException, InterruptedException {
        command("banner").target(name).run();
    }

    /** Raises a new banner at HEAD and marches the army to it. */
    public void marchToNewBanner(String name) throws IOException, InterruptedException {
        requireClearedField();
        command("march").flags("--create").target(name).run();
    }

    /** Marches the army to an existing banner. Refuses on a muddy field. */
    public void marchToBanner(String name) throws IOException, InterruptedException {
        requireClearedField();
        command("march").target(name).run();
    }

    /**
     * Fells a banner. Refuses the raised one and unmerged ones unless
     * {@code force} (the "bring the wall down" option).
     */
    public void fellBanner(String name, boolean force) throws IOException, InterruptedException {
        if (name.equals(activeBanner())) {
            throw new IOException("Cannot fell the raised banner: " + name);
        }
        command("banner").flags(force ? "-D" : "-d").target(name).run();
    }

    /**
     * Fuses the given banner into the raised one (a merge). When the
     * trails meet cleanly the fusion is sealed at once with a default
     * message; otherwise the realm enters a conflicted state and the
     * exception carries the dispatches in dispute.
     */
    public Feat fuseBanner(String name) throws IOException, InterruptedException {
        requireClearedField();
        command("fusion").flags("--no-ff", "-m",
                "Fusion: join " + name + " into " + activeBanner()).target(name).run();
        String hash = command("mark").flags("HEAD").run().trim();
        return new Feat(hash, List.of(), null, null, LocalDate.now(),
                "Fusion: join " + name, "");
    }

    /**
     * True when the realm is in a conflicted fusion (a merge in
     * progress with unresolved dispatches).
     */
    public boolean fusionInDispute() throws IOException, InterruptedException {
        String out = command("muster").flags("--porcelain").run();
        return out.lines().anyMatch(line -> line.startsWith("UU")
                || line.startsWith("AA") || line.startsWith("DD"));
    }

    /**
     * Abandons a fusion in dispute and restores the realm to the state
     * before the fusion began (merge --abort).
     */
    public void abandonDisputedFusion() throws IOException, InterruptedException {
        command("fusion").flags("--abort").run();
    }

    // ------------------------------------------------------------------
    // The Herald: allied realms (remotes) - rally, recall, emissary
    // ------------------------------------------------------------------

    /** The roll of allied realms: "name url" pairs from remote -v. */
    public List<Ally> allies() throws IOException, InterruptedException {
        String out = command("allies").flags("-v").run();
        List<Ally> allies = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String line : out.split("\n")) {
            String[] p = line.strip().split("\\s+");
            if (p.length < 2) {
                continue;
            }
            if (seen.add(p[0])) {
                allies.add(new Ally(p[0], p[1]));
            }
        }
        return allies;
    }

    /**
     * Rallies the allied hosts: brings every banner of the given ally
     * (or all allies when null) up to date without touching the realm.
     */
    public void rally(String ally) throws IOException, InterruptedException {
        Command cmd = command("rally");
        if (ally != null && !ally.isBlank()) {
            cmd.target(ally);
        }
        cmd.flags("--prune").run();
    }

    /**
     * Recalls allied wisdom: brings the raised banner up to date with
     * its counterpart at the ally (pull with rebase-free fast path).
     */
    public void recall(String ally) throws IOException, InterruptedException {
        requireClearedField();
        Command cmd = command("recall").flags("--ff-only");
        if (ally != null && !ally.isBlank()) {
            cmd.target(ally);
        }
        cmd.run();
    }

    /**
     * Sends the emissary: carries the raised banner's newest feats to the
     * ally (push). Refuses to overwrite allied history.
     */
    public void sendEmissary(String ally) throws IOException, InterruptedException {
        String banner = activeBanner();
        Command cmd = command("emissary");
        if (ally != null && !ally.isBlank()) {
            cmd.target(ally);
        }
        cmd.target(banner).run();
    }

    /** An allied realm in the roll of allies (a remote). */
    public record Ally(String name, String url) {}

    // ------------------------------------------------------------------
    // Field state (working copy), muster, enlist, seal
    // ------------------------------------------------------------------

    /**
     * Musters the field. Staged (war chest) and unstaged (field) entries
     * come from NUL-separated porcelain output: entries are "XY path\0",
     * renames carry the old path in an extra token.
     */
    public List<Dispatch> muster() throws IOException, InterruptedException {
        String out = command("muster").flags("--porcelain", "-z").runRaw();
        List<Dispatch> dispatches = new ArrayList<>();
        String[] tokens = out.split("\0", -1);
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i];
            if (token.length() < 4) {
                continue;
            }
            String chest = token.substring(0, 1);   // staged (index) code
            String field = token.substring(1, 2);   // unstaged (worktree) code
            String path = token.substring(3);
            if (path.isBlank()) {
                continue;
            }
            if (chest.equals("?") || chest.equals("!")) {
                dispatches.add(new Dispatch(path, path, chest, false));
                continue;
            }
            String oldPath = null;
            if (chest.equals("R") || chest.equals("C")) {
                if (i + 1 < tokens.length && !tokens[i + 1].isBlank()) {
                    oldPath = tokens[i + 1];
                    i++;
                }
            }
            String effectiveOld = oldPath != null ? oldPath : path;
            if (!chest.equals(" ") && !chest.isEmpty()) {
                dispatches.add(new Dispatch(path, effectiveOld, chest, true));
            }
            if (!field.equals(" ") && !field.isEmpty()) {
                dispatches.add(new Dispatch(path, effectiveOld, field, false));
            }
        }
        return dispatches;
    }

    /** Conscripts a file into the vanguard (stages it). */
    public void enlist(String path) throws IOException, InterruptedException {
        command("enlist").flag("--").target(path).run();
    }

    /** Conscripts everything (stages all changes). */
    public void enlistAll() throws IOException, InterruptedException {
        command("enlist").flags("-A").run();
    }

    /** Pulls a file back out of the vanguard (unstages it). */
    public void release(String path) throws IOException, InterruptedException {
        command("retreat").flag("--").target(path).run();
    }

    /** Restores a file to its sworn state in the chest (discards edits). */
    public void restore(String path) throws IOException, InterruptedException {
        command("restore").flag("--").target(path).run();
    }

    /** Deletes an unscouted (untracked) file from the field. */
    public void vanquishUnscouted(String path) throws IOException, InterruptedException {
        File file = new File(realmDir, path);
        if (file.isFile() && !file.delete()) {
            throw new IOException("Could not vanquish " + path);
        }
    }

    /** Seals the vanguard's pact with the given words (commits). */
    public Feat seal(String words) throws IOException, InterruptedException {
        requireVanguard();
        command("seal").flags("-m", words).run();
        String hash = command("mark").flags("HEAD").run().trim();
        return new Feat(hash, List.of(), null, null, LocalDate.now(), words, "");
    }

    /** Seals the vanguard with both summary and body words. */
    public Feat seal(String summary, String body) throws IOException, InterruptedException {
        String words = body == null || body.isBlank()
                ? summary : summary + "\n\n" + body;
        return seal(words);
    }

    /**
     * Re-seals the newest feat: folds the vanguard into it and rewrites
     * its words (amend). The resulting hash replaces the old one.
     */
    public Feat reSeal(String words) throws IOException, InterruptedException {
        requireVanguard();
        command("seal").flags("--amend", "-m", words).run();
        String hash = command("mark").flags("HEAD").run().trim();
        return new Feat(hash, List.of(), null, null, LocalDate.now(), words, "");
    }

    /** Re-seal with separate summary and tale. */
    public Feat reSeal(String summary, String body) throws IOException, InterruptedException {
        String words = body == null || body.isBlank()
                ? summary : summary + "\n\n" + body;
        return reSeal(words);
    }

    /** Words of the newest feat, for pre-filling the re-seal dialog. */
    public Feat newestFeat() throws IOException, InterruptedException {
        List<Feat> trail = surveyTrail(1);
        if (trail.isEmpty()) {
            throw new IOException("The chronicle holds no feats yet.");
        }
        return trail.get(0);
    }

    /** Throws when the vanguard is empty (nothing staged to seal). */
    public void requireVanguard() throws IOException, InterruptedException {
        boolean anyStaged = muster().stream().anyMatch(Dispatch::staged);
        if (!anyStaged) {
            throw new IOException("The vanguard is empty - enlist files before sealing.");
        }
    }

    /** Throws when the field carries staged or unstaged changes. */
    public void requireClearedField() throws IOException, InterruptedException {
        if (!muster().isEmpty()) {
            throw new IOException("The field is not clear - seal or vanquish changes first.");
        }
    }

    // ------------------------------------------------------------------
    // Kamui (stash): vanish into the dimension, and return
    // ------------------------------------------------------------------

    /** Vanishes all changes into Kamui's dimension; returns the stash mark. */
    public String kamuiVanish() throws IOException, InterruptedException {
        if (muster().isEmpty()) {
            throw new IOException("Nothing to vanish into Kamui.");
        }
        command("kamui").flags("push", "--include-untracked", "-m", "ForkKnight kamui")
                .run();
        String ref = tryCommand("mark").flags("-q", "--verify", "refs/stash")
                .runOrNull();
        return ref == null ? "" : ref.strip();
    }

    /** True when Kamui's dimension holds at least one vanished change. */
    public boolean kamuiHolds() throws IOException, InterruptedException {
        String out = tryCommand("mark")
                .flags("-q", "--verify", "refs/stash").runOrNull();
        return out != null && !out.isBlank();
    }

    /** Summons the most recent vanished change back to the field. */
    public void kamuiSummon() throws IOException, InterruptedException {
        command("kamui").flags("pop").run();
    }

    // ------------------------------------------------------------------
    // Command plumbing
    // ------------------------------------------------------------------

    private Command command(String knightly) {
        return new Command(realmDir, CODEX.get(knightly));
    }

    private TryCommand tryCommand(String knightly) {
        return new TryCommand(realmDir, CODEX.get(knightly));
    }

    /** Fluent builder for one dragon-tongue invocation. */
    private static class Command {
        protected final File realmDir;
        protected final String verb;
        private final boolean tolerateFailure;
        private final List<String> parts = new ArrayList<>();

        Command(File realmDir, String verb) {
            this(realmDir, verb, false);
        }

        Command(File realmDir, String verb, boolean tolerateFailure) {
            this.realmDir = realmDir;
            this.verb = verb;
            this.tolerateFailure = tolerateFailure;
        }

        Command flags(String... flags) {
            parts.addAll(Arrays.asList(flags));
            return this;
        }

        Command flag(String flag) {
            parts.add(flag);
            return this;
        }

        Command target(String target) {
            parts.add(target);
            return this;
        }

        String[] toArray() {
            return parts.toArray(new String[0]);
        }

        String run() throws IOException, InterruptedException {
            return Chronicle.exec(realmDir, verb, toArray(), false, tolerateFailure);
        }

        String runRaw() throws IOException, InterruptedException {
            return Chronicle.exec(realmDir, verb, toArray(), true, tolerateFailure);
        }

        String runOrNull() throws IOException, InterruptedException {
            return Chronicle.exec(realmDir, verb, toArray(), false, true);
        }
    }

    private static final class TryCommand extends Command {
        TryCommand(File realmDir, String verb) {
            super(realmDir, verb, true);
        }
    }

    static String exec(File realmDir, String verb, String[] args,
                       boolean keepNul, boolean tolerateFailure)
            throws IOException, InterruptedException {
        String[] cmd = new String[args.length + 4];
        cmd[0] = "git";
        cmd[1] = "-C";
        cmd[2] = realmDir.getAbsolutePath();
        cmd[3] = verb;
        System.arraycopy(args, 0, cmd, 4, args.length);

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
            throw new IOException("the dragon slept too long on: " + verb);
        }
        outThread.join(5000);
        errThread.join(5000);

        if (process.exitValue() != 0) {
            if (tolerateFailure) {
                return null;
            }
            throw new IOException(verb + " failed (exit " + process.exitValue()
                    + "): " + err.toString().trim());
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
