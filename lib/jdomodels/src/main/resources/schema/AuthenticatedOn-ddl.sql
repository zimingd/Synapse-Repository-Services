CREATE TABLE IF NOT EXISTS `AUTHENTICATED_ON` (
  `PRINCIPAL_ID` BIGINT NOT NULL,
  `AUTHENTICATED_ON` datetime NOT NULL,
  `ETAG` char(36) NOT NULL DEFAULT '0',
  PRIMARY KEY (`ID`),
  CONSTRAINT `PRINCIPAL_ID_FK` FOREIGN KEY (`PRINCIPAL_ID`) REFERENCES `JDOUSERGROUP` (`PRINCIPAL_ID`) ON DELETE CASCADE
)
