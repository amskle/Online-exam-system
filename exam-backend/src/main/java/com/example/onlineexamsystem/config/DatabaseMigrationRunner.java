package com.example.onlineexamsystem.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;

/**
 * 数据库迁移运行器（启动时自动执行 DDL 变更和种子数据）
 */
@Component
@RequiredArgsConstructor
public class DatabaseMigrationRunner implements ApplicationRunner {
    private static final String SEED_408_KEY = "408-question-bank-2009-2021-v1";

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    /**
     * 启动时执行数据库迁移和种子数据初始化
     */
    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            String databaseName = connection.getCatalog();
            addColumnIfMissing(databaseName, "exam_paper", "max_attempts",
                    "ALTER TABLE exam_paper ADD COLUMN max_attempts INT NOT NULL DEFAULT 1 COMMENT '考试次数限制' AFTER duration");
            addColumnIfMissing(databaseName, "exam_record", "attempt_count",
                    "ALTER TABLE exam_record ADD COLUMN attempt_count INT NOT NULL DEFAULT 1 COMMENT '当前记录累计考试次数' AFTER pass_score");
            addColumnIfMissing(databaseName, "exam_record", "warning_count",
                    "ALTER TABLE exam_record ADD COLUMN warning_count INT NOT NULL DEFAULT 0 COMMENT '切屏/离开页面次数' AFTER attempt_count");
            addColumnIfMissing(databaseName, "user", "email",
                    "ALTER TABLE user ADD COLUMN email VARCHAR(254) NULL COMMENT '邮箱' AFTER phone");
            addColumnIfMissing(databaseName, "user", "email_verify_time",
                    "ALTER TABLE user ADD COLUMN email_verify_time DATETIME NULL COMMENT '邮箱验证时间' AFTER email");
            addIndexIfMissing(databaseName, "user", "uk_user_email",
                    "ALTER TABLE user ADD UNIQUE INDEX uk_user_email (email)");
        }
        jdbcTemplate.update("UPDATE exam_paper SET max_attempts = 1 WHERE max_attempts IS NULL OR max_attempts < 1");
        jdbcTemplate.update("UPDATE exam_record SET attempt_count = 1 WHERE attempt_count IS NULL OR attempt_count < 1");
        runSeedOnce();
    }

    /**
     * 检查列是否存在，不存在则执行 ALTER TABLE 添加
     *
     * @param databaseName 数据库名
     * @param tableName    表名
     * @param columnName   列名
     * @param alterSql     添加列的 SQL
     */
    private void addColumnIfMissing(String databaseName, String tableName, String columnName, String alterSql) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = ?
                  AND TABLE_NAME = ?
                  AND COLUMN_NAME = ?
                """,
                Integer.class,
                databaseName,
                tableName,
                columnName
        );
        if (count == null || count == 0) {
            jdbcTemplate.execute(alterSql);
        }
    }

    private void addIndexIfMissing(String databaseName, String tableName, String indexName, String alterSql) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = ?
                  AND TABLE_NAME = ?
                  AND INDEX_NAME = ?
                """,
                Integer.class,
                databaseName,
                tableName,
                indexName
        );
        if (count == null || count == 0) {
            jdbcTemplate.execute(alterSql);
        }
    }

    /**
     * 执行一次种子数据初始化（以 seed_key 防重）
     */
    private void runSeedOnce() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS data_seed_log (
                  seed_key VARCHAR(100) PRIMARY KEY,
                  executed_time DATETIME NOT NULL
                )
                """);
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM data_seed_log WHERE seed_key = ?",
                Integer.class,
                SEED_408_KEY
        );
        if (count != null && count > 0) {
            return;
        }
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.setSqlScriptEncoding("UTF-8");
        populator.addScript(new ClassPathResource("data-408.sql"));
        populator.execute(dataSource);
        jdbcTemplate.update("INSERT INTO data_seed_log (seed_key, executed_time) VALUES (?, ?)", SEED_408_KEY, LocalDateTime.now());
    }
}
