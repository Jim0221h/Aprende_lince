package com.itc.healthtrack.models;

// modelos de la base de datos (campos publicos; cada clase es una tabla)
public class Models {
    public static class Subject { public String id, name; public Subject() {} public String toString() { return name; } }
    // horario de un tutor: semestral (date vacio) o asesoria extra de un dia especifico (date con valor)
    // la modalidad (Presencial / En línea) la define solo el tutor
    public static class Slot { public String id, tutorId, tutorName, subject, day, start, end, place, modality, date; public Slot() {}
        public String toString() { return tutorName + " · " + subject + " · " + (date == null || date.isBlank() ? day : date) + " " + start + "-" + end; } }
    // dia especifico en que el tutor no atiende un horario del semestre
    public static class Absence { public String id, slotId, tutorId, subject, date, reason; public Absence() {} }
    // la asesoria (cita)
    public static class Advisory { public String id, studentId, studentName, tutorId, tutorName, tutorType, subject, date, time, topic, modality, status; public double hours; public Advisory() {} }
    // recado entre usuarios
    public static class Note { public String id, fromId, fromName, toId, toName, text; public long createdAt; public boolean read; public Note() {} }
    // actividad o material de refuerzo que el tutor deja a un alumno (kind: Actividad | Material de refuerzo)
    public static class Task { public String id, tutorId, tutorName, studentId, studentName, subject, title, description, link, dueDate, status, studentComment, kind; public Task() {} }
    // credito complementario generado al completar una asesoria de un alumno-tutor
    public static class Credit { public String id, tutorId, tutorName, advisoryId, subject, date, status; public double hours; public Credit() {} }
}