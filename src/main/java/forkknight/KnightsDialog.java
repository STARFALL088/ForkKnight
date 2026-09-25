package forkknight;

import forkknight.core.Account;
import forkknight.core.AccountService;
import forkknight.core.AuthException;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * The order of knights: local profiles that scope the knight's sight,
 * notes and bookmarks. Signing in is optional - the wanderer (guest)
 * rides without one - and nothing here ever touches a network.
 *
 * <p>From this dialog a knight can join the order, sign in (which is how
 * he switches to another knight - a password is required), sign out to
 * the wanderer, claim or change his password, or be dismissed.
 */
public class KnightsDialog extends Dialog<Void> {

    private final AccountService service;

    private final Label whRides = new Label();
    private final Label notice = new Label();

    private final TextField signInName = new TextField();
    private final PasswordField signInPassword = new PasswordField();

    private final TextField joinName = new TextField();
    private final TextField joinDisplay = new TextField();
    private final PasswordField joinPassword = new PasswordField();
    private final PasswordField joinConfirm = new PasswordField();

    private final Button signOutBtn = new Button("Sign Out (to the Wanderer)");
    private final Button passwordBtn = new Button("Set Password...");
    private final Button dismissBtn = new Button("Dismiss...");

    public KnightsDialog(AccountService service) {
        this.service = service;
        setTitle("ForkKnight - The Order of Knights");
        setHeaderText("Local knights - each keeps his own sight, notes and bookmarks");

        signInName.setPromptText("knight's name");
        signInPassword.setPromptText("password");
        signInName.setOnAction(e -> signIn());
        signInPassword.setOnAction(e -> signIn());
        Button signInBtn = new Button("Sign In");
        signInBtn.setOnAction(e -> signIn());

        joinName.setPromptText("name (3-24 chars)");
        joinDisplay.setPromptText("known as (optional)");
        joinPassword.setPromptText("password (8+ chars)");
        joinConfirm.setPromptText("repeat password");
        joinName.setOnAction(e -> signUp());
        joinPassword.setOnAction(e -> signUp());
        joinConfirm.setOnAction(e -> signUp());
        Button joinBtn = new Button("Join the Order");
        joinBtn.setOnAction(e -> signUp());

        signOutBtn.setOnAction(e -> signOut());
        passwordBtn.setOnAction(e -> changePassword());
        dismissBtn.setOnAction(e -> dismiss());

        GridPane signInGrid = form(
            row("Name:", signInName),
            row("Password:", signInPassword),
            row(null, signInBtn));
        GridPane joinGrid = form(
            row("Name:", joinName),
            row("Known as:", joinDisplay),
            row("Password:", joinPassword),
            row("Repeat:", joinConfirm),
            row(null, joinBtn));

        HBox thisKnight = new HBox(10, signOutBtn, passwordBtn, dismissBtn);
        notice.setWrapText(true);

        VBox root = new VBox(14,
            whRides,
            new Separator(),
            titled("Sign in / switch knights - a password is required", signInGrid),
            titled("Join the order - a new knight of your own", joinGrid),
            titled("This knight", thisKnight),
            notice);
        root.setPadding(new Insets(14));

        getDialogPane().setContent(root);
        getDialogPane().setPrefSize(540, 560);
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        refresh();
    }

    // -------------------- Actions --------------------

    private void signIn() {
        try {
            Account account = service.logIn(signInName.getText(), signInPassword.getText());
            signInName.clear();
            signInPassword.clear();
            succeed("Now riding as " + account.username() + ".");
        } catch (AuthException refused) {
            fail(refused);
        } catch (RuntimeException broken) {
            fail("The ledger refused: " + broken.getMessage());
        }
    }

    private void signUp() {
        try {
            Account account = service.signUp(joinName.getText(), joinDisplay.getText(),
                joinPassword.getText(), joinConfirm.getText());
            joinName.clear();
            joinDisplay.clear();
            joinPassword.clear();
            joinConfirm.clear();
            succeed("Welcome, " + account.username() + " - you ride as the new knight.");
        } catch (AuthException refused) {
            fail(refused);
        } catch (RuntimeException broken) {
            fail("The ledger refused: " + broken.getMessage());
        }
    }

    private void signOut() {
        try {
            service.logOut();
            succeed("The Wanderer rides now - no name and no ledger of his own.");
        } catch (RuntimeException broken) {
            fail("The ledger refused: " + broken.getMessage());
        }
    }

