package com.itc.healthtrack.config;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

// conexion JDBC a PostgreSQL (la VM de VirtualBox); los datos salen de db.properties
public class Db {
    private static final Properties P = new Properties();
    static {
        try (InputStream in = Db.class.getClassLoader().getResourceAsStream("db.properties")) {
            if (in == null) throw new RuntimeException("Falta src/main/resources/db.properties");
            P.load(in);
        } catch (java.io.IOException e) { throw new RuntimeException(e); }
        try (Connection c = get(); Statement s = c.createStatement()) {
            try { s.execute("CREATE EXTENSION IF NOT EXISTS pgcrypto"); } catch (SQLException ignored) { }
            s.execute("CREATE TABLE IF NOT EXISTS auth (email TEXT PRIMARY KEY, uid TEXT NOT NULL, hash TEXT NOT NULL)");
        } catch (SQLException e) { throw new RuntimeException("No se pudo conectar a PostgreSQL: " + e.getMessage(), e); }
    }
    private Db() {}
    public static Connection get() throws SQLException {
        String url = "jdbc:postgresql://" + P.getProperty("db.host") + ":" + P.getProperty("db.port", "5432") + "/" + P.getProperty("db.name");
        return DriverManager.getConnection(url, P.getProperty("db.user"), P.getProperty("db.password"));
    }
}
