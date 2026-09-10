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
}
