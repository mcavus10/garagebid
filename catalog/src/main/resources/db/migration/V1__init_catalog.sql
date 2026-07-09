CREATE TABLE cars (
    id          UUID           PRIMARY KEY,
    make        VARCHAR(120)   NOT NULL,
    model       VARCHAR(120)   NOT NULL,
    model_year  INTEGER        NOT NULL,
    mileage_km  INTEGER        NOT NULL,
    price_usd   NUMERIC(12, 2) NOT NULL,
    color       VARCHAR(60),
    condition   VARCHAR(30)    NOT NULL,
    description TEXT,
    image_url   VARCHAR(500),
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX idx_cars_make_model ON cars (make, model);