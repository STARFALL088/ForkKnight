package forkknight;

import forkknight.core.Account;
import forkknight.core.AccountService;
import forkknight.core.KnightProfile;
import forkknight.core.KnightRank;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

/**
 * The knight's own page: who he is, what he has gathered, and the words
 * he writes about himself. Username, joined day and keepsake counts are
 * read-only; the display name, title and bio are his to change.
 *
 * <p>Stats ride a background road ({@link RealmTask}) so the FX thread
 * never waits on the ledger, saving seals through
 * {@link AccountService#updateProfile} the same way, and a successful
 * seal refreshes the riding label through the callback handed in.
 */
public class ProfileDialog extends Dialog<Void> {

    private final AccountService service;
    private final Runnable afterSeal;
    private final Account viewer;

    private final ObjectProperty<KnightProfile> loaded = new SimpleObjectProperty<>();

    private final Label initials = new Label();
    private final Label usernameText = new Label();
    private final Label joinedText = new Label();
    private final Label notesText = new Label();
    private final Label bookmarksText = new Label();
    private final TextField nameField = new TextField();
    private final ComboBox<KnightRank> rankBox = new ComboBox<>();
    private final TextArea bioArea = new TextArea();
    private final Label counter = new Label("0/200");
    private final Label notice = new Label();
    private final Button saveBtn = new Button("Save");
    private final Button cancelBtn = new Button("Cancel");

    private BooleanBinding unchanged;
    private BooleanBinding tooLong;

    public ProfileDialog(AccountService service, Runnable afterSeal) {
        this.service = service;
        this.afterSeal = afterSeal;
        this.viewer = service.current();

        setTitle("ForkKnight - The Knight's Own Page");
        setHeaderText("Your profile - the name you ride as, your title, and your words");

        // Avatar: one ring, two letters, no image files.
        Circle ring = new Circle(30);
        ring.setFill(Color.web("#7dcfff"));
        initials.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;"
            + " -fx-text-fill: #1a1b26;");
        StackPane avatar = new StackPane(ring, initials);
        avatar.setAlignment(Pos.CENTER);
        usernameText.setStyle("-fx-font-weight: bold;");
        joinedText.setStyle("-fx-text-fill: #a9b1d6;");
        VBox who = new VBox(2, usernameText, joinedText);
        who.setAlignment(Pos.CENTER);
        HBox head = new HBox(14, avatar, who);
        head.setAlignment(Pos.CENTER);

