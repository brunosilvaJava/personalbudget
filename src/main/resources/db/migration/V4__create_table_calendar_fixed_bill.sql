create table if not exists calerdar_fixed_bill (
id                         BIGINT         PRIMARY KEY AUTOINCREMENT    COMMENT 'Primary key',
day_launch                 SMALLINT        NOT NULL                    COMMENT 'value corresponding to the launch day',
flg_active                 TINYINT(1)      NOT NULL                    COMMENT 'logical delete field',
creation_date              DATETIME        NOT NULL                    COMMENT 'Date of creation of the bill',
last_modified_date         DATETIME        NOT NULL                    COMMENT 'Last modified date of the bill',
id_fixed_bill              BIGINT          NOT NULL                    COMMENT 'Unique identifier of the fixed bill',
FOREIGN KEY (id_fixed_bill) REFERENCES fixed_bill(id)                  COMMENT 'Foreign key'
);