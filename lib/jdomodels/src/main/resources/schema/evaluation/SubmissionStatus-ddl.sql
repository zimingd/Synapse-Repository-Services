CREATE TABLE IF NOT EXISTS JDOSUBMISSION_STATUS (
    ID BIGINT NOT NULL,
	ETAG char(36) NOT NULL,
	SUBSTATUS_VERSION BIGINT NOT NULL,
    MODIFIED_ON BIGINT NOT NULL,
    STATUS int NOT NULL,
    SCORE double DEFAULT NULL,
    SERIALIZED_ENTITY mediumblob,
    PRIMARY KEY (ID),
    FOREIGN KEY (ID) REFERENCES JDOSUBMISSION (ID) ON DELETE CASCADE
);
