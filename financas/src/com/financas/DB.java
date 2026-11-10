package com.financas;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** Fábrica de conexões JDBC com o MySQL. */
public final class DB {
    private DB() {}

    public static Connection open() throws SQLException {
        return DriverManager.getConnection(Config.get("db.url"), Config.get("db.user"), Config.get("db.password"));
    }
}
