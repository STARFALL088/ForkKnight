package forkknight;

import forkknight.git.GitService;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;

import java.util.Optional;

/**
 * Modal dialog for entering a commit message. Staging happens before the
 * dialog opens; this dialog only collects the message and runs git commit
 * on a background thread.
 */
public class CommitDialog extends Dialog<String> {

    public CommitDialog(GitService git, String summaryLine) {
        setTitle("ForkKnight - Commit");
        setHeaderText("Commit staged changes");

        Label msgLabel = new Label("Commit message:");
        TextArea messageArea = new TextArea();
        messageArea.setPromptText("Summary line\n\nDetailed description (optional)");
        messageArea.setPrefRowCount(6);
        messageArea.setPrefColumnCount(50);
        messageArea.setWrapText(true);
        if (summaryLine != null && !summaryLine.isBlank()) {
            messageArea.setText(summaryLine);
        }

        VBox content = new VBox(8, msgLabel, messageArea);
        content.setPadding(new Insets(10));
        getDialogPane().setContent(content);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        setResultConverter(button -> button == ButtonType.OK
                ? messageArea.getText() : null);

        // Disable OK while the message is empty.
        javafx.scene.Node okButton = getDialogPane().lookupButton(ButtonType.OK);
        okButton.disableProperty().bind(messageArea.textProperty().isEmpty());
    }

    /** Convenience for blocking callers that just want the message. */
    public Optional<String> message() {
        return showAndWait();
    }
}