        // The ledger rows: facts on labels, edits in fields.
        rankBox.getItems().addAll(KnightRank.values());
        rankBox.setMaxWidth(Double.MAX_VALUE);
        nameField.setPromptText("what other knights know you as (1-40 chars)");
        bioArea.setPromptText("a few words you keep (200 at most)");
        bioArea.setWrapText(true);
        bioArea.setPrefRowCount(4);
        bioArea.setMinHeight(80);
        bioArea.setMaxWidth(Double.MAX_VALUE);
        counter.setStyle("-fx-text-fill: #7dcfff;");
        counter.textProperty().bind(Bindings.concat(
            Bindings.length(bioArea.textProperty()), "/200"));
        bioArea.textProperty().addListener((obs, was, now) ->
            counter.setStyle(now != null && now.length() > 200
                ? "-fx-text-fill: #f7768e; -fx-font-weight: bold;"
                : "-fx-text-fill: #7dcfff;"));
        VBox bioWrap = new VBox(4, bioArea, counter);
        bioWrap.setMaxWidth(Double.MAX_VALUE);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        ColumnConstraints labels = new ColumnConstraints();
        ColumnConstraints values = new ColumnConstraints();
        values.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labels, values);
        addRow(grid, 0, "Riding as:", usernameText);
        addRow(grid, 1, "Joined:", joinedText);
        addRow(grid, 2, "Known as:", nameField);
        addRow(grid, 3, "Title:", rankBox);
        addRow(grid, 4, "Notes:", notesText);
        addRow(grid, 5, "Bookmarks:", bookmarksText);
        addRow(grid, 6, "Bio:", bioWrap);
        grid.setMaxWidth(Double.MAX_VALUE);

        notice.setWrapText(true);
        saveBtn.setDefaultButton(true);
        cancelBtn.setCancelButton(true);
        cancelBtn.setOnAction(e -> close());
        saveBtn.setOnAction(e -> seal());
        HBox actions = new HBox(10, notice, saveBtn, cancelBtn);
        HBox.setHgrow(notice, Priority.ALWAYS);
        actions.setAlignment(Pos.CENTER_LEFT);

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(14));
        root.setTop(head);
        VBox centerWrap = new VBox(grid);
        centerWrap.setMaxWidth(Double.MAX_VALUE);
        root.setCenter(centerWrap);
        root.setBottom(actions);

        getDialogPane().setContent(root);
        getDialogPane().setPrefSize(470, 560);
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        bindSave();
        loadProfile();
    }

    // -------------------- Actions --------------------

    /** Reads the knight's page from the ledger on a background road. */
    private void loadProfile() {
        RealmTask<KnightProfile> road = new RealmTask<>("profile-load",
            this::refuse, "") {
            @Override
            protected KnightProfile call() {
                return service.profileOf(viewer.id());
            }
        };
        road.setOnSucceeded(e -> fill(road.getValue()));
        road.start();
    }

    /** Shows what came back and decides whether anything may be written. */
    private void fill(KnightProfile page) {
        Account account = page.account();
        loaded.set(page);
        usernameText.setText(account.username()
            + (AccountService.locked(account) ? " (locked ledger)" : ""));
        joinedText.setText("Joined " + joinedOn(account.createdAt()));
        notesText.setText(Long.toString(page.noteCount()));
        bookmarksText.setText(Long.toString(page.bookmarkCount()));
        initials.setText(initialsOf(account.displayName()));
        nameField.setText(account.displayName());
        rankBox.setValue(KnightRank.of(account.title()));
        bioArea.setText(account.bio());
        notice.setText("");
        if (account.guest()) {
            saveBtn.disableProperty().unbind();
            saveBtn.setDisable(true);
            nameField.setDisable(true);
            rankBox.setDisable(true);
            bioArea.setDisable(true);
            notice.setStyle("-fx-text-fill: #7dcfff;");
            notice.setText("The wanderer keeps no page of his own"
                + " - join the Order to claim one.");
        }
    }

    /** Seals the page on a background road; on success, the label follows. */
    private void seal() {
        String name = nameField.getText();
        String words = bioArea.getText();
        KnightRank rank = rankBox.getValue();
        saveBtn.disableProperty().unbind();
        saveBtn.setDisable(true);
        notice.setStyle("-fx-text-fill: #7dcfff;");
        notice.setText("Sealing the page...");
        RealmTask<Void> road = new RealmTask<>("profile-seal", this::refuse, "") {
            @Override
            protected Void call() {
                service.updateProfile(viewer.id(), name, words, rank);
                return null;
            }
        };
        road.setOnSucceeded(e -> {
            afterSeal.run();
            close();
        });
        road.start();
    }

    /** A broken road: the words it failed with, in the knight's own tongue. */
    private void refuse(String message) {
        notice.setStyle("-fx-text-fill: #f7768e; -fx-font-weight: bold;");
        notice.setText(message);
        bindSave();
    }

    /** Save answers only to dirt and over-long bios. */
    private void bindSave() {
        unchanged = Bindings.createBooleanBinding(() -> {
            KnightProfile page = loaded.get();
            if (page == null) {
                return true;
            }
            Account account = page.account();
            return nameField.getText().equals(account.displayName())
                && rankBox.getValue() == KnightRank.of(account.title())
                && bioArea.getText().equals(account.bio());
        }, loaded, nameField.textProperty(), rankBox.valueProperty(),
            bioArea.textProperty());
        tooLong = Bindings.length(bioArea.textProperty()).greaterThan(200);
        saveBtn.disableProperty().bind(Bindings.or(unchanged, tooLong));
    }

    // -------------------- Helpers --------------------

    private static void addRow(GridPane grid, int at, String caption, javafx.scene.Node value) {
        Label captionText = new Label(caption);
        captionText.setStyle("-fx-text-fill: #a9b1d6;");
        grid.add(captionText, 0, at);
        grid.add(value, 1, at);
    }

    /** The letters the avatar wears: first letters of first and last word. */
    static String initialsOf(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return "?";
        }
        String[] words = displayName.trim().split("\\s+");
        if (words.length == 1) {
            return words[0].substring(0, 1).toUpperCase();
        }
        return (words[0].substring(0, 1)
            + words[words.length - 1].substring(0, 1)).toUpperCase();
    }

    /** The day the knight joined, as the ledger writes it (YYYY-MM-DD). */
    static String joinedOn(String createdAt) {
        if (createdAt == null || createdAt.length() < 10) {
            return "an unknown day";
        }
        return createdAt.substring(0, 10);
    }
}
