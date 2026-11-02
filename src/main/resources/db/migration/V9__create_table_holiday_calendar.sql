-- Local holiday calendar used by the income schedules (E3).
-- Two tables on purpose: a year with no holiday row can mean "reviewed, there are none"
-- (it has a holiday_calendar_review row) or "not prepared yet" (it has none). The date
-- calculator must not assume the second case is a year of only business days.
-- Holidays are not seeded: the movable ones (Carnival, Easter...) change every year and each
-- year is reviewed by the user.
CREATE TABLE IF NOT EXISTS holiday
(
    id                 BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Primary key',
    code               BINARY(16)   NOT NULL COMMENT 'Holiday uuid code',
    holiday_date       DATE         NOT NULL COMMENT 'Holiday date, at most one holiday per date',
    name               VARCHAR(100) NOT NULL COMMENT 'Holiday name',
    created_date       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation date',
    last_modified_date DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Last modification date',
    CONSTRAINT uk_holiday_code UNIQUE (code),
    CONSTRAINT uk_holiday_date UNIQUE (holiday_date)
) COMMENT 'Local holiday calendar';

CREATE TABLE IF NOT EXISTS holiday_calendar_review
(
    id                 BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Primary key',
    calendar_year      INTEGER  NOT NULL COMMENT 'Year whose holidays were checked, with or without holidays',
    created_date       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation date',
    last_modified_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Last modification date',
    CONSTRAINT uk_holiday_calendar_review_year UNIQUE (calendar_year)
) COMMENT 'Years of the holiday calendar that were reviewed';
