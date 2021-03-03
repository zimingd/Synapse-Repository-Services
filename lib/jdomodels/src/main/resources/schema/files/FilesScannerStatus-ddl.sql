CREATE TABLE IF NOT EXISTS `FILES_SCANNER_STATUS` (
  `ID` BIGINT NOT NULL,
  `STARTED_ON` TIMESTAMP(3) NOT NULL,
  `UPDATED_ON` TIMESTAMP(3) NOT NULL,
  `JOBS_STARTED_COUNT` BIGINT NOT NULL,
  `JOBS_COMPLETED_COUNT` BIGINT NOT NULL,
  PRIMARY KEY (`ID`)
)
