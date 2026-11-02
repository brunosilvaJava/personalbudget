CREATE TABLE IF NOT EXISTS category_group
(
    id                 BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Primary key',
    code               BINARY(16)  NOT NULL COMMENT 'Category group uuid code',
    name               VARCHAR(50) NOT NULL COMMENT 'Category group name',
    display_order      INTEGER     NOT NULL COMMENT 'Order in lists and reports',
    created_date       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation date',
    last_modified_date DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Last modification date',
    CONSTRAINT uk_category_group_code UNIQUE (code),
    CONSTRAINT uk_category_group_name UNIQUE (name)
) COMMENT 'Category group table';

CREATE TABLE IF NOT EXISTS category
(
    id                 BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Primary key',
    code               BINARY(16)  NOT NULL COMMENT 'Category uuid code',
    name               VARCHAR(50) NOT NULL COMMENT 'Category name',
    operation_type     VARCHAR(20) NOT NULL COMMENT 'CREDIT or DEBIT',
    status             VARCHAR(20) NOT NULL COMMENT 'ACTIVE or INACTIVE',
    category_group_id  BIGINT      NOT NULL COMMENT 'Category group',
    created_date       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation date',
    last_modified_date DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Last modification date',
    CONSTRAINT uk_category_code UNIQUE (code),
    CONSTRAINT uk_category_name_operation_type UNIQUE (name, operation_type),
    CONSTRAINT fk_category_category_group FOREIGN KEY (category_group_id) REFERENCES category_group (id)
) COMMENT 'Category table';

INSERT INTO category_group (code, name, display_order)
VALUES (UUID_TO_BIN(UUID()), 'Residência', 1),
       (UUID_TO_BIN(UUID()), 'Pessoal', 2),
       (UUID_TO_BIN(UUID()), 'Instrução', 3),
       (UUID_TO_BIN(UUID()), 'Moto', 4),
       (UUID_TO_BIN(UUID()), 'Investimento', 5),
       (UUID_TO_BIN(UUID()), 'Outros', 6);

-- Categories are not seeded here: the spreadsheet list is still pending.
-- When it arrives, add them in a new migration with INSERT ... SELECT per group, e.g.:
-- INSERT INTO category (code, name, operation_type, status, category_group_id)
-- SELECT UUID_TO_BIN(UUID()), 'Aluguel', 'DEBIT', 'ACTIVE', id FROM category_group WHERE name = 'Residência';
