package com.itc.healthtrack.controllers;

import com.itc.healthtrack.config.AppConfig;
import com.itc.healthtrack.config.Auth;
import com.itc.healthtrack.dao.GenericDAO;
import com.itc.healthtrack.models.User;
import com.itc.healthtrack.utils.DialogUtils;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.kordamp.bootstrapfx.BootstrapFX;

// controlador de registro: roles Estudiante y Tutor (maestro o alumno)
// los tutores necesitan el codigo de acceso definido en AppConfig
// los maestros no tienen carrera
public class RegisterController {

    @FXML private TextField        txtFirstName;
    @FXML private TextField        txtLastName;
    @FXML private TextField        txtEmail;
    @FXML private ComboBox<String> comboCareer;
    @FXML private PasswordField    txtPassword;
    @FXML private PasswordField    txtConfirmPassword;
    @FXML private ComboBox<String> comboGender;   // tipo de tutor: Maestro / Alumno
    @FXML private ComboBox<String> comboRole;

    @FXML private VBox      tokenSection;
    @FXML private Label     lblTokenLabel;
    @FXML private TextField txtToken;

    @FXML private Label  lblStatus;
    @FXML private Button btnRegister;

    private GenericDAO<User> userDAO;

    // se crea al primer uso para que la pantalla abra aunque la base de datos no responda
    private GenericDAO<User> dao() {
        if (userDAO == null) userDAO = new GenericDAO<>(User.class, "users");
        return userDAO;
    }

    @FXML
    public void initialize() {
        comboGender.getItems().addAll("Maestro", "Alumno");
        comboRole.getItems().addAll("Estudiante", "Tutor");
        comboCareer.getItems().addAll(
                "Ingeniería en Sistemas Computacionales",
                "Ingeniería en Gestión Empresarial",
                "Ingeniería Ambiental",
                "Ingeniería Bioquímica",
                "Ingeniería Electrónica",
                "Ingeniería Industrial",
                "Ingeniería Mecánica",
                "Ingeniería Mecatrónica",
                "Ingeniería Química",
                "Ingeniería en Semiconductores",
                "Ingeniería Biomédica",
                "Licenciatura en Administración");

        // el codigo de acceso solo se muestra para tutores
        tokenSection.setVisible(false);
        tokenSection.setManaged(false);
        lblTokenLabel.setText("Código de acceso tutor:");

        comboRole.valueProperty().addListener((o, a, b) -> actualizarCampos());
        comboGender.valueProperty().addListener((o, a, b) -> actualizarCampos());
        comboRole.setValue("Estudiante");
        actualizarCampos();
    }

    // muestra u oculta lo que depende del rol y del tipo de tutor
    private void actualizarCampos() {
        boolean esTutor   = "Tutor".equals(comboRole.getValue());
        boolean esMaestro = esTutor && "Maestro".equals(comboGender.getValue());

        tokenSection.setVisible(esTutor);
        tokenSection.setManaged(esTutor);
        if (!esTutor) txtToken.clear();

        comboGender.setDisable(!esTutor);
        if (!esTutor) comboGender.setValue(null);

        comboCareer.setDisable(esMaestro);
        if (esMaestro) comboCareer.setValue(null);
    }

