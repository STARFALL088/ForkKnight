package forkknight;

import forkknight.git.Commit;
import forkknight.git.FileChange;
import forkknight.git.GitService;
import javafx.application.Application;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * ForkKnight - a lightweight Git repository visualizer.
 * Displays the commit log of a selected local Git repository and, when a
 * commit is selected, the list of changed files with their diffs.
 */
public class App extends Application {

    private GitService git;
    private TableView<Commit> commitTable;
    private TextField repoPathField;
    private final ObservableList<Commit> commitData = FXCollections.observableArrayList();
    private Label statusBar;

    private DetailsView detailsView;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("ForkKnight - Git Log Viewer");

        Label repoLabel = new Label("Repository:");
        repoPathField = new TextField();
        repoPathField.setPromptText("Select a Git repository");
        repoPathField.setEditable(false);
        Button chooseRepoBtn = new Button("Choose...");
        chooseRepoBtn.setOnAction(e -> chooseRepository());

        HBox repoBox = new HBox(10, repoLabel, repoPathField, chooseRepoBtn);
        repoBox.setPadding(new Insets(10));

        commitTable = new TableView<>();
        commitTable.setPlaceholder(new Label("No commits to display"));
        commitTable.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);

        TableColumn<Commit, String> hashCol = new TableColumn<>("Hash");
        hashCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().shortHash()));
        hashCol.setMinWidth(80);

        TableColumn<Commit, String> authorCol = new TableColumn<>("Author");
        authorCol.setCellValueFactory(cell -> cell.getValue().author() == null ? null
                : new SimpleStringProperty(cell.getValue().author()));
        authorCol.setMinWidth(150);

        TableColumn<Commit, LocalDate> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleObjectProperty<>(cell.getValue().date()));
        dateCol.setMinWidth(100);
        dateCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.format(DateTimeFormatter.ISO_LOCAL_DATE));
            }
        });

        TableColumn<Commit, String> msgCol = new TableColumn<>("Message");
        msgCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().summary()));
        msgCol.setMinWidth(300);

        commitTable.getColumns().addAll(hashCol, authorCol, dateCol, msgCol);
        commitTable.setItems(commitData);
        commitTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        commitTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, commit) -> onCommitSelected(commit));

        detailsView = new DetailsView();

        statusBar = new Label("Ready");
        statusBar.setPadding(new Insets(4, 10, 4, 10));

        VBox top = new VBox(5, repoBox, new Separator());
        top.setPadding(new Insets(10));

        SplitPane center = new SplitPane();
        center.setOrientation(Orientation.VERTICAL);
        center.getItems().addAll(commitTable, detailsView.getNode());
        center.setDividerPosition(0, 0.55);
        SplitPane.setResizableWithParent(detailsView.getNode(), true);

        BorderPane root = new BorderPane();
        root.setTop(top);
        root.setCenter(center);
        root.setBottom(statusBar);

        Scene scene = new Scene(root, 1000, 700);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void chooseRepository() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Git Repository");
        File selectedDir = chooser.showDialog(null);
        if (selectedDir != null) {
            openRepository(selectedDir);
        }
    }

    private void openRepository(File dir) {
        GitService candidate = new GitService(dir);
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                candidate.validateRepository();
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            git = candidate;
            repoPathField.setText(dir.getAbsolutePath());
            loadCommits();
        });
        task.setOnFailed(e -> showError("Not a git repository: "
                + task.getException().getMessage()));
        new Thread(task, "repo-validate").start();
    }

    private void loadCommits() {
        commitData.clear();
        detailsView.reset();
        commitTable.setPlaceholder(new Label("Loading commits..."));
        statusBar.setText("Loading log...");

        GitService service = git;
        Task<List<Commit>> task = new Task<>() {
            @Override
            protected List<Commit> call() throws Exception {
                return service.log();
            }
        };
        task.setOnSucceeded(e -> {
            commitData.addAll(task.getValue());
            statusBar.setText(commitData.size() + " commits");
            if (commitData.isEmpty()) {
                commitTable.setPlaceholder(new Label("No commits found."));
            } else {
                commitTable.setPlaceholder(null);
            }
        });
        task.setOnFailed(e -> showError("Failed to load commits: "
                + task.getException().getMessage()));
        Thread thread = new Thread(task, "log-loader");
        thread.setDaemon(true);
        thread.start();
    }

    private void onCommitSelected(Commit commit) {
        if (commit == null) {
            detailsView.reset();
            return;
        }
        if (git == null) {
            return;
        }
        detailsView.showCommit(git, commit);
        statusBar.setText("Loading commit " + commit.shortHash() + "...");
        detailsView.setOnLoaded(() -> statusBar.setText(commitData.size() + " commits"));
    }

    private void showError(String message) {
        statusBar.setText(message);
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setHeaderText("ForkKnight");
        alert.show();
    }
}
