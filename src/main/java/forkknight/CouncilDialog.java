package forkknight;

import forkknight.core.Chronicler;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * The council dialog: the chronicler reads the realm's statistics aloud -
 * standings of heroes, the busiest days, the hottest paths and the
 * campaign's span. Purely read-only, safe to open on any realm state.
 */
public class CouncilDialog extends Dialog<Void> {

    private static final int TOP_K = 8;

    public CouncilDialog(Chronicler chronicler,
                         List<Chronicler.PathHeat> pathHeat) {
        setTitle("ForkKnight - The Council's Reading");
        setHeaderText("The chronicler reads the realm's tale");

        // ----- summary grid -----
        GridPane summary = new GridPane();
        summary.setHgap(16);
        summary.setVgap(8);
        summary.setPadding(new Insets(10));
        int row = 0;
        summary.add(bold("Feats sealed:"), 0, row);
        summary.add(new Label(String.valueOf(chronicler.featCount())), 1, row++);
        summary.add(bold("Fusions:"), 0, row);
        summary.add(new Label(String.valueOf(chronicler.fusionCount())), 1, row++);
        summary.add(bold("Campaign span:"), 0, row);
        summary.add(new Label(chronicler.campaignSpanDays() + " days"), 1, row++);

        // ----- leader boards -----
        ListView<String> heroes = new ListView<>(FXCollections.observableArrayList(
                chronicler.heroStandings(TOP_K).stream()
                        .map(s -> s.hero() + "  -  " + s.feats() + " feats")
                        .toList()));
        heroes.setPlaceholder(new Label("No heroes yet"));
        VBox heroesBox = section("Heroes' Standing", heroes);

        ListView<String> days = new ListView<>(FXCollections.observableArrayList(
                chronicler.busiestDays(TOP_K).stream()
                        .map(d -> d.day() + "  -  " + d.feats() + " feats")
                        .toList()));
        days.setPlaceholder(new Label("No days yet"));
        VBox daysBox = section("Busiest Days", days);

        ListView<String> paths = new ListView<>(FXCollections.observableArrayList(
                pathHeat.stream()
                        .map(h -> h.path() + "  -  " + h.touches() + " touches")
                        .toList()));
        paths.setPlaceholder(new Label("No dispatches yet"));
        VBox pathsBox = section("Hottest Paths", paths);

        HBox boards = new HBox(16, heroesBox, daysBox, pathsBox);
        boards.setPadding(new Insets(0, 10, 0, 10));

        BorderPane content = new BorderPane();
        content.setTop(summary);
        content.setCenter(boards);

        getDialogPane().setContent(content);
        getDialogPane().setPrefSize(680, 420);
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
    }

    private static Label bold(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-weight: bold;");
        return label;
    }

    private static VBox section(String title, ListView<String> list) {
        Label header = new Label(title);
        header.setStyle("-fx-font-weight: bold;");
        VBox box = new VBox(6, header, list);
        box.setPadding(new Insets(6));
        HBox.setHgrow(box, javafx.scene.layout.Priority.ALWAYS);
        return box;
    }
}
