package forkknight.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class ChronicleTest {

    @TempDir
    Path tempDir;

    private Chronicle initRealm() throws IOException, InterruptedException {
        Path realm = tempDir.resolve("realm");
        Files.createDirectories(realm);
        Chronicle chronicle = new Chronicle(realm.toFile());
        dragon(realm, "init", "-q", "-b", "main");
        dragon(realm, "config", "user.email", "t@example.com");
        dragon(realm, "config", "user.name", "Tester");
        return chronicle;
    }

    /** Direct dragon-tongue access for ARRANGING tests only. */
    private static void dragon(Path realm, String... args)
            throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>();
        cmd.add("git");
        cmd.add("-C");
        cmd.add(realm.toString());
        cmd.addAll(List.of(args));
        Process p = new ProcessBuilder(cmd).start();
        if (p.waitFor() != 0) {
            throw new IOException("dragon refused: " + String.join(" ", args));
        }
    }

    @Test
    void validatesRealRealm() throws Exception {
        Chronicle chronicle = initRealm();
        assertDoesNotThrow(chronicle::validateRealm);
    }

    @Test
    void rejectsNonRealm() throws Exception {
        Files.createDirectories(tempDir.resolve("plain"));
        Chronicle chronicle = new Chronicle(tempDir.resolve("plain").toFile());
        assertThrows(IOException.class, chronicle::validateRealm);
    }

    @Test
    void surveyParsesFeatsWithParents() throws Exception {
        Chronicle chronicle = initRealm();
        Path realm = chronicle.getRealmDir().toPath();
        Files.writeString(realm.resolve("a.txt"), "hello\n");
        dragon(realm, "add", "a.txt");
        dragon(realm, "commit", "-q", "-m", "first feat");
        Files.writeString(realm.resolve("a.txt"), "hello world\n");
        dragon(realm, "commit", "-aqm", "second feat");

        List<Feat> feats = chronicle.surveyTrail();
        assertEquals(2, feats.size());
        assertEquals("second feat", feats.get(0).summary());
        assertEquals(List.of(), feats.get(1).parentHashes());
        assertEquals(feats.get(1).hash(), feats.get(0).parentHashes().get(0));
        assertFalse(feats.get(0).isFusion());
        assertEquals("Tester", feats.get(0).author());
    }

    @Test
    void fusionFeatsCarryTwoParents() throws Exception {
        Chronicle chronicle = initRealm();
        Path realm = chronicle.getRealmDir().toPath();
        dragon(realm, "commit", "-q", "--allow-empty", "-m", "base");
        dragon(realm, "checkout", "-qb", "side");
        dragon(realm, "commit", "-q", "--allow-empty", "-m", "side feat");
        dragon(realm, "checkout", "-q", "main");
        dragon(realm, "merge", "-q", "--no-ff", "-m", "fusion", "side");

        List<Feat> feats = chronicle.surveyTrail();
        Feat fusion = feats.get(0);
        assertEquals("fusion", fusion.summary());
        assertTrue(fusion.isFusion());
        assertEquals(2, fusion.parentHashes().size());
    }

    @Test
    void tollOfListsChangedPaths() throws Exception {
        Chronicle chronicle = initRealm();
        Path realm = chronicle.getRealmDir().toPath();
        Files.writeString(realm.resolve("x.txt"), "1\n");
        dragon(realm, "add", "x.txt");
        dragon(realm, "commit", "-qm", "add x");
        Files.writeString(realm.resolve("y.txt"), "2\n");
        dragon(realm, "add", "y.txt");
        dragon(realm, "commit", "-qm", "add y");

        List<Dispatch> toll = chronicle.tollOf(chronicle.surveyTrail().get(0).hash());
        assertEquals(1, toll.size());
        assertEquals("A", toll.get(0).statusCode());
        assertEquals("y.txt", toll.get(0).path());
    }

    @Test
    void tollOfDetectsRenames() throws Exception {
        Chronicle chronicle = initRealm();
        Path realm = chronicle.getRealmDir().toPath();
        Files.writeString(realm.resolve("old.txt"), "one\ntwo\nthree\n");
        dragon(realm, "add", "old.txt");
        dragon(realm, "commit", "-qm", "add old");
        dragon(realm, "mv", "old.txt", "new.txt");
        dragon(realm, "commit", "-qm", "rename");

        List<Dispatch> toll = chronicle.tollOf(chronicle.surveyTrail().get(0).hash());
        assertEquals(1, toll.size());
        assertTrue(toll.get(0).isRenaming());
        assertEquals("old.txt", toll.get(0).oldPath());
        assertEquals("new.txt", toll.get(0).path());
    }

    @Test
    void musterSplitsChestAndField() throws Exception {
        Chronicle chronicle = initRealm();
        Path realm = chronicle.getRealmDir().toPath();
        dragon(realm, "commit", "-q", "--allow-empty", "-m", "base");
        Files.writeString(realm.resolve("m.txt"), "v1\n");
        dragon(realm, "add", "m.txt");
        Files.writeString(realm.resolve("m.txt"), "v2\n");
        Files.writeString(realm.resolve("u.txt"), "new\n");

        List<Dispatch> dispatches = chronicle.muster();
        assertEquals(3, dispatches.size());
        assertTrue(dispatches.stream().anyMatch(d -> d.staged()
                && d.statusCode().equals("A") && d.path().equals("m.txt")));
        assertTrue(dispatches.stream().anyMatch(d -> !d.staged()
                && d.statusCode().equals("M") && d.path().equals("m.txt")));
        assertTrue(dispatches.stream().anyMatch(d -> d.statusCode().equals("?")));
    }

    @Test
    void enlistSealReleaseRoundTrip() throws Exception {
        Chronicle chronicle = initRealm();
        Path realm = chronicle.getRealmDir().toPath();
        Files.writeString(realm.resolve("f.txt"), "content\n");

        chronicle.enlist("f.txt");
        assertTrue(chronicle.muster().stream().anyMatch(Dispatch::staged));

        chronicle.release("f.txt");
        assertTrue(chronicle.muster().stream().noneMatch(Dispatch::staged));

        chronicle.enlist("f.txt");
        Feat sealed = chronicle.seal("add f");
        assertEquals("add f", sealed.summary());
        assertTrue(chronicle.muster().isEmpty());
        assertEquals("add f", chronicle.surveyTrail().get(0).summary());
    }

    @Test
    void sealRefusesEmptyVanguard() throws Exception {
        Chronicle chronicle = initRealm();
        dragon(tempDir.resolve("realm"), "commit", "-q", "--allow-empty", "-m", "base");
        assertThrows(IOException.class, chronicle::requireVanguard);
    }

    @Test
    void sealWithBodyEmbedsBothParts() throws Exception {
        Chronicle chronicle = initRealm();
        Path realm = chronicle.getRealmDir().toPath();
        Files.writeString(realm.resolve("b.txt"), "b\n");
        chronicle.enlist("b.txt");
        chronicle.seal("the title", "the long tale");
        Feat top = chronicle.surveyTrail().get(0);
        assertEquals("the title", top.summary());
        assertTrue(top.body().contains("the long tale"));
    }

    @Test
    void restoreUndoesFieldEdits() throws Exception {
        Chronicle chronicle = initRealm();
        Path realm = chronicle.getRealmDir().toPath();
        Files.writeString(realm.resolve("h.txt"), "original\n");
        dragon(realm, "add", "h.txt");
        dragon(realm, "commit", "-qm", "add h");
        Files.writeString(realm.resolve("h.txt"), "changed\n");
        chronicle.restore("h.txt");
        assertEquals("original\n", Files.readString(realm.resolve("h.txt")));
    }

    @Test
    void bannerOpsRaiseMarchAndFell() throws Exception {
        Chronicle chronicle = initRealm();
        dragon(tempDir.resolve("realm"), "commit", "-q", "--allow-empty", "-m", "base");

        chronicle.raiseBanner("feature");
        assertEquals("main", chronicle.activeBanner());
        assertTrue(chronicle.banners().stream()
                .anyMatch(b -> b.name().equals("feature") && !b.active()));

        chronicle.marchToNewBanner("topic");
        assertEquals("topic", chronicle.activeBanner());
        chronicle.marchToBanner("main");
        assertEquals("main", chronicle.activeBanner());

        chronicle.fellBanner("feature", false);
        assertFalse(chronicle.banners().stream().anyMatch(b -> b.name().equals("feature")));
    }

    @Test
    void fellBannerRefusesRaisedAndUnmerged() throws Exception {
        Chronicle chronicle = initRealm();
        Path realm = chronicle.getRealmDir().toPath();
        dragon(realm, "commit", "-q", "--allow-empty", "-m", "base");
        dragon(realm, "branch", "side");
        dragon(realm, "checkout", "-q", "side");
        dragon(realm, "commit", "-q", "--allow-empty", "-m", "side work");
        dragon(realm, "checkout", "-q", "main");

        assertThrows(IOException.class, () -> chronicle.fellBanner("main", false));
        assertThrows(IOException.class, () -> chronicle.fellBanner("side", false));
        chronicle.fellBanner("side", true);
    }

    @Test
    void marchRefusesMuddyField() throws Exception {
        Chronicle chronicle = initRealm();
        Path realm = chronicle.getRealmDir().toPath();
        dragon(realm, "commit", "-q", "--allow-empty", "-m", "base");
        chronicle.raiseBanner("other");
        Files.writeString(realm.resolve("d.txt"), "dirt\n");
        assertThrows(IOException.class, () -> chronicle.marchToBanner("other"));
        assertEquals("main", chronicle.activeBanner());
    }

    @Test
    void sigilsOrderByFeatRecency() throws Exception {
        Chronicle chronicle = initRealm();
        Path realm = chronicle.getRealmDir().toPath();
        dragon(realm, "commit", "-q", "--allow-empty", "-m", "base");
        dragon(realm, "commit", "-q", "--allow-empty", "-m", "second");
        List<Feat> feats = chronicle.surveyTrail();
        chronicle.pressSigil("v2", null);   // HEAD -> second
        chronicle.pressSigil("v1", feats.get(1).hash());

        List<Sigil> sigils = chronicle.sigils();
        assertEquals(List.of("v2", "v1"),
                sigils.stream().map(Sigil::name).toList());
        assertEquals("second", sigils.get(0).feat().summary());
        assertEquals("base", sigils.get(1).feat().summary());
    }

    @Test
    void meltSigilRemovesIt() throws Exception {
        Chronicle chronicle = initRealm();
        dragon(tempDir.resolve("realm"), "commit", "-q", "--allow-empty", "-m", "base");
        chronicle.pressSigil("gone", null);
        chronicle.meltSigil("gone");
        assertTrue(chronicle.sigils().isEmpty());
    }

    @Test
    void kamuiVanishAndSummon() throws Exception {
        Chronicle chronicle = initRealm();
        Path realm = chronicle.getRealmDir().toPath();
        Files.writeString(realm.resolve("w.txt"), "v1\n");
        dragon(realm, "add", "w.txt");
        dragon(realm, "commit", "-qm", "base");
        Files.writeString(realm.resolve("w.txt"), "v2\n");
        Files.writeString(realm.resolve("u.txt"), "unscouted\n");

        assertFalse(chronicle.kamuiHolds());
        chronicle.kamuiVanish();
        assertTrue(chronicle.kamuiHolds());
        assertTrue(chronicle.muster().isEmpty());

        chronicle.kamuiSummon();
        assertFalse(chronicle.kamuiHolds());
        assertEquals("v2\n", Files.readString(realm.resolve("w.txt")));
        assertEquals("unscouted\n", Files.readString(realm.resolve("u.txt")));
    }

    @Test
    void kamuiRefusesEmptyField() throws Exception {
        Chronicle chronicle = initRealm();
        dragon(tempDir.resolve("realm"), "commit", "-q", "--allow-empty", "-m", "base");
        assertThrows(IOException.class, chronicle::kamuiVanish);
    }

    @Test
    void recountAndRecountPath() throws Exception {
        Chronicle chronicle = initRealm();
        Path realm = chronicle.getRealmDir().toPath();
        Files.writeString(realm.resolve("a.txt"), "alpha\n");
        Files.writeString(realm.resolve("b.txt"), "beta\n");
        dragon(realm, "add", "a.txt", "b.txt");
        dragon(realm, "commit", "-qm", "two files");
        Files.writeString(realm.resolve("a.txt"), "alpha 2\n");
        dragon(realm, "commit", "-aqm", "touch a");

        String hash = chronicle.surveyTrail().get(0).hash();
        assertTrue(chronicle.recount(hash).contains("alpha"));
        assertTrue(chronicle.recountPath(hash, "a.txt").contains("alpha"));
        assertFalse(chronicle.recountPath(hash, "a.txt").contains("beta"));
        assertEquals("", chronicle.recountPath(hash, "no/such.txt"));
    }

    @Test
    void surveySpecificBanner() throws Exception {
        Chronicle chronicle = initRealm();
        Path realm = chronicle.getRealmDir().toPath();
        dragon(realm, "commit", "-q", "--allow-empty", "-m", "base");
        dragon(realm, "checkout", "-qb", "feature");
        dragon(realm, "commit", "-q", "--allow-empty", "-m", "feature feat");
        dragon(realm, "checkout", "-q", "main");
        dragon(realm, "commit", "-q", "--allow-empty", "-m", "main feat");

        assertEquals("feature feat",
                chronicle.surveyTrail("feature", Integer.MAX_VALUE).get(0).summary());
        assertEquals("main feat",
                chronicle.surveyTrail("main", Integer.MAX_VALUE).get(0).summary());
        assertEquals("main feat", chronicle.surveyTrail().get(0).summary());
    }

    @Test
    void vanquishUnscoutedDeletesFile() throws Exception {
        Chronicle chronicle = initRealm();
        Path realm = chronicle.getRealmDir().toPath();
        Files.writeString(realm.resolve("ghost.txt"), "boo\n");
        chronicle.vanquishUnscouted("ghost.txt");
        assertFalse(Files.exists(realm.resolve("ghost.txt")));
    }

    // ------------------------------------------------------------------
    // Fusion (merge)
    // ------------------------------------------------------------------

    @Test
    void fuseBannerSealsAFusionFeat() throws Exception {
        Chronicle chronicle = initRealm();
        Path realm = chronicle.getRealmDir().toPath();
        dragon(realm, "commit", "-q", "--allow-empty", "-m", "base");
        dragon(realm, "checkout", "-qb", "side");
        Files.writeString(realm.resolve("s.txt"), "side\n");
        dragon(realm, "add", "s.txt");
        dragon(realm, "commit", "-qm", "side feat");
        dragon(realm, "checkout", "-q", "main");
        Files.writeString(realm.resolve("m.txt"), "main\n");
        dragon(realm, "add", "m.txt");
        dragon(realm, "commit", "-qm", "main feat");

        assertFalse(chronicle.fusionInDispute());
        Feat fusion = chronicle.fuseBanner("side");
        assertTrue(fusion.hash() != null && !fusion.hash().isBlank());
        // The newest feat on main is now a fusion with two parents.
        Feat head = chronicle.surveyTrail().get(0);
        assertTrue(head.isFusion());
        assertEquals(2, head.parentHashes().size());
        assertFalse(chronicle.fusionInDispute());
        // Both sides' work is present in the tree.
        assertTrue(Files.exists(realm.resolve("s.txt")));
        assertTrue(Files.exists(realm.resolve("m.txt")));
    }

    @Test
    void fuseBannerRefusesMuddyField() throws Exception {
        Chronicle chronicle = initRealm();
        Path realm = chronicle.getRealmDir().toPath();
        dragon(realm, "commit", "-q", "--allow-empty", "-m", "base");
        dragon(realm, "checkout", "-qb", "side");
        dragon(realm, "commit", "-q", "--allow-empty", "-m", "side feat");
        dragon(realm, "checkout", "-q", "main");
        Files.writeString(realm.resolve("d.txt"), "dirt\n");
        assertThrows(IOException.class, () -> chronicle.fuseBanner("side"));
        // No fusion feat was sealed.
        assertFalse(chronicle.surveyTrail().get(0).isFusion());
    }

    @Test
    void disputedFusionCanBeAbandoned() throws Exception {
        Chronicle chronicle = initRealm();
        Path realm = chronicle.getRealmDir().toPath();
        dragon(realm, "commit", "-q", "--allow-empty", "-m", "base");
        dragon(realm, "checkout", "-qb", "side");
        Files.writeString(realm.resolve("c.txt"), "side version\n");
        dragon(realm, "add", "c.txt");
        dragon(realm, "commit", "-qm", "side writes c");
        dragon(realm, "checkout", "-q", "main");
        Files.writeString(realm.resolve("c.txt"), "main version\n");
        dragon(realm, "add", "c.txt");
        dragon(realm, "commit", "-qm", "main writes c");

        // Both sides touched the same path: the fusion enters a dispute.
        try {
            chronicle.fuseBanner("side");
        } catch (IOException expected) {
            // the dragon refuses to auto-seal a conflicted fusion
        }
        assertTrue(chronicle.fusionInDispute());
        chronicle.abandonDisputedFusion();
        assertFalse(chronicle.fusionInDispute());
        // The trail is back to the pre-fusion state.
        assertFalse(chronicle.surveyTrail().get(0).isFusion());
        assertEquals("main writes c", chronicle.surveyTrail().get(0).summary());
    }

    // ------------------------------------------------------------------
    // The Herald: allies (remotes), rally, recall, emissary
    // ------------------------------------------------------------------

    /** Creates a bare "allied realm" and links the working realm to it. */
    private Chronicle realmWithAlly() throws Exception {
        Chronicle chronicle = initRealm();
        Path realm = chronicle.getRealmDir().toPath();
        Path ally = tempDir.resolve("allied-realm.git");
        Files.createDirectories(ally);
        dragon(ally, "init", "-q", "--bare", "-b", "main", ".");
        dragon(realm, "remote", "add", "ally", ally.toAbsolutePath().toString());
        dragon(realm, "commit", "-q", "--allow-empty", "-m", "first feat");
        return chronicle;
    }

    @Test
    void alliesRollListsTheAlly() throws Exception {
        Chronicle chronicle = realmWithAlly();
        List<Chronicle.Ally> allies = chronicle.allies();
        assertEquals(1, allies.size());
        assertEquals("ally", allies.get(0).name());
        assertTrue(allies.get(0).url().endsWith("allied-realm.git"));
    }

    @Test
    void emissaryThenRallyRoundTripsFeats() throws Exception {
        Chronicle chronicle = realmWithAlly();
        Path realm = chronicle.getRealmDir().toPath();
        dragon(realm, "push", "-q", "-u", "ally", "main");
        dragon(realm, "commit", "-q", "--allow-empty", "-m", "second feat");

        // Emissary carries the new feat to the ally...
        chronicle.sendEmissary("ally");
        // ...and a second clone rallying from the ally sees it.
        Path elsewhere = tempDir.resolve("elsewhere");
        Files.createDirectories(elsewhere);
        dragon(elsewhere, "clone", "-q",
                tempDir.resolve("allied-realm.git").toAbsolutePath().toString(), ".");
        Chronicle otherSide = new Chronicle(elsewhere.toFile());
        assertEquals("second feat", otherSide.surveyTrail().get(0).summary());
    }

    @Test
    void recallBringsAllyFeatsIntoTheRealm() throws Exception {
        Chronicle chronicle = realmWithAlly();
        Path realm = chronicle.getRealmDir().toPath();
        dragon(realm, "push", "-q", "-u", "ally", "main");

        // An ally-side knight adds a feat on their copy of the trail.
        Path elsewhere = tempDir.resolve("elsewhere");
        Files.createDirectories(elsewhere);
        dragon(elsewhere, "clone", "-q",
                tempDir.resolve("allied-realm.git").toAbsolutePath().toString(), ".");
        dragon(elsewhere, "config", "user.email", "o@x");
        dragon(elsewhere, "config", "user.name", "Other");
        dragon(elsewhere, "commit", "-q", "--allow-empty", "-m", "ally-side feat");
        dragon(elsewhere, "push", "-q", "origin", "main");

        // Rally refreshes knowledge; recall fast-forwards our banner.
        chronicle.rally("ally");
        int before = chronicle.surveyTrail().size();
        chronicle.recall("ally");
        assertEquals(before + 1, chronicle.surveyTrail().size());
        assertEquals("ally-side feat", chronicle.surveyTrail().get(0).summary());
    }

    @Test
    void recallRefusesWhenTrailWouldRewrite() throws Exception {
        Chronicle chronicle = realmWithAlly();
        Path realm = chronicle.getRealmDir().toPath();
        dragon(realm, "push", "-q", "-u", "ally", "main");

        // Both sides seal independent feats: histories have diverged.
        Path elsewhere = tempDir.resolve("elsewhere");
        Files.createDirectories(elsewhere);
        dragon(elsewhere, "clone", "-q",
                tempDir.resolve("allied-realm.git").toAbsolutePath().toString(), ".");
        dragon(elsewhere, "config", "user.email", "o@x");
        dragon(elsewhere, "config", "user.name", "Other");
        dragon(elsewhere, "commit", "-q", "--allow-empty", "-m", "ally feat");
        dragon(elsewhere, "push", "-q", "origin", "main");
        dragon(realm, "commit", "-q", "--allow-empty", "-m", "local feat");

        chronicle.rally("ally");
        assertThrows(IOException.class, () -> chronicle.recall("ally"));
        // Nothing was rewritten: our local feat still leads the trail.
        assertEquals("local feat", chronicle.surveyTrail().get(0).summary());
    }

    @Test
    void rallyWithNoAlliesIsHarmless() throws Exception {
        Chronicle chronicle = initRealm();
        dragon(tempDir.resolve("realm"), "commit", "-q", "--allow-empty", "-m", "base");
        // No allies configured: rallying all must not throw.
        assertDoesNotThrow(() -> chronicle.rally(null));
    }

    // ------------------------------------------------------------------
    // Re-seal (amend)
    // ------------------------------------------------------------------

    @Test
    void reSealFoldsWorkIntoNewestFeat() throws Exception {
        Chronicle chronicle = initRealm();
        Path realm = chronicle.getRealmDir().toPath();
        Files.writeString(realm.resolve("a.txt"), "one\n");
        dragon(realm, "add", "a.txt");
        dragon(realm, "commit", "-qm", "first words");

        Files.writeString(realm.resolve("b.txt"), "two\n");
        chronicle.enlist("b.txt");
        Feat reSealed = chronicle.reSeal("better words");

        // Still exactly ONE feat, now carrying both files.
        List<Feat> trail = chronicle.surveyTrail();
        assertEquals(1, trail.size());
        assertEquals(reSealed.hash(), trail.get(0).hash());
        assertEquals("better words", trail.get(0).summary());
        List<Dispatch> toll = chronicle.tollOf(trail.get(0).hash());
        assertEquals(2, toll.size());
    }

    @Test
    void reSealRefusesEmptyVanguard() throws Exception {
        Chronicle chronicle = initRealm();
        dragon(tempDir.resolve("realm"), "commit", "-q", "--allow-empty", "-m", "base");
        assertThrows(IOException.class, () -> chronicle.reSeal("nothing to fold"));
    }

    @Test
    void newestFeatExposesHead() throws Exception {
        Chronicle chronicle = initRealm();
        dragon(tempDir.resolve("realm"), "commit", "-q", "--allow-empty", "-m", "only feat");
        assertEquals("only feat", chronicle.newestFeat().summary());
    }

    @Test
    void newestFeatRejectsEmptyChronicle() throws Exception {
        Chronicle chronicle = initRealm();
        assertThrows(IOException.class, chronicle::newestFeat);
    }
}
