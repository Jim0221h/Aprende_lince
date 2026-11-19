package com.itc.healthtrack.dao;

import com.itc.healthtrack.config.Db;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

// DAO generico sobre PostgreSQL: una tabla por entidad, una columna por campo
// la tabla se crea sola y, si se agregan campos nuevos a la clase, se agregan las columnas que falten
public class GenericDAO<T> {
    private final Class<T> cls;
    private final String table, pk;
    private final List<Field> fields = new ArrayList<>();

    public GenericDAO(Class<T> cls, String table) {
        this.cls = cls; this.table = table;
        for (Field f : cls.getDeclaredFields()) { if (Modifier.isStatic(f.getModifiers())) continue; f.setAccessible(true); fields.add(f); }
        pk = fields.stream().map(Field::getName).filter(n -> n.equals("uid") || n.equals("id")).findFirst().orElse(fields.get(0).getName());
        String cols = fields.stream().map(f -> "\"" + f.getName() + "\" " + sqlType(f.getType()) + (f.getName().equals(pk) ? " PRIMARY KEY" : "")).collect(Collectors.joining(", "));
        try (Connection c = Db.get(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS \"" + table + "\" (" + cols + ")");
            // columnas nuevas en tablas que ya existian
            for (Field f : fields)
                s.execute("ALTER TABLE \"" + table + "\" ADD COLUMN IF NOT EXISTS \"" + f.getName() + "\" " + sqlType(f.getType()));
        } catch (SQLException e) { throw new RuntimeException("No se pudo preparar la tabla " + table + ": " + e.getMessage(), e); }
    }

    private static String sqlType(Class<?> t) {
        if (t == double.class || t == Double.class) return "DOUBLE PRECISION";
        if (t == long.class || t == Long.class) return "BIGINT";
        if (t == boolean.class || t == Boolean.class) return "BOOLEAN";
        return "TEXT";
    }

    public String createDocumentId() { return UUID.randomUUID().toString(); }

    // inserta o actualiza (upsert) la entidad
    public void save(String id, T e) throws Exception {
        for (Field f : fields) if (f.getName().equals(pk) && f.get(e) == null) f.set(e, id);
        String cols = fields.stream().map(f -> "\"" + f.getName() + "\"").collect(Collectors.joining(","));
        String ph = fields.stream().map(f -> "?").collect(Collectors.joining(","));
        String upd = fields.stream().filter(f -> !f.getName().equals(pk)).map(f -> "\"" + f.getName() + "\"=EXCLUDED.\"" + f.getName() + "\"").collect(Collectors.joining(","));
        String sql = "INSERT INTO \"" + table + "\" (" + cols + ") VALUES (" + ph + ")" + (upd.isEmpty() ? "" : " ON CONFLICT (\"" + pk + "\") DO UPDATE SET " + upd);
        try (Connection c = Db.get(); PreparedStatement p = c.prepareStatement(sql)) {
            int i = 1; for (Field f : fields) p.setObject(i++, f.get(e));
            p.executeUpdate();
        }
    }

    public T getById(String id) throws Exception { List<T> l = query("WHERE \"" + pk + "\" = ?", id); return l.isEmpty() ? null : l.get(0); }
    public List<T> getAll() throws Exception { return query(""); }
    public List<T> getByField(String field, Object value) throws Exception { return query("WHERE \"" + field + "\" = ?", value); }

    public void delete(String id) throws Exception {
        try (Connection c = Db.get(); PreparedStatement p = c.prepareStatement("DELETE FROM \"" + table + "\" WHERE \"" + pk + "\" = ?")) {
            p.setString(1, id); p.executeUpdate();
        }
    }

    private List<T> query(String where, Object... args) throws Exception {
        List<T> out = new ArrayList<>();
        try (Connection c = Db.get(); PreparedStatement p = c.prepareStatement("SELECT * FROM \"" + table + "\" " + where)) {
            for (int i = 0; i < args.length; i++) p.setObject(i + 1, args[i]);
            try (ResultSet rs = p.executeQuery()) {
                while (rs.next()) {
                    T o = cls.getDeclaredConstructor().newInstance();
                    for (Field f : fields) {
                        Class<?> t = f.getType(); String n = f.getName();
                        if (t == double.class || t == Double.class) f.set(o, rs.getDouble(n));
                        else if (t == long.class || t == Long.class) f.set(o, rs.getLong(n));
                        else if (t == boolean.class || t == Boolean.class) f.set(o, rs.getBoolean(n));
                        else f.set(o, rs.getString(n));
                    }
                    out.add(o);
                }
            }
        }
        return out;
    }
}