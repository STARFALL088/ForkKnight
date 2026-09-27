package forkknight;

import forkknight.core.Background;
import forkknight.core.Beacon;
import forkknight.core.Beacon.FarFeat;
import forkknight.core.Beacon.FarReport;
import forkknight.core.JdkHttpGateway;
import java.util.List;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.concurrent.Task;

/**
 * The far call: reach across the wider realm and read what another world
 * answers about one remote realm - its report and its newest feats.
 *
 * <p>The request runs on a background thread (the FX thread never waits
 * on the network), and the answer or failure lands back in this dialog
 * through the task's success/failure handlers.
 */
public class BeaconDialog extends Dialog<Void> {

    private final Beacon beacon = new Beacon(new JdkHttpGateway());

    private final TextField holderField = new TextField();
    private final TextField realmField = new TextField();
    private final Button callBtn = new Button("Send the Beacon");
    private final Label notice = new Label();

    private final Label fullName = new Label();
    private final Label tale = new Label();
    private final Label stars = new Label();
    private final Label forks = new Label();
    private final Label issues = new Label();
    private final Label banner = new Label();
    private final Label sealed = new Label();

    private final ObservableList<FarFeat> feats = FXCollections.observableArrayList();

    public BeaconDialog() {
        setTitle("ForkKnight - The Far Call");
        setHeaderText("Reach across the wider realm and read what another world answers");

        holderField.setPromptText("the holder (e.g. STARFALL088)");
        realmField.setPromptText("the realm (e.g. ForkKnight)");

        GridPane form = new GridPane();
        form.setHgap(8);
        form.setVgap(8);
        form.add(new Label("Holder:"), 0, 0);
        form.add(holderField, 1, 0);
        form.add(new Label("Realm:"), 0, 1);
        form.add(realmField, 1, 1);
        HBox callRow = new HBox(callBtn);
        form.add(callRow, 2, 1);

        notice.setWrapText(true);

        GridPane summary = new GridPane();
        summary.setHgap(16);
        summary.setVgap(6);
        summary.add(new Label("Named:"), 0, 0);
        summary.add(fullName, 1, 0);
        summary.add(new Label("Told:"), 0, 1);
        summary.add(tale, 1, 1);
        summary.add(new Label("Stars:"), 0, 2);
        summary.add(stars, 1, 2);
        summary.add(new Label("Forks:"), 2, 2);
        summary.add(forks, 3, 2);
        summary.add(new Label("Open:"), 4, 2);
        summary.add(issues, 5, 2);
        summary.add(new Label("Banner:"), 0, 3);
        summary.add(banner, 1, 3);
        summary.add(new Label("Last sealed:"), 2, 3);
        summary.add(sealed, 3, 3);

        TableColumn<FarFeat, String> shaCol = new TableColumn<>("Feat");
        shaCol.setCellValueFactory(d -> new SimpleStringProperty(
            d.getValue().sha().substring(0, Math.min(7, d.getValue().sha().length()))));
        TableColumn<FarFeat, String> wordsCol = new TableColumn<>("Words");
        wordsCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().message()));
        TableColumn<FarFeat, String> heroCol = new TableColumn<>("Hero");
        heroCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().hero()));
        TableColumn<FarFeat, String> dayCol = new TableColumn<>("Day");
        dayCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().day()));
        TableView<FarFeat> table = new TableView<>(feats);
        table.getColumns().addAll(shaCol, wordsCol, heroCol, dayCol);
        shaCol.setPrefWidth(80);
        wordsCol.setPrefWidth(300);
        heroCol.setPrefWidth(140);
        dayCol.setPrefWidth(180);
        table.setPrefHeight(240);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_LAST_COLUMN);

        VBox content = new VBox(12, form, notice, new Separator(), summary, table);
        content.setPadding(new Insets(12));
        getDialogPane().setContent(content);
        getDialogPane().setPrefSize(720, 560);
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        callBtn.setOnAction(e -> send(holderField.getText().strip(),
            realmField.getText().strip()));
        holderField.setOnAction(e -> callBtn.fire());
        realmField.setOnAction(e -> callBtn.fire());
    }

    private void send(String holder, String realm) {
        clear();
        if (holder.isEmpty() || realm.isEmpty()) {
            refuse("Name both a holder and a realm to send the beacon.");
            return;
        }
        Task<Fetched> task = new Task<>() {
            @Override
            protected Fetched call() throws Exception {
                FarReport report = beacon.report(holder, realm);
                List<FarFeat> newest = beacon.newestFeats(holder, realm);
                return new Fetched(report, newest);
            }
        };
        task.setOnSucceeded(e -> show(task.getValue()));
        task.setOnFailed(e -> {
            Throwable cause = task.getException();
            if (cause instanceof InterruptedException) {
                refuse("The far call was cut off.");
            } else {
                refuse(cause == null ? "The far call failed."
                    : cause.getMessage());
            }
        });
        callBtn.setDisable(true);
        startDaemon(task, "far-call");
    }

    private void show(Fetched fetched) {
        callBtn.setDisable(false);
        FarReport report = fetched.report();
        fullName.setText(report.fullName());
        tale.setText(report.tale() == null ? "-" : report.tale());
        stars.setText(Long.toString(report.stars()));
        forks.setText(Long.toString(report.forks()));
        issues.setText(Long.toString(report.openIssues()));
        banner.setText(report.banner() == null ? "-" : report.banner());
        sealed.setText(report.lastSealed() == null ? "-" : report.lastSealed());
        feats.setAll(fetched.feats());
        notice.setStyle("-fx-text-fill: #7dcfff;");
        notice.setText("The wider realm answers.");
    }

    private void clear() {
        callBtn.setDisable(false);
        notice.setStyle("");
        notice.setText("");
        fullName.setText("-");
        tale.setText("-");
        stars.setText("-");
        forks.setText("-");
        issues.setText("-");
        banner.setText("-");
        sealed.setText("-");
        feats.clear();
    }

    private void refuse(String message) {
        callBtn.setDisable(false);
        notice.setStyle("-fx-text-fill: #f7768e; -fx-font-weight: bold;");
        notice.setText(message == null ? "The far call failed." : message);
    }

    private static void startDaemon(Task<?> task, String name) {
        Background.shared().start(task, name);
    }

    private record Fetched(FarReport report, List<FarFeat> feats) {
    }
}
