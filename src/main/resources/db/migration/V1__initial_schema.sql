CREATE TABLE links (
	id              VARCHAR(36) NOT NULL PRIMARY KEY,
	owner_id        VARCHAR(36) NOT NULL,
	target_id       VARCHAR(36) NOT NULL,
	status          VARCHAR(36) NOT NULL,
	creation_date   TIMESTAMP NOT NULL,
	confirm_date    TIMESTAMP,
	unique_key 		VARCHAR(200) NOT NULL
);

ALTER TABLE links
    ADD CONSTRAINT check_status
    CHECK (status in ('Accepted', 'Pending'));

ALTER TABLE links
	ADD CONSTRAINT unique_unique_key
	unique (unique_key);
