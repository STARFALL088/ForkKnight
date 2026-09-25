package forkknight;

import forkknight.core.Banner;
import forkknight.core.Chronicler;
import forkknight.core.Chronicle;
import forkknight.core.KnightMemory;
import forkknight.core.Dispatch;
import forkknight.core.Feat;
import forkknight.core.RealmSession;
import forkknight.core.Scryer;
import forkknight.core.Sigil;
import forkknight.core.Weave;
import forkknight.ui.TalePane;
import javafx.application.Application;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * ForkKnight - a lightweight scroll of the realm (a desktop Git client).
 * The chronicle is displayed as a woven trail of feats; the knight can
 * enlist, seal, raise banners, press sigils and vanish changes into
 * Kamui - all without speaking the dragon's tongue.
 */
public class App extends Application {

    private static final String DARK_THEME_URL =
            App.class.getResource("/forkknight/dark-theme.css").toExternalForm();

    private static final double LANE_WIDTH = 18;
    private static final double NODE_RADIUS = 5;
    private static final Color[] LANE_COLORS = {
            Color.web("#7aa2f7"), Color.web("#bb9af7"), Color.web("#9ece6a"),
            Color.web("#e0af68"), Color.web("#f7768e"), Color.web("#7dcfff"),
            Color.web("#ff9e64"), Color.web("#73daca")
    };

    private Chronicle chronicle;
    /** Every realm the knight has open, in the order they were taken. */
    private final List<RealmSession> realms = new ArrayList<>();
    /** The realm the whole app currently serves (null when none is open). */
    private RealmSession activeRealm;
    private ComboBox<RealmSession> realmPicker;
    /** True while the banner box is set by the app, not by the knight. */
    private boolean restoringBanner;
    private TableView<Weave.Woven> chronicleTable;
    private TextField realmPathField;
    private final ObservableList<Weave.Woven> chronicleData =
            FXCollections.observableArrayList();
    private Label statusBar;
    private ComboBox<String> bannerBox;
    private TextField scryField;
    private ComboBox<Scryer.Scope> scryScopeBox;
    private ComboBox<String> recentBox;
    private ComboBox<String> heroBox;
    private ComboBox<String> sigilBox;
    private ComboBox<String> allyBox;
    private Button summonKamuiBtn;
    private Scene mainScene;
    private boolean darkTheme = true;

    private TalePane talePane;

    private TableView<Dispatch> fieldTable;
    private final ObservableList<Dispatch> fieldData = FXCollections.observableArrayList();
    private TabPane tabPane;
    private Tab scrollTab;
    private Tab fieldTab;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        memory = new KnightMemory();
        primaryStage.setTitle("ForkKnight - Scroll of the Realm");
        restoreSightAndBounds(primaryStage);

        // ----- realm chooser row: the open realms, and the active one's path -----
        Label realmLabel = new Label("Realm:");
        realmPicker = new ComboBox<>();
        realmPicker.setPromptText("No realm open");
        realmPicker.setPrefWidth(190);
        realmPicker.setCellFactory(list -> new RealmCell());
        realmPicker.setButtonCell(new RealmCell());
        realmPicker.setOnAction(e -> activateRealm(realmPicker.getValue()));
        realmPathField = new TextField();
        realmPathField.setPromptText("Choose a realm to serve");
        realmPathField.setEditable(false);
        Button chooseRealmBtn = new Button("Seek...");
        chooseRealmBtn.setOnAction(e -> seekRealm());
        HBox realmBox = new HBox(10, realmLabel, realmPicker, realmPathField, chooseRealmBtn);
        HBox.setHgrow(realmPathField, javafx.scene.layout.Priority.ALWAYS);

        // ----- banner + sigil row -----
        Label bannerLabel = new Label("Banner:");
        bannerBox = new ComboBox<>();
        bannerBox.setPromptText("Raised banner");
        bannerBox.setDisable(true);
        bannerBox.setPrefWidth(150);
        bannerBox.setOnAction(e -> {
            if (restoringBanner) {
                return; // the app filled the box, the knight chose nothing
            }
            String banner = bannerBox.getSelectionModel().getSelectedItem();
            if (banner != null && chronicle != null) {
                if (activeRealm != null) {
                    activeRealm.chooseBanner(banner);
                }
                surveyTrail(banner);
            }
        });

        Button raiseBannerBtn = new Button("Raise...");
        raiseBannerBtn.setDisable(true);
        raiseBannerBtn.setOnAction(e -> raiseBannerDialog());
        Button bannersRollBtn = new Button("Roll...");
        bannersRollBtn.setDisable(true);
        bannersRollBtn.setOnAction(e -> showBannersRoll());
        Button marchBtn = new Button("March");
        marchBtn.setDisable(true);
        marchBtn.setOnAction(e -> marchToSelectedBanner());
        Button fuseBtn = new Button("Fuse");
        fuseBtn.setDisable(true);
        fuseBtn.setOnAction(e -> fuseSelectedBanner());
        Button fellBtn = new Button("Fell...");
        fellBtn.setDisable(true);
        fellBtn.setOnAction(e -> fellSelectedBanner());
        bannerBox.disableProperty().addListener((obs, was, is) -> {
            boolean off = is;
            raiseBannerBtn.setDisable(off);
            bannersRollBtn.setDisable(off);
            marchBtn.setDisable(off);
            fuseBtn.setDisable(off);
            fellBtn.setDisable(off);
            sigilBox.setDisable(off);
            pressSigilBtn.setDisable(off);
            meltSigilBtn.setDisable(off);
        });

        Label sigilLabel = new Label("Sigil:");
        sigilBox = new ComboBox<>();
        sigilBox.setPromptText("No sigils");
        sigilBox.setDisable(true);
        sigilBox.setPrefWidth(110);
        sigilBox.setOnAction(e -> {
            String sigil = sigilBox.getSelectionModel().getSelectedItem();
            if (sigil != null && chronicle != null) {
                revealSigil(sigil);
            }
        });
        pressSigilBtn = new Button("Press...");
        pressSigilBtn.setDisable(true);
        pressSigilBtn.setOnAction(e -> pressSigilDialog());
        meltSigilBtn = new Button("Melt");
        meltSigilBtn.setDisable(true);
        meltSigilBtn.setOnAction(e -> meltSelectedSigil());

        // ----- herald row: allied realms -----
        Label allyLabel = new Label("Allies:");
        allyBox = new ComboBox<>();
        allyBox.setPromptText("No allies");
        allyBox.setDisable(true);
        allyBox.setPrefWidth(120);
        Button rallyBtn = new Button("Rally");
        rallyBtn.setDisable(true);
        rallyBtn.setOnAction(e -> rallyAllies());
        Button recallBtn = new Button("Recall");
        recallBtn.setDisable(true);
        recallBtn.setOnAction(e -> recallFromAlly());
        Button emissaryBtn = new Button("Emissary");
        emissaryBtn.setDisable(true);
        emissaryBtn.setOnAction(e -> sendEmissary());
        allyBox.disableProperty().addListener((obs, was, is) -> {
            boolean off = is;
            rallyBtn.setDisable(off);
            recallBtn.setDisable(off);
            emissaryBtn.setDisable(off);
        });

        HBox bannerRow = new HBox(10, bannerLabel, bannerBox, raiseBannerBtn, bannersRollBtn,
                marchBtn, fuseBtn, fellBtn,
                new Separator(),
                sigilLabel, sigilBox, pressSigilBtn, meltSigilBtn,
                new Separator(),
                allyLabel, allyBox, rallyBtn, recallBtn, emissaryBtn);
        bannerRow.setPadding(new Insets(0, 10, 10, 10));
        bannerRow.setAlignment(Pos.CENTER_LEFT);

