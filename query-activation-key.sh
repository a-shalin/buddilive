#!/bin/bash
# Query the latest activation key from the standalone Derby database.
# The BuddiLive server must be stopped before running this script.

set -e

DERBY_JAR="$HOME/.m2/repository/org/apache/derby/derby/10.9.1.0/derby-10.9.1.0.jar"
DB_PATH="$HOME/.buddilive/derby"
TMPDIR=$(mktemp -d)

cat > "$TMPDIR/Query.java" <<'JAVA'
import java.sql.*;

public class Query {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:derby:directory:" + args[0];
        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT ua.activation_key, u.email, ua.created " +
                 "FROM user_activations ua " +
                 "JOIN users u ON u.id = ua.user_id " +
                 "ORDER BY ua.created DESC")) {
            int count = 0;
            while (rs.next()) {
                System.out.println("Key:     " + rs.getString(1));
                System.out.println("Email:   " + rs.getString(2));
                System.out.println("Created: " + rs.getString(3));
                System.out.println();
                count++;
            }
            if (count == 0) {
                System.out.println("No activation keys found.");
            }
        }
    }
}
JAVA

javac -cp "$DERBY_JAR" "$TMPDIR/Query.java"
java -cp "$DERBY_JAR:$TMPDIR" Query "$DB_PATH"
rm -rf "$TMPDIR"
