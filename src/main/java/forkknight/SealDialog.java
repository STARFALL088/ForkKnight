package forkknight;

import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.Node;

/**
 * Words that bind the vanguard's pact: a summary line plus an optional
 * longer tale.
 */
record SealWords(String summary, String body) {}

/**
 * The sealing dialog: collects the words that will bind the vanguard's
 * pact. OK stays disabled until a summary is written.
 */
public class SealDialog extends Dialog<SealWords> {

    public SealDialog() {
        setTitle("ForkKnight - Seal the Pact");
        setHeaderText("Bind the vanguard's deeds into the chronicle");

        TextField summaryField = new TextField();
        summaryField.setPromptText("One bold line");
        TextArea bodyArea = new TextArea();
        bodyArea.setPromptText("The longer tale (optional)");
        bodyArea.setPrefRowCount(6);
        bodyArea.setPrefColumnCount(50);
        bodyArea.setWrapText(true);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(10));
        grid.add(new Label("Summary:"), 0, 0);
        grid.add(summaryField, 1, 0);
        grid.add(new Label("Tale:"), 0, 1);
        grid.add(bodyArea, 1, 1);

        getDialogPane().setContent(grid);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        setResultConverter(button -> {
            if (button != ButtonType.OK) {
                return null;
            }
            String summary = summaryField.getText() == null
                    ? "" : summaryField.getText().strip();
            String body = bodyArea.getText() == null
                    ? "" : bodyArea.getText().strip();
            return new SealWords(summary, body);
        });

        Node okButton = getDialogPane().lookupButton(ButtonType.OK);
        okButton.disableProperty().bind(
                summaryField.textProperty().isEmpty());
    }
}
