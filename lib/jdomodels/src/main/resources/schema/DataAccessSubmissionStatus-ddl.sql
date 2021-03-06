CREATE TABLE IF NOT EXISTS `DATA_ACCESS_SUBMISSION_STATUS` (
  `SUBMISSION_ID` BIGINT NOT NULL,
  `CREATED_BY` BIGINT NOT NULL,
  `CREATED_ON` BIGINT NOT NULL,
  `MODIFIED_BY` BIGINT NOT NULL,
  `MODIFIED_ON` BIGINT NOT NULL,
  `STATE` enum('SUBMITTED','APPROVED','REJECTED','CANCELLED') NOT NULL,
  `REASON` blob,
  PRIMARY KEY (`SUBMISSION_ID`),
  CONSTRAINT `DATA_ACCESS_SUBMISSION_STATUS_SUBMISSION_ID_FK` FOREIGN KEY (`SUBMISSION_ID`) REFERENCES `DATA_ACCESS_SUBMISSION` (`ID`) ON DELETE CASCADE
)
