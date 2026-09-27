package forkknight.core;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JsonTest {

    @Test
    void readsAnObjectOfStringsNumbersTruthAndNull() {
        Map<String, Object> object = Json.object(Json.parse(
            "{\"name\":\"ForkKnight\",\"stars\":7,\"forked\":false,"
            + "\"tale\":null}"));
        assertEquals("ForkKnight", object.get("name"));
        assertEquals(7L, object.get("stars"));
        assertEquals(Boolean.FALSE, object.get("forked"));
        assertNull(object.get("tale"));
    }

    @Test
    void readsNestedObjectsAndArrays() {
        Map<String, Object> outer = Json.object(Json.parse(
            "{\"repo\":{\"name\":\"ForkKnight\"},"
            + "\"feats\":[{\"sha\":\"abc\"},{\"sha\":\"def\"}]}"));
        Map<String, Object> repo = Json.object(outer.get("repo"));
        assertEquals("ForkKnight", repo.get("name"));
        List<Object> feats = Json.array(outer.get("feats"));
        assertEquals(2, feats.size());
        assertEquals("abc", Json.object(feats.get(0)).get("sha"));
    }

    @Test
    void decodesEveryEscapeIncludingUnicode() {
        Map<String, Object> object = Json.object(Json.parse(
            "{\"line\":\"a\\tb\\nc\",\"quote\":\"say \\\"hi\\\"\","
            + "\"slash\":\"a\\\\b\",\"star\":\"\\u2605\"}"));
        assertEquals("a\tb\nc", object.get("line"));
        assertEquals("say \"hi\"", object.get("quote"));
        assertEquals("a\\b", object.get("slash"));
        assertEquals("★", object.get("star"));
    }

    @Test
    void readsRealsAsRealsAndWholeNumbersAsWhole() {
        Map<String, Object> object = Json.object(Json.parse(
            "{\"real\":1.5,\"big\":123456789012,\"exp\":2e3}"));
        assertEquals(1.5, (Double) object.get("real"), 0.0);
        assertEquals(123456789012L, object.get("big"));
        assertEquals(2000.0, (Double) object.get("exp"), 0.0);
        assertEquals(2000L, Json.integer(object.get("exp")));
    }

    @Test
    void refusesTruncatedOrTrailingText() {
        assertThrows(Json.FormatException.class,
            () -> Json.parse("{\"name\":\"Fork"));
        assertThrows(Json.FormatException.class,
            () -> Json.parse("{\"name\":\"x\"} rubbish"));
        assertThrows(Json.FormatException.class, () -> Json.parse(""));
        assertThrows(Json.FormatException.class, () -> Json.parse(null));
    }

    @Test
    void refusesTheNestingThatCouldStackOverflow() {
        String deep = "[".repeat(200) + "]".repeat(200);
        assertThrows(Json.FormatException.class, () -> Json.parse(deep));
    }

    @Test
    void typedHelpersRefuseTheWrongShapeByName() {
        List<Object> array = Json.array(Json.parse("[1,2]"));
        Json.FormatException wrongArray = assertThrows(Json.FormatException.class,
            () -> Json.object(array));
        assertTrue(wrongArray.getMessage().contains("expected a JSON object"));
        Json.FormatException wrongString = assertThrows(Json.FormatException.class,
            () -> Json.string(7L));
        assertTrue(wrongString.getMessage().contains("a number"));
        Json.FormatException wrongNumber = assertThrows(Json.FormatException.class,
            () -> Json.integer("7"));
        assertTrue(wrongNumber.getMessage().contains("a string"));
    }

    @Test
    void stringLetsNullPassThrough() {
        assertNull(Json.string(null));
        assertEquals("keeper", Json.string("keeper"));
    }
}
