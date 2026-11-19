package com.itc.healthtrack.controllers;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.itc.healthtrack.config.AppConfig;
import com.itc.healthtrack.dao.GenericDAO;
import com.itc.healthtrack.models.User;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.kordamp.bootstrapfx.BootstrapFX;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

// controlador de la pantalla de inicio de sesion
// tiene dos formularios uno para pacientes y admin y otro exclusivo para medicos que requiere token
public class LoginController {


    // formulario de paciente visible por defecto
    @FXML private TextField     emailField;
    @FXML private PasswordField passwordField;
    @FXML private Button        loginButton;
    @FXML private Label         errorLabel;

    // panel alterno de medico que reemplaza el formulario anterior al activarse
    @FXML private VBox          studentForm;
    @FXML private VBox          tutorPanel;
    @FXML private Button        btnToggleTutor;
    @FXML private TextField     tutorEmailField;
    @FXML private PasswordField tutorPasswordField;
    @FXML private TextField     tutorTokenField;
    @FXML private Button        tutorLoginButton;

    private final GenericDAO<User> userDAO = new GenericDAO<>(User.class, "users");

    // alterna entre el formulario de paciente y el panel de medico limpiando campos al cerrar
    @FXML
    protected void onToggleTutorPanel() {
        boolean showTutor = !tutorPanel.isVisible();

        tutorPanel.setVisible(showTutor);
        tutorPanel.setManaged(showTutor);
        studentForm.setVisible(!showTutor);
        studentForm.setManaged(!showTutor);

        if (!showTutor) {
            tutorEmailField.clear();
            tutorPasswordField.clear();
            tutorTokenField.clear();
            errorLabel.setVisible(false);
        }
    }

