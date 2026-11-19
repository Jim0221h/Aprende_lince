package com.itc.healthtrack.controllers;

import com.itc.healthtrack.models.User;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.kordamp.bootstrapfx.BootstrapFX;

import java.io.IOException;
import java.util.function.Supplier;

// menu lateral dinamico segun el rol: admin, tutor (maestro o alumno) o student
public class DashboardController {

    @FXML private Label userNameLabel;
    @FXML private Label roleLabel;
    @FXML private VBox contentArea;
    @FXML private VBox menuBox;

    public void initData(User user) {
        String role = user.getRole() != null ? user.getRole() : "student";
        boolean tutor = "tutor".equals(role), admin = "admin".equals(role);
        boolean est = !tutor && !admin;
        boolean alumnoTutor = tutor && !"teacher".equals(user.getTutorType());
        userNameLabel.setText(user.getFirstName() + " " + user.getLastName());
        roleLabel.setText(admin ? "Administrador" : tutor ? ("teacher".equals(user.getTutorType()) ? "Tutor (Maestro)" : "Tutor (Alumno)") : "Estudiante");

        add("Inicio", () -> est ? Modules.homeStudent(user) : tutor ? Modules.homeTutor(user) : Modules.home(user));
        add("Asesorías", () -> est ? Modules.advisoriesStudent(user) : tutor ? Modules.advisoriesTutor(user) : Modules.advisories(user));
        add("Horarios", () -> Modules.slots(user));
        add("Recados", () -> Modules.notes(user));
        add("Actividades", () -> Modules.tasks(user));
        add("Materias", () -> est ? Modules.subjectsStudent(user) : tutor ? Modules.subjectsTutor(user) : Modules.subjects(user));
        if (alumnoTutor || admin) add("Créditos", () -> Modules.credits(user));
        if (admin) add("Usuarios", () -> Modules.users(user));
        show(() -> est ? Modules.homeStudent(user) : tutor ? Modules.homeTutor(user) : Modules.home(user));
    }

    private void add(String text, Supplier<Node> view) {
        Button b = new Button(text);
        b.setMaxWidth(Double.MAX_VALUE);
        b.getStyleClass().addAll("btn", "btn-default");
        b.setOnAction(e -> show(view));
        menuBox.getChildren().add(b);
    }

    private void show(Supplier<Node> view) {
        contentArea.getChildren().setAll(view.get());
    }

    // cierra la sesion del usuario y regresa a la pantalla de login manteniendo pantalla completa
    @FXML
    protected void onLogout(ActionEvent event) {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/com/itc/healthtrack/views/login-view.fxml"));
            Scene loginScene = new Scene(fxmlLoader.load(), 960, 620);

            loginScene.getStylesheets().add(BootstrapFX.bootstrapFXStylesheet());
            String cssPath = getClass().getResource("/css/main.css").toExternalForm();
            loginScene.getStylesheets().add(cssPath);

            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(loginScene);
            stage.setFullScreen(true);
            stage.setFullScreenExitKeyCombination(javafx.scene.input.KeyCombination.NO_MATCH);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}