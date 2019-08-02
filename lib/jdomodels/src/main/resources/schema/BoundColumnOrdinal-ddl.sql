CREATE TABLE IF NOT EXISTS `BOUND_COLUMN_ORDINAL` (
  `COLUMN_ID` bigint(20) NOT NULL,
  `OBJECT_ID` bigint(20) NOT NULL,
  `OBJECT_VERSION` bigint(20) NOT NULL,
  `ORDINAL` bigint(20) NOT NULL,
  PRIMARY KEY (`COLUMN_ID`, `OBJECT_VERSION`, `OBJECT_ID`),
  INDEX `OBJECT_ID_INDEX` (`OBJECT_ID`),
  CONSTRAINT `ORD_COL_OWNER_FK` FOREIGN KEY (`OBJECT_ID`) REFERENCES `BOUND_COLUMN_OWNER` (`OBJECT_ID`) ON DELETE CASCADE,
  CONSTRAINT `ORD_COL_MODEL_FK` FOREIGN KEY (`COLUMN_ID`) REFERENCES `COLUMN_MODEL` (`ID`) ON DELETE RESTRICT
)
