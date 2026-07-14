package com.feijimiao.xianyuassistant.config;

import java.sql.Statement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SqlScriptExecutor {

    private SqlScriptExecutor() {
    }

    public static int executeSqlScript(Statement stmt, String sql) throws Exception {
        int executedCount = 0;
        for (String sqlStatement : splitSqlStatements(sql)) {
            String cleanSql = removeComments(sqlStatement.trim());
            if (!cleanSql.isEmpty()) {
                try {
                    stmt.execute(cleanSql);
                    executedCount++;
                } catch (SQLException e) {
                    if (!isIgnorableAlreadyAppliedColumn(cleanSql, e)) {
                        throw e;
                    }
                }
            }
        }
        return executedCount;
    }

    private static boolean isIgnorableAlreadyAppliedColumn(String sql, SQLException e) {
        String normalizedSql = sql.toUpperCase(Locale.ROOT);
        String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
        return normalizedSql.startsWith("ALTER TABLE")
                && normalizedSql.contains("ADD COLUMN")
                && message.contains("duplicate column name");
    }

    public static List<String> splitSqlStatements(String sql) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inTrigger = false;

        for (String line : sql.split("\n")) {
            String trimmed = line.trim();

            if (trimmed.startsWith("--")) {
                continue;
            }

            if (trimmed.toUpperCase().startsWith("CREATE TRIGGER")) {
                inTrigger = true;
                current = new StringBuilder();
            }

            current.append(line).append("\n");

            if (inTrigger) {
                if (trimmed.equalsIgnoreCase("END;")) {
                    statements.add(current.toString().trim());
                    current = new StringBuilder();
                    inTrigger = false;
                }
            } else if (trimmed.endsWith(";")) {
                statements.add(current.toString().trim());
                current = new StringBuilder();
            }
        }

        String remaining = current.toString().trim();
        if (!remaining.isEmpty()) {
            statements.add(remaining);
        }

        return statements;
    }

    public static String removeComments(String sql) {
        if (sql == null || sql.isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        for (String line : sql.split("\n")) {
            String trimmedLine = line.trim();
            if (!trimmedLine.startsWith("--") && !trimmedLine.isEmpty()) {
                result.append(line).append("\n");
            }
        }
        return result.toString().trim();
    }
}
