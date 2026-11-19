package com.itc.healthtrack.config;

import java.sql.*;
import java.util.UUID;

// cuentas (correo + contraseña cifrada con bcrypt de pgcrypto) en la tabla auth
public class Auth {
    private Auth() {}

    // crea la cuenta y regresa el uid nuevo
    public static String register(String email, String password) throws SQLException {
        String uid = UUID.randomUUID().toString();
        try (Connection c = Db.get(); PreparedStatement p = c.prepareStatement(
                "INSERT INTO auth (email, uid, hash) VALUES (?, ?, crypt(?, gen_salt('bf')))")) {
            p.setString(1, email.toLowerCase()); p.setString(2, uid); p.setString(3, password);
            p.executeUpdate();
            return uid;
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) throw new SQLException("Ese correo ya está registrado.");
            throw e;
        }
    }

    // regresa el uid si correo y contraseña son correctos, o null
    public static String login(String email, String password) throws SQLException {
        try (Connection c = Db.get(); PreparedStatement p = c.prepareStatement(
                "SELECT uid FROM auth WHERE email = ? AND hash = crypt(?, hash)")) {
            p.setString(1, email.toLowerCase()); p.setString(2, password);
            try (ResultSet rs = p.executeQuery()) { return rs.next() ? rs.getString(1) : null; }
        }
    }
}
