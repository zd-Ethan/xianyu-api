package com.feijimiao.xianyuassistant.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.util.FileCopyUtils;

import javax.sql.DataSource;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;

/**
 * 数据库配置类
 */
@Slf4j
@Configuration
public class DatabaseConfig {

    @Value("${spring.datasource.url:jdbc:sqlite:xianyu_assistant.db}")
    private String databaseUrl;

    @Bean
    public DataSource dataSource() {
        log.info("初始化SQLite数据库...");
        
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.sqlite.JDBC");
        dataSource.setUrl(databaseUrl);
        
        // 打印数据库文件路径
        String dbPath = databaseUrl.replace("jdbc:sqlite:", "");
        File dbFile = new File(dbPath);
        try {
            log.info("数据库文件路径: {}", dbFile.getCanonicalPath());
        } catch (Exception e) {
            log.warn("获取数据库文件绝对路径失败: {}", e.getMessage());
            log.info("数据库文件路径(相对路径): {}", dbPath);
        }
        
        // 初始化数据库表结构
        initDatabase(dataSource);
        
        log.info("SQLite数据库初始化完成");
        return dataSource;
    }

    /**
     * 初始化数据库表结构
     */
    private void initDatabase(DataSource dataSource) {
        try {
            // 检查数据库文件是否存在
            String dbPath = databaseUrl.replace("jdbc:sqlite:", "");
            File dbFile = new File(dbPath);
            
            // 确保数据库目录存在
            File dbDir = dbFile.getParentFile();
            if (dbDir != null && !dbDir.exists()) {
                boolean created = dbDir.mkdirs();
                if (created) {
                    log.info("创建数据库目录: {}", dbDir.getAbsolutePath());
                } else {
                    log.warn("数据库目录创建失败: {}", dbDir.getAbsolutePath());
                }
            }
            
            boolean isNewDatabase = !dbFile.exists();
            
            if (isNewDatabase) {
                log.info("数据库文件不存在，将创建新数据库: {}", dbFile.getAbsolutePath());
            } else {
                log.info("使用现有数据库文件: {}", dbFile.getAbsolutePath());
            }

            // 读取SQL脚本
            ClassPathResource resource = new ClassPathResource("sql/schema.sql");
            String sql = FileCopyUtils.copyToString(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)
            );

            // 执行SQL脚本
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                
                int executedCount = SqlScriptExecutor.executeSqlScript(stmt, sql);
                
                if (isNewDatabase) {
                    log.info("数据库表结构创建成功，执行了 {} 条SQL语句", executedCount);
                } else {
                    log.info("数据库表结构检查完成，执行了 {} 条SQL语句", executedCount);
                }
            }

            new DatabaseMigrationRunner().migrate(dataSource);

            // 验证表是否创建成功
            verifyTables(dataSource);

        } catch (Exception e) {
            log.error("初始化数据库失败", e);
            throw new RuntimeException("初始化数据库失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 验证表是否创建成功
     */
    private void verifyTables(DataSource dataSource) {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            
            // 查询表列表
            var rs = stmt.executeQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'"
            );
            
            log.info("数据库表列表:");
            while (rs.next()) {
                String tableName = rs.getString("name");
                log.info("  - {}", tableName);
            }
            
        } catch (Exception e) {
            log.error("验证数据库表失败", e);
        }
    }
}
