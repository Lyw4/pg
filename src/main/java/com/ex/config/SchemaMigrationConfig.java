package com.ex.config;

import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class SchemaMigrationConfig {

    /*
     * H2가 과거 enum 목록으로 만든 CHECK 제약조건을 제거합니다.
     * 상태 값은 애플리케이션 enum으로 계속 검증하며 기존 데이터는 보존합니다.
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    CommandLineRunner relaxDeliveryStatusConstraints(
            JdbcTemplate jdbcTemplate) {

        return args -> {
            List<ConstraintTarget> constraints = jdbcTemplate.query("""
                    select table_name, constraint_name
                    from information_schema.table_constraints
                    where constraint_type = 'CHECK'
                      and table_name in ('DELIVERY', 'DELIVERY_STATUS_HISTORY')
                    """,
                    (resultSet, rowNumber) -> new ConstraintTarget(
                            resultSet.getString("table_name"),
                            resultSet.getString("constraint_name")));

            constraints.stream()
                    .filter(target -> target.tableName().matches("[A-Z0-9_]+"))
                    .filter(target -> target.constraintName().matches("[A-Z0-9_]+"))
                    .forEach(target -> jdbcTemplate.execute(
                            "alter table " + target.tableName()
                                    + " drop constraint "
                                    + target.constraintName()));
        };
    }

    private record ConstraintTarget(
            String tableName,
            String constraintName) {
    }
}
