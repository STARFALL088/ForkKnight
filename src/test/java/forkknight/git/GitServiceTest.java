package forkknight.git;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GitServiceTest {

    @TempDir
    Path tempDir;
    private GitService initRepo() throws IOException, InterruptedException {
        Path repo = tempDir.resolve("repo");
        Files.createDirectories(repo);
        GitService service = new GitService(repo.toFile());
        Process p = new ProcessBuilder("git", "init", "-q", "-b", "main")
                .directory(repo.toFile()).start();
        p.waitFor();
        new ProcessBuilder("git", "-C", repo.toString(), "config", "user.email", "t@example.com").start().waitFor();
        new ProcessBuilder("git", "-C", repo.toString(), "config", "user.name", "Tester").start().waitFor();
        return service;
    }

    private void git(GitService service, String... args) throws Exception {
        List<String> cmd = new java.util.ArrayList<>();
        cmd.add("git");
        cmd.add("-C");
        cmd.add(service.getRepoDir().getAbsolutePath());
        cmd.addAll(List.of(args));
        Process proc = new ProcessBuilder(cmd).start();
        if (proc.waitFor() != 0) {
            throw new IOException("git " + String.join(" ", args) + " failed");
        }
    }

    @Test
    void validatesRealRepository() throws Exception {
        GitService service = initRepo();
        assertDoesNotThrow(service::validateRepository);
    }

    @Test
    void rejectsNonRepository() throws Exception {
        Path plain = tempDir.resolve("plain");
        Files.createDirectories(plain);
        GitService service = new GitService(plain.toFile());
        assertThrows(IOException.class, service::validateRepository);
    }

    @Test
    void parsesCommitLog() throws Exception {
        GitService service = initRepo();
        Files.writeString(service.getRepoDir().toPath().resolve("a.txt"), "hello\n");
        git(service, "add", "a.txt");
        git(service, "commit", "-q", "-m", "first commit");
        Files.writeString(service.getRepoDir().toPath().resolve("a.txt"), "hello world\n");
        git(service, "commit", "-aqm", "second commit");

        List<Commit> commits = service.log();
        assertEquals(2, commits.size());
        assertEquals("second commit", commits.get(0).summary());
        assertEquals("first commit", commits.get(1).summary());
        assertEquals("Tester", commits.get(0).author());
        assertEquals("t@example.com", commits.get(0).email());
        assertEquals(7, commits.get(0).shortHash().length());
    }

    @Test
    void changedFilesForCommit() throws Exception {
        GitService service = initRepo();
        Files.writeString(service.getRepoDir().toPath().resolve("x.txt"), "1\n");
        git(service, "add", "x.txt");
        git(service, "commit", "-qm", "add x");
        Files.writeString(service.getRepoDir().toPath().resolve("y.txt"), "2\n");
        git(service, "add", "y.txt");
        git(service, "commit", "-qm", "add y");

        List<Commit> commits = service.log();
        List<FileChange> files = service.changedFiles(commits.get(0).hash());
        assertEquals(1, files.size());
        assertEquals("A", files.get(0).status());
        assertEquals("y.txt", files.get(0).newPath());
        assertFalse(files.get(0).isRename());
        assertEquals("Added", files.get(0).displayStatus());
    }

    @Test
    void changedFilesDetectsRename() throws Exception {
        GitService service = initRepo();
        Path repo = service.getRepoDir().toPath();
        Files.writeString(repo.resolve("old.txt"), "one\ntwo\nthree\n");
        git(service, "add", "old.txt");
        git(service, "commit", "-qm", "add old");
        git(service, "mv", "old.txt", "new.txt");
        git(service, "commit", "-qm", "rename old");

        List<Commit> commits = service.log();
        List<FileChange> files = service.changedFiles(commits.get(0).hash());
        assertEquals(1, files.size());
        FileChange change = files.get(0);
        assertTrue(change.isRename(), "expected rename, got " + change.status()
                + " " + change.oldPath() + " -> " + change.newPath());
        assertEquals("old.txt", change.oldPath());
        assertEquals("new.txt", change.newPath());
        assertEquals("Renamed", change.displayStatus());
    }

    @Test
    void changedFilesForMergeCommit() throws Exception {
        GitService service = initRepo();
        git(service, "commit", "-q", "--allow-empty", "-m", "base");
        git(service, "checkout", "-qb", "side");
        Files.writeString(service.getRepoDir().toPath().resolve("s.txt"), "s\n");
        git(service, "add", "s.txt");
        git(service, "commit", "-qm", "side work");
        git(service, "checkout", "-q", "main");
        git(service, "merge", "-q", "--no-ff", "-m", "merge side", "side");

        List<Commit> commits = service.log();
        // The merge commit should list the file brought in from the side branch.
        List<FileChange> files = service.changedFiles(commits.get(0).hash());
        assertEquals(1, files.size());
        assertEquals("s.txt", files.get(0).newPath());
    }

    @Test
    void diffContainsContent() throws Exception {
        GitService service = initRepo();
        Files.writeString(service.getRepoDir().toPath().resolve("d.txt"), "line one\n");
        git(service, "add", "d.txt");
        git(service, "commit", "-qm", "add d");

        List<Commit> commits = service.log();
        String diff = service.showDiff(commits.get(0).hash());
        assertTrue(diff.contains("line one"));
        assertTrue(diff.contains("add d"));
    }

    @Test
    void fileDiffShowsOnlyThatFile() throws Exception {
        GitService service = initRepo();
        Path repo = service.getRepoDir().toPath();
        Files.writeString(repo.resolve("a.txt"), "alpha\n");
        Files.writeString(repo.resolve("b.txt"), "beta\n");
        git(service, "add", "a.txt", "b.txt");
        git(service, "commit", "-qm", "two files");
        Files.writeString(repo.resolve("a.txt"), "alpha 2\n");
        git(service, "commit", "-aqm", "touch a");

        List<Commit> commits = service.log();
        String diff = service.showFileDiff(commits.get(0).hash(), "a.txt");
        assertTrue(diff.contains("alpha"));
        assertFalse(diff.contains("beta"));
    }

    @Test
    void fileDiffForMissingPathIsEmpty() throws Exception {
        GitService service = initRepo();
        Files.writeString(service.getRepoDir().toPath().resolve("z.txt"), "z\n");
        git(service, "add", "z.txt");
        git(service, "commit", "-qm", "add z");

        List<Commit> commits = service.log();
        assertEquals("", service.showFileDiff(commits.get(0).hash(), "no/such/file.txt"));
    }

    @Test
    void listsBranchesWithCurrentFirst() throws Exception {
        GitService service = initRepo();
        git(service, "commit", "-q", "--allow-empty", "-m", "base");
        git(service, "branch", "feature");

        assertEquals(List.of("main", "feature"), service.branches());
        assertEquals("main", service.currentBranch());
    }

    @Test
    void logForSpecificBranch() throws Exception {
        GitService service = initRepo();
        git(service, "commit", "-q", "--allow-empty", "-m", "base");
        git(service, "checkout", "-qb", "feature");
        Files.writeString(service.getRepoDir().toPath().resolve("f.txt"), "f\n");
        git(service, "add", "f.txt");
        git(service, "commit", "-qm", "feature work");
        git(service, "checkout", "-q", "main");
        git(service, "commit", "-q", "--allow-empty", "-m", "main work");

        List<Commit> featureLog = service.log("feature", Integer.MAX_VALUE);
        assertEquals(2, featureLog.size());
        assertEquals("feature work", featureLog.get(0).summary());

        List<Commit> mainLog = service.log("main", Integer.MAX_VALUE);
        assertEquals(2, mainLog.size());
        assertEquals("main work", mainLog.get(0).summary());

        // The no-branch overload follows the current HEAD (main).
        List<Commit> headLog = service.log();
        assertEquals("main work", headLog.get(0).summary());
    }

    // ------------------------------------------------------------------
    // Working-copy status, staging, commits
    // ------------------------------------------------------------------

    @Test
    void statusParsesStagedUnstagedAndUntracked() throws Exception {
        GitService service = initRepo();
        Path repo = service.getRepoDir().toPath();
        Files.writeString(repo.resolve("base.txt"), "base\n");
        git(service, "add", "base.txt");
        git(service, "commit", "-qm", "base");
        Files.writeString(repo.resolve("mod.txt"), "m1\n");
        git(service, "add", "mod.txt");
        Files.writeString(repo.resolve("mod.txt"), "m2\n");
        Files.writeString(repo.resolve("new.txt"), "n\n");

        List<WorkDirChange> changes = service.status();
        // staged A mod.txt, unstaged M mod.txt, untracked new.txt
        assertEquals(3, changes.size());
        assertTrue(changes.stream().anyMatch(c -> c.staged() && c.statusCode().equals("A")
                && c.newPath().equals("mod.txt")));
        assertTrue(changes.stream().anyMatch(c -> !c.staged() && c.statusCode().equals("M")
                && c.newPath().equals("mod.txt")));
        assertTrue(changes.stream().anyMatch(c -> c.statusCode().equals("?")
                && c.newPath().equals("new.txt")));
    }

    @Test
    void statusParsesStagedRename() throws Exception {
        GitService service = initRepo();
        Path repo = service.getRepoDir().toPath();
        Files.writeString(repo.resolve("a.txt"), "a\n");
        git(service, "add", "a.txt");
        git(service, "commit", "-qm", "add a");
        git(service, "mv", "a.txt", "b.txt");

        List<WorkDirChange> changes = service.status();
        assertEquals(1, changes.size());
        WorkDirChange change = changes.get(0);
        assertTrue(change.staged());
        assertEquals("R", change.statusCode());
        assertEquals("a.txt", change.oldPath());
        assertEquals("b.txt", change.newPath());
    }

    @Test
    void stageAndCommitRoundTrip() throws Exception {
        GitService service = initRepo();
        Path repo = service.getRepoDir().toPath();
        Files.writeString(repo.resolve("f.txt"), "content\n");

        service.stage("f.txt");
        assertTrue(service.status().stream().anyMatch(WorkDirChange::staged));

        Commit head = service.commit("add f");
        assertEquals("add f", head.summary());
        assertTrue(service.status().isEmpty());
        assertEquals("add f", service.log().get(0).summary());
    }

    @Test
    void unstageRemovesFromIndex() throws Exception {
        GitService service = initRepo();
        Path repo = service.getRepoDir().toPath();
        Files.writeString(repo.resolve("g.txt"), "g\n");
        service.stage("g.txt");
        service.unstage("g.txt");
        List<WorkDirChange> changes = service.status();
        assertEquals(1, changes.size());
        assertFalse(changes.get(0).staged());
        assertEquals("?", changes.get(0).statusCode());
    }

    @Test
    void commitWithoutStagedChangesFails() throws Exception {
        GitService service = initRepo();
        git(service, "commit", "-q", "--allow-empty", "-m", "base");
        assertThrows(IOException.class, service::requireStaged);
    }

    @Test
    void discardRestoresWorktreeFile() throws Exception {
        GitService service = initRepo();
        Path repo = service.getRepoDir().toPath();
        Files.writeString(repo.resolve("h.txt"), "original\n");
        git(service, "add", "h.txt");
        git(service, "commit", "-qm", "add h");
        Files.writeString(repo.resolve("h.txt"), "changed\n");
        service.discard("h.txt");
        assertEquals("original\n", Files.readString(repo.resolve("h.txt")));
    }

    // ------------------------------------------------------------------
    // Branch management
    // ------------------------------------------------------------------

    @Test
    void createBranchPointsAtHead() throws Exception {
        GitService service = initRepo();
        git(service, "commit", "-q", "--allow-empty", "-m", "base");
        service.createBranch("feature");
        List<String> branches = service.branches();
        assertTrue(branches.contains("feature"));
        assertEquals("main", service.currentBranch());
        // New branch points at the same commit as HEAD.
        String head = service.log().get(0).hash();
        assertEquals(head, service.log("feature", 1).get(0).hash());
    }

    @Test
    void switchBranchMovesHead() throws Exception {
        GitService service = initRepo();
        git(service, "commit", "-q", "--allow-empty", "-m", "base");
        service.switchToNewBranch("topic");
        assertEquals("topic", service.currentBranch());
        git(service, "commit", "-q", "--allow-empty", "-m", "topic work");
        assertEquals("topic work", service.log().get(0).summary());

        service.switchBranch("main");
        assertEquals("main", service.currentBranch());
        assertEquals("base", service.log().get(0).summary());
    }

    @Test
    void switchBranchRefusesDirtyWorktree() throws Exception {
        GitService service = initRepo();
        Path repo = service.getRepoDir().toPath();
        git(service, "commit", "-q", "--allow-empty", "-m", "base");
        service.createBranch("other");
        Files.writeString(repo.resolve("dirty.txt"), "d\n");
        assertThrows(IOException.class, () -> service.switchBranch("other"));
        assertEquals("main", service.currentBranch());
    }

    @Test
    void deleteBranchRemovesRef() throws Exception {
        GitService service = initRepo();
        git(service, "commit", "-q", "--allow-empty", "-m", "base");
        service.createBranch("doomed");
        service.deleteBranch("doomed", false);
        assertFalse(service.branches().contains("doomed"));
    }

    @Test
    void deleteBranchRefusesCurrentBranch() throws Exception {
        GitService service = initRepo();
        git(service, "commit", "-q", "--allow-empty", "-m", "base");
        assertThrows(IOException.class, () -> service.deleteBranch("main", false));
    }

    @Test
    void deleteBranchRefusesUnmergedWithoutForce() throws Exception {
        GitService service = initRepo();
        git(service, "commit", "-q", "--allow-empty", "-m", "base");
        service.switchToNewBranch("side");
        git(service, "commit", "-q", "--allow-empty", "-m", "side work");
        service.switchBranch("main");
        assertThrows(IOException.class, () -> service.deleteBranch("side", false));
        service.deleteBranch("side", true);
        assertFalse(service.branches().contains("side"));
    }

    // ------------------------------------------------------------------
    // Tags and stash
    // ------------------------------------------------------------------

    @Test
    void createAndListTags() throws Exception {
        GitService service = initRepo();
        git(service, "commit", "-q", "--allow-empty", "-m", "base");
        git(service, "commit", "-q", "--allow-empty", "-m", "second");
        service.createTag("v2", null);          // HEAD
        service.createTag("v1", service.log().get(1).hash());

        List<String> tags = service.tags();
        assertEquals(List.of("v2", "v1"), tags);
        assertEquals("second", service.tagCommit("v2").summary());
        assertEquals("base", service.tagCommit("v1").summary());
    }

    @Test
    void deleteTagRemovesRef() throws Exception {
        GitService service = initRepo();
        git(service, "commit", "-q", "--allow-empty", "-m", "base");
        service.createTag("gone", null);
        service.deleteTag("gone");
        assertTrue(service.tags().isEmpty());
    }

    @Test
    void tagCommitRejectsUnknownTag() throws Exception {
        GitService service = initRepo();
        git(service, "commit", "-q", "--allow-empty", "-m", "base");
        assertThrows(Exception.class, () -> service.tagCommit("nope"));
    }

    @Test
    void stashRoundTrip() throws Exception {
        GitService service = initRepo();
        Path repo = service.getRepoDir().toPath();
        Files.writeString(repo.resolve("w.txt"), "work in progress\n");
        git(service, "add", "w.txt");
        git(service, "commit", "-qm", "base");
        Files.writeString(repo.resolve("w.txt"), "stashed content\n");
        Files.writeString(repo.resolve("u.txt"), "untracked\n");

        assertFalse(service.hasStash());
        service.stash();
        assertTrue(service.hasStash());
        assertTrue(service.status().isEmpty(), "worktree should be clean after stash");

        service.stashPop();
        assertFalse(service.hasStash());
        assertEquals("stashed content\n", Files.readString(repo.resolve("w.txt")));
        assertEquals("untracked\n", Files.readString(repo.resolve("u.txt")));
    }

    @Test
    void stashWithNothingToStashFails() throws Exception {
        GitService service = initRepo();
        git(service, "commit", "-q", "--allow-empty", "-m", "base");
        assertThrows(IOException.class, service::stash);
        assertFalse(service.hasStash());
    }
}
