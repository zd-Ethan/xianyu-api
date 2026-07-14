package com.feijimiao.xianyuassistant.config;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
public final class DatabaseBackupHelper {

    private static final DateTimeFormatter BACKUP_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private DatabaseBackupHelper() {
    }

    public static Path backupDatabase(Connection conn, String reason) {
        try {
            String url = conn.getMetaData().getURL();
            return backupDatabase(url, reason);
        } catch (Exception e) {
            log.warn("Create database backup skipped, failed to resolve database path: {}", e.getMessage());
            return null;
        }
    }

    public static Path backupDatabase(String databaseUrl, String reason) {
        try {
            File dbFile = resolveSqliteFile(databaseUrl);
            if (dbFile == null) {
                return null;
            }

            Path dbPath = dbFile.toPath();
            if (!Files.exists(dbPath) || Files.size(dbPath) == 0) {
                log.info("Database backup skipped, database file is empty or missing: {}", dbPath.toAbsolutePath());
                return null;
            }

            Path backupDir = dbPath.getParent() == null
                    ? Path.of("backups")
                    : dbPath.getParent().resolve("backups");
            Files.createDirectories(backupDir);

            String safeReason = reason == null || reason.isBlank()
                    ? "before_change"
                    : reason.replaceAll("[^A-Za-z0-9_-]", "_");
            String fileName = stripExtension(dbFile.getName())
                    + "_"
                    + BACKUP_TIME_FORMATTER.format(LocalDateTime.now())
                    + "_"
                    + safeReason
                    + ".db";
            Path backupPath = backupDir.resolve(fileName);

            Files.copy(dbPath, backupPath, StandardCopyOption.COPY_ATTRIBUTES);
            log.info("Database backup created: {}", backupPath.toAbsolutePath());
            return backupPath;
        } catch (Exception e) {
            throw new IllegalStateException("Create database backup failed: " + e.getMessage(), e);
        }
    }

    private static File resolveSqliteFile(String databaseUrl) {
        if (databaseUrl == null || !databaseUrl.startsWith("jdbc:sqlite:")) {
            return null;
        }

        String path = databaseUrl.substring("jdbc:sqlite:".length());
        int queryIndex = path.indexOf('?');
        if (queryIndex >= 0) {
            path = path.substring(0, queryIndex);
        }

        if (path.isBlank() || ":memory:".equalsIgnoreCase(path)) {
            log.info("Database backup skipped for in-memory SQLite database.");
            return null;
        }
        return new File(path);
    }

    private static String stripExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex <= 0) {
            return fileName;
        }
        return fileName.substring(0, dotIndex);
    }
}
