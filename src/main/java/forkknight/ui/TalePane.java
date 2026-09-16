package forkknight.ui;

import forkknight.core.Chronicle;
import forkknight.core.Dispatch;
import forkknight.core.Feat;
import forkknight.core.Vault;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * The tale pane: when the knight selects a feat in the chronicle, this
 * pane recounts the dispatches (changed paths) on the left and the full
 * battle report (diff) on the right. Recounts are cached in a bounded
 * LRU vault so flipping between feats is instant after the first look.
 */
public class TalePane {

    private static final int VAULT_CAPACITY = 64;

    private final BorderPane node = new BorderPane();
    private final ListView<Dispatch> dispatchList = new ListView<>();
    private final TextArea recountArea = new TextArea();
    private final Label header = new Label();
    private final Label noteLabel = new Label();
    private final Button annotateBtn = new Button("Annotate...");
    private final ObservableList<Dispatch> dispatchData = FXCollections.observableArrayList();

    /** Monotonic ticket so stale background results are discarded. */
    private long loadTicket;

    private final Vault<String, String> recountVault = new Vault<>(VAULT_CAPACITY);

    private Chronicle currentChronicle;
    private String currentHash;
    private forkknight.core.KnightMemory memory;
    private Runnable onNoteChanged;

    public void setMemory(forkknight.core.KnightMemory memory, Runnable onNoteChanged) {
        this.memory = memory;
        this.onNoteChanged = onNoteChanged;
    }

    /** Selection listener lazily loads per-path recounts through the vault. */
    private final javafx.beans.value.ChangeListener<Dispatch> dispatchListener =
            (obs, old, dispatch) -> {
                if (dispatch == null) {
                    return;
                }
                Chronicle chronicle = currentChronicle;
                String hash = currentHash;
                if (chronicle == null || hash == null) {
                    return;
                }
                String key = hash + ":" + dispatch.path();
                String cached = recountVault.fetch(key);
                if (cached != null) {
                    recountArea.setText(cached);
                    return;
                }
                long ticket = loadTicket;
                Task<String> recountTask = new Task<>() {
                    @Override
                    protected String call() throws Exception {
                        return recountVault.acquire(key,
                                k -> {
                                    try {
                                        return chronicle.recountPath(hash, dispatch.path());
                                    } catch (Exception ex) {
                                        return null;
                                    }
                                });
                    }
                };
                recountTask.setOnSucceeded(e -> {
                    if (ticket != loadTicket) {
                        return;
                    }
                    recountArea.setText(recountTask.getValue() == null
                            ? "" : recountTask.getValue());
                });
                startDaemon(recountTask, "recount-path");
            };

    public TalePane() {
        dispatchList.setCellFactory(view -> new DispatchCell());
        dispatchList.setItems(dispatchData);
        dispatchList.setPlaceholder(new Label("Choose a feat to recount"));
        dispatchList.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        dispatchList.setPrefWidth(300);
        dispatchList.setMinWidth(200);

        recountArea.setEditable(false);
        recountArea.setWrapText(false);
        recountArea.setPromptText("The battle report appears here");
        recountArea.getStyleClass().add("diff-area");
        recountArea.setStyle("-fx-font-family: 'monospace'; -fx-font-size: 12px;");

        header.setPadding(new Insets(4, 8, 4, 8));
        header.getStyleClass().add("commit-header");

        noteLabel.setPadding(new Insets(2, 8, 2, 8));
        noteLabel.setStyle("-fx-font-style: italic; -fx-text-fill: #e0af68;");
        annotateBtn.setOnAction(e -> annotateDialog());
        HBox noteRow = new HBox(10, noteLabel, annotateBtn);
        noteRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        noteRow.setPadding(new Insets(0, 8, 4, 8));

        SplitPane split = new SplitPane(dispatchList, recountArea);
        split.setDividerPosition(0, 0.35);
        HBox.setHgrow(split, Priority.ALWAYS);

        VBox headerBox = new VBox(4, header, noteRow, new Separator());
        BorderPane content = new BorderPane();
        content.setTop(headerBox);
        content.setCenter(split);

        node.setCenter(content);
    }

    public Node getNode() {
        return node;
    }

