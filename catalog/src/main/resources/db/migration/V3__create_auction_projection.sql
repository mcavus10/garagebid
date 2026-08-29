CREATE TABLE processed_events
(
    consumer_name VARCHAR(150) NOT NULL,
    event_id      UUID         NOT NULL,
    event_type    VARCHAR(150) NOT NULL,
    processed_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (consumer_name, event_id)
);

CREATE TABLE car_auction_projection
(
    car_id            UUID        PRIMARY KEY,
    auction_id        UUID        NOT NULL,
    auction_ends_at   TIMESTAMPTZ NOT NULL,
    event_occurred_at TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);