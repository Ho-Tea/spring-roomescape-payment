DROP TABLE IF EXISTS PAYMENT;
DROP TABLE IF EXISTS RESERVATION;
DROP TABLE IF EXISTS RESERVATION_TIME;
DROP TABLE IF EXISTS THEME;
DROP TABLE IF EXISTS MEMBER;

CREATE TABLE IF NOT EXISTS reservation_time
(
    id       BIGINT NOT NULL AUTO_INCREMENT,
    start_at TIME   NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS theme
(
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(255) NOT NULL,
    description VARCHAR(255) NOT NULL,
    thumbnail   VARCHAR(255) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS member
(
    id       BIGINT       NOT NULL AUTO_INCREMENT,
    name     VARCHAR(255) NOT NULL,
    role     VARCHAR(255) NOT NULL,
    email    VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS reservation
(
    id         BIGINT NOT NULL AUTO_INCREMENT,
    date       DATE NOT NULL,
    created_at TIMESTAMP NOT NULL,
    time_id    BIGINT NOT NULL,
    theme_id   BIGINT NOT NULL,
    member_id  BIGINT NOT NULL,
    status     VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
--     CONSTRAINT uk_reservation_date_time_theme UNIQUE (date, time_id, theme_id),
    FOREIGN KEY (member_id) REFERENCES member (id),
    FOREIGN KEY (time_id) REFERENCES reservation_time (id),
    FOREIGN KEY (theme_id) REFERENCES theme (id)
    );

CREATE TABLE IF NOT EXISTS payment
(
    id            BIGINT NOT NULL AUTO_INCREMENT,
    reservation_id BIGINT NOT NULL,
    payment_key   VARCHAR(255) NOT NULL,
    order_name    VARCHAR(255),
    requested_at  VARCHAR(255),
    approved_at   VARCHAR(255),
    amount        DECIMAL(19, 4) NOT NULL,
    easy_pay_type VARCHAR(255),
    currency_code      VARCHAR(20),
    created_at      TIMESTAMP   NOT NULL,
    PRIMARY KEY (id)
);
