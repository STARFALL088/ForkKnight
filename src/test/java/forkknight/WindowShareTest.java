package forkknight;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.control.TableColumn;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class WindowShareTest {

    @BeforeAll
    static void wakeTheFxToolkit() {
        FxKit.awake();
    }

    @Test
    void aColumnTakesItsShareOfTheWindowAndFollowsIt() {
        assumeTrue(FxKit.awake(), "no display for the FX toolkit");
        TableColumn<String, String> column = new TableColumn<>("Feat");
        SimpleDoubleProperty window = new SimpleDoubleProperty(1100);

        App.takeWindowShare(column, window, 0.30);

        assertEquals(330.0, column.getMinWidth(), 0.01);
        window.set(900);
        assertEquals(270.0, column.getMinWidth(), 0.01);
        window.set(1400);
        assertEquals(420.0, column.getMinWidth(), 0.01);
    }
}
