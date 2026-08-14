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
    CommandLineRunner migrateIntegratedOrderSchema(
            JdbcTemplate jdbcTemplate) {

        return args -> {
            ensureOptimisticLockColumns(jdbcTemplate);
            normalizeMemberUsernames(jdbcTemplate);
            normalizeCustomerOrderFlags(jdbcTemplate);
            normalizeCustomerOrderEnumColumns(jdbcTemplate);
            normalizeLegacyOrderItemLotColumn(jdbcTemplate);
            repairCustomerOrderForeignKeys(jdbcTemplate);
            migrateLegacyPurchaseOrders(jdbcTemplate);
            migrateLegacyOrderItemLots(jdbcTemplate);
            ensurePaymentTransactionUniqueIndex(jdbcTemplate);

            List<ConstraintTarget> constraints = jdbcTemplate.query("""
                    select table_name, constraint_name
                    from information_schema.table_constraints
                    where constraint_type = 'CHECK'
                      and table_name in (
                          'CUSTOMER_ORDER',
                          'DELIVERY',
                          'DELIVERY_STATUS_HISTORY'
                      )
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

    /**
     * 기존 파일 H2는 Hibernate 자동 갱신이 @Version 컬럼을 추가하지 못하는
     * 경우가 있으므로 데이터 초기화가 시작되기 전에 명시적으로 보강한다.
     * 기존 행은 version=0으로 시작하며 데이터와 기본키는 그대로 보존된다.
     */
    private void ensureOptimisticLockColumns(JdbcTemplate jdbcTemplate) {
        for (String tableName : List.of(
                "PRODUCT_LOT", "BIN_INVENTORY", "WAREHOUSE_ALLOCATION")) {
            if (!tableExists(jdbcTemplate, tableName)) continue;
            jdbcTemplate.execute(
                    "alter table " + tableName
                            + " add column if not exists version bigint default 0 not null");
            jdbcTemplate.update(
                    "update " + tableName + " set version = 0 where version is null");
        }
    }

    /** 결제 공급자 거래번호는 한 주문에서만 사용할 수 있어야 합니다. */
    private void ensurePaymentTransactionUniqueIndex(JdbcTemplate jdbcTemplate) {
        if (tableExists(jdbcTemplate, "CUSTOMER_ORDER")
                && columnExists(jdbcTemplate, "CUSTOMER_ORDER", "PROVIDER_TRANSACTION_ID")) {
            // 과거 테스트 데이터에 중복 거래번호가 남아 있으면 가장 먼저 저장된
            // 주문만 보존하고 나머지는 미연결 상태로 돌려 인덱스 생성을 계속합니다.
            jdbcTemplate.update("""
                    update customer_order
                    set provider_transaction_id = null
                    where provider_transaction_id is not null
                      and order_id not in (
                          select min(order_id)
                          from customer_order
                          where provider_transaction_id is not null
                          group by provider_transaction_id
                      )
                    """);
            // Hibernate가 이미 같은 이름의 UNIQUE 제약/인덱스를 만든 경우
            // H2는 해당 인덱스를 직접 삭제할 수 없으므로 IF NOT EXISTS로 유지합니다.
            jdbcTemplate.execute("create unique index if not exists "
                    + "idx_customer_order_provider_tx "
                    + "on customer_order(provider_transaction_id)");
        }
    }

    /**
     * Hibernate/H2 버전에 따라 @Enumerated(EnumType.STRING)이 과거에
     * native ENUM 또는 오래된 CHECK 제약조건으로 만들어진 경우가 있습니다.
     * 애플리케이션은 문자열 enum을 사용하므로 문자열 컬럼으로 맞춰야
     * PAYMENT_PENDING 같은 신규 상태를 기존 DB에서도 저장할 수 있습니다.
     */
    private void normalizeCustomerOrderEnumColumns(
            JdbcTemplate jdbcTemplate) {
        if (!tableExists(jdbcTemplate, "CUSTOMER_ORDER")) {
            return;
        }
        for (String column : new String[]{
                "STATUS",
                "PAYMENT_METHOD",
                "PAYMENT_PROVIDER",
                "PAYMENT_STATUS",
                "ORDER_CHANNEL"}) {
            if (columnExists(jdbcTemplate, "CUSTOMER_ORDER", column)) {
                String dataType = jdbcTemplate.queryForObject("""
                        select data_type
                        from information_schema.columns
                        where table_schema = 'PUBLIC'
                          and table_name = 'CUSTOMER_ORDER'
                          and column_name = ?
                        """, String.class, column);
                if (dataType != null
                        && !"CHARACTER VARYING".equalsIgnoreCase(dataType)
                        && !"VARCHAR".equalsIgnoreCase(dataType)) {
                    jdbcTemplate.execute(
                            "alter table customer_order alter column "
                                    + column + " varchar(255)");
                }
            }
        }
    }

    /**
     * 예전 주문 스키마는 order_item.lot_id를 필수 컬럼으로 두었지만,
     * 현재 주문은 한 주문 항목이 여러 LOT로 나뉠 수 있어
     * order_lot_allocations 테이블에 LOT를 저장합니다.
     *
     * 기존 H2 파일 DB를 그대로 사용하는 경우 Hibernate가 lot_id를
     * INSERT하지 않으므로 해당 컬럼이 NOT NULL이면 신규 결제가 실패합니다.
     * 기존 LOT 값은 아래의 레거시 마이그레이션에서 allocations로 복사하고,
     * 이후에는 null을 허용해 현재 모델과 호환시킵니다.
     */
    private void normalizeLegacyOrderItemLotColumn(
            JdbcTemplate jdbcTemplate) {
        if (!tableExists(jdbcTemplate, "ORDER_ITEM")
                || !columnExists(jdbcTemplate, "ORDER_ITEM", "LOT_ID")) {
            return;
        }
        jdbcTemplate.execute(
                "alter table order_item alter column lot_id drop not null");
    }

    /**
     * H2가 CUSTOMER_ORDER 컬럼 타입을 변경할 때 COPY 테이블을 만들 수
     * 있습니다. 기존 외래키가 그 임시 테이블을 계속 참조하면 신규 주문의
     * order_item 저장이 실패하므로 실제 CUSTOMER_ORDER로 복구합니다.
     */
    private void repairCustomerOrderForeignKeys(
            JdbcTemplate jdbcTemplate) {
        if (!tableExists(jdbcTemplate, "CUSTOMER_ORDER")) {
            return;
        }

        for (String tableName : List.of("ORDER_ITEM", "DELIVERY", "SHIPMENT")) {
            repairCustomerOrderForeignKey(jdbcTemplate, tableName);
        }
    }

    private void repairCustomerOrderForeignKey(
            JdbcTemplate jdbcTemplate,
            String tableName) {
        if (!tableName.matches("[A-Z0-9_]+")
                || !tableExists(jdbcTemplate, tableName)
                || !columnExists(jdbcTemplate, tableName, "ORDER_ID")) {
            return;
        }

        List<ForeignKeyTarget> foreignKeys = jdbcTemplate.query("""
                select fk.constraint_name,
                       referenced.table_name as referenced_table_name
                from information_schema.table_constraints fk
                join information_schema.referential_constraints ref
                  on ref.constraint_schema = fk.constraint_schema
                 and ref.constraint_name = fk.constraint_name
                join information_schema.table_constraints referenced
                  on referenced.constraint_schema =
                         ref.unique_constraint_schema
                 and referenced.constraint_name =
                         ref.unique_constraint_name
                where fk.table_schema = 'PUBLIC'
                  and fk.table_name = ?
                  and fk.constraint_type = 'FOREIGN KEY'
                """,
                (resultSet, rowNumber) -> new ForeignKeyTarget(
                        resultSet.getString("constraint_name"),
                        resultSet.getString("referenced_table_name")),
                tableName);

        List<ForeignKeyTarget> invalidForeignKeys = foreignKeys.stream()
                .filter(foreignKey -> isCustomerOrderCopyForeignKey(
                        foreignKey))
                .filter(foreignKey -> foreignKey.constraintName()
                        .matches("[A-Z0-9_]+"))
                .toList();

        invalidForeignKeys.forEach(foreignKey -> jdbcTemplate.execute(
                        "alter table " + tableName + " drop constraint "
                                + foreignKey.constraintName()));

        boolean hasCustomerOrderForeignKey = foreignKeys.stream()
                .filter(foreignKey -> !isCustomerOrderCopyForeignKey(
                        foreignKey))
                .anyMatch(foreignKey -> "CUSTOMER_ORDER"
                        .equalsIgnoreCase(foreignKey.referencedTableName()));

        if (!hasCustomerOrderForeignKey) {
            jdbcTemplate.execute(
                    "alter table " + tableName
                            + " add constraint FK_" + tableName
                            + "_CUSTOMER_ORDER"
                            + " foreign key (order_id)"
                            + " references customer_order(order_id)");
        }
    }

    private boolean isCustomerOrderCopyForeignKey(
            ForeignKeyTarget foreignKey) {
        return foreignKey.constraintName()
                        .matches("(?i)CUSTOMER_ORDER_COPY_[A-Z0-9_]+")
                || foreignKey.referencedTableName()
                        .matches("(?i)CUSTOMER_ORDER_COPY_[A-Z0-9_]+");
    }

    private void normalizeMemberUsernames(JdbcTemplate jdbcTemplate) {
        if (!tableExists(jdbcTemplate, "MEMBER")) {
            return;
        }
        jdbcTemplate.execute("""
                alter table member
                add column if not exists username varchar(20)
                """);
        jdbcTemplate.update("""
                update member
                set username = concat('member', member_id)
                where username is null
                   or trim(username) = ''
                """);
        jdbcTemplate.execute("""
                create unique index if not exists
                    idx_member_username
                on member(username)
                """);
    }

    private void normalizeCustomerOrderFlags(JdbcTemplate jdbcTemplate) {
        if (!tableExists(jdbcTemplate, "CUSTOMER_ORDER")) {
            return;
        }
        if (columnExists(
                jdbcTemplate,
                "CUSTOMER_ORDER",
                "REGULAR_DELIVERY")) {
            jdbcTemplate.update("""
                    update customer_order
                    set regular_delivery = false
                    where regular_delivery is null
                    """);
        }
        if (columnExists(
                jdbcTemplate,
                "CUSTOMER_ORDER",
                "INVENTORY_COMMITTED")) {
            jdbcTemplate.update("""
                    update customer_order
                    set inventory_committed = false
                    where inventory_committed is null
                    """);
        }
        if (columnExists(
                jdbcTemplate,
                "CUSTOMER_ORDER",
                "ORDER_NUMBER")) {
            jdbcTemplate.update("""
                    update customer_order
                    set order_number =
                        concat('LEGACY-CO-', order_id)
                    where order_number is null
                       or trim(order_number) = ''
                    """);
        }
        if (columnExists(
                jdbcTemplate,
                "CUSTOMER_ORDER",
                "ORDER_CHANNEL")) {
            jdbcTemplate.update("""
                    update customer_order
                    set order_channel = 'ADMIN'
                    where order_channel is null
                    """);
        }
        if (columnExists(
                jdbcTemplate,
                "CUSTOMER_ORDER",
                "CUSTOMER_NAME")) {
            jdbcTemplate.update("""
                    update customer_order
                    set customer_name =
                        coalesce(customer_name, recipient_name),
                        phone = coalesce(phone, recipient_phone),
                        product_amount =
                            coalesce(
                                product_amount,
                                total_price,
                                0),
                        delivery_fee =
                            coalesce(delivery_fee, 0),
                        discount_price =
                            coalesce(discount_price, 0),
                        total_price =
                            coalesce(
                                total_price,
                                product_amount,
                                0),
                        final_price =
                            coalesce(
                                final_price,
                                total_price - discount_price,
                                0),
                        updated_at =
                            coalesce(updated_at, created_at)
                    """);
        }
    }

    private void migrateLegacyPurchaseOrders(
            JdbcTemplate jdbcTemplate) {
        if (!tableExists(jdbcTemplate, "PURCHASE_ORDER")
                || !tableExists(
                        jdbcTemplate,
                        "PURCHASE_ORDER_ITEM")
                || !tableExists(
                        jdbcTemplate,
                        "CUSTOMER_ORDER")
                || !tableExists(jdbcTemplate, "ORDER_ITEM")) {
            return;
        }

        jdbcTemplate.update("""
                insert into customer_order (
                    order_number,
                    user_id,
                    member_id,
                    order_channel,
                    payment_method,
                    customer_name,
                    phone,
                    recipient_name,
                    recipient_phone,
                    shipping_address,
                    detail_address,
                    unloading_location,
                    delivery_request,
                    product_amount,
                    delivery_fee,
                    total_price,
                    discount_price,
                    final_price,
                    regular_delivery,
                    inventory_committed,
                    status,
                    created_at,
                    updated_at
                )
                select
                    purchase.order_number,
                    coalesce(purchase.member_id, 0),
                    purchase.member_id,
                    'SHOP',
                    purchase.payment_method,
                    purchase.customer_name,
                    purchase.phone,
                    purchase.customer_name,
                    purchase.phone,
                    purchase.address,
                    purchase.detail_address,
                    purchase.unloading_location,
                    purchase.delivery_request,
                    purchase.product_amount,
                    purchase.delivery_fee,
                    purchase.product_amount
                        + purchase.delivery_fee,
                    purchase.discount_amount,
                    purchase.total_amount,
                    false,
                    case
                        when purchase.status = 'CANCELLED'
                            then false
                        else true
                    end,
                    case
                        when purchase.status = 'PAYMENT_PENDING'
                            then 'PAID'
                        else purchase.status
                    end,
                    purchase.created_at,
                    purchase.updated_at
                from purchase_order purchase
                where not exists (
                    select 1
                    from customer_order integrated
                    where integrated.order_number =
                        purchase.order_number
                )
                """);

        jdbcTemplate.execute("""
                alter table order_item
                add column if not exists
                    legacy_purchase_order_item_id bigint
                """);

        jdbcTemplate.update("""
                insert into order_item (
                    order_id,
                    product_id,
                    product_name,
                    quantity,
                    order_price,
                    line_amount,
                    legacy_purchase_order_item_id
                )
                select
                    integrated.order_id,
                    item.product_id,
                    item.product_name,
                    item.quantity,
                    item.unit_price,
                    item.line_amount,
                    item.purchase_order_item_id
                from purchase_order_item item
                join purchase_order purchase
                  on purchase.purchase_order_id =
                     item.purchase_order_id
                join customer_order integrated
                  on integrated.order_number =
                     purchase.order_number
                where not exists (
                    select 1
                    from order_item migrated
                    where migrated.legacy_purchase_order_item_id =
                        item.purchase_order_item_id
                )
                """);

        migrateLegacyPurchaseOrderLots(jdbcTemplate);
    }

    private void migrateLegacyPurchaseOrderLots(
            JdbcTemplate jdbcTemplate) {
        if (!tableExists(
                    jdbcTemplate,
                    "ORDER_LOT_ALLOCATION")
                || !tableExists(
                    jdbcTemplate,
                    "ORDER_LOT_ALLOCATIONS")) {
            return;
        }
        jdbcTemplate.update("""
                insert into order_lot_allocations (
                    order_item_id,
                    product_lot_id,
                    quantity,
                    created_at,
                    updated_at
                )
                select
                    migrated.order_item_id,
                    allocation.product_lot_id,
                    allocation.quantity,
                    allocation.created_at,
                    allocation.updated_at
                from order_lot_allocation allocation
                join order_item migrated
                  on migrated.legacy_purchase_order_item_id =
                     allocation.purchase_order_item_id
                where not exists (
                    select 1
                    from order_lot_allocations integrated
                    where integrated.order_item_id =
                            migrated.order_item_id
                      and integrated.product_lot_id =
                            allocation.product_lot_id
                )
                """);
    }

    private void migrateLegacyOrderItemLots(JdbcTemplate jdbcTemplate) {
        if (!tableExists(jdbcTemplate, "ORDER_ITEM")
                || !tableExists(
                        jdbcTemplate,
                        "ORDER_LOT_ALLOCATIONS")
                || !columnExists(
                        jdbcTemplate,
                        "ORDER_ITEM",
                        "LOT_ID")) {
            return;
        }
        jdbcTemplate.update("""
                insert into order_lot_allocations (
                    order_item_id,
                    product_lot_id,
                    quantity,
                    created_at,
                    updated_at
                )
                select
                    item.order_item_id,
                    item.lot_id,
                    item.quantity,
                    current_timestamp,
                    current_timestamp
                from order_item item
                where item.lot_id is not null
                  and not exists (
                      select 1
                      from order_lot_allocations allocation
                      where allocation.order_item_id =
                          item.order_item_id
                        and allocation.product_lot_id =
                          item.lot_id
                  )
                """);
    }

    private boolean tableExists(
            JdbcTemplate jdbcTemplate,
            String tableName) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.tables
                where table_schema = 'PUBLIC'
                  and table_name = ?
                """,
                Integer.class,
                tableName);
        return count != null && count > 0;
    }

    private boolean columnExists(
            JdbcTemplate jdbcTemplate,
            String tableName,
            String columnName) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.columns
                where table_schema = 'PUBLIC'
                  and table_name = ?
                  and column_name = ?
                """,
                Integer.class,
                tableName,
                columnName);
        return count != null && count > 0;
    }

    private record ConstraintTarget(
            String tableName,
            String constraintName) {
    }

    private record ForeignKeyTarget(
            String constraintName,
            String referencedTableName) {
    }
}