    /** Claims a locked ledger, or opens one to set the next password. */
    private void changePassword() {
        Account who = service.current();
        boolean claim = AccountService.locked(who);

        PasswordField current = new PasswordField();
        current.setPromptText("current password");
        PasswordField next = new PasswordField();
        next.setPromptText(claim ? "password (8+ chars)" : "new password (8+ chars)");
        PasswordField confirm = new PasswordField();
        confirm.setPromptText("repeat password");
        Label refusal = new Label();
        refusal.setWrapText(true);

        GridPane grid = form(
            claim ? null : row("Current:", current),
            row(claim ? "New password:" : "Password:", next),
            row("Repeat:", confirm));

        Dialog<Void> sub = new Dialog<>();
        sub.setTitle("ForkKnight - " + (claim ? "Claim the Ledger" : "Change Password"));
        sub.setHeaderText(claim
            ? who.username() + " holds a locked ledger - claim it with a password"
            : "Set the next password for " + who.username());
        sub.initOwner(getDialogPane().getScene().getWindow());
        sub.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);

        Runnable trySeal = () -> {
            try {
                service.changePassword(who.id(), claim ? null : current.getText(),
                    next.getText(), confirm.getText());
                sub.close();
                succeed(claim
                    ? "Ledger claimed - " + who.username() + " opens with that password now."
                    : "Password changed for " + who.username() + ".");
            } catch (AuthException refused) {
                // Stays open: the knight corrects the password right here.
                refusal.setStyle("-fx-text-fill: #f7768e; -fx-font-weight: bold;");
                refusal.setText(refused.getMessage());
            } catch (RuntimeException broken) {
                refusal.setStyle("-fx-text-fill: #f7768e; -fx-font-weight: bold;");
                refusal.setText("The ledger refused: " + broken.getMessage());
            }
        };
        next.setOnAction(e -> trySeal.run());
        confirm.setOnAction(e -> trySeal.run());

        Button sealBtn = new Button(claim ? "Claim the Ledger" : "Seal");
        sealBtn.setOnAction(e -> trySeal.run());
        grid.add(sealBtn, 1, grid.getRowCount());

        VBox subRoot = new VBox(10, grid, refusal);
        subRoot.setPadding(new Insets(12));
        sub.getDialogPane().setContent(subRoot);
        sub.showAndWait();
    }

    private void dismiss() {
        Account who = service.current();
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Dismiss " + who.username() + "?\nHis settings, notes and bookmarks fall with him.",
            ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText("Dismiss " + who.username());
        confirm.initOwner(getDialogPane().getScene().getWindow());
        confirm.showAndWait().filter(b -> b == ButtonType.YES).ifPresent(b -> {
            try {
                service.deleteAccount(who.id());
                succeed(who.username() + " is dismissed - the Wanderer rides now.");
            } catch (RuntimeException broken) {
                fail("The ledger refused: " + broken.getMessage());
            }
        });
    }

    // -------------------- State --------------------

    private void refresh() {
        Account who = service.current();
        whRides.setText(describe(who));
        boolean guest = who.guest();
        signOutBtn.setDisable(guest);
        passwordBtn.setDisable(guest);
        dismissBtn.setDisable(guest);
    }

    /** Who occupies the seat right now, lock and all. */
    static String describe(Account who) {
        if (who.guest()) {
            return "Who rides now: the Wanderer - no name, no password, no ledger of his own";
        }
        if (AccountService.locked(who)) {
            return "Who rides now: " + who.username()
                + " - holds a locked ledger; set a password to claim it";
        }
        String known = who.displayName() != null && !who.displayName().isBlank()
            && !who.displayName().equals(who.username())
            ? " (" + who.displayName() + ")" : "";
        return "Who rides now: " + who.username() + known;
    }

    private void succeed(String message) {
        notice.setStyle("-fx-text-fill: #7dcfff;");
        notice.setText(message);
        refresh();
    }

    private void fail(AuthException refused) {
        fail(refused.getMessage());
    }

    private void fail(String message) {
        notice.setStyle("-fx-text-fill: #f7768e; -fx-font-weight: bold;");
        notice.setText(message == null ? "The ledger refused." : message);
    }

    // -------------------- Form building --------------------

    private static final class Row {
        final String label;
        final Node field;

        Row(String label, Node field) {
            this.label = label;
            this.field = field;
        }
    }

    private static Row row(String label, Node field) {
        return new Row(label, field);
    }

    private static GridPane form(Row... rows) {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        int at = 0;
        for (Row r : rows) {
            if (r == null) {
                continue;
            }
            if (r.label != null) {
                grid.add(new Label(r.label), 0, at);
            }
            grid.add(r.field, 1, at);
            at++;
        }
        return grid;
    }

    private static VBox titled(String title, Node content) {
        Label header = new Label(title);
        header.setStyle("-fx-font-weight: bold;");
        VBox box = new VBox(6, header, content);
        box.setPadding(new Insets(8));
        box.setStyle("-fx-background-color: rgba(255,255,255,0.04);"
            + "-fx-background-radius: 6;");
        return box;
    }
}
