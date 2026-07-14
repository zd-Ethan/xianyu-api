package com.feijimiao.xianyuassistant.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.FileCopyUtils;

import javax.sql.DataSource;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Slf4j
public class DatabaseMigrationRunner {

    private static final String MIGRATION_LOCATION = "classpath*:sql/migration/V*__*.sql";

    public void migrate(DataSource dataSource) {
        try (Connection conn = dataSource.getConnection()) {
            ensureSchemaVersionTable(conn);

            List<MigrationScript> scripts = loadMigrationScripts();
            if (scripts.isEmpty()) {
                log.info("No database migration scripts found.");
                return;
            }

            Map<String, String> installedChecksums = loadInstalledChecksums(conn);
            List<MigrationScript> pendingScripts = new ArrayList<>();
            for (MigrationScript script : scripts) {
                String installedChecksum = installedChecksums.get(script.version());
                if (installedChecksum == null) {
                    pendingScripts.add(script);
                } else if (!installedChecksum.equals(script.checksum())) {
                    throw new IllegalStateException("Migration checksum mismatch: " + script.fileName());
                }
            }

            if (pendingScripts.isEmpty()) {
                log.info("Database migrations are up to date. Installed count: {}", installedChecksums.size());
                return;
            }

            DatabaseBackupHelper.backupDatabase(conn, "before_migration");
            for (MigrationScript script : pendingScripts) {
                applyMigration(conn, script);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Database migration failed: " + e.getMessage(), e);
        }
    }

    private void ensureSchemaVersionTable(Connection conn) throws Exception {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS schema_version (
                        version VARCHAR(50) PRIMARY KEY,
                        description VARCHAR(200),
                        script VARCHAR(200) NOT NULL,
                        checksum VARCHAR(64) NOT NULL,
                        installed_on DATETIME DEFAULT (datetime('now', 'localtime'))
                    )
                    """);
        }
    }

    private Map<String, String> loadInstalledChecksums(Connection conn) throws Exception {
        Map<String, String> installed = new HashMap<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT version, checksum FROM schema_version")) {
            while (rs.next()) {
                installed.put(rs.getString("version"), rs.getString("checksum"));
            }
        }
        return installed;
    }

    private List<MigrationScript> loadMigrationScripts() throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources(MIGRATION_LOCATION);
        List<MigrationScript> scripts = new ArrayList<>();

        for (Resource resource : resources) {
            String fileName = resource.getFilename();
            if (fileName == null || !fileName.startsWith("V") || !fileName.contains("__")) {
                continue;
            }

            String version = extractVersion(fileName);
            String description = extractDescription(fileName);
            String sql = FileCopyUtils.copyToString(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)
            );
            scripts.add(new MigrationScript(version, description, fileName, sha256(sql), sql));
        }

        scripts.sort(Comparator.comparing(MigrationScript::version, DatabaseMigrationRunner::compareVersion));
        return scripts;
    }

    private void applyMigration(Connection conn, MigrationScript script) throws Exception {
        boolean previousAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try (Statement stmt = conn.createStatement()) {
            int executedCount = SqlScriptExecutor.executeSqlScript(stmt, script.sql());
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO schema_version (version, description, script, checksum) VALUES (?, ?, ?, ?)")) {
                ps.setString(1, script.version());
                ps.setString(2, script.description());
                ps.setString(3, script.fileName());
                ps.setString(4, script.checksum());
                ps.executeUpdate();
            }
            conn.commit();
            log.info("Applied database migration {} ({} statements)", script.fileName(), executedCount);
        } catch (Exception e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(previousAutoCommit);
        }
    }

    private static String extractVersion(String fileName) {
        int separator = fileName.indexOf("__");
        return fileName.substring(1, separator).replace('_', '.');
    }

    private static String extractDescription(String fileName) {
        int separator = fileName.indexOf("__");
        int dot = fileName.lastIndexOf('.');
        if (dot < separator) {
            dot = fileName.length();
        }
        return fileName.substring(separator + 2, dot).replace('_', ' ');
    }

    private static String sha256(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        String normalizedValue = value.replace("\r\n", "\n").replace("\r", "\n");
        return HexFormat.of().formatHex(digest.digest(normalizedValue.getBytes(StandardCharsets.UTF_8))).toUpperCase();
    }

    private static int compareVersion(String left, String right) {
        String[] leftParts = left.split("\\.");
        String[] rightParts = right.split("\\.");
        int max = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < max; i++) {
            int compare = compareVersionPart(partAt(leftParts, i), partAt(rightParts, i));
            if (compare != 0) {
                return compare;
            }
        }
        return 0;
    }

    private static String partAt(String[] parts, int index) {
        return index < parts.length ? parts[index] : "0";
    }

    private static int compareVersionPart(String left, String right) {
        try {
            return Integer.compare(Integer.parseInt(left), Integer.parseInt(right));
        } catch (NumberFormatException ignored) {
            return left.compareTo(right);
        }
    }

    private record MigrationScript(
            String version,
            String description,
            String fileName,
            String checksum,
            String sql
    ) {
    }
}
