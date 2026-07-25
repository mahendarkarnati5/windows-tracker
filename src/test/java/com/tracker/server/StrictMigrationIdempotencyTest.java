package com.tracker.server;

import java.sql.Connection;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class StrictMigrationIdempotencyTest {

    private static final List<String> RECOVERY_MIGRATIONS = List.of(
            "db/migration/V9__strict_activity_columns_recovery.sql",
            "db/migration/V10__strict_activity_unique_indexes_recovery.sql",
            "db/migration/V11__strict_activity_open_lookup_indexes_recovery.sql",
            "db/migration/V12__strict_activity_dashboard_indexes_recovery.sql");

    @Autowired
    private DataSource dataSource;

    @Test
    void strictMigrationsCanBeReplayedAfterPartialDeployment() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            for (int replay = 0; replay < 2; replay++) {
                for (String migration : RECOVERY_MIGRATIONS) {
                    ScriptUtils.executeSqlScript(connection, new ClassPathResource(migration));
                }
            }
        }
    }
}
