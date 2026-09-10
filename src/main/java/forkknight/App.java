package forkknight;

import forkknight.git.Commit;
import forkknight.git.CommitFilters;
import forkknight.git.FileChange;
import forkknight.git.GitService;
import forkknight.git.WorkDirChange;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
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
    private FilteredList<Commit> filteredCommits;
    private Label statusBar;
    private ComboBox<String> branchBox;
    private TextField searchField;
    private ComboBox<String> searchScopeBox;

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

        Label branchLabel = new Label("Branch:");
        branchBox = new ComboBox<>();
        branchBox.setPromptText("Current branch");
        branchBox.setDisable(true);
        branchBox.setPrefWidth(160);
        branchBox.setOnAction(e -> {
            String branch = branchBox.getSelectionModel().getSelectedItem();
            if (branch != null && git != null) {
                loadCommits(branch);
            }
        });
        HBox branchBoxUi = new HBox(10, branchLabel, branchBox);
        branchBoxUi.setPadding(new Insets(0, 10, 10, 10));
        branchBoxUi.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

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
        filteredCommits = new FilteredList<>(commitData, p -> true);
        commitTable.setItems(filteredCommits);
        commitTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        commitTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, commit) -> onCommitSelected(commit));

        Label searchLabel = new Label("Search:");
        searchField = new TextField();
        searchField.setPromptText("Filter commits...");
        HBox.setHgrow(searchField, javafx.scene.layout.Priority.ALWAYS);
        searchScopeBox = new ComboBox<>();
        searchScopeBox.getItems().addAll("Message", "Author", "Hash");
        searchScopeBox.getSelectionModel().selectFirst();
        Runnable applyFilter = this::applyCommitFilter;
        searchField.textProperty().addListener((obs, oldText, newText) -> applyFilter.run());
        searchScopeBox.valueProperty().addListener((obs, oldV, newV) -> applyFilter.run());
        Button clearBtn = new Button("Clear");
        clearBtn.setOnAction(e -> {
            searchField.clear();
            searchField.requestFocus();
        });
        HBox searchBox = new HBox(8, searchLabel, searchField, searchScopeBox, clearBtn);
        searchBox.setPadding(new Insets(5, 10, 5, 10));

        detailsView = new DetailsView();

        statusBar = new Label("Ready");
        statusBar.setPadding(new Insets(4, 10, 4, 10));

        VBox top = new VBox(5, repoBox, branchBoxUi, new Separator());
        top.setPadding(new Insets(10, 10, 0, 10));

        SplitPane center = new SplitPane();
        center.setOrientation(Orientation.VERTICAL);
        center.getItems().addAll(commitTable, detailsView.getNode());
        center.setDividerPosition(0, 0.55);
        SplitPane.setResizableWithParent(detailsView.getNode(), true);

        BorderPane historyTab = new BorderPane();
        historyTab.setTop(searchBox);
        historyTab.setCenter(center);

        TabPane tabs = new TabPane();
        Tab historyTabItem = new Tab("History");
        historyTabItem.setClosable(false);
        historyTabItem.setContent(historyTab);
        Tab workTab = new Tab("Working changes");
        workTab.setClosable(false);
        workTab.setContent(buildWorkingChangesView());
        tabs.getTabs().addAll(historyTabItem, workTab);
        tabs.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldTab, newTab) -> {
                    if (newTab == workTab) {
                        loadWorkingChanges();
                    }
                });

        BorderPane root = new BorderPane();
        root.setTop(top);
        root.setCenter(tabs);
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
            loadBranches();
            loadCommits(null);
        });
        task.setOnFailed(e -> showError("Not a git repository: "
                + task.getException().getMessage()));
        new Thread(task, "repo-validate").start();
    }

    private void loadBranches() {
        GitService service = git;
        Task<List<String>> task = new Task<>() {
            @Override
            protected List<String> call() throws Exception {
                return service.branches();
            }
        };
        task.setOnSucceeded(e -> {
            branchBox.getItems().setAll(task.getValue());
            String current;
            try {
                current = service.currentBranch();
            } catch (Exception ex) {
                current = null;
            }
            if (current != null && !current.isBlank()) {
                branchBox.getSelectionModel().select(current);
            } else if (!branchBox.getItems().isEmpty()) {
                branchBox.getSelectionModel().selectFirst();
            }
            branchBox.setDisable(branchBox.getItems().isEmpty());
        });
        task.setOnFailed(e -> branchBox.setDisable(true));
        Thread thread = new Thread(task, "branch-loader");
        thread.setDaemon(true);
        thread.start();
    }

    private void loadCommits(String branch) {
        commitData.clear();
        detailsView.reset();
        commitTable.setPlaceholder(new Label("Loading commits..."));
        statusBar.setText("Loading log...");

        GitService service = git;
        Task<List<Commit>> task = new Task<>() {
            @Override
            protected List<Commit> call() throws Exception {
                return service.log(branch, Integer.MAX_VALUE);
            }
        };
        task.setOnSucceeded(e -> {
            commitData.addAll(task.getValue());
            applyCommitFilter();
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

    /** Applies the search filter to the commit list and updates the status bar. */
    private void applyCommitFilter() {
        String query = searchField.getText();
        String scope = searchScopeBox.getValue();
        if (query == null || query.isBlank()) {
            filteredCommits.setPredicate(null);
            statusBar.setText(commitData.size() + " commits");
            return;
        }
        filteredCommits.setPredicate(CommitFilters.byScope(scope, query));
        statusBar.setText(filteredCommits.size() + " of " + commitData.size()
                + " commits match \"" + query.strip() + "\"");
        if (filteredCommits.isEmpty() && !commitData.isEmpty()) {
            commitTable.setPlaceholder(new Label("No commits match the search."));
        } else if (!commitData.isEmpty()) {
            commitTable.setPlaceholder(null);
        }
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

    // ------------------------------------------------------------------
    // Working changes tab
    // ------------------------------------------------------------------

    private TableView<WorkDirChange> workTable;
    private final ObservableList<WorkDirChange> workData = FXCollections.observableArrayList();

    private javafx.scene.Node buildWorkingChangesView() {
        workTable = new TableView<>();
        workTable.setPlaceholder(new Label("No changes"));

        TableColumn<WorkDirChange, String> stateCol = new TableColumn<>("State");
        stateCol.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().staged() ? "Staged" : "Unstaged"));
        stateCol.setMinWidth(80);

        TableColumn<WorkDirChange, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().description()));
        statusCol.setMinWidth(80);

        TableColumn<WorkDirChange, String> pathCol = new TableColumn<>("Path");
        pathCol.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().isRename()
                        ? cell.getValue().oldPath() + " -> " + cell.getValue().newPath()
                        : cell.getValue().newPath()));
        pathCol.setMinWidth(300);

        workTable.getColumns().addAll(stateCol, statusCol, pathCol);
        workTable.setItems(workData);
        workTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        workTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        Button refreshBtn = new Button("Refresh");
        refreshBtn.setOnAction(e -> loadWorkingChanges());
        Button stageBtn = new Button("Stage");
        stageBtn.setOnAction(e -> stageSelected(true));
        Button unstageBtn = new Button("Unstage");
        unstageBtn.setOnAction(e -> stageSelected(false));
        Button stageAllBtn = new Button("Stage all");
        stageAllBtn.setOnAction(e -> runGitAction("Stage all", () -> git.stageAll()));
        Button commitBtn = new Button("Commit...");
        commitBtn.setOnAction(e -> commitStaged());
        Button discardBtn = new Button("Discard changes...");
        discardBtn.setOnAction(e -> discardSelected());

        HBox buttons = new HBox(8, refreshBtn, new Separator(javafx.geometry.Orientation.VERTICAL),
                stageBtn, unstageBtn, stageAllBtn, new Separator(javafx.geometry.Orientation.VERTICAL),
                commitBtn, discardBtn);
        buttons.setPadding(new Insets(8));

        BorderPane pane = new BorderPane();
        pane.setTop(buttons);
        pane.setCenter(workTable);
        return pane;
    }

    private void loadWorkingChanges() {
        if (git == null) {
            return;
        }
        GitService service = git;
        Task<List<WorkDirChange>> task = new Task<>() {
            @Override
            protected List<WorkDirChange> call() throws Exception {
                return service.status();
            }
        };
        task.setOnSucceeded(e -> workData.setAll(task.getValue()));
        task.setOnFailed(e -> showError("Failed to load status: "
                + task.getException().getMessage()));
        Thread thread = new Thread(task, "status-loader");
        thread.setDaemon(true);
        thread.start();
    }

    private void stageSelected(boolean stage) {
        List<WorkDirChange> selected = List.copyOf(workTable.getSelectionModel().getSelectedItems());
        if (selected.isEmpty()) {
            return;
        }
        runGitAction(stage ? "Stage" : "Unstage", () -> {
            for (WorkDirChange change : selected) {
                if (stage) {
                    git.stage(change.newPath());
                } else {
                    git.unstage(change.newPath());
                }
            }
        });
    }

    private void commitStaged() {
        try {
            git.requireStaged();
        } catch (Exception ex) {
            showError(ex.getMessage());
            return;
        }
        CommitDialog dialog = new CommitDialog(git, null);
        dialog.message().ifPresent(message -> {
            if (message.isBlank()) {
                return;
            }
            runGitAction("Commit", () -> git.commit(message.trim()));
        });
    }

    private void discardSelected() {
        List<WorkDirChange> selected = List.copyOf(workTable.getSelectionModel().getSelectedItems());
        if (selected.isEmpty()) {
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Discard changes in " + selected.size() + " selected file(s)?\n"
                        + "This cannot be undone.",
                ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText("Discard changes");
        confirm.showAndWait().filter(b -> b == ButtonType.YES).ifPresent(b ->
                runGitAction("Discard", () -> {
                    for (WorkDirChange change : selected) {
                        if (change.staged()) {
                            git.unstage(change.newPath());
                        }
                        if (!change.statusCode().equals("?") && !change.statusCode().equals("D")) {
                            git.discard(change.newPath());
                        } else if (change.statusCode().equals("?")) {
                            git.deleteUntracked(change.newPath());
                        }
                    }
                }));
    }

    /** Runs a mutating git action on a background thread, then refreshes. */
    private void runGitAction(String name, GitAction action) {
        if (git == null) {
            return;
        }
        statusBar.setText(name + "...");
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                action.run();
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            statusBar.setText(name + " done");
            loadWorkingChanges();
            loadCommits(branchBox.getSelectionModel().getSelectedItem());
        });
        task.setOnFailed(e -> showError(name + " failed: "
                + task.getException().getMessage()));
        Thread thread = new Thread(task, "git-action");
        thread.setDaemon(true);
        thread.start();
    }

    @FunctionalInterface
    private interface GitAction {
        void run() throws Exception;
    }

    private void showError(String message) {
        statusBar.setText(message);
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setHeaderText("ForkKnight");
        alert.show();
    }
}
