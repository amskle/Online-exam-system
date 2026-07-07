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

@Component
@RequiredArgsConstructor
public class DatabaseMigrationRunner implements ApplicationRunner {
    private static final String SEED_408_KEY = "408-question-bank-2009-2021-v1";

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            String databaseName = connection.getCatalog();
            addColumnIfMissing(databaseName, "exam_paper", "max_attempts",
                    "ALTER TABLE exam_paper ADD COLUMN max_attempts INT NOT NULL DEFAULT 1 COMMENT '考试次数限制' AFTER duration");
            addColumnIfMissing(databaseName, "exam_record", "attempt_count",
                    "ALTER TABLE exam_record ADD COLUMN attempt_count INT NOT NULL DEFAULT 1 COMMENT '当前记录累计考试次数' AFTER pass_score");
        }
        jdbcTemplate.update("UPDATE exam_paper SET max_attempts = 1 WHERE max_attempts IS NULL OR max_attempts < 1");
        jdbcTemplate.update("UPDATE exam_record SET attempt_count = 1 WHERE attempt_count IS NULL OR attempt_count < 1");
        runSeedOnce();
    }

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
