package forkknight;

import forkknight.core.Banner;
import forkknight.core.Chronicle;
import forkknight.core.Dispatch;
import forkknight.core.Feat;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
    private TableView<Weave.Woven> chronicleTable;
    private TextField realmPathField;
    private final ObservableList<Weave.Woven> chronicleData =
            FXCollections.observableArrayList();
    private Label statusBar;
    private ComboBox<String> bannerBox;
    private TextField scryField;
    private ComboBox<Scryer.Scope> scryScopeBox;
    private ComboBox<String> sigilBox;
    private Button summonKamuiBtn;
    private Scene mainScene;
    private boolean darkTheme = true;

    private TalePane talePane;

    private TableView<Dispatch> fieldTable;
    private final ObservableList<Dispatch> fieldData = FXCollections.observableArrayList();

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("ForkKnight - Scroll of the Realm");

        // ----- realm chooser row -----
        Label realmLabel = new Label("Realm:");
        realmPathField = new TextField();
        realmPathField.setPromptText("Choose a realm to serve");
        realmPathField.setEditable(false);
        Button chooseRealmBtn = new Button("Seek...");
        chooseRealmBtn.setOnAction(e -> seekRealm());
        HBox realmBox = new HBox(10, realmLabel, realmPathField, chooseRealmBtn);
        realmBox.setPadding(new Insets(10));
        HBox.setHgrow(realmPathField, javafx.scene.layout.Priority.ALWAYS);

        // ----- banner + sigil row -----
        Label bannerLabel = new Label("Banner:");
        bannerBox = new ComboBox<>();
        bannerBox.setPromptText("Raised banner");
        bannerBox.setDisable(true);
        bannerBox.setPrefWidth(150);
        bannerBox.setOnAction(e -> {
            String banner = bannerBox.getSelectionModel().getSelectedItem();
            if (banner != null && chronicle != null) {
                surveyTrail(banner);
            }
        });

        Button raiseBannerBtn = new Button("Raise...");
        raiseBannerBtn.setDisable(true);
        raiseBannerBtn.setOnAction(e -> raiseBannerDialog());
        Button marchBtn = new Button("March");
        marchBtn.setDisable(true);
        marchBtn.setOnAction(e -> marchToSelectedBanner());
        Button fellBtn = new Button("Fell...");
        fellBtn.setDisable(true);
        fellBtn.setOnAction(e -> fellSelectedBanner());
        bannerBox.disableProperty().addListener((obs, was, is) -> {
            boolean off = is;
            raiseBannerBtn.setDisable(off);
            marchBtn.setDisable(off);
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

        HBox bannerRow = new HBox(10, bannerLabel, bannerBox, raiseBannerBtn, marchBtn, fellBtn,
                new Separator(),
                sigilLabel, sigilBox, pressSigilBtn, meltSigilBtn);
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
        taleCol.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().feat().isFusion()
                        ? cell.getValue().feat().summary() + "  \u2694 fusion"
                        : cell.getValue().feat().summary()));
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
        Button clearScryBtn = new Button("Still");
        clearScryBtn.setOnAction(e -> {
            scryField.clear();
            scryField.requestFocus();
        });
        HBox scryRow = new HBox(8, scryLabel, scryField, scryScopeBox, clearScryBtn);
        scryRow.setPadding(new Insets(5, 10, 5, 10));

        talePane = new TalePane();

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

        TabPane tabs = new TabPane();
        Tab scrollTab = new Tab("Scroll");
        scrollTab.setClosable(false);
        scrollTab.setContent(chronicleTab);
        Tab fieldTab = new Tab("The Field");
        fieldTab.setClosable(false);
        fieldTab.setContent(buildFieldView());
        tabs.getTabs().addAll(scrollTab, fieldTab);
        tabs.getSelectionModel().selectedItemProperty().addListener((obs, o, newTab) -> {
            if (newTab == fieldTab) {
                musterTheField();
            }
        });

        BorderPane root = new BorderPane();
        root.setTop(new VBox(buildMenuBar(), top));
        root.setCenter(tabs);
        root.setBottom(statusBar);

        mainScene = new Scene(root, 1100, 750);
        primaryStage.setScene(mainScene);
        applyTheme();
        primaryStage.show();
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
        Menu realmMenu = new Menu("Realm");
        MenuItem seekItem = new MenuItem("Seek Realm...");
        seekItem.setOnAction(e -> seekRealm());
        MenuItem quitItem = new MenuItem("Depart");
        quitItem.setOnAction(e -> javafx.application.Platform.exit());
        realmMenu.getItems().addAll(seekItem, new SeparatorMenuItem(), quitItem);

        Menu viewMenu = new Menu("Sight");
        ToggleGroup themeGroup = new ToggleGroup();
        RadioMenuItem darkItem = new RadioMenuItem("Night Sight");
        RadioMenuItem lightItem = new RadioMenuItem("Day Sight");
        darkItem.setToggleGroup(themeGroup);
        lightItem.setToggleGroup(themeGroup);
        darkItem.setSelected(darkTheme);
        darkItem.setOnAction(e -> setTheme(true));
        lightItem.setOnAction(e -> setTheme(false));
        viewMenu.getItems().addAll(darkItem, lightItem);

        MenuBar menuBar = new MenuBar();
        menuBar.getMenus().addAll(realmMenu, viewMenu);
        return menuBar;
    }

    private void setTheme(boolean dark) {
        darkTheme = dark;
        applyTheme();
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

    private void openRealm(File dir) {
        Chronicle candidate = new Chronicle(dir);
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                candidate.validateRealm();
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            chronicle = candidate;
            realmPathField.setText(dir.getAbsolutePath());
            loadBanners();
            loadSigils();
            surveyTrail(null);
            refreshKamuiButton();
        });
        task.setOnFailed(e -> showError("This land answers to no realm: "
                + task.getException().getMessage()));
        new Thread(task, "realm-verify").start();
    }

    private void loadBanners() {
        Chronicle service = chronicle;
        Task<List<Banner>> task = new Task<>() {
            @Override
            protected List<Banner> call() throws Exception {
                return service.banners();
            }
        };
        task.setOnSucceeded(e -> {
            bannerBox.getItems().setAll(task.getValue().stream()
                    .map(Banner::name).toList());
            String active = task.getValue().stream()
                    .filter(Banner::active).findFirst()
                    .map(Banner::name).orElse(null);
            if (active != null) {
                bannerBox.getSelectionModel().select(active);
            } else if (!bannerBox.getItems().isEmpty()) {
                bannerBox.getSelectionModel().selectFirst();
            }
            bannerBox.setDisable(bannerBox.getItems().isEmpty());
        });
        task.setOnFailed(e -> bannerBox.setDisable(true));
        startDaemon(task, "banner-load");
    }

    private void surveyTrail(String banner) {
        chronicleData.clear();
        talePane.reset();
        chronicleTable.setPlaceholder(new Label("Unrolling the scroll..."));
        statusBar.setText("Surveying the trail...");

        Chronicle service = chronicle;
        Task<Weave> task = new Task<>() {
            @Override
            protected Weave call() throws Exception {
                List<Feat> feats = service.surveyTrail(banner, Integer.MAX_VALUE);
                // Index the fresh trail for scrying; built on the worker
                // thread so the UI never stalls on big realms.
                scryer = new Scryer(feats);
                return Weave.of(feats);
            }
        };
        task.setOnSucceeded(e -> {
            chronicleData.setAll(task.getValue().rows());
            applyScrying();
            if (chronicleData.isEmpty()) {
                chronicleTable.setPlaceholder(new Label("The chronicle is empty."));
            } else {
                chronicleTable.setPlaceholder(null);
            }
        });
        task.setOnFailed(e -> showError("The survey failed: "
                + task.getException().getMessage()));
        startDaemon(task, "trail-survey");
    }

    // ------------------------------------------------------------------
    // Scrying (search) via the Scryer index
    // ------------------------------------------------------------------

    private Scryer scryer;

    private void applyScrying() {
        String query = scryField.getText();
        if (scryer == null) {
            return;
        }
        Scryer.Scope scope = scryScopeBox.getValue();
        if (query == null || query.isBlank()) {
            chronicleTable.setItems(chronicleData);
            statusBar.setText(chronicleData.size() + " feats");
            return;
        }
        Predicate<Weave.Woven> predicate = woven ->
                scryer.predicateFor(query, scope).test(woven.feat());
        chronicleTable.setItems(chronicleData.filtered(predicate::test));
        int shown = chronicleTable.getItems().size();
        statusBar.setText(shown + " of " + chronicleData.size()
                + " feats answer \"" + query.strip() + "\"");
        if (shown == 0 && !chronicleData.isEmpty()) {
            chronicleTable.setPlaceholder(new Label("The scry found nothing."));
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
        Task<List<Sigil>> task = new Task<>() {
            @Override
            protected List<Sigil> call() throws Exception {
                return service.sigils();
            }
        };
        task.setOnSucceeded(e -> sigilBox.getItems().setAll(task.getValue().stream()
                .map(Sigil::name).toList()));
        task.setOnFailed(e -> sigilBox.getItems().clear());
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
        Button banishBtn = new Button("Banish...");
        banishBtn.setOnAction(e -> banishSelected());
        Button kamuiBtn = new Button("Kamui");
        kamuiBtn.setOnAction(e -> vanishIntoKamui());
        summonKamuiBtn = new Button("Summon");
        summonKamuiBtn.setDisable(true);
        summonKamuiBtn.setOnAction(e -> summonFromKamui());

        HBox buttons = new HBox(8, musterBtn, new Separator(),
                enlistBtn, releaseBtn, enlistAllBtn, new Separator(),
                sealBtn, banishBtn, new Separator(),
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
        TextInputDialog dialog = new TextInputDialog();
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
