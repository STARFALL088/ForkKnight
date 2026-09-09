package forkknight;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * ForkKnight - A lightweight Git repository visualizer for JavaFX assignment.
 * Displays commit log of a selected local Git repository.
 */
public class App extends Application {

    private TableView<Commit> commitTable;
    private TextField repoPathField;
    private final ObservableList<Commit> commitData = FXCollections.observableArrayList();

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("ForkKnight - Git Log Viewer");

        // UI components
        Label repoLabel = new Label("Repository:");
        repoPathField = new TextField();
        repoPathField.setPromptText("Select a Git repository");
        repoPathField.setEditable(false);
        Button chooseRepoBtn = new Button("Choose...");
        chooseRepoBtn.setOnAction(e -> chooseRepository());

        HBox repoBox = new HBox(10, repoLabel, repoPathField, chooseRepoBtn);
        repoBox.setPadding(new Insets(10));

        // Table for commits
        commitTable = new TableView<>();
        commitTable.setPlaceholder(new Label("No commits to display"));

        TableColumn<Commit, String> hashCol = new TableColumn<>("Hash");
        hashCol.setCellValueFactory(cell -> javafx.beans.binding.Bindings.createStringBinding(
                () -> cell.getValue().getHash().substring(0, 7),
                cell.getValue().hashProperty()));
        hashCol.setMinWidth(80);

        TableColumn<Commit, String> authorCol = new TableColumn<>("Author");
        authorCol.setCellValueFactory(cell -> cell.getValue().authorProperty());
        authorCol.setMinWidth(150);

        TableColumn<Commit, String> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(cell -> cell.getValue().dateProperty());
        dateCol.setMinWidth(100);

        TableColumn<Commit, String> msgCol = new TableColumn<>("Message");
        msgCol.setCellValueFactory(cell -> cell.getValue().messageProperty());
        msgCol.setMinWidth(300);
        msgCol.setResizable(true);

        commitTable.getColumns().addAll(hashCol, authorCol, dateCol, msgCol);
        commitTable.setItems(commitData);
        commitTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        // Layout
        VBox top = new VBox(5, repoBox, new Separator());
        top.setPadding(new Insets(10));

        BorderPane root = new BorderPane();
        root.setTop(top);
        root.setCenter(commitTable);

        Scene scene = new Scene(root, 900, 600);
        primaryStage.setScene(scene);
        primaryStage.show();

        // Load commits if a repo path is already set (e.g., from arguments)
        // For now, we wait for user to choose.
    }

    private void chooseRepository() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Git Repository");
        File selectedDir = chooser.showDialog(null);
        if (selectedDir != null) {
            repoPathField.setText(selectedDir.getAbsolutePath());
            loadCommits(selectedDir);
        }
    }

    private void loadCommits(File repoDir) {
        // Clear previous data
        commitData.clear();

        // Show loading indicator
        commitTable.setPlaceholder(new Label("Loading commits..."));

        // Background task to run git log
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                ProcessBuilder pb = new ProcessBuilder(
                        "git", "-C", repoDir.getAbsolutePath(),
                        "log", "--pretty=format:%H%x09%an%x09%ad%x09%s",
                        "--date=short");
                Map<String, String> env = pb.environment();
                // Ensure we get dates in English locale for parsing
                env.put("LC_TIME", "en_US.UTF");
                Process process = pb.start();

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    List<Commit> batch = new ArrayList<>(100);
                    while ((line = reader.readLine()) != null) {
                        String[] parts = line.split("\t", 4);
                        if (parts.length == 4) {
                            String hash = parts[0];
                            String author = parts[1];
                            String dateStr = parts[2];
                            String message = parts[3];
                            // Parse date (yyyy-MM-dd) to LocalDate for consistency
                            LocalDate date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
                            batch.add(new Commit(hash, author, date, message));
                            if (batch.size() >= 100) {
                                updateCommits(batch);
                                batch.clear();
                            }
                        }
                    }
                    if (!batch.isEmpty()) {
                        updateCommits(batch);
                    }
                }

                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    // Try to read error stream
                    StringBuilder err = new StringBuilder();
                    try (BufferedReader errReader = new BufferedReader(
                            new InputStreamReader(process.getErrorStream()))) {
                        String errLine;
                        while ((errLine = errReader.readLine()) != null) {
                            err.append(errLine).append("\n");
                        }
                    }
                    throw new IOException("Git command failed (exit " + exitCode + "): " + err);
                }
                return null;
            }

            private void updateCommits(List<Commit> commits) {
                // Update UI on JavaFX thread
                if (isCancelled()) return;
                commitData.addAll(commits);
            }

            @Override
            protected void cancelled() {
                updateMessage("Loading cancelled.");
            }

            @Override
            protected void failed() {
                Throwable exc = getException();
                String msg = exc != null ? exc.getMessage() : "Unknown error";
                commitTable.setPlaceholder(new Label("Error loading commits: " + msg));
            }

            @Override
            protected void succeeded() {
                if (commitData.isEmpty()) {
                    commitTable.setPlaceholder(new Label("No commits found."));
                } else {
                    commitTable.setPlaceholder(new Label("")); // remove placeholder
                }
            }
        };

        // Run task on background thread
        new Thread(task).setDaemon(true).start();
    }

    /**
     * Simple model class for a Git commit.
     */
    public static class Commit {
        private final String hash;
        private final String author;
        private final LocalDate date;
        private final String message;

        public Commit(String hash, String author, LocalDate date, String message) {
            this.hash = hash;
            this.author = author;
            this.date = date;
            this.message = message;
        }

        public String getHash() {
            return hash;
        }

        public String getAuthor() {
            return author;
        }

        public LocalDate getDate() {
            return date;
        }

        public String getMessage() {
            return message;
        }

        // JavaFX properties for TableView
        public javafx.beans.property.StringProperty hashProperty() {
            return javafx.beans.property.SimpleStringProperty(this, "hash", hash);
        }
        public javafx.beans.property.StringProperty authorProperty() {
            return javafx.beans.property.SimpleStringProperty(this, "author", author);
        }
        public javafx.beans.property.ObjectProperty<LocalDate> dateProperty() {
            return javafx.beans.property.SimpleObjectProperty<>(this, "date", date);
        }
        public javafx.beans.property.StringProperty messageProperty() {
            return javafx.beans.property.SimpleStringProperty(this, "message", message);
        }
    }
}