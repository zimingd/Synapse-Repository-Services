CREATE TABLE `THROTTLE_RULES`(
    `THROTTLE_ID` bigint(20) unsigned NOT NULL,
    `NORMALIZED_URI` varchar(256),
    `MAX_CALLS` bigint(20) unsigned NOT NULL,
    `CALL_PERIOD_IN_SECONDS` bigint(20) unsigned NOT NULL,
    `MODIFIED_ON` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY(`THROTTLE_ID`)
);