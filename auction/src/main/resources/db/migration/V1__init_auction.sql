CREATE TABLE auctions (
                          id                UUID           PRIMARY KEY,
                          car_id            UUID           NOT NULL,
                          seller_id         UUID           NOT NULL,
                          starting_amount   NUMERIC(14, 2) NOT NULL,
                          currency          VARCHAR(3)     NOT NULL,
                          ends_at           TIMESTAMPTZ    NOT NULL,
                          status            VARCHAR(20)    NOT NULL,
                          highest_bidder_id UUID,
                          highest_amount    NUMERIC(14, 2),
                          highest_placed_at TIMESTAMPTZ,
                          created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
                          updated_at        TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX idx_auctions_status ON auctions (status);
CREATE INDEX idx_auctions_car_id ON auctions (car_id);