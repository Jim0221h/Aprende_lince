package com.itc.healthtrack.controllers;

import com.itc.healthtrack.dao.GenericDAO;
import com.itc.healthtrack.models.Models;
import com.itc.healthtrack.models.User;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;

import java.io.File;
import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.stream.Collectors;

// vistas de cada modulo (se arman por codigo para mantener el proyecto compacto)
public class Modules {

    static final List<String> DAYS = List.of("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado");
    static final List<String> TIMES = List.of("08:00", "09:00", "10:00", "11:00", "12:00", "13:00", "14:00", "15:00", "16:00", "17:00", "18:00", "19:00");

    private interface Job { void run() throws Exception; }

    // ---------- helpers ----------
    private static void bg(Label st, Job j) {
        new Thread(() -> { try { j.run(); } catch (Exception e) { e.printStackTrace(); Platform.runLater(() -> msg(st, "Error: " + e.getMessage(), false)); } }).start();
    }
    private static void msg(Label l, String t, boolean ok) { l.setText(t); l.setTextFill(Color.web(ok ? "#4caf50" : "#ff9800")); }
    private static <T> void load(Label st, ObservableList<T> list, Callable<List<T>> c) {
        bg(st, () -> { List<T> r = c.call(); Platform.runLater(() -> list.setAll(r)); });
    }
    private static <T> void fill(Label st, ComboBox<T> cb, Callable<List<T>> c) {
        bg(st, () -> { List<T> r = c.call(); Platform.runLater(() -> cb.setItems(FXCollections.observableArrayList(r))); });
    }
    private static VBox page(String title, String sub, Node... n) {
        VBox v = new VBox(14); v.setPadding(new Insets(28)); v.setStyle("-fx-background-color: #0f1f18;");
        Label a = new Label(title); a.setTextFill(Color.WHITE); a.setStyle("-fx-font-size: 26px; -fx-font-weight: bold;");
        Label b = new Label(sub); b.setTextFill(Color.web("#aaaaaa"));
        v.getChildren().addAll(a, b); v.getChildren().addAll(n); return v;
    }
    private static HBox row(Node... n) { HBox h = new HBox(8, n); h.setAlignment(Pos.CENTER_LEFT); return h; }
    private static Button btn(String t, String cls, Runnable r) { Button b = new Button(t); b.getStyleClass().addAll("btn", cls); b.setOnAction(e -> r.run()); return b; }
    private static TextField tf(String p) { TextField t = new TextField(); t.setPromptText(p); return t; }
    private static <T> ComboBox<T> cb(String p) { ComboBox<T> c = new ComboBox<>(); c.setPromptText(p); return c; }
    private static <T> TableView<T> table(ObservableList<T> items) {
        TableView<T> t = new TableView<>(items); t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY); VBox.setVgrow(t, Priority.ALWAYS); return t;
    }
    private static <T> void col(TableView<T> t, String title, Function<T, String> f) {
        TableColumn<T, String> c = new TableColumn<>(title);
        c.setCellValueFactory(x -> new SimpleStringProperty(Objects.toString(f.apply(x.getValue()), "")));
        t.getColumns().add(c);
    }
    private static String role(User u) { return u.getRole() == null ? "student" : u.getRole(); }
    private static String name(User u) { return u.getFirstName() + " " + u.getLastName(); }
    private static String nz(String s) { return s == null || s.isBlank() ? "—" : s; }

    // prototipo: datos escolares y laborales simulados a partir del uid
    // en la version real vendrian del sistema escolar; si el usuario ya tiene el dato guardado, se usa ese
    private static String simControl(User u) {
        if (u.getControlNumber() != null && !u.getControlNumber().isBlank()) return u.getControlNumber();
        int h = u.getUid().hashCode();
        return "2" + (2 + Math.floorMod(h, 4)) + "03" + String.format("%04d", Math.floorMod(h, 10000));
    }
    private static String simSemester(User u) {
        if (u.getSemester() != null && !u.getSemester().isBlank()) return u.getSemester();
        return String.valueOf(1 + Math.floorMod(u.getUid().hashCode(), 9));
    }
    private static String simPromedio(User u) {
        return String.format("%.1f", 80 + Math.floorMod(u.getUid().hashCode(), 19) / 2.0);
    }
    private static String simDepto(User u) {
        String[] d = {"Sistemas y Computación", "Ciencias Básicas", "Ingeniería Industrial",
                "Ingeniería Química y Bioquímica", "Ingeniería Eléctrica y Electrónica", "Ciencias Económico-Administrativas"};
        return d[Math.floorMod(u.getUid().hashCode(), d.length)];
    }
    private static String simPlaza(User u) { return Math.floorMod(u.getUid().hashCode(), 2) == 0 ? "Planta" : "Honorarios"; }
    private static String simGrado(User u) { String[] g = {"Licenciatura", "Maestría", "Doctorado"}; return g[Math.floorMod(u.getUid().hashCode(), 3)]; }

    // materias habilitadas (simuladas): los maestros las han impartido; los alumnos tutores pasaron encuesta y examen
    private static final String[] GENERAL = {"Cálculo Diferencial", "Álgebra Lineal", "Cálculo Integral", "Física General"};
    private static final Map<String, String[]> POR_CARRERA = Map.ofEntries(
            Map.entry("Ingeniería en Sistemas Computacionales", new String[]{"Cálculo Diferencial", "Programación Orientada a Objetos", "Estructura de Datos", "Fundamentos de Bases de Datos", "Álgebra Lineal", "Redes de Computadoras"}),
            Map.entry("Ingeniería en Gestión Empresarial", new String[]{"Contabilidad Financiera", "Administración de la Producción", "Estadística Descriptiva", "Costos Empresariales", "Economía Empresarial"}),
            Map.entry("Ingeniería Ambiental", new String[]{"Química Ambiental", "Ecología", "Cálculo Integral", "Hidrología", "Química Orgánica"}),
            Map.entry("Ingeniería Bioquímica", new String[]{"Bioquímica", "Química Orgánica", "Cálculo Integral", "Microbiología", "Fisicoquímica"}),
            Map.entry("Ingeniería Electrónica", new String[]{"Circuitos Eléctricos", "Electrónica Analógica", "Cálculo Vectorial", "Electromagnetismo", "Física General"}),
            Map.entry("Ingeniería Industrial", new String[]{"Estadística Inferencial", "Investigación de Operaciones", "Física General", "Cálculo Integral", "Control de Calidad"}),
            Map.entry("Ingeniería Mecánica", new String[]{"Estática", "Dinámica", "Termodinámica", "Cálculo Vectorial", "Mecánica de Materiales"}),
            Map.entry("Ingeniería Mecatrónica", new String[]{"Física General", "Cálculo Diferencial", "Programación Orientada a Objetos", "Electrónica Analógica", "Control Automático"}),
            Map.entry("Ingeniería Química", new String[]{"Química Orgánica", "Fisicoquímica", "Balance de Materia y Energía", "Ecuaciones Diferenciales", "Termodinámica"}),
            Map.entry("Ingeniería en Materiales", new String[]{"Ciencia de Materiales", "Química Orgánica", "Cálculo Diferencial", "Física General", "Fisicoquímica"}),
            Map.entry("Ingeniería Biomédica", new String[]{"Anatomía y Fisiología", "Física General", "Cálculo Diferencial", "Electrónica Analógica", "Bioquímica"}),
            Map.entry("Licenciatura en Administración", new String[]{"Contabilidad Financiera", "Economía Empresarial", "Estadística Descriptiva", "Mercadotecnia", "Administración de la Producción"}));
    private static final Map<String, String[]> POR_DEPTO = Map.of(
            "Sistemas y Computación", new String[]{"Programación Orientada a Objetos", "Estructura de Datos", "Fundamentos de Bases de Datos", "Redes de Computadoras", "Álgebra Lineal"},
            "Ciencias Básicas", new String[]{"Cálculo Diferencial", "Cálculo Integral", "Cálculo Vectorial", "Álgebra Lineal", "Ecuaciones Diferenciales", "Física General"},
            "Ingeniería Industrial", new String[]{"Estadística Inferencial", "Investigación de Operaciones", "Control de Calidad", "Estadística Descriptiva", "Costos Empresariales"},
            "Ingeniería Química y Bioquímica", new String[]{"Química Orgánica", "Fisicoquímica", "Balance de Materia y Energía", "Bioquímica", "Microbiología"},
            "Ingeniería Eléctrica y Electrónica", new String[]{"Circuitos Eléctricos", "Electrónica Analógica", "Electromagnetismo", "Control Automático", "Física General"},
            "Ciencias Económico-Administrativas", new String[]{"Contabilidad Financiera", "Economía Empresarial", "Administración de la Producción", "Mercadotecnia", "Estadística Descriptiva"});

    // materias en las que el tutor esta habilitado (4 el maestro, 3 el alumno tutor)
    private static List<String> simMaterias(User u) {
        boolean maestro = "teacher".equals(u.getTutorType());
        String[] base = maestro ? POR_DEPTO.getOrDefault(simDepto(u), GENERAL)
                : (u.getCareer() == null ? GENERAL : POR_CARRERA.getOrDefault(u.getCareer(), GENERAL));
        int start = Math.floorMod(u.getUid().hashCode(), base.length);
        List<String> r = new ArrayList<>();
        for (int i = 0; i < Math.min(maestro ? 4 : 3, base.length); i++) r.add(base[(start + i) % base.length]);
        return r;
    }
    // calificacion simulada del examen de conocimientos del alumno tutor (85 a 100)
    private static int simExamen(User u, String materia) { return 85 + Math.floorMod((u.getUid() + materia).hashCode(), 16); }
    private static String habilitadas(User u) {
        return simMaterias(u).stream().map(m -> m + " (" + simExamen(u, m) + ")").collect(Collectors.joining(" · "));
    }

    // asesoria pendiente o confirmada que aun no pasa (vista del estudiante)
    private static boolean upcoming(Models.Advisory x) {
        return ("Pendiente".equals(x.status) || "Confirmada".equals(x.status)) && x.date.compareTo(LocalDate.now().toString()) >= 0;
    }
    // asesoria que el tutor aun debe atender, sin importar la fecha
    private static boolean open(Models.Advisory x) { return "Pendiente".equals(x.status) || "Confirmada".equals(x.status); }

    // registro de asistencia segun el estado de la asesoria
    private static String asist(Models.Advisory x) {
        if ("Completada".equals(x.status)) return "✔ Asistió";
        if ("No asistió".equals(x.status)) return "✖ No asistió";
        if ("Cancelada".equals(x.status)) return "✖ Cancelada";
        return upcoming(x) ? "Próxima" : "Sin registro";
    }

    // abre un enlace o un archivo local con el programa del sistema
    private static void abrirRecurso(String s) throws Exception {
        if (s.startsWith("http")) java.awt.Desktop.getDesktop().browse(java.net.URI.create(s));
        else java.awt.Desktop.getDesktop().open(new File(s));
    }

    // ---------- horarios: ayudas ----------
    private static boolean esExtra(Models.Slot s) { return s.date != null && !s.date.isBlank(); }
    private static String tipo(Models.Slot s) { return esExtra(s) ? "Extra" : "Semestral"; }
    private static String cuando(Models.Slot s) { return esExtra(s) ? s.date + " (" + s.day + ")" : s.day; }
    // "12:00" y "13:00" -> "12-13" (como en el horario del Tec)
    private static String hrs(Models.Slot s) {
        return Integer.parseInt(s.start.substring(0, 2)) + "-" + Integer.parseInt(s.end.substring(0, 2));
    }
    // primera fecha libre para registrarse en un horario: cae en su dia de la semana (o es la fecha fija de la extra),
    // el tutor no avisó ausencia y el horario no está ocupado por otro alumno
    private static LocalDate proximaFecha(Models.Slot s, String studentId, List<Models.Absence> aus, List<Models.Advisory> deTutor) {
        LocalDate hoy = LocalDate.now();
        fechas:
        for (int i = 0; i < 60; i++) {
            final LocalDate f = hoy.plusDays(i);
            if (esExtra(s) ? !f.toString().equals(s.date) : !dayName(f).equalsIgnoreCase(s.day)) continue;
            if (i == 0 && LocalTime.now().isAfter(LocalTime.parse(s.start))) continue;   // hoy, pero la hora ya pasó
            if (aus.stream().anyMatch(x -> f.toString().equals(x.date))) continue;
            for (Models.Advisory x : deTutor)
                if (!"Cancelada".equals(x.status) && f.toString().equals(x.date) && s.start.equals(x.time)) {
                    if (studentId.equals(x.studentId)) throw new IllegalArgumentException("Ya estás registrado en esa asesoría el " + f + ".");
                    continue fechas;
                }
            return f;
        }
        return null;
    }
    private static String dayName(LocalDate f) {
        String n = f.getDayOfWeek().getDisplayName(TextStyle.FULL, new Locale("es", "MX"));
        return n.substring(0, 1).toUpperCase() + n.substring(1);
    }
    // minusculas y sin acentos, para que "calculo" encuentre "Cálculo"
    private static String norm(String s) { return s == null ? "" : Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(); }

    // prototipo: horario del semestre simulado, ya establecido para cada tutor (se crea una sola vez si no tiene)
    private static void ensureHorario(User u, GenericDAO<Models.Slot> d) throws Exception {
        if (!d.getByField("tutorId", u.getUid()).isEmpty()) return;
        List<String> ms = simMaterias(u); int h = Math.floorMod(u.getUid().hashCode(), 5);
        for (int i = 0; i < ms.size(); i++) {
            int t0 = 2 + (i + h) % 6;
            Models.Slot s = new Models.Slot(); s.id = d.createDocumentId(); s.tutorId = u.getUid(); s.tutorName = name(u);
            s.subject = ms.get(i); s.day = DAYS.get((i * 2 + h) % 5); s.start = TIMES.get(t0); s.end = TIMES.get(t0 + 1);
            s.modality = i % 3 == 2 ? "En línea" : "Presencial";
            s.place = "En línea".equals(s.modality) ? "Videollamada (enlace por recado)" : "Aula " + (char) ('A' + h) + (101 + i);
            s.date = ""; d.save(s.id, s);
        }
    }
    private static void ensureAllHorarios() throws Exception {
        GenericDAO<Models.Slot> d = new GenericDAO<>(Models.Slot.class, "slots");
        for (User t : new GenericDAO<>(User.class, "users").getByField("role", "tutor")) ensureHorario(t, d);
    }

    // el tutor avisa que un dia especifico no atendera un horario: se cancelan las asesorias de ese dia y se avisa por recado
    private static String avisarAusencia(User u, Models.Slot s, LocalDate f, String motivo) throws Exception {
        if (f.isBefore(LocalDate.now())) throw new IllegalArgumentException("La fecha no puede ser pasada.");
        if (!dayName(f).equalsIgnoreCase(s.day)) throw new IllegalArgumentException("Ese horario es de los " + s.day + "; elige una fecha de ese día.");
        GenericDAO<Models.Absence> ad = new GenericDAO<>(Models.Absence.class, "absences");
        if (ad.getByField("slotId", s.id).stream().anyMatch(x -> f.toString().equals(x.date))) throw new IllegalArgumentException("Ya avisaste que no atenderás ese día.");
        Models.Absence x = new Models.Absence(); x.id = ad.createDocumentId(); x.slotId = s.id; x.tutorId = u.getUid(); x.subject = s.subject; x.date = f.toString(); x.reason = motivo;
        ad.save(x.id, x);
        GenericDAO<Models.Advisory> adv = new GenericDAO<>(Models.Advisory.class, "advisories");
        GenericDAO<Models.Note> nd = new GenericDAO<>(Models.Note.class, "notes");
        int n = 0;
        for (Models.Advisory a : adv.getByField("tutorId", u.getUid()))
            if (open(a) && f.toString().equals(a.date) && s.subject.equals(a.subject) && s.start.equals(a.time)) {
                a.status = "Cancelada"; adv.save(a.id, a);
                Models.Note nt = new Models.Note(); nt.id = nd.createDocumentId(); nt.fromId = u.getUid(); nt.fromName = name(u);
                nt.toId = a.studentId; nt.toName = a.studentName; nt.createdAt = System.currentTimeMillis();
                nt.text = "Aviso de " + name(u) + ": no podré atender la asesoría de " + s.subject + " del " + f + " a las " + s.start + "."
                        + (motivo.isBlank() ? "" : " Motivo: " + motivo + ".") + " Puedes agendar otro horario.";
                nd.save(nt.id, nt); n++;
            }
        return "Aviso registrado: se canceló " + n + " asesoría(s) de ese día y se enviaron los recados.";
    }

    // ---------- inicio (admin) ----------
    static Node home(User u) {
        Label l = new Label("Gestiona materias, usuarios y valida los créditos complementarios."); l.setWrapText(true);
        return page("Hola, " + u.getFirstName(), "Panel principal", l);
    }

    // ---------- inicio del estudiante: resumen ----------
    static Node homeStudent(User u) {
        Label st = new Label();
        GridPane perfil = new GridPane(); perfil.setHgap(24); perfil.setVgap(6);
        String[][] datos = {{"Nombre", name(u)}, {"Correo", nz(u.getEmail())}, {"Carrera", nz(u.getCareer())},
                {"No. de control", simControl(u)}, {"Semestre", simSemester(u)},
                {"Promedio general", simPromedio(u)}, {"Estatus", "Inscrito · Ago–Dic 2026"}};
        for (int i = 0; i < datos.length; i++) {
            Label k = new Label(datos[i][0] + ":"); k.setStyle("-fx-font-weight: bold;");
            perfil.addRow(i, k, new Label(datos[i][1]));
        }
        String[] titulos = {"Próximas asesorías", "Asesorías asistidas", "Horas de asesoría", "Actividades pendientes", "Recados sin leer"};
        Label[] vals = new Label[5]; HBox cards = new HBox(12);
        for (int i = 0; i < 5; i++) {
            vals[i] = new Label("…"); vals[i].setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");
            VBox c = new VBox(4, vals[i], new Label(titulos[i])); c.setPadding(new Insets(14));
            c.setStyle("-fx-background-color: #1a4130; -fx-background-radius: 6;"); cards.getChildren().add(c);
        }
        Label next = new Label("");
        bg(st, () -> {
            List<Models.Advisory> a = new GenericDAO<>(Models.Advisory.class, "advisories").getByField("studentId", u.getUid());
            List<Models.Task> k = new GenericDAO<>(Models.Task.class, "tasks").getByField("studentId", u.getUid());
            List<Models.Note> n = new GenericDAO<>(Models.Note.class, "notes").getByField("toId", u.getUid());
            List<Models.Advisory> prox = a.stream().filter(Modules::upcoming)
                    .sorted(Comparator.comparing((Models.Advisory x) -> x.date + x.time)).collect(Collectors.toList());
            long hechas = a.stream().filter(x -> "Completada".equals(x.status)).count();
            double horas = a.stream().filter(x -> "Completada".equals(x.status)).mapToDouble(x -> x.hours).sum();
            long pend = k.stream().filter(x -> "Pendiente".equals(x.status)).count();
            long sinLeer = n.stream().filter(x -> !x.read).count();
            String sig = prox.isEmpty() ? "No tienes asesorías próximas." : "Próxima asesoría: " + prox.get(0).subject
                    + " con " + prox.get(0).tutorName + ", " + prox.get(0).date + " a las " + prox.get(0).time;
            Platform.runLater(() -> {
                vals[0].setText("" + prox.size()); vals[1].setText("" + hechas); vals[2].setText(String.valueOf(horas));
                vals[3].setText("" + pend); vals[4].setText("" + sinLeer); next.setText(sig);
            });
        });
        return page("Hola, " + u.getFirstName(), "Resumen del estudiante", perfil, cards, next, st);
    }

    // ---------- inicio del tutor (maestro o alumno): resumen ----------
    static Node homeTutor(User u) {
        boolean maestro = "teacher".equals(u.getTutorType());
        Label st = new Label();
        GridPane perfil = new GridPane(); perfil.setHgap(24); perfil.setVgap(6);
        int h = Math.floorMod(u.getUid().hashCode(), 5);
        double prom = simMaterias(u).stream().mapToInt(m -> simExamen(u, m)).average().orElse(0);
        String[][] datos = maestro
                ? new String[][]{{"Nombre completo", name(u)}, {"Correo institucional", nz(u.getEmail())}, {"Departamento", simDepto(u)},
                {"Tipo de plaza", simPlaza(u)}, {"Grado académico", simGrado(u)}, {"Cubículo", "Edificio " + (char) ('A' + h) + "-" + (101 + h)},
                {"Materias que ha impartido", String.join(" · ", simMaterias(u))}}
                : new String[][]{{"Nombre completo", name(u)}, {"Correo institucional", nz(u.getEmail())}, {"Tipo", "Alumno tutor (crédito complementario)"},
                {"Carrera", nz(u.getCareer())}, {"No. de control", simControl(u)}, {"Semestre", simSemester(u)}, {"Promedio general", simPromedio(u)},
                {"Encuesta de perfil", "Aprobada"}, {"Examen de conocimientos", String.format("Aprobado · promedio %.1f", prom)},
                {"Materias habilitadas (calificación)", habilitadas(u)}};
        for (int i = 0; i < datos.length; i++) {
            Label k = new Label(datos[i][0] + ":"); k.setStyle("-fx-font-weight: bold;");
            Label val = new Label(datos[i][1]); val.setWrapText(true); val.setMaxWidth(720);
            perfil.addRow(i, k, val);
        }
        String[] titulos = maestro
                ? new String[]{"Pendientes por confirmar", "Confirmadas", "Completadas", "Alumnos atendidos", "Actividades por revisar", "Recados sin leer"}
                : new String[]{"Pendientes por confirmar", "Confirmadas", "Completadas", "Alumnos atendidos", "Actividades por revisar", "Recados sin leer", "Horas de crédito"};
        Label[] vals = new Label[titulos.length]; HBox cards = new HBox(12);
        for (int i = 0; i < titulos.length; i++) {
            vals[i] = new Label("…"); vals[i].setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");
            VBox c = new VBox(4, vals[i], new Label(titulos[i])); c.setPadding(new Insets(14));
            c.setStyle("-fx-background-color: #1a4130; -fx-background-radius: 6;"); cards.getChildren().add(c);
        }
        Label next = new Label("");
        bg(st, () -> {
            List<Models.Advisory> a = new GenericDAO<>(Models.Advisory.class, "advisories").getByField("tutorId", u.getUid());
            List<Models.Task> k = new GenericDAO<>(Models.Task.class, "tasks").getByField("tutorId", u.getUid());
            List<Models.Note> n = new GenericDAO<>(Models.Note.class, "notes").getByField("toId", u.getUid());
            long pend = a.stream().filter(x -> "Pendiente".equals(x.status)).count();
            long conf = a.stream().filter(x -> "Confirmada".equals(x.status)).count();
            long comp = a.stream().filter(x -> "Completada".equals(x.status)).count();
            long alumnos = a.stream().map(x -> x.studentId).distinct().count();
            long porRev = k.stream().filter(x -> "Entregada".equals(x.status)).count();
            long sinLeer = n.stream().filter(x -> !x.read).count();
            List<Models.Advisory> prox = a.stream().filter(x -> "Confirmada".equals(x.status) && x.date.compareTo(LocalDate.now().toString()) >= 0)
                    .sorted(Comparator.comparing((Models.Advisory x) -> x.date + x.time)).collect(Collectors.toList());
            String sig = prox.isEmpty() ? "No tienes asesorías confirmadas próximas." : "Próxima asesoría: " + prox.get(0).subject
                    + " con " + prox.get(0).studentName + ", " + prox.get(0).date + " a las " + prox.get(0).time;
            String[] v = new String[titulos.length];
            v[0] = "" + pend; v[1] = "" + conf; v[2] = "" + comp; v[3] = "" + alumnos; v[4] = "" + porRev; v[5] = "" + sinLeer;
            if (!maestro) {
                double cred = new GenericDAO<>(Models.Credit.class, "credits").getByField("tutorId", u.getUid()).stream()
                        .filter(c -> "Validado".equals(c.status)).mapToDouble(c -> c.hours).sum();
                v[6] = String.valueOf(cred);
            }
            Platform.runLater(() -> { for (int i = 0; i < v.length; i++) vals[i].setText(v[i]); next.setText(sig); });
        });
        return page("Hola, " + u.getFirstName(), maestro ? "Resumen del maestro tutor" : "Resumen del alumno tutor", perfil, cards, next, st);
    }

    // ---------- materias (admin las administra) ----------
    static Node subjects(User u) {
        GenericDAO<Models.Subject> d = new GenericDAO<>(Models.Subject.class, "subjects");
        ObservableList<Models.Subject> l = FXCollections.observableArrayList(); Label st = new Label();
        TableView<Models.Subject> t = table(l); col(t, "Materia", s -> s.name);
        Runnable ref = () -> load(st, l, d::getAll); ref.run();
        if (!"admin".equals(role(u))) return page("Materias", "Catálogo de materias con asesoría", t, st);
        TextField n = tf("Nombre de la materia");
        Button add = btn("Agregar", "btn-success", () -> { String v = n.getText().trim(); if (v.isEmpty()) return;
            bg(st, () -> { Models.Subject s = new Models.Subject(); s.id = d.createDocumentId(); s.name = v; d.save(s.id, s); Platform.runLater(() -> { n.clear(); ref.run(); }); }); });
        Button del = btn("Eliminar", "btn-danger", () -> { Models.Subject s = t.getSelectionModel().getSelectedItem();
            if (s != null) bg(st, () -> { d.delete(s.id); Platform.runLater(ref); }); });
        return page("Materias", "Catálogo de materias con asesoría", row(n, add, del), t, st);
    }

    // ---------- materias del estudiante: tutores disponibles y su avance ----------
    static Node subjectsStudent(User u) {
        Label st = new Label(); ObservableList<String[]> l = FXCollections.observableArrayList(); TableView<String[]> t = table(l);
        String[] h = {"Materia", "Tutores disponibles", "Mis asesorías", "Próxima asesoría"};
        for (int i = 0; i < 4; i++) { final int c = i; col(t, h[i], x -> x[c]); }
        bg(st, () -> {
            List<Models.Subject> sub = new GenericDAO<>(Models.Subject.class, "subjects").getAll();
            List<Models.Slot> sl = new GenericDAO<>(Models.Slot.class, "slots").getAll();
            List<Models.Advisory> ad = new GenericDAO<>(Models.Advisory.class, "advisories").getByField("studentId", u.getUid());
            List<String[]> rows = new ArrayList<>();
            for (Models.Subject s : sub) {
                long tutores = sl.stream().filter(x -> s.name.equals(x.subject)).map(x -> x.tutorId).distinct().count();
                List<Models.Advisory> mias = ad.stream().filter(x -> s.name.equals(x.subject)).collect(Collectors.toList());
                String prox = mias.stream().filter(Modules::upcoming).map(x -> x.date + " " + x.time).min(Comparator.naturalOrder()).orElse("—");
                rows.add(new String[]{s.name, "" + tutores, "" + mias.size(), prox});
            }
            Platform.runLater(() -> l.setAll(rows));
        });
        return page("Materias", "Materias con asesoría y tu avance en cada una", t, st);
    }

    // ---------- materias del tutor: sus materias habilitadas y los alumnos de cada una ----------
    static Node subjectsTutor(User u) {
        boolean maestro = "teacher".equals(u.getTutorType());
        Label st = new Label();
        ObservableList<String[]> subs = FXCollections.observableArrayList(), alum = FXCollections.observableArrayList();
        TableView<String[]> ts = table(subs), ta = table(alum);
        String[] h1 = {"Materia", "Habilitación", "Horarios", "Alumnos", "Asesorías"};
        for (int i = 0; i < 5; i++) { final int c = i; col(ts, h1[i], x -> x[c]); }
        String[] h2 = {"Alumno", "Correo", "Carrera", "No. de control", "Semestre", "Asesorías", "Asistió"};
        for (int i = 0; i < 7; i++) { final int c = i; col(ta, h2[i], x -> x[c]); }
        Map<String, List<String[]>> detalle = new HashMap<>();
        bg(st, () -> {
            List<Models.Slot> sl = new GenericDAO<>(Models.Slot.class, "slots").getByField("tutorId", u.getUid());
            List<Models.Advisory> ad = new GenericDAO<>(Models.Advisory.class, "advisories").getByField("tutorId", u.getUid());
            GenericDAO<User> ud = new GenericDAO<>(User.class, "users");
            Map<String, User> us = new HashMap<>();
            for (String id : ad.stream().map(x -> x.studentId).distinct().collect(Collectors.toList())) { User s = ud.getById(id); if (s != null) us.put(id, s); }
            List<String> hab = simMaterias(u);
            Set<String> materias = new TreeSet<>(hab);
            sl.forEach(x -> materias.add(x.subject)); ad.forEach(x -> materias.add(x.subject));
            List<String[]> filas = new ArrayList<>();
            for (String m : materias) {
                String habil = !hab.contains(m) ? "—" : maestro ? "Ha impartido la materia" : "Examen aprobado: " + simExamen(u, m);
                String horarios = sl.stream().filter(x -> m.equals(x.subject)).map(x -> x.day.substring(0, 3) + " " + x.start + "-" + x.end).collect(Collectors.joining("; "));
                List<Models.Advisory> am = ad.stream().filter(x -> m.equals(x.subject)).collect(Collectors.toList());
                List<String> ids = am.stream().map(x -> x.studentId).distinct().collect(Collectors.toList());
                filas.add(new String[]{m, habil, horarios.isEmpty() ? "Sin horario" : horarios, "" + ids.size(), "" + am.size()});
                List<String[]> al = new ArrayList<>();
                for (String sid : ids) {
                    User s = us.get(sid); if (s == null) continue;
                    long total = am.stream().filter(x -> sid.equals(x.studentId)).count();
                    long asis = am.stream().filter(x -> sid.equals(x.studentId) && "Completada".equals(x.status)).count();
                    al.add(new String[]{name(s), nz(s.getEmail()), nz(s.getCareer()), simControl(s), simSemester(s), "" + total, "" + asis});
                }
                detalle.put(m, al);
            }
            Platform.runLater(() -> subs.setAll(filas));
        });
        ts.getSelectionModel().selectedItemProperty().addListener((o, a, n) ->
                alum.setAll(n == null ? new ArrayList<String[]>() : detalle.getOrDefault(n[0], new ArrayList<String[]>())));
        Label sub2 = new Label("Alumnos de la materia seleccionada:"); sub2.setStyle("-fx-font-weight: bold;");
        return page("Materias", maestro ? "Materias en las que puedes dar tutoría (las que has impartido) y tus alumnos"
                : "Materias en las que aprobaste el examen de conocimientos y tus alumnos", ts, sub2, ta, st);
    }

    // ---------- horarios: el estudiante ve todos; el tutor administra su horario del semestre ----------
    static Node slots(User u) {
        GenericDAO<Models.Slot> d = new GenericDAO<>(Models.Slot.class, "slots");
        ObservableList<Models.Slot> l = FXCollections.observableArrayList(); Label st = new Label();
        TableView<Models.Slot> t = table(l);
        col(t, "Materia", s -> s.subject); col(t, "Tutor", s -> s.tutorName); col(t, "Día / fecha", s -> cuando(s));
        col(t, "Horario", s -> s.start + " - " + s.end); col(t, "Modalidad", s -> s.modality == null ? "Presencial" : s.modality);
        col(t, "Lugar / enlace", s -> s.place); col(t, "Tipo", s -> tipo(s));
        boolean tutor = "tutor".equals(role(u));
        Runnable ref = () -> load(st, l, () -> {
            if (tutor) ensureHorario(u, d); else ensureAllHorarios();
            List<Models.Slot> r = tutor ? d.getByField("tutorId", u.getUid()) : d.getAll();
            r.removeIf(x -> esExtra(x) && x.date.compareTo(LocalDate.now().toString()) < 0);   // extras que ya pasaron
            if (tutor) r.sort(Comparator.comparing((Models.Slot s) -> esExtra(s) ? 1 : 0).thenComparing(s -> DAYS.indexOf(s.day)).thenComparing(s -> s.start == null ? "" : s.start));
            else r.sort(Comparator.comparing((Models.Slot s) -> s.subject == null ? "" : s.subject).thenComparing(s -> s.tutorName == null ? "" : s.tutorName));
            return r; });
        ref.run();
        if (!tutor) return page("Horarios", "Todas las materias con sus tutores, horarios del semestre y modalidad", t, st);

        ComboBox<String> subj = cb("Materia"), day = cb("Día"), a = cb("Inicio"), b = cb("Fin"), mod = cb("Modalidad");
        TextField place = tf("Aula o enlace"); DatePicker dt = new DatePicker(); dt.setPromptText("Fecha (solo asesoría extra)");
        subj.setItems(FXCollections.observableArrayList(simMaterias(u)));   // solo materias en las que esta habilitado
        day.setItems(FXCollections.observableArrayList(DAYS)); a.setItems(FXCollections.observableArrayList(TIMES));
        b.setItems(FXCollections.observableArrayList(TIMES)); mod.setItems(FXCollections.observableArrayList("Presencial", "En línea"));
        // al seleccionar un horario se carga en el formulario para modificarlo
        t.getSelectionModel().selectedItemProperty().addListener((o, x, s) -> { if (s == null) return;
            subj.setValue(s.subject); day.setValue(s.day); a.setValue(s.start); b.setValue(s.end);
            mod.setValue(s.modality == null ? "Presencial" : s.modality); place.setText(s.place == null ? "" : s.place);
            dt.setValue(esExtra(s) ? LocalDate.parse(s.date) : null); });
        // valida el formulario y lo copia al horario; regresa el error o null
        Function<Models.Slot, String> leer = s -> {
            if (subj.getValue() == null || a.getValue() == null || b.getValue() == null || mod.getValue() == null) return "Completa materia, horas y modalidad.";
            if (a.getValue().compareTo(b.getValue()) >= 0) return "La hora de fin debe ser posterior al inicio.";
            LocalDate f = dt.getValue();
            if (f != null && f.isBefore(LocalDate.now())) return "La fecha no puede ser pasada.";
            String dia = f != null ? dayName(f) : day.getValue();
            if (dia == null) return "Elige el día de la semana (o una fecha si es una asesoría extra).";
            s.subject = subj.getValue(); s.day = dia; s.start = a.getValue(); s.end = b.getValue(); s.modality = mod.getValue();
            s.place = place.getText().trim(); s.date = f == null ? "" : f.toString();
            return null; };
        Button add = btn("Agregar horario / asesoría extra", "btn-success", () -> {
            Models.Slot s = new Models.Slot(); String err = leer.apply(s);
            if (err != null) { msg(st, err, false); return; }
            s.id = d.createDocumentId(); s.tutorId = u.getUid(); s.tutorName = name(u);
            bg(st, () -> { d.save(s.id, s); Platform.runLater(() -> { msg(st, esExtra(s) ? "Asesoría extra agregada." : "Horario agregado al semestre.", true); ref.run(); }); }); });
        Button save = btn("Guardar cambios", "btn-primary", () -> {
            Models.Slot s = t.getSelectionModel().getSelectedItem();
            if (s == null) { msg(st, "Selecciona el horario que quieres modificar.", false); return; }
            String err = leer.apply(s); if (err != null) { msg(st, err, false); return; }
            bg(st, () -> { d.save(s.id, s); Platform.runLater(() -> { msg(st, "Horario modificado.", true); ref.run(); }); }); });
        Button del = btn("Eliminar", "btn-danger", () -> { Models.Slot s = t.getSelectionModel().getSelectedItem(); if (s != null) bg(st, () -> { d.delete(s.id); Platform.runLater(ref); }); });
        Button aus = btn("No podré atender un día…", "btn-warning", () -> {
            Models.Slot s = t.getSelectionModel().getSelectedItem();
            if (s == null || esExtra(s)) { msg(st, "Selecciona un horario del semestre para avisar tu ausencia.", false); return; }
            Dialog<ButtonType> dlg = new Dialog<>(); dlg.setTitle("Aviso de ausencia"); dlg.setHeaderText(s.subject + " · " + s.day + " " + s.start + "-" + s.end);
            DatePicker fd = new DatePicker(); TextField mt = new TextField(); mt.setPromptText("Motivo (opcional)");
            dlg.getDialogPane().setContent(new VBox(10, new Label("Día en que no podrás atender:"), fd, mt));
            dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            dlg.showAndWait().filter(bt -> bt == ButtonType.OK).ifPresent(bt -> {
                if (fd.getValue() == null) { msg(st, "Elige la fecha.", false); return; }
                bg(st, () -> { String res = avisarAusencia(u, s, fd.getValue(), mt.getText().trim()); Platform.runLater(() -> { msg(st, res, true); ref.run(); }); }); }); });
        return page("Mis horarios", "Tu horario del semestre ya está establecido: puedes modificarlo, agregar asesorías extra o avisar que no atenderás un día",
                row(subj, day, a, b), row(mod, place, dt), row(add, save, del, aus), t, st);
    }

    // ---------- asesorias del estudiante: asesorias disponibles (buscador y registro), horario por docente, proximas e historial ----------
    static Node advisoriesStudent(User u) {
        GenericDAO<Models.Advisory> d = new GenericDAO<>(Models.Advisory.class, "advisories");
        ObservableList<Models.Advisory> l = FXCollections.observableArrayList(); Label st = new Label();
        FilteredList<Models.Advisory> prox = new FilteredList<>(l, Modules::upcoming);
        FilteredList<Models.Advisory> pasadas = new FilteredList<>(l, x -> !upcoming(x));
        SortedList<Models.Advisory> hist = new SortedList<>(pasadas, Comparator.comparing((Models.Advisory x) -> x.date + x.time).reversed());
        TableView<Models.Advisory> tp = table(prox), th = table(hist);
        for (TableView<Models.Advisory> t : List.of(tp, th)) {
            col(t, "Fecha", x -> x.date); col(t, "Hora", x -> x.time); col(t, "Materia", x -> x.subject);
            col(t, "Maestro / tutor", x -> x.tutorName); col(t, "Tema", x -> x.topic);
            col(t, "Modalidad", x -> x.modality); col(t, "Estado", x -> x.status);
        }
        col(th, "Asistencia", Modules::asist);
        Runnable ref = () -> load(st, l, () -> { List<Models.Advisory> a = d.getByField("studentId", u.getUid());
            a.sort(Comparator.comparing((Models.Advisory x) -> x.date + x.time)); return a; });
        ref.run();

        // buscador: por materia (todos los maestros que la dan) o por maestro (todas sus materias)
        ObservableList<Models.Slot> todos = FXCollections.observableArrayList();
        FilteredList<Models.Slot> res = new FilteredList<>(todos, x -> true);
        ComboBox<String> modo = cb("Buscar por"); modo.setItems(FXCollections.observableArrayList("Materia", "Maestro")); modo.setValue("Materia");
        TextField q = tf("Escribe una materia o el nombre de un maestro"); q.setPrefWidth(420);
        Runnable filtrar = () -> {
            String txt = norm(q.getText().trim()); boolean porMateria = !"Maestro".equals(modo.getValue());
            res.setPredicate(x -> txt.isEmpty() || norm(porMateria ? x.subject : x.tutorName).contains(txt)); };
        q.textProperty().addListener((o, a, b) -> filtrar.run());
        modo.valueProperty().addListener((o, a, b) -> filtrar.run());
        load(st, todos, () -> {
            ensureAllHorarios();
            List<Models.Slot> r = new GenericDAO<>(Models.Slot.class, "slots").getAll();
            r.removeIf(x -> esExtra(x) && x.date.compareTo(LocalDate.now().toString()) < 0);
            r.sort(Comparator.comparing((Models.Slot s) -> s.subject == null ? "" : s.subject).thenComparing(s -> s.tutorName == null ? "" : s.tutorName));
            return r; });

        // tabla 1: una fila por materia y horario
        TableView<Models.Slot> tr = table(res);
        col(tr, "Materia", s -> s.subject); col(tr, "Maestro / tutor", s -> s.tutorName); col(tr, "Día / fecha", s -> cuando(s));
        col(tr, "Horario", s -> s.start + " - " + s.end); col(tr, "Modalidad", s -> s.modality == null ? "Presencial" : s.modality);
        col(tr, "Lugar / enlace", s -> s.place); col(tr, "Tipo", s -> tipo(s));

        // tabla 2: formato del horario de asesorias del Tec (una fila por docente, horas por dia, asignaturas)
        ObservableList<String[]> semanal = FXCollections.observableArrayList();
        TableView<String[]> tw = table(semanal);
        String[] hw = {"Docente", "Lugar", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Asignaturas"};
        for (int i = 0; i < hw.length; i++) { final int c = i; col(tw, hw[i], x -> x[c]); }
        Runnable pintar = () -> {
            Map<String, List<Models.Slot>> porTutor = new LinkedHashMap<>();
            for (Models.Slot s : res) porTutor.computeIfAbsent(s.tutorName, k -> new ArrayList<>()).add(s);
            List<String[]> filas = new ArrayList<>();
            for (Map.Entry<String, List<Models.Slot>> e : porTutor.entrySet()) {
                List<Models.Slot> ss = e.getValue();
                String[] f = new String[8];
                f[0] = e.getKey();
                f[1] = ss.stream().map(x -> x.place).filter(Objects::nonNull).distinct().collect(Collectors.joining(" / "));
                for (int i = 0; i < 5; i++) {
                    String dia = DAYS.get(i);
                    f[2 + i] = ss.stream().filter(x -> !esExtra(x) && dia.equals(x.day))
                            .sorted(Comparator.comparing((Models.Slot x) -> x.start)).map(Modules::hrs).collect(Collectors.joining(", "));
                }
                f[7] = ss.stream().map(x -> x.subject).distinct().sorted().collect(Collectors.joining(", "));
                filas.add(f);
            }
            semanal.setAll(filas);
        };
        res.addListener((ListChangeListener<Models.Slot>) c -> pintar.run());

        // registro: la fecha se calcula sola; el alumno solo elige la asesoria y confirma
        Button reg = btn("Registrarme en la asesoría seleccionada", "btn-success", () -> {
            Models.Slot s = tr.getSelectionModel().getSelectedItem();
            if (s == null) { msg(st, "Selecciona una asesoría en la pestaña \"Asesorías disponibles\".", false); return; }
            bg(st, () -> {
                List<Models.Absence> aus = new GenericDAO<>(Models.Absence.class, "absences").getByField("slotId", s.id);
                LocalDate f = proximaFecha(s, u.getUid(), aus, d.getByField("tutorId", s.tutorId));
                if (f == null) { Platform.runLater(() -> msg(st, "No hay fechas libres próximas para esa asesoría.", false)); return; }
                Platform.runLater(() -> {
                    Alert al = new Alert(Alert.AlertType.CONFIRMATION);
                    al.setTitle("Registrar asesoría"); al.setHeaderText(s.subject + " con " + s.tutorName);
                    al.setContentText(dayName(f) + " " + f + ", " + s.start + " - " + s.end + "\n"
                            + (s.modality == null ? "Presencial" : s.modality) + " · " + s.place + "\n\n¿Confirmas tu registro?");
                    al.showAndWait().filter(bt -> bt == ButtonType.OK).ifPresent(bt -> bg(st, () -> {
                        User tutor = new GenericDAO<>(User.class, "users").getById(s.tutorId);
                        Models.Advisory a = new Models.Advisory(); a.id = d.createDocumentId(); a.studentId = u.getUid(); a.studentName = name(u);
                        a.tutorId = s.tutorId; a.tutorName = s.tutorName; a.tutorType = tutor != null ? tutor.getTutorType() : "teacher"; a.subject = s.subject;
                        a.date = f.toString(); a.time = s.start; a.topic = "Asesoría de " + s.subject;
                        a.modality = s.modality == null ? "Presencial" : s.modality;   // la modalidad la define el tutor
                        a.status = "Pendiente";
                        a.hours = Duration.between(LocalTime.parse(s.start), LocalTime.parse(s.end)).toMinutes() / 60.0;
                        d.save(a.id, a);
                        Platform.runLater(() -> { msg(st, "Registro enviado: " + s.subject + ", " + f + " a las " + s.start + " (pendiente de confirmación del tutor).", true); ref.run(); });
                    }));
                });
            });
        });

        TabPane tabs = new TabPane(new Tab("Asesorías disponibles", tr), new Tab("Horario por docente", tw),
                new Tab("Próximas", tp), new Tab("Historial y asistencia", th));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE); VBox.setVgrow(tabs, Priority.ALWAYS);
        return page("Asesorías", "Consulta las asesorías disponibles por materia o por maestro, regístrate en una y consulta tu asistencia",
                row(modo, q), tabs,
                row(reg, btn("Cancelar asesoría seleccionada (pestaña Próximas)", "btn-danger", () -> change(d, tp, st, null, "Cancelada", ref))), st);
    }

    // ---------- asesorias del tutor: por atender e historial ----------
    static Node advisoriesTutor(User u) {
        GenericDAO<Models.Advisory> d = new GenericDAO<>(Models.Advisory.class, "advisories");
        ObservableList<Models.Advisory> l = FXCollections.observableArrayList(); Label st = new Label();
        FilteredList<Models.Advisory> abiertas = new FilteredList<>(l, Modules::open);
        FilteredList<Models.Advisory> cerradas = new FilteredList<>(l, x -> !open(x));
        SortedList<Models.Advisory> hist = new SortedList<>(cerradas, Comparator.comparing((Models.Advisory x) -> x.date + x.time).reversed());
        TableView<Models.Advisory> tp = table(abiertas), th = table(hist);
        for (TableView<Models.Advisory> t : List.of(tp, th)) {
            col(t, "Fecha", x -> x.date); col(t, "Hora", x -> x.time); col(t, "Materia", x -> x.subject);
            col(t, "Alumno", x -> x.studentName); col(t, "Tema", x -> x.topic);
            col(t, "Modalidad", x -> x.modality); col(t, "Estado", x -> x.status);
        }
        col(th, "Asistencia", Modules::asist);
        Runnable ref = () -> load(st, l, () -> { List<Models.Advisory> a = d.getByField("tutorId", u.getUid());
            a.sort(Comparator.comparing((Models.Advisory x) -> x.date + x.time)); return a; });
        ref.run();
        TabPane tabs = new TabPane(new Tab("Pendientes y próximas", tp), new Tab("Historial", th));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE); VBox.setVgrow(tabs, Priority.ALWAYS);
        return page("Asesorías", "Asesorías que tienes por atender y tu historial con registro de asistencia",
                row(btn("Confirmar", "btn-primary", () -> change(d, tp, st, "Pendiente", "Confirmada", ref)),
                        btn("Completar (asistió)", "btn-success", () -> change(d, tp, st, "Confirmada", "Completada", ref)),
                        btn("No asistió", "btn-warning", () -> change(d, tp, st, null, "No asistió", ref)),
                        btn("Cancelar", "btn-danger", () -> change(d, tp, st, null, "Cancelada", ref))), tabs, st);
    }

    // ---------- asesorias (admin) ----------
    static Node advisories(User u) {
        GenericDAO<Models.Advisory> d = new GenericDAO<>(Models.Advisory.class, "advisories");
        ObservableList<Models.Advisory> l = FXCollections.observableArrayList(); Label st = new Label();
        TableView<Models.Advisory> t = table(l);
        col(t, "Fecha", x -> x.date); col(t, "Hora", x -> x.time); col(t, "Materia", x -> x.subject); col(t, "Tema", x -> x.topic);
        col(t, "Alumno", x -> x.studentName); col(t, "Tutor", x -> x.tutorName); col(t, "Modalidad", x -> x.modality); col(t, "Estado", x -> x.status);
        Runnable ref = () -> load(st, l, () -> { List<Models.Advisory> a = d.getAll();
            a.sort(Comparator.comparing((Models.Advisory x) -> x.date + x.time)); return a; });
        ref.run();
        VBox v = page("Asesorías", "Todas las asesorías del sistema");
        v.getChildren().add(row(btn("Cancelar asesoría", "btn-danger", () -> change(d, t, st, null, "Cancelada", ref))));
        v.getChildren().addAll(t, st); return v;
    }

    // cambia el estado; al completar una asesoria de alumno-tutor se genera su credito complementario
    private static void change(GenericDAO<Models.Advisory> d, TableView<Models.Advisory> t, Label st, String from, String to, Runnable ref) {
        Models.Advisory a = t.getSelectionModel().getSelectedItem();
        if (a == null) { msg(st, "Selecciona una asesoría.", false); return; }
        if ("Cancelada".equals(a.status) || "Completada".equals(a.status) || "No asistió".equals(a.status) || (from != null && !from.equals(a.status))) {
            msg(st, "No se puede pasar de " + a.status + " a " + to + ".", false); return; }
        bg(st, () -> {
            a.status = to; d.save(a.id, a);
            if ("Completada".equals(to) && "student".equals(a.tutorType)) {
                GenericDAO<Models.Credit> cd = new GenericDAO<>(Models.Credit.class, "credits");
                Models.Credit c = new Models.Credit(); c.id = cd.createDocumentId(); c.tutorId = a.tutorId; c.tutorName = a.tutorName; c.advisoryId = a.id;
                c.subject = a.subject; c.date = a.date; c.hours = a.hours; c.status = "Pendiente"; cd.save(c.id, c);
            }
            Platform.runLater(() -> { msg(st, "Asesoría " + to.toLowerCase() + ".", true); ref.run(); }); });
    }

    // ---------- recados (el estudiante y el tutor solo escriben a quienes comparten asesorias) ----------
    static Node notes(User u) {
        GenericDAO<Models.Note> d = new GenericDAO<>(Models.Note.class, "notes");
        Label st = new Label(); TextArea body = new TextArea(); body.setEditable(false); body.setPrefRowCount(3);
        ObservableList<Models.Note> in = FXCollections.observableArrayList(), out = FXCollections.observableArrayList();
        TableView<Models.Note> ti = table(in), to = table(out);
        col(ti, "De", n -> n.fromName); col(ti, "Recado", n -> n.text); col(ti, "Leído", n -> n.read ? "Sí" : "No");
        col(to, "Para", n -> n.toName); col(to, "Recado", n -> n.text);
        Runnable ref = () -> { load(st, in, () -> d.getByField("toId", u.getUid())); load(st, out, () -> d.getByField("fromId", u.getUid())); };
        ref.run();
        ti.getSelectionModel().selectedItemProperty().addListener((o, a, n) -> { if (n == null) return; body.setText(n.text);
            if (!n.read) bg(st, () -> { n.read = true; d.save(n.id, n); Platform.runLater(ti::refresh); }); });
        to.getSelectionModel().selectedItemProperty().addListener((o, a, n) -> { if (n != null) body.setText(n.text); });
        ComboBox<User> dest = cb("Para"); TextField txt = tf("Escribe tu recado"); txt.setPrefWidth(420);
        fill(st, dest, () -> {
            List<User> all = new GenericDAO<>(User.class, "users").getAll();
            String rol = role(u);
            if ("admin".equals(rol)) return all.stream().filter(x -> !u.getUid().equals(x.getUid())).collect(Collectors.toList());
            boolean est = "student".equals(rol);
            Set<String> ids = new GenericDAO<>(Models.Advisory.class, "advisories").getByField(est ? "studentId" : "tutorId", u.getUid())
                    .stream().map(x -> est ? x.tutorId : x.studentId).collect(Collectors.toSet());
            return all.stream().filter(x -> ids.contains(x.getUid())).collect(Collectors.toList());
        });
        Button send = btn("Enviar", "btn-success", () -> { if (dest.getValue() == null || txt.getText().trim().isEmpty()) { msg(st, "Elige destinatario y escribe el recado.", false); return; }
            bg(st, () -> { Models.Note n = new Models.Note(); n.id = d.createDocumentId(); n.fromId = u.getUid(); n.fromName = name(u); n.toId = dest.getValue().getUid();
                n.toName = dest.getValue().toString(); n.text = txt.getText().trim(); n.createdAt = System.currentTimeMillis(); d.save(n.id, n);
                Platform.runLater(() -> { txt.clear(); msg(st, "Recado enviado.", true); ref.run(); }); }); });
        TabPane tabs = new TabPane(new Tab("Recibidos", ti), new Tab("Enviados", to)); tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE); VBox.setVgrow(tabs, Priority.ALWAYS);
        return page("Recados", "Mensajes entre alumnos, tutores y administración", row(dest, txt, send), tabs, body, st);
    }

    // ---------- actividades: el tutor asigna y revisa; el estudiante entrega ----------
    static Node tasks(User u) {
        GenericDAO<Models.Task> d = new GenericDAO<>(Models.Task.class, "tasks");
        ObservableList<Models.Task> l = FXCollections.observableArrayList(); Label st = new Label();
        TextArea det = new TextArea(); det.setEditable(false); det.setPrefRowCount(4);
        TableView<Models.Task> t = table(l);
        col(t, "Alumno", x -> x.studentName); col(t, "Tutor", x -> x.tutorName); col(t, "Tipo", x -> x.kind == null ? "Actividad" : x.kind);
        col(t, "Materia", x -> x.subject); col(t, "Título", x -> x.title); col(t, "Entrega", x -> x.dueDate); col(t, "Estado", x -> x.status);
        t.getSelectionModel().selectedItemProperty().addListener((o, a, x) -> { if (x != null) det.setText(x.description + "\nEnlace/archivo: " + x.link + "\nRespuesta del alumno: " + Objects.toString(x.studentComment, "—")); });
        String r = role(u);
        Runnable ref = () -> load(st, l, () -> "student".equals(r) ? d.getByField("studentId", u.getUid()) : "tutor".equals(r) ? d.getByField("tutorId", u.getUid()) : d.getAll()); ref.run();
        VBox v = page("Actividades", "tutor".equals(r) ? "Sube actividades y material de refuerzo para tus alumnos" : "Actividades y material que dejan tus tutores");
        Button abrir = btn("Abrir archivo / enlace", "btn-primary", () -> {
            Models.Task k = t.getSelectionModel().getSelectedItem();
            if (k == null || k.link == null || k.link.isBlank()) { msg(st, "Selecciona una actividad que tenga archivo o enlace.", false); return; }
            bg(st, () -> abrirRecurso(k.link)); });
        if ("tutor".equals(r)) {
            ComboBox<User> stu = cb("Alumno"); ComboBox<String> subj = cb("Materia"), kind = cb("Tipo");
            kind.setItems(FXCollections.observableArrayList("Actividad", "Material de refuerzo"));
            fill(st, stu, () -> {
                Set<String> ids = new GenericDAO<>(Models.Advisory.class, "advisories").getByField("tutorId", u.getUid()).stream().map(x -> x.studentId).collect(Collectors.toSet());
                return new GenericDAO<>(User.class, "users").getByField("role", "student").stream().filter(x -> ids.contains(x.getUid())).collect(Collectors.toList()); });
            subj.setItems(FXCollections.observableArrayList(simMaterias(u)));   // solo materias en las que esta habilitado
            TextField title = tf("Título"), desc = tf("Descripción"), link = tf("Enlace o ruta del archivo"); DatePicker due = new DatePicker();
            Button adj = btn("Adjuntar archivo…", "btn-default", () -> { File f = new FileChooser().showOpenDialog(v.getScene().getWindow()); if (f != null) link.setText(f.getAbsolutePath()); });
            Button add = btn("Asignar", "btn-success", () -> {
                if (stu.getValue() == null || kind.getValue() == null || subj.getValue() == null || title.getText().trim().isEmpty()) { msg(st, "Elige alumno, tipo, materia y título.", false); return; }
                bg(st, () -> { Models.Task k = new Models.Task(); k.id = d.createDocumentId(); k.tutorId = u.getUid(); k.tutorName = name(u); k.studentId = stu.getValue().getUid();
                    k.studentName = stu.getValue().toString(); k.kind = kind.getValue(); k.subject = subj.getValue(); k.title = title.getText().trim(); k.description = desc.getText().trim();
                    k.link = link.getText().trim(); k.dueDate = due.getValue() == null ? "" : due.getValue().toString(); k.status = "Pendiente"; d.save(k.id, k);
                    Platform.runLater(() -> { title.clear(); desc.clear(); link.clear(); msg(st, "Asignado al alumno.", true); ref.run(); }); }); });
            Button rev = btn("Marcar revisada", "btn-primary", () -> { Models.Task k = t.getSelectionModel().getSelectedItem();
                if (k == null) return;
                if (!"Entregada".equals(k.status)) { msg(st, "Solo se revisan las actividades ya entregadas.", false); return; }
                bg(st, () -> { k.status = "Revisada"; d.save(k.id, k); Platform.runLater(ref); }); });
            Button del = btn("Eliminar", "btn-danger", () -> { Models.Task k = t.getSelectionModel().getSelectedItem(); if (k != null) bg(st, () -> { d.delete(k.id); Platform.runLater(ref); }); });
            v.getChildren().addAll(row(stu, kind, subj, due), row(title, desc), row(link, adj, add), row(rev, abrir, del));
        } else if ("student".equals(r)) {
            v.getChildren().add(row(btn("Entregar", "btn-success", () -> { Models.Task k = t.getSelectionModel().getSelectedItem(); if (k == null || "Revisada".equals(k.status)) return;
                TextInputDialog dlg = new TextInputDialog(); dlg.setHeaderText("Comentario o enlace de tu entrega");
                dlg.showAndWait().ifPresent(s -> bg(st, () -> { k.studentComment = s; k.status = "Entregada"; d.save(k.id, k); Platform.runLater(() -> { ref.run(); msg(st, "Entrega enviada.", true); }); })); }), abrir));
        }
        v.getChildren().addAll(t, det, st); return v;
    }

    // ---------- creditos complementarios ----------
    static Node credits(User u) {
        GenericDAO<Models.Credit> d = new GenericDAO<>(Models.Credit.class, "credits");
        ObservableList<Models.Credit> l = FXCollections.observableArrayList(); Label st = new Label(), total = new Label();
        TableView<Models.Credit> t = table(l);
        col(t, "Tutor", c -> c.tutorName); col(t, "Materia", c -> c.subject); col(t, "Fecha", c -> c.date); col(t, "Horas", c -> String.valueOf(c.hours)); col(t, "Estado", c -> c.status);
        boolean admin = "admin".equals(role(u));
        Runnable ref = () -> bg(st, () -> { List<Models.Credit> r = admin ? d.getAll() : d.getByField("tutorId", u.getUid());
            double h = r.stream().filter(c -> "Validado".equals(c.status)).mapToDouble(c -> c.hours).sum();
            Platform.runLater(() -> { l.setAll(r); total.setText("Horas validadas: " + h); }); });
        ref.run();
        VBox v = page("Créditos complementarios", admin ? "Valida las horas de los alumnos-tutores" : "Horas acumuladas por tus asesorías");
        if (admin) v.getChildren().add(btn("Validar crédito", "btn-success", () -> { Models.Credit c = t.getSelectionModel().getSelectedItem();
            if (c != null) bg(st, () -> { c.status = "Validado"; d.save(c.id, c); Platform.runLater(ref); }); }));
        v.getChildren().addAll(t, total, st); return v;
    }

    // ---------- usuarios (admin) ----------
    static Node users(User u) {
        GenericDAO<User> d = new GenericDAO<>(User.class, "users");
        ObservableList<User> l = FXCollections.observableArrayList(); Label st = new Label();
        TableView<User> t = table(l);
        col(t, "Nombre", User::toString); col(t, "Correo", User::getEmail); col(t, "Rol", User::getRole);
        col(t, "Tipo de tutor", x -> "teacher".equals(x.getTutorType()) ? "Maestro" : "tutor".equals(x.getRole()) ? "Alumno" : ""); col(t, "Carrera", User::getCareer);
        Runnable ref = () -> load(st, l, d::getAll); ref.run();
        Button del = btn("Eliminar perfil", "btn-danger", () -> { User x = t.getSelectionModel().getSelectedItem(); if (x == null || x.getUid().equals(u.getUid())) return;
            bg(st, () -> { d.delete(x.getUid()); Platform.runLater(ref); }); });
        return page("Usuarios", "Cuentas registradas en el sistema", del, t, st);
    }
}