    // navega a la pantalla de registro manteniendo pantalla completa
    @FXML
    protected void onGoToRegister(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/itc/healthtrack/views/register-view.fxml"));
            Scene scene = new Scene(loader.load(), 800, 700);
            scene.getStylesheets().add(BootstrapFX.bootstrapFXStylesheet());
            scene.getStylesheets().add(getClass().getResource("/css/main.css").toExternalForm());
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(scene);
            stage.setFullScreen(true);
            stage.setFullScreenExitKeyCombination(javafx.scene.input.KeyCombination.NO_MATCH);
        } catch (IOException e) {
            e.printStackTrace();
            showError("Error al cargar la pantalla de registro.");
        }
    }

    // flujo de login para pacientes y admins autentica contra firebase y carga el perfil de firestore
    @FXML
    protected void onLoginButtonClick(ActionEvent event) {
        String email    = emailField.getText().trim();
        String password = passwordField.getText();

        if (email.isEmpty() || password.isEmpty()) {
            showError("Por favor, ingresa correo y contraseña.");
            return;
        }

        loginButton.setDisable(true);
        errorLabel.setVisible(false);

        new Thread(() -> {
            try {
                // autenticamos con firebase identity toolkit y obtenemos el uid
                String localId = signInWithEmailAndPassword(email, password);

                // traemos el perfil de firestore si no existe o falla regresamos null y avisamos
                User currentUser = loadProfileSafely(localId, loginButton);
                if (currentUser == null) return;

                Platform.runLater(() -> {
                    loginButton.setDisable(false);

                    String role = currentUser.getRole() != null ? currentUser.getRole() : "student";

                    // los medicos deben usar el panel de medico no este formulario
                    if ("tutor".equals(role)) {
                        showError("Las cuentas de tutor deben acceder desde el formulario \"Tutor\".");
                        return;
                    }

                    if ("student".equals(role)) {
                        loadDashboard(event, currentUser);
                    } else {
                        loadDashboard(event, currentUser);
                    }
                });

            } catch (FirebaseLoginException loginError) {
                showLoginError(loginButton, loginError.getMessage());
            } catch (Exception generalError) {
                System.err.println("[LoginController] Error inesperado ("
                        + generalError.getClass().getSimpleName() + "): " + generalError.getMessage());
                generalError.printStackTrace();
                showLoginError(loginButton, "Error de conexión. Verifica tu red e inténtalo de nuevo.");
            }
        }).start();
    }

    // flujo de login exclusivo para medicos requiere token valido ademas de credenciales
    @FXML
    protected void onTutorLoginButtonClick(ActionEvent event) {
        String email    = tutorEmailField.getText().trim();
        String password = tutorPasswordField.getText();
        String token    = tutorTokenField.getText().trim();

        if (email.isEmpty() || password.isEmpty() || token.isEmpty()) {
            showError("Por favor, ingresa correo, contraseña y código de acceso.");
            return;
        }

        if (!AppConfig.TOKEN_TUTOR.equals(token)) {
            showError("Código de acceso tutor incorrecto.");
            return;
        }

        tutorLoginButton.setDisable(true);
        errorLabel.setVisible(false);

        new Thread(() -> {
            try {
                // autenticamos con firebase identity toolkit y obtenemos el uid
                String localId = signInWithEmailAndPassword(email, password);

                // traemos el perfil de firestore si no existe o falla regresamos null y avisamos
                User currentUser = loadProfileSafely(localId, tutorLoginButton);
                if (currentUser == null) return;

                Platform.runLater(() -> {
                    tutorLoginButton.setDisable(false);

                    if (!"tutor".equals(currentUser.getRole())) {
                        showError("Esta cuenta no tiene rol de tutor en el sistema.");
                        return;
                    }

                    loadDashboard(event, currentUser);
                });

            } catch (FirebaseLoginException loginError) {
                showLoginError(tutorLoginButton, loginError.getMessage());
            } catch (Exception generalError) {
                System.err.println("[LoginController] Error inesperado ("
                        + generalError.getClass().getSimpleName() + "): " + generalError.getMessage());
                generalError.printStackTrace();
                showLoginError(tutorLoginButton, "Error de conexión. Verifica tu red e inténtalo de nuevo.");
            }
        }).start();
    }

    // valida correo y contraseña contra la tabla auth de PostgreSQL y regresa el uid
    private String signInWithEmailAndPassword(String email, String password) throws Exception {
        String uid = com.itc.healthtrack.config.Auth.login(email, password);
        if (uid == null) throw new FirebaseLoginException("Correo o contraseña incorrectos.");
        return uid;
    }

    // extrae el codigo de error del json que devuelve firebase cuando la autenticacion falla
    private String extractFirebaseErrorCode(String errorJson) {
        try {
            JsonObject root  = JsonParser.parseString(errorJson).getAsJsonObject();
            JsonObject error = root.getAsJsonObject("error");
            if (error != null && error.has("message")) {
                // firebase a veces agrega detalle despues de un espacio por ejemplo INVALID_EMAIL seguido de mas info
                return error.get("message").getAsString().split("\\s")[0];
            }
        } catch (Exception e) {
            System.err.println("[LoginController] No se pudo parsear el error JSON: " + errorJson);
        }
        return null;
    }

    private String readStream(InputStream inputStream) throws IOException {
        if (inputStream == null) return "";
        StringBuilder sb  = new StringBuilder();
        byte[] buffer = new byte[1024];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            sb.append(new String(buffer, 0, bytesRead, StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    // convierte los codigos de error de firebase a mensajes entendibles en espanol para mostrar al usuario
    private String parseLoginError(String errorCode) {
        if (errorCode == null) {
            return "Error al iniciar sesión. Verifica tu conexión e inténtalo de nuevo.";
        }
        switch (errorCode) {
            case "INVALID_PASSWORD":
            case "EMAIL_NOT_FOUND":
            case "INVALID_LOGIN_CREDENTIALS":
                return "Correo o contraseña incorrectos.";
            case "USER_DISABLED":
                return "Esta cuenta ha sido deshabilitada. Contacta al administrador.";
            case "TOO_MANY_ATTEMPTS_TRY_LATER":
                return "Demasiados intentos fallidos. Espera unos minutos e inténtalo de nuevo.";
            case "OPERATION_NOT_ALLOWED":
                return "El inicio de sesión con correo y contraseña no está habilitado en este proyecto.";
            case "WEAK_PASSWORD":
                return "La contraseña es demasiado débil.";
            case "INVALID_EMAIL":
                return "El formato del correo electrónico no es válido.";
            default:
                System.err.println("[LoginController] Código Firebase no reconocido: " + errorCode);
                return "Error al iniciar sesión (" + errorCode + "). Contacta al administrador.";
        }
    }

    // carga el dashboard tras un login exitoso pasandole el usuario y manteniendo pantalla completa
    private void loadDashboard(ActionEvent event, User user) {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(
                    getClass().getResource("/com/itc/healthtrack/views/dashboard-view.fxml"));
            Scene scene = new Scene(fxmlLoader.load(), 1000, 700);
            scene.getStylesheets().add(BootstrapFX.bootstrapFXStylesheet());
            scene.getStylesheets().add(getClass().getResource("/css/main.css").toExternalForm());

            DashboardController controller = fxmlLoader.getController();
            controller.initData(user);

            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(scene);
            stage.setFullScreen(true);
            stage.setFullScreenExitKeyCombination(javafx.scene.input.KeyCombination.NO_MATCH);
        } catch (IOException e) {
            e.printStackTrace();
            showError("Error crítico al cargar la interfaz principal.");
        }
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }

    // trae el perfil de firestore tras autenticar si no existe o falla avisa al usuario reactiva el boton y regresa null
    private User loadProfileSafely(String uid, Button button) {
        try {
            User profile = userDAO.getById(uid);
            if (profile == null) {
                Platform.runLater(() -> {
                    showError("Perfil no encontrado. Contacta al administrador.");
                    button.setDisable(false);
                });
            }
            return profile;
        } catch (Exception ex) {
            System.err.println("[LoginController] Error al leer perfil de Firestore ("
                    + ex.getClass().getSimpleName() + "): " + ex.getMessage());
            ex.printStackTrace();
            Platform.runLater(() -> {
                showError("Autenticación correcta, pero no se pudo cargar el perfil. Intenta de nuevo.");
                button.setDisable(false);
            });
            return null;
        }
    }

    // muestra el error y reactiva el boton en el hilo de fx sirve para ambos flujos de login sin duplicar codigo
    private void showLoginError(Button button, String message) {
        Platform.runLater(() -> {
            showError(message);
            button.setDisable(false);
        });
    }

    // excepcion interna para distinguir errores de autenticacion con mensaje conocido de errores de red o inesperados
    private static class FirebaseLoginException extends Exception {
        public FirebaseLoginException(String message) { super(message); }
    }
}