    @FXML
    protected void onRegister(ActionEvent event) {

        // leemos la ui en el hilo fx antes de lanzar el hilo de fondo
        final String firstName  = txtFirstName.getText().trim();
        final String lastName   = txtLastName.getText().trim();
        final String email      = txtEmail.getText().trim().toLowerCase();
        final String password   = txtPassword.getText();
        final String confirm    = txtConfirmPassword.getText();
        final String tutorKind  = comboGender.getValue();
        final String roleLabel  = comboRole.getValue();
        final String tokenInput = txtToken.getText().trim();
        final String career     = comboCareer.getValue() == null ? "" : comboCareer.getValue();

        // validaciones basicas
        if (firstName.isEmpty() || lastName.isEmpty() || email.isEmpty() || password.isEmpty()) {
            showStatus("Por favor, completa todos los campos obligatorios.", false);
            return;
        }
        if (!password.equals(confirm)) {
            showStatus("Las contraseñas no coinciden.", false);
            return;
        }
        if (password.length() < 6) {
            showStatus("La contraseña debe tener al menos 6 caracteres.", false);
            return;
        }
        if (roleLabel == null || roleLabel.isEmpty()) {
            showStatus("Por favor, selecciona un rol.", false);
            return;
        }

        final boolean esTutor   = "Tutor".equals(roleLabel);
        final boolean esMaestro = esTutor && "Maestro".equals(tutorKind);
        final String  role      = esTutor ? "tutor" : "student";

        // codigo de acceso para tutores
        if (esTutor) {
            if (tokenInput.isEmpty()) {
                showTokenAlert("Se requiere el código de acceso de tutor para registrarse como Tutor.\n"
                        + "Solicítalo al administrador del sistema.");
                return;
            }
            if (!AppConfig.TOKEN_TUTOR.equals(tokenInput)) {
                showTokenAlert("Código de acceso tutor incorrecto.\n"
                        + "Verifica el código e intenta de nuevo.");
                return;
            }
            if (tutorKind == null) {
                showStatus("Selecciona el tipo de tutor (Maestro o Alumno).", false);
                return;
            }
        }

        // los maestros no tienen carrera; los demas si
        if (!esMaestro && career.isEmpty()) {
            showStatus("Selecciona tu carrera.", false);
            return;
        }

        btnRegister.setDisable(true);

        new Thread(() -> {
            try {
                // cuenta (correo + contraseña cifrada) en la tabla auth de PostgreSQL
                String uid = Auth.register(email, password);

                // perfil en la tabla users, sin la contraseña
                User profile = new User();
                profile.setUid(uid);
                profile.setEmail(email);
                profile.setFirstName(firstName);
                profile.setLastName(lastName);
                profile.setRole(role);
                profile.setCareer(esMaestro ? null : career);
                if (esTutor) profile.setTutorType(esMaestro ? "teacher" : "student");

                dao().save(uid, profile);
                System.out.println("[RegisterController] Perfil guardado — UID: " + uid + ", rol: " + role);

                Platform.runLater(() -> {
                    String displayRole = !esTutor ? "Estudiante" : (esMaestro ? "Tutor (Maestro)" : "Tutor (Alumno)");
                    showStatus("¡Cuenta creada como " + displayRole + "! Redirigiendo al login...", true);
                    btnRegister.setDisable(false);
                    new Thread(() -> {
                        try { Thread.sleep(1500); }
                        catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                        Platform.runLater(() -> goToLogin(event));
                    }).start();
                });

            } catch (Exception ex) {
                ex.printStackTrace();
                Platform.runLater(() -> {
                    showStatus("Error al crear la cuenta: " + ex.getMessage(), false);
                    btnRegister.setDisable(false);
                });
            }
        }).start();
    }

    // alerta de codigo invalido con el estilo de la app
    private void showTokenAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Código de Acceso Requerido");
        alert.setHeaderText("Verificación de seguridad fallida");
        alert.setContentText(message);
        DialogUtils.applyWhiteStyle(alert.getDialogPane());
        alert.showAndWait();
    }

    @FXML
    protected void onGoToLogin(ActionEvent event) {
        goToLogin(event);
    }

    private void goToLogin(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/itc/healthtrack/views/login-view.fxml"));
            Scene scene = new Scene(loader.load(), 960, 620);
            scene.getStylesheets().add(BootstrapFX.bootstrapFXStylesheet());
            scene.getStylesheets().add(getClass().getResource("/css/main.css").toExternalForm());
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(scene);
            stage.setFullScreen(true);
            stage.setFullScreenExitKeyCombination(javafx.scene.input.KeyCombination.NO_MATCH);
        } catch (Exception e) {
            e.printStackTrace();
            showStatus("Error al volver al login.", false);
        }
    }

    private void showStatus(String message, boolean isSuccess) {
        lblStatus.setText(message);
        lblStatus.setTextFill(isSuccess ? Color.web("#4caf50") : Color.web("#ff5252"));
        lblStatus.setVisible(true);
    }
}