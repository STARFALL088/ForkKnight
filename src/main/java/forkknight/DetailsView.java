package forkknight;

import forkknight.git.Commit;
import forkknight.git.FileChange;
import forkknight.git.GitService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Shows the details of a selected commit: the list of changed files on the
 * left and a colored unified diff on the right. All git access runs on
 * background threads; results are applied on the FX thread.
 */
public class DetailsView {

    private final BorderPane node = new BorderPane();
    private final ListView<FileChange> fileList = new ListView<>();
    private final TextArea diffArea = new TextArea();
    private final Label header = new Label();
    private final ObservableList<FileChange> fileData = FXCollections.observableArrayList();

    /** Monotonic ticket so stale background results are discarded. */
    private final AtomicLong loadTicket = new AtomicLong();

    private Runnable onLoaded;

    public DetailsView() {
        fileList.setCellFactory(view -> new FileChangeCell());
        fileList.setItems(fileData);
        fileList.setPlaceholder(new Label("Select a commit"));
        fileList.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        fileList.setPrefWidth(280);
        fileList.setMinWidth(180);

        diffArea.setEditable(false);
        diffArea.setWrapText(false);
        diffArea.setPromptText("Diff appears here");
        diffArea.setStyle("-fx-font-family: 'monospace'; -fx-font-size: 12px;");

        header.setPadding(new Insets(4, 8, 4, 8));
        header.setStyle("-fx-font-weight: bold;");

        SplitPane split = new SplitPane(fileList, diffArea);
        split.setDividerPosition(0, 0.35);

        VBox headerBox = new VBox(4, header, new Separator());
        BorderPane content = new BorderPane();
        content.setTop(headerBox);
        content.setCenter(split);

        node.setCenter(content);
        node.setPadding(new Insets(0, 10, 0, 10));
    }

    public javafx.scene.Node getNode() {
        return node;
    }

    /** Callback fired (on the FX thread) when a commit's details finish loading. */
    public void setOnLoaded(Runnable onLoaded) {
        this.onLoaded = onLoaded;
    }

    /** Clears files/diff and stops reacting to stale background loads. */
    public void reset() {
        loadTicket.incrementAndGet();
        fileData.clear();
        diffArea.clear();
        header.setText("");
    }

    /** Loads changed files and the whole-commit diff for the commit. */
    public void showCommit(GitService git, Commit commit) {
        long ticket = loadTicket.incrementAndGet();
        String hash = commit.hash();
        header.setText(commit.shortHash() + " - " + commit.summary());
        fileData.clear();
        diffArea.clear();
        fileList.setPlaceholder(new Label("Loading..."));

        GitService service = git;
        Task<List<FileChange>> fileTask = new Task<>() {
            @Override
            protected List<FileChange> call() throws Exception {
                return service.changedFiles(hash);
            }
        };
        fileTask.setOnSucceeded(e -> {
            if (ticket != loadTicket.get()) {
                return;
            }
            fileData.setAll(fileTask.getValue());
            fileList.setPlaceholder(new Label("No changed files"));
            selectFirstFile(service, hash, ticket);
            if (onLoaded != null) {
                onLoaded.run();
            }
        });
        fileTask.setOnFailed(e -> {
            if (ticket != loadTicket.get()) {
                return;
            }
            fileList.setPlaceholder(new Label("Failed to load files"));
            if (onLoaded != null) {
                onLoaded.run();
            }
        });
        startDaemon(fileTask, "commit-files");
    }

    private void selectFirstFile(GitService service, String hash, long ticket) {
        if (fileData.isEmpty()) {
            return;
        }
        fileList.getSelectionModel().selectFirst();
        FileChange first = fileList.getSelectionModel().getSelectedItem();
        loadFileDiff(service, hash, first, ticket);
    }

    private void loadFileDiff(GitService service, String hash, FileChange change, long ticket) {
        String path = change == null ? null : change.newPath();
        Task<String> diffTask = new Task<>() {
            @Override
            protected String call() throws Exception {
                return service.showFileDiff(hash, path);
            }
        };
        diffTask.setOnSucceeded(e -> {
            if (ticket != loadTicket.get()) {
                return;
            }
            diffArea.setText(diffTask.getValue());
        });
        diffTask.setOnFailed(e -> {
            if (ticket != loadTicket.get()) {
                return;
            }
            diffArea.setText("Failed to load diff:\n" + diffTask.getException().getMessage());
        });
        startDaemon(diffTask, "file-diff");
    }

    private static void startDaemon(Task<?> task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
    }

    /** List cell that renders a file change with a status badge and path. */
    private static final class FileChangeCell extends ListCell<FileChange> {
        private final Label statusLabel = new Label();
        private final Label pathLabel = new Label();

        FileChangeCell() {
            statusLabel.setMinWidth(60);
            HBox box = new HBox(8, statusLabel, pathLabel);
            box.setPadding(new Insets(2, 4, 2, 4));
            setGraphic(box);
        }

        @Override
        protected void updateItem(FileChange item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                statusLabel.setText("");
                pathLabel.setText("");
                setTooltip(null);
            } else {
                statusLabel.setText(item.displayStatus());
                pathLabel.setText(item.displayPath());
                statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: " + colorFor(item));
                setTooltip(new Tooltip(item.displayPath()));
            }
        }

        private static String colorFor(FileChange item) {
            return switch (item.status()) {
                case "A" -> "#22863a";
                case "D" -> "#cb2431";
                default -> "#586069";
            };
        }
    }
}
