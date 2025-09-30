package com.stream.pipeline.tools;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;

public class DbPing {
    public static void main(String[] args) throws Exception {
        Properties cfg = new Properties();
        cfg.load(DbPing.class.getClassLoader().getResourceAsStream("app.properties"));

        String url  = cfg.getProperty("db.url");
        String user = cfg.getProperty("db.user");
        String pass = cfg.getProperty("db.pass");

        System.out.println("JDBC URL = " + url);
        System.out.println("USER     = " + user);
        System.out.println("PASS LEN = " + (pass == null ? "null" : pass.length()));

        try (Connection cn = DriverManager.getConnection(url, user, pass);
             Statement st = cn.createStatement();
             ResultSet rs = st.executeQuery("SELECT 1")) {
            rs.next();
            System.out.println("DB PING OK, result = " + rs.getInt(1));
        }
    }
}