    /** Clears dispatches/reports and stops reacting to stale loads. */
    public void reset() {
        loadTicket++;
        dispatchData.clear();
        recountArea.clear();
        header.setText("");
        noteLabel.setText("");
        currentHash = null;
    }

    private void refreshNoteDisplay() {
        if (currentHash == null || memory == null) {
            noteLabel.setText("");
            return;
        }
        String note = memory.recallNote(currentHash).orElse(null);
        if (note == null || note.isBlank()) {
            noteLabel.setText("No knight's note");
        } else {
            noteLabel.setText("\uD83D\uDCDC Note: " + note);
        }
    }

    private void annotateDialog() {
        if (currentHash == null || memory == null) {
            return;
        }
        String currentNote = memory.recallNote(currentHash).orElse("");
        TextInputDialog dialog = new TextInputDialog(currentNote);
        dialog.setTitle("ForkKnight - Knight's Note");
        dialog.setHeaderText("Annotate feat " + currentHash.substring(0, Math.min(7, currentHash.length())));
        dialog.setContentText("Note:");
        dialog.showAndWait().ifPresent(text -> {
            try {
                memory.rememberNote(currentHash, text.strip());
                refreshNoteDisplay();
                if (onNoteChanged != null) {
                    onNoteChanged.run();
                }
            } catch (Exception ignored) {
            }
        });
    }

    /** Recounts the chosen feat: dispatch list + report of first path. */
    public void recount(Chronicle chronicle, Feat feat) {
        long ticket = ++loadTicket;
        this.currentChronicle = chronicle;
        this.currentHash = feat.hash();
        dispatchList.getSelectionModel().selectedItemProperty().removeListener(dispatchListener);
        String hash = feat.hash();
        header.setText(feat.shortHash() + " - " + feat.summary());
        refreshNoteDisplay();
        dispatchData.clear();
        recountArea.clear();
        dispatchList.setPlaceholder(new Label("Loading..."));

        Task<List<Dispatch>> tollTask = new Task<>() {
            @Override
            protected List<Dispatch> call() throws Exception {
                return chronicle.tollOf(hash);
            }
        };
        tollTask.setOnSucceeded(e -> {
            if (ticket != loadTicket) {
                return;
            }
            dispatchData.setAll(tollTask.getValue());
            dispatchList.setPlaceholder(new Label("No dispatches"));
            dispatchList.getSelectionModel().selectedItemProperty()
                    .addListener(dispatchListener);
            if (!dispatchData.isEmpty()) {
                dispatchList.getSelectionModel().selectFirst();
            }
        });
        tollTask.setOnFailed(e -> {
            if (ticket != loadTicket) {
                return;
            }
            dispatchList.setPlaceholder(new Label("Failed to muster dispatches"));
        });
        startDaemon(tollTask, "toll-of");
    }

    private static void startDaemon(Task<?> task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
    }

    /** List cell rendering a dispatch with a status badge and path. */
    private static final class DispatchCell extends ListCell<Dispatch> {
        private final Label statusLabel = new Label();
        private final Label pathLabel = new Label();

        DispatchCell() {
            statusLabel.setMinWidth(70);
            HBox box = new HBox(8, statusLabel, pathLabel);
            box.setPadding(new Insets(2, 4, 2, 4));
            setGraphic(box);
        }

        @Override
        protected void updateItem(Dispatch item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                statusLabel.setText("");
                pathLabel.setText("");
                setTooltip(null);
            } else {
                statusLabel.setText(badge(item));
                pathLabel.setText(item.isRenaming()
                        ? item.oldPath() + " \u2192 " + item.path() : item.path());
                statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: "
                        + colorFor(item));
                setTooltip(new Tooltip(pathLabel.getText()));
            }
        }

        private static String badge(Dispatch item) {
            return switch (item.statusCode()) {
                case "A" -> "Conscripted";
                case "D" -> "Fallen";
                case "M" -> "Reforged";
                case "R" -> "Renamed";
                default -> item.statusCode();
            };
        }

        private static String colorFor(Dispatch item) {
            return switch (item.statusCode()) {
                case "A" -> "#22863a";   // conscripted: fresh green
                case "D" -> "#cb2431";   // fallen: crimson
                default -> "#586069";
            };
        }
    }
}
