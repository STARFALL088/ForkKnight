package forkknight.core;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RealmSessionTest {

    private RealmSession session(String path) {
        return new RealmSession(new Chronicle(new File(path)));
    }

    @Test
    void aSessionStartsUnsurveyedAndRemembersItsState() {
        RealmSession session = session("/tmp/example");
        assertFalse(session.surveyed());
        assertNull(session.weave());
        assertNull(session.scryer());
        assertNull(session.banner());
        assertNull(session.selectedHash());

        session.chooseBanner("origin/main");
        session.selectFeat("deadbeef");
        assertEquals("origin/main", session.banner());
        assertEquals("deadbeef", session.selectedHash());
        assertEquals("/tmp/example", session.path());
    }

    @Test
    void aSurveyedTrailSticksUntilItIsForgotten() {
        RealmSession session = session("/tmp/example");
        session.rememberTrail(Weave.of(List.of()), new Scryer(List.of()));
        assertTrue(session.surveyed());
        assertNotNull(session.weave());
        assertNotNull(session.scryer());

        session.forgetTrail();
        assertFalse(session.surveyed());
        assertNull(session.weave());
        assertNull(session.scryer());
    }

    @Test
    void theOpenSetRoundTripsThroughStorage() {
        String stored = RealmSession.encodeRealms(List.of("/a/one", "/b/two", "/c/three"));
        assertEquals("/a/one\n/b/two\n/c/three", stored);
        assertEquals(List.of("/a/one", "/b/two", "/c/three"), RealmSession.decodeRealms(stored));
    }

    @Test
    void storageDropsBlanksAndRepeatsButKeepsOrder() {
        List<String> messy = new ArrayList<>();
        messy.add("/a/one");
        messy.add(null);
        messy.add("   ");
        messy.add("/b/two");
        messy.add("/a/one");

        String stored = RealmSession.encodeRealms(messy);
        assertEquals("/a/one\n/b/two", stored);
        assertEquals(List.of("/a/one", "/b/two"), RealmSession.decodeRealms(stored));
    }

    @Test
    void unreadableMarksReadAsNoRealms() {
        assertEquals(List.of(), RealmSession.decodeRealms(null));
        assertEquals(List.of(), RealmSession.decodeRealms(""));
        assertEquals(List.of(), RealmSession.decodeRealms("   "));
        assertEquals(List.of(), RealmSession.decodeRealms("\n\n"));
        assertEquals("", RealmSession.encodeRealms(null));
        assertEquals("", RealmSession.encodeRealms(List.of()));
        assertEquals(List.of("/a"), RealmSession.decodeRealms("\n\n/a\n\n"));
    }

    @Test
    void sessionsAreEqualWhenTheyPointAtTheSameRealm() {
        RealmSession first = session("/tmp/example");
        RealmSession second = session("/tmp/example");
        RealmSession other = session("/tmp/elsewhere");

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, other);
        assertNotEquals(first, "not a session");
        assertEquals("example", first.toString());
    }
}