        // ----- chronicle table with weave column -----
        chronicleTable = new TableView<>();
        chronicleTable.setPlaceholder(new Label("The chronicle awaits a realm"));
        chronicleTable.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);

        TableColumn<Weave.Woven, Weave.Woven> weaveCol = new TableColumn<>("Weave");
        weaveCol.setMinWidth(80);
        weaveCol.setSortable(false);
        weaveCol.setCellFactory(col -> new WeaveCell());

        TableColumn<Weave.Woven, String> markCol = new TableColumn<>("Mark");
        markCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().feat().shortHash()));
        markCol.setMinWidth(80);
        markCol.setSortable(false);

        TableColumn<Weave.Woven, String> heroCol = new TableColumn<>("Hero");
        heroCol.setCellValueFactory(cell -> cell.getValue().feat().author() == null ? null
                : new SimpleStringProperty(cell.getValue().feat().author()));
        heroCol.setMinWidth(150);
        heroCol.setSortable(false);

        TableColumn<Weave.Woven, LocalDate> dayCol = new TableColumn<>("Day");
        dayCol.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleObjectProperty<>(cell.getValue().feat().date()));
        dayCol.setMinWidth(100);
        dayCol.setSortable(false);
        dayCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null
                        : item.format(DateTimeFormatter.ISO_LOCAL_DATE));
            }
        });

        TableColumn<Weave.Woven, String> taleCol = new TableColumn<>("Feat");
        taleCol.setCellValueFactory(cell -> {
            Feat feat = cell.getValue().feat();
            boolean hasNote = memory != null && memory.recallNote(feat.hash()).isPresent();
            String prefix = hasNote ? "\uD83D\uDCDC " : "";
            String suffix = feat.isFusion() ? "  \u2694 fusion" : "";
            return new SimpleStringProperty(prefix + feat.summary() + suffix);
        });
        taleCol.setMinWidth(300);
        taleCol.setSortable(false);

        chronicleTable.getColumns().addAll(weaveCol, markCol, heroCol, dayCol, taleCol);
        chronicleTable.setItems(chronicleData);
        chronicleTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        chronicleTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, woven) -> onFeatChosen(woven));

        // ----- scrying row -----
        Label scryLabel = new Label("Scry:");
        scryField = new TextField();
        scryField.setPromptText("Peer into the chronicle... (words AND-match, prefixes bloom)");
        HBox.setHgrow(scryField, javafx.scene.layout.Priority.ALWAYS);
        scryScopeBox = new ComboBox<>();
        scryScopeBox.getItems().addAll(Scryer.Scope.values());
        scryScopeBox.getSelectionModel().selectFirst();
        scryField.textProperty().addListener((obs, o, n) -> applyScrying());
        scryScopeBox.valueProperty().addListener((obs, o, n) -> applyScrying());

        Label recentLabel = new Label("Days:");
        recentBox = new ComboBox<>();
        recentBox.getItems().addAll("All", "7", "30", "90");
        recentBox.getSelectionModel().selectFirst();
        recentBox.setPrefWidth(80);
        recentBox.valueProperty().addListener((obs, o, n) -> applyScrying());

        heroBox = new ComboBox<>();
        heroBox.setPromptText("Any hero");
        heroBox.setPrefWidth(130);
        heroBox.valueProperty().addListener((obs, o, n) -> applyScrying());

        Button clearScryBtn = new Button("Still");
        clearScryBtn.setOnAction(e -> {
            scryField.clear();
            recentBox.getSelectionModel().selectFirst();
            heroBox.getSelectionModel().clearSelection();
            scryField.requestFocus();
        });
        HBox scryRow = new HBox(8, scryLabel, scryField, scryScopeBox,
                recentLabel, recentBox, heroBox, clearScryBtn);
        scryRow.setPadding(new Insets(5, 10, 5, 10));

        talePane = new TalePane();
        talePane.setMemory(memory, () -> chronicleTable.refresh());

        statusBar = new Label("Ready");
        statusBar.setPadding(new Insets(4, 10, 4, 10));
        statusBar.getStyleClass().add("status-bar");

        VBox top = new VBox(5, realmBox, bannerRow, new Separator());
        top.setPadding(new Insets(10, 10, 0, 10));

        SplitPane center = new SplitPane();
        center.setOrientation(javafx.geometry.Orientation.VERTICAL);
        center.getItems().addAll(chronicleTable, talePane.getNode());
        center.setDividerPosition(0, 0.55);

        BorderPane chronicleTab = new BorderPane();
        chronicleTab.setTop(scryRow);
        chronicleTab.setCenter(center);

        tabPane = new TabPane();
        Tab scrollTab = new Tab("Scroll");
        scrollTab.setClosable(false);
        scrollTab.setContent(chronicleTab);
        this.scrollTab = scrollTab;
        Tab fieldTab = new Tab("The Field");
        fieldTab.setClosable(false);
        fieldTab.setContent(buildFieldView());
        this.fieldTab = fieldTab;
        tabPane.getTabs().addAll(scrollTab, fieldTab);
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, o, newTab) -> {
            if (newTab == fieldTab) {
                musterTheField();
            }
        });

        BorderPane root = new BorderPane();
        root.setTop(new VBox(buildMenuBar(), top));
        root.setCenter(tabPane);
        root.setBottom(statusBar);

        mainScene = new Scene(root, 1100, 750);
        primaryStage.setScene(mainScene);
        applyTheme();
        primaryStage.show();

        // The knight remembers where he rode last; on departure, he
        // writes down the open realms, the sight he favored and his place.
        primaryStage.setOnCloseRequest(e -> persistMemory());
        reopenRememberedRealms();
    }

    /**
     * Reopens every realm the knight had open (R2), then stands in the one
     * he left standing in. Realms that vanished since are skipped; a realm
     * that no longer answers simply stays closed.
     */
    private void reopenRememberedRealms() {
        List<String> remembered = RealmSession.decodeRealms(memory.recall("realms").orElse(null));
        if (remembered.isEmpty()) {
            // Ledgers written before R2 only ever held the single realm.
            String last = memory.recall("realm").orElse(null);
            if (last != null) {
                remembered = List.of(last);
            }
        }
        String activePath = memory.recall("realm").orElse(null);
        List<File> dirs = new ArrayList<>();
        for (String path : remembered) {
            File dir = new File(path);
            if (dir.isDirectory()) {
                dirs.add(dir);
            }
        }
        if (dirs.isEmpty()) {
            return;
        }
        Task<List<RealmSession>> task = new Task<>() {
            @Override
            protected List<RealmSession> call() throws Exception {
                List<RealmSession> opened = new ArrayList<>();
                for (File dir : dirs) {
                    try {
                        Chronicle candidate = new Chronicle(dir);
                        candidate.validateRealm();
                        opened.add(new RealmSession(candidate));
                    } catch (Exception vanished) {
                        // The realm is gone (deleted, unmounted): leave it closed.
                    }
                }
                return opened;
            }
        };
        task.setOnSucceeded(e -> {
            List<RealmSession> opened = task.getValue();
            if (opened.isEmpty()) {
                return;
            }
            realms.addAll(opened);
            refreshRealmPicker();
            RealmSession target = findByPath(activePath);
            activateRealm(target != null ? target : opened.get(opened.size() - 1));
        });
        task.setOnFailed(e -> showError("The remembered realms could not be reopened: "
                + task.getException().getMessage()));
        startDaemon(task, "realm-restore");
    }

    /** Writes the last memory, then closes the ledger connection. */
    @Override
    public void stop() {
        try {
            if (memory != null) {
                persistMemory();
                memory.close();
            }
        } catch (RuntimeException ignored) {
            // Departing must never surface a ledger failure.
        }
    }

    // ------------------------------------------------------------------
    // The knight's memory (settings persistence)
    // ------------------------------------------------------------------

    private Stage primaryStage;
    private KnightMemory memory;

    /** Restores window bounds and the favored sight from the memory. */
    private void restoreSightAndBounds(Stage stage) {
        try {
            String w = memory.recall("window.width").orElse(null);
            String h = memory.recall("window.height").orElse(null);
            if (w != null && h != null) {
                double width = Double.parseDouble(w);
                double height = Double.parseDouble(h);
                if (width >= 640 && height >= 400) {
                    stage.setWidth(width);
                    stage.setHeight(height);
                }
            }
        } catch (NumberFormatException ignored) {
            // scribbled bounds: keep the defaults
        }
        darkTheme = !"day".equals(memory.recall("sight").orElse("night"));
    }

    private void persistMemory() {
        memory.remember("sight", darkTheme ? "night" : "day");
        memory.remember("window.width", String.valueOf(primaryStage.getWidth()));
        memory.remember("window.height", String.valueOf(primaryStage.getHeight()));
        String realm = realmPathField.getText();
        if (realm != null && !realm.isBlank()) {
            memory.remember("realm", realm);
        }
    }

    private Button pressSigilBtn;
    private Button meltSigilBtn;

    // ------------------------------------------------------------------
    // Weave cell: draws the lane lines and fusion curves on a canvas
    // ------------------------------------------------------------------

    private final class WeaveCell extends TableCell<Weave.Woven, Weave.Woven> {
        private final Canvas canvas;
        private final StackPane pane;

        private WeaveCell() {
            pane = new StackPane();
            canvas = new Canvas();
            pane.getChildren().add(canvas);
            // Bind canvas size to the cell so repaints track layout.
            canvas.widthProperty().bind(pane.widthProperty());
            canvas.heightProperty().bind(pane.heightProperty());
            canvas.widthProperty().addListener((obs, w, nw) -> repaint());
            canvas.heightProperty().addListener((obs, h, nh) -> repaint());
            setGraphic(pane);
        }

        @Override
        protected void updateItem(Weave.Woven item, boolean empty) {
            super.updateItem(item, empty);
            if (empty) {
                setGraphic(null);
                return;
            }
            setGraphic(pane);
            repaint();
        }

        private void repaint() {
            Weave.Woven woven = getItem();
            if (woven == null || canvas.getWidth() <= 0 || canvas.getHeight() <= 0) {
                return;
            }
            GraphicsContext g = canvas.getGraphicsContext2D();
            double w = canvas.getWidth();
            double h = canvas.getHeight();
            g.clearRect(0, 0, w, h);

            double y = h / 2;

            // Link down to each on-screen parent lane.
            for (int parentLane : woven.parentLanes()) {
                Color color = laneColor(parentLane);
                g.setStroke(color);
                g.setLineWidth(2);
                if (parentLane == woven.lane()) {
                    g.strokeLine(laneX(woven.lane()), y, laneX(parentLane), h + 6);
                } else {
                    // Fusion bend: down this lane, then diagonal into the parent's.
                    g.strokeLine(laneX(woven.lane()), y, laneX(woven.lane()), h * 0.75);
                    g.strokeLine(laneX(woven.lane()), h * 0.75,
                            laneX(parentLane), h + 6);
                }
            }
            // This feat's node.
            g.setFill(laneColor(woven.lane()));
            g.fillOval(laneX(woven.lane()) - NODE_RADIUS, y - NODE_RADIUS,
                    NODE_RADIUS * 2, NODE_RADIUS * 2);
            if (woven.feat().isFusion()) {
                g.setStroke(Color.web("#f7768e"));
                g.setLineWidth(1.5);
                g.strokeOval(laneX(woven.lane()) - NODE_RADIUS - 2.5,
                        y - NODE_RADIUS - 2.5, NODE_RADIUS * 2 + 5, NODE_RADIUS * 2 + 5);
            }
        }

        private double laneX(int lane) {
            return 12 + lane * LANE_WIDTH;
        }

        private Color laneColor(int lane) {
            return LANE_COLORS[Math.floorMod(lane, LANE_COLORS.length)];
        }
    }

    // ------------------------------------------------------------------
    // Menu + theme
    // ------------------------------------------------------------------

    private MenuBar buildMenuBar() {
        Menu realmMenu = new Menu("_Realm");
        MenuItem seekItem = new MenuItem("Seek Realm...");
        seekItem.setAccelerator(Shortcut.SEEK.keys());
        seekItem.setOnAction(e -> seekRealm());
        realmMenu.getItems().add(seekItem);

        MenuItem bookmarkCurrentItem = new MenuItem("Bookmark Current Realm...");
        bookmarkCurrentItem.setOnAction(e -> bookmarkCurrentRealm());
        realmMenu.getItems().add(bookmarkCurrentItem);

        MenuItem listBookmarksItem = new MenuItem("Bookmarked Realms...");
        listBookmarksItem.setOnAction(e -> showBookmarkedRealmsDialog());
        realmMenu.getItems().add(listBookmarksItem);

        MenuItem closeRealmItem = new MenuItem("Close This Realm");
        closeRealmItem.setOnAction(e -> closeActiveRealm());
        realmMenu.getItems().add(closeRealmItem);
        realmMenu.getItems().add(new SeparatorMenuItem());

        MenuItem musterItem = new MenuItem("Muster the Field");
        musterItem.setAccelerator(Shortcut.MUSTER.keys());
        musterItem.setOnAction(e -> {
            tabPane.getSelectionModel().select(fieldTab);
            musterTheField();
        });
        realmMenu.getItems().add(musterItem);

        MenuItem sealItem = new MenuItem("Seal the Vanguard...");
        sealItem.setAccelerator(Shortcut.SEAL.keys());
        sealItem.setOnAction(e -> {
            tabPane.getSelectionModel().select(fieldTab);
            sealVanguard();
        });
        realmMenu.getItems().add(sealItem);

        MenuItem rallyItem = new MenuItem("Rally the Allies");
        rallyItem.setAccelerator(Shortcut.RALLY.keys());
        rallyItem.setOnAction(e -> {
            rallyAllies();
            surveyTrail(bannerBox.getSelectionModel().getSelectedItem());
        });
        realmMenu.getItems().add(rallyItem);

        MenuItem councilItem = new MenuItem("Summon the Council...");
        councilItem.setAccelerator(Shortcut.COUNCIL.keys());
        councilItem.setOnAction(e -> summonCouncil());
        realmMenu.getItems().add(councilItem);

        MenuItem rollItem = new MenuItem("Banners Roll...");
        rollItem.setAccelerator(Shortcut.ROLL.keys());
        rollItem.setOnAction(e -> showBannersRoll());
        realmMenu.getItems().add(rollItem);

        MenuItem quitItem = new MenuItem("Depart");
        quitItem.setAccelerator(Shortcut.DEPART.keys());
        quitItem.setOnAction(e -> javafx.application.Platform.exit());
        realmMenu.getItems().addAll(new SeparatorMenuItem(), quitItem);

        Menu viewMenu = new Menu("_Sight");
        ToggleGroup themeGroup = new ToggleGroup();
        RadioMenuItem darkItem = new RadioMenuItem("Night Sight");
        RadioMenuItem lightItem = new RadioMenuItem("Day Sight");
        darkItem.setToggleGroup(themeGroup);
        lightItem.setToggleGroup(themeGroup);
        darkItem.setSelected(darkTheme);
        darkItem.setOnAction(e -> setTheme(true));
        lightItem.setOnAction(e -> setTheme(false));
        lightItem.setAccelerator(Shortcut.DAY_SIGHT.keys());
        viewMenu.getItems().addAll(darkItem, lightItem);

        viewMenu.getItems().add(new SeparatorMenuItem());
        MenuItem scryItem = new MenuItem("Peer into the Scryer...");
        scryItem.setAccelerator(Shortcut.SCRY.keys());
        scryItem.setOnAction(e -> {
            tabPane.getSelectionModel().select(scrollTab);
            scryField.requestFocus();
            scryField.selectAll();
        });
        viewMenu.getItems().add(scryItem);

        MenuBar menuBar = new MenuBar();
        menuBar.getMenus().addAll(realmMenu, viewMenu);
        return menuBar;
    }

    private void setTheme(boolean dark) {
        darkTheme = dark;
        applyTheme();
        memory.remember("sight", dark ? "night" : "day");
    }

    private void applyTheme() {
        if (darkTheme) {
            if (!mainScene.getStylesheets().contains(DARK_THEME_URL)) {
                mainScene.getStylesheets().add(DARK_THEME_URL);
            }
        } else {
            mainScene.getStylesheets().remove(DARK_THEME_URL);
        }
    }

    // ------------------------------------------------------------------
    // Realm discovery
    // ------------------------------------------------------------------

    private void seekRealm() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose Your Realm");
        File selectedDir = chooser.showDialog(null);
        if (selectedDir != null) {
            openRealm(selectedDir);
        }
    }

    /**
     * Brings a realm into the open set (R2). A realm already open is simply
     * stood in again - never reopened from disk.
     */
    private void openRealm(File dir) {
        RealmSession already = findByPath(dir.getAbsolutePath());
        if (already != null) {
            activateRealm(already);
            statusBar.setText("Already open: " + displayFor(already));
            return;
        }
        Chronicle candidate = new Chronicle(dir);
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                candidate.validateRealm();
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            RealmSession session = new RealmSession(candidate);
            realms.add(session);
            refreshRealmPicker();
            activateRealm(session);
        });
        task.setOnFailed(e -> showError("This land answers to no realm: "
                + task.getException().getMessage()));
        new Thread(task, "realm-verify").start();
    }

    /** Stands the whole app in the given realm, keeping every other one open. */
    private void activateRealm(RealmSession session) {
        if (session == null || session == activeRealm) {
            return; // nothing to do, and the picker fires this when set
        }
        rememberViewState();

        activeRealm = session;
        chronicle = session.chronicle();
        scryer = session.scryer();
        realmPathField.setText(session.path());
        realmPicker.setValue(session);   // re-enters, but the guard above stops it

        loadBanners();
        loadSigils();
        loadAllies();
        refreshKamuiButton();
        if (session.surveyed()) {
            // Paint from memory first - the switch costs no git, no wait...
            renderTrail(session);
            restoreSelection(session.selectedHash());
            // ...then quietly confirm the trail still stands as remembered.
            refreshTrailQuietly(session);
        } else {
            surveyTrail(session.banner());
        }
        if (fieldTab.isSelected()) {
            musterTheField();
        }
        rememberOpenRealms();
        statusBar.setText(displayFor(session) + " - " + session.path());
    }

    /** Closes the realm the knight stands in, keeping the rest open. */
    private void closeActiveRealm() {
        if (activeRealm == null) {
            return;
        }
        rememberViewState();
        int closed = realms.indexOf(activeRealm);
        realms.remove(activeRealm);
        activeRealm = null;
        refreshRealmPicker();
        if (realms.isEmpty()) {
            clearRealmView();
        } else {
            activateRealm(realms.get(Math.min(closed, realms.size() - 1)));
        }
        rememberOpenRealms();
    }

    /** Returns the app to its no-realm state (the way it boots). */
    private void clearRealmView() {
        chronicle = null;
        scryer = null;
        activeRealm = null;
        realmPicker.setValue(null);
        realmPathField.setText("");
        memory.forget("realm");
        chronicleData.clear();
        talePane.reset();
        chronicleTable.setItems(chronicleData);
        chronicleTable.setPlaceholder(new Label("Seek a realm to serve."));
        bannerBox.getItems().clear();
        bannerBox.setDisable(true);
        sigilBox.getItems().clear();
        sigilBox.setDisable(true);
        allyBox.getItems().clear();
        allyBox.setDisable(true);
        fieldData.clear();
        summonKamuiBtn.setDisable(true);
        statusBar.setText("No realm is open.");
    }

    /** The state the knight left behind in the realm he is leaving. */
    private void rememberViewState() {
        if (activeRealm != null) {
            activeRealm.selectFeat(selectedHashNow());
        }
    }

    /** Draws a surveyed trail into the view, then puts the knight back. */
    private void renderTrail(RealmSession session) {
        chronicleData.setAll(session.weave().rows());
        scryer = session.scryer();
        refreshHeroLens();
        applyScrying();
        if (chronicleData.isEmpty()) {
            chronicleTable.setPlaceholder(new Label("The chronicle is empty."));
        } else {
            chronicleTable.setPlaceholder(null);
        }
    }

    /** Puts the selection (and the scroll) back where the knight left it. */
    private void restoreSelection(String hash) {
        if (hash == null) {
            talePane.reset();
            return;
        }
        for (int i = 0; i < chronicleTable.getItems().size(); i++) {
            Weave.Woven row = chronicleTable.getItems().get(i);
            if (hash.equals(row.feat().hash())) {
                chronicleTable.getSelectionModel().select(i);
                chronicleTable.scrollTo(i);
                return;
            }
        }
        talePane.reset();
    }

    /** The feat the knight is looking at right now, if any. */
    private String selectedHashNow() {
        Weave.Woven chosen = chronicleTable.getSelectionModel().getSelectedItem();
        return chosen != null ? chosen.feat().hash() : null;
    }

    /**
     * Re-swarms a cached realm in the background, so anything that changed
     * while the knight was elsewhere lands without ever blanking the view.
     * The selection rides along.
     */
    private void refreshTrailQuietly(RealmSession session) {
        Chronicle service = session.chronicle();
        String banner = session.banner();
        Task<Survey> task = new Task<>() {
            @Override
            protected Survey call() throws Exception {
                List<Feat> feats = service.surveyTrail(banner, Integer.MAX_VALUE);
                return new Survey(Weave.of(feats), new Scryer(feats));
            }
        };
        task.setOnSucceeded(e -> {
            Survey result = task.getValue();
            session.rememberTrail(result.weave(), result.scryer());
            if (session != activeRealm) {
                return;
            }
            String keep = selectedHashNow();
            renderTrail(session);
            restoreSelection(keep);
        });
        // A failed refresh keeps the cached view: better a slightly stale
        // trail than an empty one with an error over it.
        startDaemon(task, "trail-refresh");
    }

    private RealmSession findByPath(String path) {
        if (path == null) {
            return null;
        }
        String wanted = new File(path).getAbsolutePath();
        for (RealmSession session : realms) {
            if (session.path().equals(wanted)) {
                return session;
            }
        }
        return null;
    }

    private void refreshRealmPicker() {
        RealmSession current = activeRealm;
        realmPicker.getItems().setAll(realms);   // re-renders the cells
        if (current != null) {
            realmPicker.setValue(current);       // setAll may have shaken the value loose
        }
    }

    /** The knight's name for a realm: its bookmark if it has one. */
    private String displayFor(RealmSession session) {
        return memory.recallBookmarkName(session.path()).orElseGet(session::toString);
    }

    /** The open set, and the one the knight stands in, written down. */
    private void rememberOpenRealms() {
        List<String> paths = new ArrayList<>();
        for (RealmSession session : realms) {
            paths.add(session.path());
        }
        memory.remember("realms", RealmSession.encodeRealms(paths));
        if (activeRealm != null) {
            memory.remember("realm", activeRealm.path());
        } else {
            memory.forget("realm");
        }
    }

    /** A cell in the realm picker: the realm's name, its path beneath. */
    private final class RealmCell extends javafx.scene.control.ListCell<RealmSession> {
        @Override
        protected void updateItem(RealmSession item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setTooltip(null);
            } else {
                setText(displayFor(item));
                setTooltip(new javafx.scene.control.Tooltip(item.path()));
            }
        }
    }

    private void bookmarkCurrentRealm() {
        if (chronicle == null) {
            showError("No realm is currently open.");
            return;
        }
        String path = chronicle.getRealmDir().getAbsolutePath();
        String currentName = memory.recallBookmarkName(path).orElse(chronicle.getRealmDir().getName());
        nameDialog("Bookmark Realm", "Name this realm:", "Realm Name:", currentName).ifPresent(name -> {
            if (!name.isBlank()) {
                memory.setBookmarkName(path, name);
                refreshRealmPicker();   // the picker names realms by bookmark
                statusBar.setText("Realm bookmarked as '" + name + "'.");
            }
        });
    }

    private void showBookmarkedRealmsDialog() {
        java.util.Map<String, String> bookmarks = memory.recallAllBookmarks();
        if (bookmarks.isEmpty()) {
            showError("No realms have been bookmarked yet.");
            return;
        }

        ListView<String> list = new ListView<>();
        java.util.Map<String, String> displayToPath = new java.util.HashMap<>();
        for (java.util.Map.Entry<String, String> entry : bookmarks.entrySet()) {
            String display = entry.getValue() + " (" + entry.getKey() + ")";
            list.getItems().add(display);
            displayToPath.put(display, entry.getKey());
        }

        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Bookmarked Realms");
        dialog.setHeaderText("Select a realm to open:");
        dialog.getDialogPane().setContent(list);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        list.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && list.getSelectionModel().getSelectedItem() != null) {
                dialog.setResult(list.getSelectionModel().getSelectedItem());
                dialog.close();
            }
        });

        dialog.setResultConverter(button -> {
            if (button == ButtonType.OK) {
                return list.getSelectionModel().getSelectedItem();
            }
            return null;
        });

        dialog.showAndWait().ifPresent(selected -> {
            String path = displayToPath.get(selected);
            if (path != null) {
                openRealm(new File(path));
            }
        });
    }

    private void loadBanners() {
        Chronicle service = chronicle;
        RealmSession session = activeRealm;
        Task<List<Banner>> task = new Task<>() {
            @Override
            protected List<Banner> call() throws Exception {
                return service.banners();
            }
        };
        task.setOnSucceeded(e -> {
            if (session != activeRealm) {
                return; // the knight rode elsewhere while the banners loaded
            }
            bannerBox.getItems().setAll(task.getValue().stream()
                    .map(Banner::name).toList());
            String active = task.getValue().stream()
                    .filter(Banner::active).findFirst()
                    .map(Banner::name).orElse(null);
            String remembered = session != null ? session.banner() : null;
            String wanted = remembered != null && bannerBox.getItems().contains(remembered)
                    ? remembered
                    : active;
            // Fill the box without asking the knight to survey again.
            restoringBanner = true;
            try {
                if (wanted != null) {
                    bannerBox.getSelectionModel().select(wanted);
                } else if (!bannerBox.getItems().isEmpty()) {
                    bannerBox.getSelectionModel().selectFirst();
                }
            } finally {
                restoringBanner = false;
            }
            bannerBox.setDisable(bannerBox.getItems().isEmpty());
        });
        task.setOnFailed(e -> bannerBox.setDisable(true));
        startDaemon(task, "banner-load");
    }

    private void surveyTrail(String banner) {
        RealmSession session = activeRealm;
        if (session != null && banner != null) {
            session.chooseBanner(banner);
        }
        chronicleData.clear();
        talePane.reset();
        chronicleTable.setPlaceholder(new Label("Unrolling the scroll..."));
        statusBar.setText("Surveying the trail...");

        Chronicle service = chronicle;
        Task<Survey> task = new Task<>() {
            @Override
            protected Survey call() throws Exception {
                List<Feat> feats = service.surveyTrail(banner, Integer.MAX_VALUE);
                // Index the fresh trail for scrying; built on the worker
                // thread so the UI never stalls on big realms.
                return new Survey(Weave.of(feats), new Scryer(feats));
            }
        };
        task.setOnSucceeded(e -> {
            Survey result = task.getValue();
            if (session != null) {
                // Cache it in the realm it belongs to, even if the knight
                // has since ridden on: the next visit costs no git.
                session.rememberTrail(result.weave(), result.scryer());
            }
            if (session == null || session != activeRealm) {
                return;   // the knight rode elsewhere (or closed everything)
            }
            renderTrail(session);
            restoreSelection(session.selectedHash());
        });
        task.setOnFailed(e -> showError("The survey failed: "
                + task.getException().getMessage()));
        startDaemon(task, "trail-survey");
    }

    /** A trail and its scrying index, built together off the FX thread. */
    private record Survey(Weave weave, Scryer scryer) {
    }

    // ------------------------------------------------------------------
    // Scrying (search) via the Scryer index
    // ------------------------------------------------------------------

    private Scryer scryer;

    /** Applies every scrying lens together: text AND recency AND hero. */
    private void applyScrying() {
        if (scryer == null) {
            return;
        }
        String query = scryField.getText();
        Scryer.Scope scope = scryScopeBox.getValue();

        Predicate<Weave.Woven> combined = null;
        int lensCount = 0;

        if (query != null && !query.isBlank()) {
            Predicate<Feat> text = scryer.predicateFor(query, scope);
            combined = woven -> text.test(woven.feat());
            lensCount++;
        }
        String days = recentBox.getSelectionModel().getSelectedItem();
        if (days != null && !days.equals("All")) {
            try {
                Predicate<Feat> recent = scryer.scryRecent(Integer.parseInt(days));
                Predicate<Weave.Woven> next = woven -> recent.test(woven.feat());
                combined = combined == null ? next : combined.and(next);
                lensCount++;
            } catch (NumberFormatException ignored) {
                // a custom value landed in the box: ignore it
            }
        }
        String hero = heroBox.getSelectionModel().getSelectedItem();
        if (hero != null && !hero.isBlank()) {
            Predicate<Feat> byHero = scryer.scryByHero(hero);
            Predicate<Weave.Woven> next = woven -> byHero.test(woven.feat());
            combined = combined == null ? next : combined.and(next);
            lensCount++;
        }

        if (combined == null) {
            chronicleTable.setItems(chronicleData);
            statusBar.setText(chronicleData.size() + " feats");
            return;
        }
        Predicate<Weave.Woven> lens = combined;
        chronicleTable.setItems(chronicleData.filtered(lens::test));
        int shown = chronicleTable.getItems().size();
        statusBar.setText(shown + " of " + chronicleData.size()
                + " feats answer the scry");
        if (shown == 0 && !chronicleData.isEmpty()) {
            chronicleTable.setPlaceholder(new Label("The scry found nothing."));
        } else {
            chronicleTable.setPlaceholder(null);
        }
    }

    /** Refreshes the hero lens options from the current trail. */
    private void refreshHeroLens() {
        if (scryer == null) {
            return;
        }
        String chosen = heroBox.getSelectionModel().getSelectedItem();
        heroBox.getItems().setAll(scryer.heroes());
        if (chosen != null && heroBox.getItems().contains(chosen)) {
            heroBox.getSelectionModel().select(chosen);
        } else {
            heroBox.getSelectionModel().clearSelection();
        }
    }

    // ------------------------------------------------------------------
    // Feat selection -> tale pane
    // ------------------------------------------------------------------

    private void onFeatChosen(Weave.Woven woven) {
        if (woven == null) {
            talePane.reset();
            return;
        }
        if (chronicle == null) {
            return;
        }
        talePane.recount(chronicle, woven.feat());
        statusBar.setText("Recounting " + woven.feat().shortHash() + "...");
    }

    // ------------------------------------------------------------------
    // Banners (branches)
    // ------------------------------------------------------------------

    private void raiseBannerDialog() {
        if (chronicle == null) {
            return;
        }
        nameDialog("Raise Banner", "Raise a new banner at the frontier",
                "Banner name:").ifPresent(name -> {
            if (name.isEmpty()) {
                return;
            }
            if (isBannerNameForbidden(name)) {
                showError("A banner may not bear such a name: " + name);
                return;
            }
            runRealmAction("Raise '" + name + "'",
                    () -> chronicle.raiseBanner(name), this::loadBanners);
        });
    }

    private void marchToSelectedBanner() {
        if (chronicle == null) {
            return;
        }
        String target = bannerBox.getSelectionModel().getSelectedItem();
        String active = activeBannerName();
        if (target == null || target.equals(active)) {
            showError("Choose a different banner to march to.");
            return;
        }
        confirmDialog("March", "March the host to '" + target + "'?", () ->
                runRealmAction("March to '" + target + "'",
                        () -> chronicle.marchToBanner(target),
                        () -> {
                            loadBanners();
                            surveyTrail(target);
                        }));
    }

    private void fellSelectedBanner() {
        if (chronicle == null) {
            return;
        }
        String target = bannerBox.getSelectionModel().getSelectedItem();
        if (target == null) {
            showError("Choose a banner to fell.");
            return;
        }
        if (target.equals(activeBannerName())) {
            showError("The raised banner cannot be felled.");
            return;
        }
        confirmDialog("Fell Banner",
                "Fell the banner '" + target + "'?\nUnmerged feats will be lost.",
                () -> runRealmAction("Fell '" + target + "'",
                        () -> chronicle.fellBanner(target, false), this::loadBanners));
    }

    /** Fuses the selected banner into the raised one. */
    private void fuseSelectedBanner() {
        if (chronicle == null) {
            return;
        }
        String target = bannerBox.getSelectionModel().getSelectedItem();
        if (target == null || target.equals(activeBannerName())) {
            showError("Choose another banner to fuse into this one.");
            return;
        }
        confirmDialog("Fusion",
                "Fuse '" + target + "' into '" + activeBannerName() + "'?",
                () -> runRealmAction("Fusion with '" + target + "'",
                        () -> {
                            try {
                                chronicle.fuseBanner(target);
                            } catch (IOException ex) {
                                // A disputed fusion is not a hard failure:
                                // surface it, offer abandonment.
                                if (chronicle.fusionInDispute()) {
                                    javafx.application.Platform.runLater(() ->
                                            offerFusionAbandon(target));
                                }
                                throw ex;
                            }
                        },
                        () -> {
                            loadBanners();
                            surveyTrail(null);
                        }));
    }

    /** Shown when a fusion ends in conflict; lets the knight withdraw. */
    private void offerFusionAbandon(String target) {
        Alert dispute = new Alert(Alert.AlertType.WARNING,
                "The fusion with '" + target + "' is in dispute.\n"
                        + "Resolve the dispatches outside ForkKnight, or withdraw.",
                new ButtonType("Withdraw", ButtonBar.ButtonData.YES),
                new ButtonType("Keep", ButtonBar.ButtonData.NO));
        dispute.setHeaderText("Disputed Fusion");
        dispute.showAndWait().ifPresent(choice -> {
            if (choice.getButtonData() == ButtonBar.ButtonData.YES) {
                runRealmAction("Withdraw from fusion",
                        chronicle::abandonDisputedFusion,
                        () -> {
                            loadBanners();
                            surveyTrail(null);
                            statusBar.setText("Fusion withdrawn; the realm stands as before.");
                        });
            } else {
                statusBar.setText("Fusion dispute left in place - resolve it, then muster again.");
            }
        });
    }

    /** Displays all banners in the hall along with their ahead/behind counts relative to HEAD. */
    private void showBannersRoll() {
        if (chronicle == null) {
            showError("Seek a realm first.");
            return;
        }
        statusBar.setText("Surveying the banners roll...");
        Chronicle service = chronicle;
        Task<List<Chronicle.BannerStanding>> task = new Task<>() {
            @Override
            protected List<Chronicle.BannerStanding> call() throws Exception {
                return service.bannerStandings();
            }
        };
        task.setOnSucceeded(e -> {
            statusBar.setText("Banners roll ready.");
            List<Chronicle.BannerStanding> standings = task.getValue();
            showBannersRollDialog(standings);
        });
        task.setOnFailed(e -> showError("Could not read banners roll: " + task.getException().getMessage()));
        startDaemon(task, "banners-roll");
    }

    private void showBannersRollDialog(List<Chronicle.BannerStanding> standings) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("ForkKnight - Banners Roll");
        dialog.setHeaderText("The Hall of Banners & Campaign Divergence");

        TableView<Chronicle.BannerStanding> table = new TableView<>();
        table.setPrefWidth(550);
        table.setPrefHeight(300);

        TableColumn<Chronicle.BannerStanding, String> nameCol = new TableColumn<>("Banner");
        nameCol.setCellValueFactory(cell -> new SimpleStringProperty(
                (cell.getValue().banner().active() ? "* " : "  ") + cell.getValue().banner().name()));
        nameCol.setMinWidth(150);

        TableColumn<Chronicle.BannerStanding, String> markCol = new TableColumn<>("Frontier Mark");
        markCol.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().banner().shortHash()));
        markCol.setMinWidth(100);

        TableColumn<Chronicle.BannerStanding, String> standingCol = new TableColumn<>("Standing vs Raised");
        standingCol.setCellValueFactory(cell -> {
            Chronicle.BannerStanding s = cell.getValue();
            if (s.banner().active()) {
                return new SimpleStringProperty("Raised (Sworn Frontier)");
            }
            return new SimpleStringProperty("+" + s.ahead() + " / -" + s.behind());
        });
        standingCol.setMinWidth(200);

        table.getColumns().addAll(nameCol, markCol, standingCol);
        table.setItems(FXCollections.observableArrayList(standings));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        ButtonType marchBtnType = new ButtonType("March to Banner", ButtonBar.ButtonData.OK_DONE);
        ButtonType closeBtnType = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(marchBtnType, closeBtnType);
        dialog.getDialogPane().setContent(table);

        dialog.setResultConverter(btn -> {
            if (btn == marchBtnType) {
                Chronicle.BannerStanding selected = table.getSelectionModel().getSelectedItem();
                if (selected != null && !selected.banner().active()) {
                    bannerBox.getSelectionModel().select(selected.banner().name());
                    marchToSelectedBanner();
                }
            }
            return null;
        });

        dialog.showAndWait();
    }

    private String activeBannerName() {
        return bannerBox.getSelectionModel().getSelectedItem();
    }

    private boolean isBannerNameForbidden(String name) {
        return name.startsWith("-") || name.endsWith("/") || name.endsWith(".lock")
                || name.contains("..") || name.contains(" ");
    }

    // ------------------------------------------------------------------
    // Sigils (tags)
    // ------------------------------------------------------------------

    private void loadSigils() {
        if (chronicle == null) {
            return;
        }
        Chronicle service = chronicle;
        RealmSession session = activeRealm;
        Task<List<Sigil>> task = new Task<>() {
            @Override
            protected List<Sigil> call() throws Exception {
                return service.sigils();
            }
        };
        task.setOnSucceeded(e -> {
            if (session != activeRealm) {
                return; // another realm stands here now
            }
            sigilBox.getItems().setAll(task.getValue().stream()
                    .map(Sigil::name).toList());
        });
        task.setOnFailed(e -> {
            if (session == activeRealm) {
                sigilBox.getItems().clear();
            }
        });
        startDaemon(task, "sigil-load");
    }

    /** Jumps the chronicle view to the feat carrying the sigil. */
    private void revealSigil(String name) {
        if (chronicle == null) {
            return;
        }
        Chronicle service = chronicle;
        Task<Feat> task = new Task<>() {
            @Override
            protected Feat call() throws Exception {
                List<Sigil> sigils = service.sigils();
                return sigils.stream().filter(s -> s.name().equals(name))
                        .map(Sigil::feat).findFirst().orElse(null);
            }
        };
        task.setOnSucceeded(e -> {
            Feat feat = task.getValue();
            if (feat == null) {
                return;
            }
            chronicleData.stream()
                    .filter(w -> w.feat().hash().equals(feat.hash()))
                    .findFirst()
                    .ifPresentOrElse(w -> {
                        chronicleTable.getSelectionModel().select(w);
                        chronicleTable.scrollTo(w);
                    }, () -> statusBar.setText("Sigil '" + name + "' marks "
                            + feat.shortHash() + ", off this trail"));
        });
        task.setOnFailed(e -> showError("The sigil is unreadable: "
                + task.getException().getMessage()));
        startDaemon(task, "sigil-reveal");
    }

    private void pressSigilDialog() {
        if (chronicle == null) {
            return;
        }
        nameDialog("Press Sigil", "Press a sigil onto the chosen feat (or the frontier)",
                "Sigil name:").ifPresent(name -> {
            if (name.isEmpty()) {
                return;
            }
            Weave.Woven chosen = chronicleTable.getSelectionModel().getSelectedItem();
            String hash = chosen != null ? chosen.feat().hash() : null;
            runRealmAction("Press '" + name + "'",
                    () -> chronicle.pressSigil(name, hash), this::loadSigils);
        });
    }

    private void meltSelectedSigil() {
        if (chronicle == null) {
            return;
        }
        String name = sigilBox.getSelectionModel().getSelectedItem();
        if (name == null) {
            showError("Choose a sigil to melt.");
            return;
        }
        confirmDialog("Melt Sigil", "Melt the sigil '" + name + "'?",
                () -> runRealmAction("Melt '" + name + "'",
                        () -> chronicle.meltSigil(name), this::loadSigils));
    }

    // ------------------------------------------------------------------
    // Kamui (stash)
    // ------------------------------------------------------------------

    private void vanishIntoKamui() {
        if (chronicle == null) {
            return;
        }
        confirmDialog("Kamui", "Vanish all field changes into Kamui's dimension?",
                () -> runRealmAction("Kamui vanish",
                        chronicle::kamuiVanish, this::refreshKamuiButton));
    }

    private void summonFromKamui() {
        if (chronicle == null) {
            return;
        }
        runRealmAction("Kamui summon", chronicle::kamuiSummon, this::refreshKamuiButton);
    }

    private void refreshKamuiButton() {
        if (chronicle == null) {
            return;
        }
        Chronicle service = chronicle;
        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                return service.kamuiHolds();
            }
        };
        task.setOnSucceeded(e -> summonKamuiBtn.setDisable(!task.getValue()));
        startDaemon(task, "kamui-check");
    }

    // ------------------------------------------------------------------
    // The Herald: allies (remotes), rally, recall, emissary
    // ------------------------------------------------------------------

    /** Loads the roll of allies into the combo box. */
    private void loadAllies() {
        if (chronicle == null) {
            return;
        }
        Chronicle service = chronicle;
        RealmSession session = activeRealm;
        Task<List<Chronicle.Ally>> task = new Task<>() {
            @Override
            protected List<Chronicle.Ally> call() throws Exception {
                return service.allies();
            }
        };
        task.setOnSucceeded(e -> {
            if (session != activeRealm) {
                return; // another realm stands here now
            }
            allyBox.getItems().setAll(task.getValue().stream()
                    .map(Chronicle.Ally::name).toList());
            if (!allyBox.getItems().isEmpty()) {
                allyBox.getSelectionModel().selectFirst();
            }
            allyBox.setDisable(allyBox.getItems().isEmpty());
        });
        task.setOnFailed(e -> {
            if (session == activeRealm) {
                allyBox.setDisable(true);
            }
        });
        startDaemon(task, "ally-load");
    }

    /** The selected ally name, or null to address all allies. */
    private String selectedAlly() {
        return allyBox.getSelectionModel().getSelectedItem();
    }

    /** Rally: refresh knowledge of allied banners (fetch). */
    private void rallyAllies() {
        if (chronicle == null) {
            return;
        }
        String ally = selectedAlly();
        runRealmAction("Rally " + (ally == null ? "all allies" : "'" + ally + "'"),
                () -> chronicle.rally(ally),
                () -> statusBar.setText("The allied hosts have been rallied."));
    }

    /** Recall: fast-forward the raised banner from the ally (pull). */
    private void recallFromAlly() {
        if (chronicle == null) {
            return;
        }
        String ally = selectedAlly();
        String label = ally == null ? "all allies" : ally;
        confirmDialog("Recall",
                "Recall allied wisdom from '" + label + "'?",
                () -> runRealmAction("Recall from '" + label + "'",
                        () -> chronicle.recall(ally),
                        () -> {
                            loadBanners();
                            surveyTrail(null);
                            statusBar.setText("Allied wisdom recalled; the trail is current.");
                        }));
    }

    /** Emissary: carry the raised banner's feats to the ally (push). */
    private void sendEmissary() {
        if (chronicle == null) {
            return;
        }
        String ally = selectedAlly();
        String label = ally == null ? "all allies" : ally;
        confirmDialog("Emissary",
                "Send the emissary to '" + label + "' with the newest feats?",
                () -> runRealmAction("Emissary to '" + label + "'",
                        () -> chronicle.sendEmissary(ally),
                        () -> statusBar.setText("The emissary returned; word has been delivered.")));
    }

    // ------------------------------------------------------------------
    // The Council (realm statistics)
    // ------------------------------------------------------------------

    /** Feats whose tolls feed the path heat (bounded for speed). */
    private static final int HEAT_SAMPLE = 200;

    /**
     * Summons the council: the chronicler reads the realm's tale. Toll
     * gathering runs on a worker thread (one subprocess per feat would
     * otherwise stall the UI on big realms); the heat samples at most
     * the newest HEAT_SAMPLE feats.
     */
    private void summonCouncil() {
        if (chronicle == null) {
            showError("Seek a realm first.");
            return;
        }
        Chronicle service = chronicle;
        statusBar.setText("The chronicler is reading the realm...");
        Task<Chronicler> task = new Task<>() {
            @Override
            protected Chronicler call() throws Exception {
                List<Feat> feats = service.surveyTrail();
                Chronicler chronicler = Chronicler.of(feats);
                java.util.Map<String, List<Dispatch>> tolls = new java.util.HashMap<>();
                List<Feat> sample = feats.subList(0,
                        Math.min(feats.size(), HEAT_SAMPLE));
                for (Feat feat : sample) {
                    tolls.put(feat.hash(), service.tollOf(feat.hash()));
                }
                pathHeatForCouncil = chronicler.pathHeat(tolls, 8);
                return chronicler;
            }
        };
        task.setOnSucceeded(e -> {
            statusBar.setText("The council has spoken.");
            new CouncilDialog(task.getValue(), pathHeatForCouncil).showAndWait();
        });
        task.setOnFailed(e -> showError("The chronicler could not read: "
                + task.getException().getMessage()));
        startDaemon(task, "council-reading");
    }

    private List<Chronicler.PathHeat> pathHeatForCouncil = List.of();

    // ------------------------------------------------------------------
    // The Field tab (working changes)
    // ------------------------------------------------------------------

    private javafx.scene.Node buildFieldView() {
        fieldTable = new TableView<>();
        fieldTable.setPlaceholder(new Label("The field is clear"));

        TableColumn<Dispatch, String> stateCol = new TableColumn<>("Post");
        stateCol.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().staged() ? "Vanguard" : "Field"));
        stateCol.setMinWidth(90);

        TableColumn<Dispatch, String> statusCol = new TableColumn<>("Word");
        statusCol.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().description()));
        statusCol.setMinWidth(100);

        TableColumn<Dispatch, String> pathCol = new TableColumn<>("Path");
        pathCol.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().isRenaming()
                        ? cell.getValue().oldPath() + " \u2192 " + cell.getValue().path()
                        : cell.getValue().path()));
        pathCol.setMinWidth(300);

        fieldTable.getColumns().addAll(stateCol, statusCol, pathCol);
        fieldTable.setItems(fieldData);
        fieldTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        fieldTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        Button musterBtn = new Button("Muster");
        musterBtn.setOnAction(e -> musterTheField());
        Button enlistBtn = new Button("Enlist");
        enlistBtn.setOnAction(e -> enlistSelected(true));
        Button releaseBtn = new Button("Release");
        releaseBtn.setOnAction(e -> enlistSelected(false));
        Button enlistAllBtn = new Button("Enlist All");
        enlistAllBtn.setOnAction(e ->
                runRealmAction("Enlist all", chronicle::enlistAll));
        Button sealBtn = new Button("Seal...");
        sealBtn.setOnAction(e -> sealVanguard());
        Button reSealBtn = new Button("Re-seal...");
        reSealBtn.setOnAction(e -> reSealNewest());
        Button banishBtn = new Button("Banish...");
        banishBtn.setOnAction(e -> banishSelected());
        Button kamuiBtn = new Button("Kamui");
        kamuiBtn.setOnAction(e -> vanishIntoKamui());
        summonKamuiBtn = new Button("Summon");
        summonKamuiBtn.setDisable(true);
        summonKamuiBtn.setOnAction(e -> summonFromKamui());

        HBox buttons = new HBox(8, musterBtn, new Separator(),
                enlistBtn, releaseBtn, enlistAllBtn, new Separator(),
                sealBtn, reSealBtn, banishBtn, new Separator(),
                kamuiBtn, summonKamuiBtn);
        buttons.setPadding(new Insets(8));

        BorderPane pane = new BorderPane();
        pane.setTop(buttons);
        pane.setCenter(fieldTable);
        return pane;
    }

    private void musterTheField() {
        if (chronicle == null) {
            return;
        }
        Chronicle service = chronicle;
        Task<List<Dispatch>> task = new Task<>() {
            @Override
            protected List<Dispatch> call() throws Exception {
                return service.muster();
            }
        };
        task.setOnSucceeded(e -> fieldData.setAll(task.getValue()));
        task.setOnFailed(e -> showError("The muster failed: "
                + task.getException().getMessage()));
        startDaemon(task, "field-muster");
    }

    private void enlistSelected(boolean enlist) {
        List<Dispatch> selected = List.copyOf(fieldTable.getSelectionModel().getSelectedItems());
        if (selected.isEmpty()) {
            return;
        }
        runRealmAction(enlist ? "Enlist" : "Release", () -> {
            for (Dispatch dispatch : selected) {
                if (enlist) {
                    chronicle.enlist(dispatch.path());
                } else {
                    chronicle.release(dispatch.path());
                }
            }
        });
    }

    private void sealVanguard() {
        try {
            chronicle.requireVanguard();
        } catch (Exception ex) {
            showError(ex.getMessage());
            return;
        }
        SealDialog dialog = new SealDialog();
        Optional<SealWords> words = dialog.showAndWait();
        words.ifPresent(w -> runRealmAction("Seal",
                () -> chronicle.seal(w.summary(), w.body())));
    }

    /** Re-seal: fold the vanguard into the newest feat (amend). */
    private void reSealNewest() {
        if (chronicle == null) {
            return;
        }
        Feat newest;
        try {
            newest = chronicle.newestFeat();
        } catch (Exception ex) {
            showError(ex.getMessage());
            return;
        }
        try {
            chronicle.requireVanguard();
        } catch (Exception ex) {
            showError("Nothing to fold - enlist files before re-sealing.");
            return;
        }
        SealDialog dialog = new SealDialog(newest.summary(),
                newest.body() == null ? "" : newest.body());
        dialog.setTitle("ForkKnight - Re-seal the Newest Feat");
        dialog.setHeaderText("Fold the vanguard into '"
                + newest.shortHash() + "' and rewrite its words");
        Optional<SealWords> words = dialog.showAndWait();
        words.ifPresent(w -> runRealmAction("Re-seal",
                () -> chronicle.reSeal(w.summary(), w.body())));
    }

    private void banishSelected() {
        List<Dispatch> selected = List.copyOf(fieldTable.getSelectionModel().getSelectedItems());
        if (selected.isEmpty()) {
            return;
        }
        confirmDialog("Banish",
                "Banish changes in " + selected.size() + " path(s)?\nThis cannot be undone.",
                () -> runRealmAction("Banish", () -> {
                    for (Dispatch dispatch : selected) {
                        if (dispatch.staged()) {
                            chronicle.release(dispatch.path());
                        }
                        if (!dispatch.statusCode().equals("?")
                                && !dispatch.statusCode().equals("D")) {
                            chronicle.restore(dispatch.path());
                        } else if (dispatch.statusCode().equals("?")) {
                            chronicle.vanquishUnscouted(dispatch.path());
                        }
                    }
                }));
    }

    // ------------------------------------------------------------------
    // Shared plumbing
    // ------------------------------------------------------------------

    /** Runs a mutating chronicle action on a background thread, then refreshes. */
    private void runRealmAction(String name, RealmAction action) {
        runRealmAction(name, action, null);
    }

    private void runRealmAction(String name, RealmAction action, Runnable onDone) {
        if (chronicle == null) {
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
            statusBar.setText(name + " - done");
            musterTheField();
            surveyTrail(bannerBox.getSelectionModel().getSelectedItem());
            refreshKamuiButton();
            if (onDone != null) {
                onDone.run();
            }
        });
        task.setOnFailed(e -> showError(name + " faltered: "
                + task.getException().getMessage()));
        startDaemon(task, "realm-action");
    }

    @FunctionalInterface
    private interface RealmAction {
        void run() throws Exception;
    }

    private void showError(String message) {
        statusBar.setText(message);
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setHeaderText("ForkKnight");
        alert.show();
    }

    private Optional<String> nameDialog(String title, String header, String label) {
        return nameDialog(title, header, label, "");
    }

    private Optional<String> nameDialog(String title, String header, String label, String defaultValue) {
        TextInputDialog dialog = new TextInputDialog(defaultValue);
        dialog.setTitle("ForkKnight - " + title);
        dialog.setHeaderText(header);
        dialog.setContentText(label);
        return dialog.showAndWait().map(String::strip);
    }

    private void confirmDialog(String title, String message, Runnable onYes) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, message,
                ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText(title);
        confirm.showAndWait().filter(b -> b == ButtonType.YES).ifPresent(b -> onYes.run());
    }

    private static void startDaemon(Task<?> task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
    }
}
