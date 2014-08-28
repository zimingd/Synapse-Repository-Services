CREATE TABLE `BOUND_COLUMN` (
  `COLUMN_ID` bigint(20) NOT NULL,
  `OBJECT_ID` bigint(20) NOT NULL,
  `UPDATED_ON` bigint(20) NOT NULL,
  PRIMARY KEY (`COLUMN_ID`, `OBJECT_ID`),
  CONSTRAINT `COL_MODEL_FK` FOREIGN KEY (`COLUMN_ID`) REFERENCES `COLUMN_MODEL` (`ID`) ON DELETE CASCADE
)