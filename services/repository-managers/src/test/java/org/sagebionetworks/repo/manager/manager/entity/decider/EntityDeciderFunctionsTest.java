package org.sagebionetworks.repo.manager.manager.entity.decider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.sagebionetworks.repo.model.AuthorizationConstants.ERR_MSG_ANONYMOUS_USERS_HAVE_ONLY_READ_ACCESS_PERMISSION;
import static org.sagebionetworks.repo.model.AuthorizationConstants.ERR_MSG_ENTITY_IN_TRASH_TEMPLATE;
import static org.sagebionetworks.repo.model.AuthorizationConstants.ERR_MSG_CERTIFIED_USER_CONTENT;
import static org.sagebionetworks.repo.model.AuthorizationConstants.ERR_MSG_THERE_ARE_UNMET_ACCESS_REQUIREMENTS;
import static org.sagebionetworks.repo.model.AuthorizationConstants.ERR_MSG_THE_RESOURCE_YOU_ARE_ATTEMPTING_TO_ACCESS_CANNOT_BE_FOUND;
import static org.sagebionetworks.repo.model.AuthorizationConstants.ERR_MSG_YOU_DO_NOT_HAVE_PERMISSION_TEMPLATE;
import static org.sagebionetworks.repo.model.AuthorizationConstants.ERR_MSG_YOU_HAVE_NOT_YET_AGREED_TO_THE_SYNAPSE_TERMS_OF_USE;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sagebionetworks.repo.manager.entity.decider.AccessContext;
import org.sagebionetworks.repo.manager.entity.decider.EntityDeciderFunctions;
import org.sagebionetworks.repo.manager.entity.decider.UserInfoState;
import org.sagebionetworks.repo.manager.entity.decider.UsersEntityAccessInfo;
import org.sagebionetworks.repo.manager.trash.EntityInTrashCanException;
import org.sagebionetworks.repo.model.ACCESS_TYPE;
import org.sagebionetworks.repo.model.AuthorizationConstants;
import org.sagebionetworks.repo.model.DataType;
import org.sagebionetworks.repo.model.NodeConstants;
import org.sagebionetworks.repo.model.UserInfo;
import org.sagebionetworks.repo.model.ar.UsersRestrictionStatus;
import org.sagebionetworks.repo.model.auth.AuthorizationStatus;
import org.sagebionetworks.repo.model.dbo.entity.UserEntityPermissionsState;
import org.sagebionetworks.repo.web.NotFoundException;

@ExtendWith(MockitoExtension.class)
public class EntityDeciderFunctionsTest {

	UserInfoState adminUser;
	UserInfoState nonAdminUser;
	UserInfoState anonymousUser;
	UserInfoState notCertifiedUser;
	UserInfoState certifiedUser;

	UsersRestrictionStatus restrictionStatus;
	UserEntityPermissionsState permissionState;
	AccessContext context;

	@BeforeEach
	public void before() {
		Long entityId = 111L;
		permissionState = new UserEntityPermissionsState(entityId);
		adminUser = new UserInfoState(new UserInfo(true/* isAdmin */, 222L));
		nonAdminUser = new UserInfoState(new UserInfo(false/* isAdmin */, 333L));
		anonymousUser = new UserInfoState(new UserInfo(false/* isAdmin */,
				AuthorizationConstants.BOOTSTRAP_PRINCIPAL.ANONYMOUS_USER.getPrincipalId()));
		notCertifiedUser = new UserInfoState(new UserInfo(false/* isAdmin */, 444L));
		certifiedUser = new UserInfoState(new UserInfo(false/* isAdmin */, 555L));
		certifiedUser.getUserInfo().getGroups().add(AuthorizationConstants.BOOTSTRAP_PRINCIPAL.CERTIFIED_USERS.getPrincipalId());

		restrictionStatus = new UsersRestrictionStatus(entityId, nonAdminUser.getUserInfo().getId());
		context = new AccessContext().withUser(nonAdminUser).withPermissionState(permissionState)
				.withRestrictionStatus(restrictionStatus);
	}

	@Test
	public void testGrantIfAdminWithAdmin() {
		context = new AccessContext().withUser(adminUser).withPermissionState(permissionState);
		// call under test
		Optional<UsersEntityAccessInfo> resultOptional = EntityDeciderFunctions.GRANT_IF_ADMIN
				.determineAccess(context);
		assertTrue(resultOptional.isPresent());
		UsersEntityAccessInfo expected = new UsersEntityAccessInfo(context, AuthorizationStatus.authorized());
		assertEquals(expected, resultOptional.get());
	}

	@Test
	public void testGrantIfAdminWithNonAdmin() {
		context = new AccessContext().withUser(nonAdminUser).withPermissionState(permissionState);
		// call under test
		Optional<UsersEntityAccessInfo> resultOptional = EntityDeciderFunctions.GRANT_IF_ADMIN
				.determineAccess(context);
		assertFalse(resultOptional.isPresent());
	}

	@Test
	public void testDenyIfInTrashWithInTrash() {
		permissionState.withBenefactorId(NodeConstants.BOOTSTRAP_NODES.TRASH.getId());
		// call under test
		Optional<UsersEntityAccessInfo> resultOptional = EntityDeciderFunctions.DENY_IF_IN_TRASH
				.determineAccess(context);
		assertTrue(resultOptional.isPresent());
		UsersEntityAccessInfo expected = new UsersEntityAccessInfo(context,
				AuthorizationStatus.accessDenied(new EntityInTrashCanException(
						String.format(ERR_MSG_ENTITY_IN_TRASH_TEMPLATE, permissionState.getEntityIdAsString()))));
		assertEquals(expected, resultOptional.get());
	}

	@Test
	public void testDenyIfInTrashWithNotInTrash() {
		permissionState.withBenefactorId(888L);

		// call under test
		Optional<UsersEntityAccessInfo> resultOptional = EntityDeciderFunctions.DENY_IF_IN_TRASH
				.determineAccess(context);
		assertFalse(resultOptional.isPresent());
	}

	@Test
	public void testDenyIfDoesNotExistWithExistsFalse() {
		permissionState.withtDoesEntityExist(false);

		// call under test
		Optional<UsersEntityAccessInfo> resultOptional = EntityDeciderFunctions.DENY_IF_DOES_NOT_EXIST
				.determineAccess(context);
		assertTrue(resultOptional.isPresent());
		UsersEntityAccessInfo expected = new UsersEntityAccessInfo(context, AuthorizationStatus
				.accessDenied(new NotFoundException(ERR_MSG_THE_RESOURCE_YOU_ARE_ATTEMPTING_TO_ACCESS_CANNOT_BE_FOUND)));
		assertEquals(expected, resultOptional.get());
	}

	@Test
	public void testDenyIfDoesNotExistWithExistsTrue() {
		permissionState.withtDoesEntityExist(true);

		// call under test
		Optional<UsersEntityAccessInfo> resultOptional = EntityDeciderFunctions.DENY_IF_DOES_NOT_EXIST
				.determineAccess(context);
		assertFalse(resultOptional.isPresent());
	}

	@Test
	public void testDenyIfHasUnmetAccessRestrictionsWithUnmet() {
		restrictionStatus.setHasUnmet(true);

		// call under test
		Optional<UsersEntityAccessInfo> resultOptional = EntityDeciderFunctions.DENY_IF_HAS_UNMET_ACCESS_RESTRICTIONS
				.determineAccess(context);
		assertTrue(resultOptional.isPresent());
		UsersEntityAccessInfo expected = new UsersEntityAccessInfo(context,
				AuthorizationStatus.accessDenied(ERR_MSG_THERE_ARE_UNMET_ACCESS_REQUIREMENTS));
		assertEquals(expected, resultOptional.get());
	}

	@Test
	public void testDenyIfHasUnmetAccessRestrictionsWithMet() {
		restrictionStatus.setHasUnmet(false);

		// call under test
		Optional<UsersEntityAccessInfo> resultOptional = EntityDeciderFunctions.DENY_IF_HAS_UNMET_ACCESS_RESTRICTIONS
				.determineAccess(context);
		assertFalse(resultOptional.isPresent());
	}

	@Test
	public void testGrantIfOpenDataWithBoth() {
		permissionState.withDataType(DataType.OPEN_DATA);
		permissionState.withHasRead(true);

		// call under test
		Optional<UsersEntityAccessInfo> resultOptional = EntityDeciderFunctions.GRANT_IF_OPEN_DATA_WITH_READ
				.determineAccess(context);
		assertTrue(resultOptional.isPresent());
		UsersEntityAccessInfo expected = new UsersEntityAccessInfo(context, AuthorizationStatus.authorized());
		assertEquals(expected, resultOptional.get());
	}

	@Test
	public void testGrantIfOpenDataWithSenstiveDataRead() {
		permissionState.withDataType(DataType.SENSITIVE_DATA);
		permissionState.withHasRead(true);

		// call under test
		Optional<UsersEntityAccessInfo> resultOptional = EntityDeciderFunctions.GRANT_IF_OPEN_DATA_WITH_READ
				.determineAccess(context);
		assertFalse(resultOptional.isPresent());
	}

	@Test
	public void testGrantIfOpenDataWithOpenDataNoRead() {
		permissionState.withDataType(DataType.OPEN_DATA);
		permissionState.withHasRead(false);

		// call under test
		Optional<UsersEntityAccessInfo> resultOptional = EntityDeciderFunctions.GRANT_IF_OPEN_DATA_WITH_READ
				.determineAccess(context);
		assertFalse(resultOptional.isPresent());
	}

	@Test
	public void testGrantOrDenyIfHasDownloadWithDownloadTrue() {
		permissionState.withHasDownload(true);

		// call under test
		Optional<UsersEntityAccessInfo> resultOptional = EntityDeciderFunctions.GRANT_OR_DENY_IF_HAS_DOWNLOAD
				.determineAccess(context);
		assertTrue(resultOptional.isPresent());
		UsersEntityAccessInfo expected = new UsersEntityAccessInfo(context, AuthorizationStatus.authorized());
		assertEquals(expected, resultOptional.get());
	}

	@Test
	public void testGrantOrDenyIfHasDownloadWithDownloadFalse() {
		permissionState.withHasDownload(false);

		// call under test
		Optional<UsersEntityAccessInfo> resultOptional = EntityDeciderFunctions.GRANT_OR_DENY_IF_HAS_DOWNLOAD
				.determineAccess(context);
		assertTrue(resultOptional.isPresent());
		UsersEntityAccessInfo expected = new UsersEntityAccessInfo(context,
				AuthorizationStatus.accessDenied(String.format(ERR_MSG_YOU_DO_NOT_HAVE_PERMISSION_TEMPLATE,
						ACCESS_TYPE.DOWNLOAD.name(), permissionState.getEntityIdAsString())));
		assertEquals(expected, resultOptional.get());
	}

	@Test
	public void testGrantOrDenyIfHasModerateWithModerateTrue() {
		permissionState.withHasModerate(true);

		// call under test
		Optional<UsersEntityAccessInfo> resultOptional = EntityDeciderFunctions.GRANT_OR_DENY_IF_HAS_MODERATE
				.determineAccess(context);
		assertTrue(resultOptional.isPresent());
		UsersEntityAccessInfo expected = new UsersEntityAccessInfo(context, AuthorizationStatus.authorized());
		assertEquals(expected, resultOptional.get());
	}

	@Test
	public void testGrantOrDenyIfHasModerateWithModerateFalse() {
		permissionState.withHasModerate(false);

		// call under test
		Optional<UsersEntityAccessInfo> resultOptional = EntityDeciderFunctions.GRANT_OR_DENY_IF_HAS_MODERATE
				.determineAccess(context);
		assertTrue(resultOptional.isPresent());
		UsersEntityAccessInfo expected = new UsersEntityAccessInfo(context,
				AuthorizationStatus.accessDenied(String.format(ERR_MSG_YOU_DO_NOT_HAVE_PERMISSION_TEMPLATE,
						ACCESS_TYPE.MODERATE.name(), permissionState.getEntityIdAsString())));
		assertEquals(expected, resultOptional.get());
	}

	@Test
	public void testGrantOrDenyIfHasChangeSettingsWithTrue() {
		permissionState.withHasChangeSettings(true);

		// call under test
		Optional<UsersEntityAccessInfo> resultOptional = EntityDeciderFunctions.GRANT_OR_DENY_IF_HAS_CHANGE_SETTINGS
				.determineAccess(context);
		assertTrue(resultOptional.isPresent());
		UsersEntityAccessInfo expected = new UsersEntityAccessInfo(context, AuthorizationStatus.authorized());
		assertEquals(expected, resultOptional.get());
	}

	@Test
	public void testGrantOrDenyIfHasChangeSettingsWithFalse() {
		permissionState.withHasChangeSettings(false);

		// call under test
		Optional<UsersEntityAccessInfo> resultOptional = EntityDeciderFunctions.GRANT_OR_DENY_IF_HAS_CHANGE_SETTINGS
				.determineAccess(context);
		assertTrue(resultOptional.isPresent());
		UsersEntityAccessInfo expected = new UsersEntityAccessInfo(context,
				AuthorizationStatus.accessDenied(String.format(ERR_MSG_YOU_DO_NOT_HAVE_PERMISSION_TEMPLATE,
						ACCESS_TYPE.CHANGE_SETTINGS.name(), permissionState.getEntityIdAsString())));
		assertEquals(expected, resultOptional.get());
	}

	@Test
	public void testGrantOrDenyIfHasChangePermissionsWithTrue() {
		permissionState.withHasChangePermissions(true);

		// call under test
		Optional<UsersEntityAccessInfo> resultOptional = EntityDeciderFunctions.GRANT_OR_DENY_IF_HAS_CHANGE_PERMISSION
				.determineAccess(context);
		assertTrue(resultOptional.isPresent());
		UsersEntityAccessInfo expected = new UsersEntityAccessInfo(context, AuthorizationStatus.authorized());
		assertEquals(expected, resultOptional.get());
	}

	@Test
	public void testGrantOrDenyIfHasChangePermissionsWithFalse() {
		permissionState.withHasChangePermissions(false);

		// call under test
		Optional<UsersEntityAccessInfo> resultOptional = EntityDeciderFunctions.GRANT_OR_DENY_IF_HAS_CHANGE_PERMISSION
				.determineAccess(context);
		assertTrue(resultOptional.isPresent());
		UsersEntityAccessInfo expected = new UsersEntityAccessInfo(context,
				AuthorizationStatus.accessDenied(String.format(ERR_MSG_YOU_DO_NOT_HAVE_PERMISSION_TEMPLATE,
						ACCESS_TYPE.CHANGE_PERMISSIONS.name(), permissionState.getEntityIdAsString())));
		assertEquals(expected, resultOptional.get());
	}

	@Test
	public void testGrantOrDenyIfHasDeleteWithTrue() {
		permissionState.withHasDelete(true);

		// call under test
		Optional<UsersEntityAccessInfo> resultOptional = EntityDeciderFunctions.GRANT_OR_DENY_IF_HAS_DELETE
				.determineAccess(context);
		assertTrue(resultOptional.isPresent());
		UsersEntityAccessInfo expected = new UsersEntityAccessInfo(context, AuthorizationStatus.authorized());
		assertEquals(expected, resultOptional.get());
	}

	@Test
	public void testGrantOrDenyIfHasDeleteWithFalse() {
		permissionState.withHasDelete(false);

		// call under test
		Optional<UsersEntityAccessInfo> resultOptional = EntityDeciderFunctions.GRANT_OR_DENY_IF_HAS_DELETE
				.determineAccess(context);
		assertTrue(resultOptional.isPresent());
		UsersEntityAccessInfo expected = new UsersEntityAccessInfo(context,
				AuthorizationStatus.accessDenied(String.format(ERR_MSG_YOU_DO_NOT_HAVE_PERMISSION_TEMPLATE,
						ACCESS_TYPE.DELETE.name(), permissionState.getEntityIdAsString())));
		assertEquals(expected, resultOptional.get());
	}

	@Test
	public void testDenyIfAnonymousWithTrue() {
		context = new AccessContext().withUser(anonymousUser).withPermissionState(permissionState);
		// call under test
		Optional<UsersEntityAccessInfo> resultOptional = EntityDeciderFunctions.DENY_IF_ANONYMOUS
				.determineAccess(context);
		assertTrue(resultOptional.isPresent());
		UsersEntityAccessInfo expected = new UsersEntityAccessInfo(context,
				AuthorizationStatus.accessDenied(ERR_MSG_ANONYMOUS_USERS_HAVE_ONLY_READ_ACCESS_PERMISSION));
		assertEquals(expected, resultOptional.get());
	}

	@Test
	public void testDenyIfAnonymousWithFalse() {
		context = new AccessContext().withUser(nonAdminUser).withPermissionState(permissionState);

		// call under test
		Optional<UsersEntityAccessInfo> resultOptional = EntityDeciderFunctions.DENY_IF_ANONYMOUS
				.determineAccess(context);
		assertFalse(resultOptional.isPresent());
	}

	@Test
	public void testDenyIfNotCertifiedWithNoCertification() {
		context = new AccessContext().withUser(notCertifiedUser).withPermissionState(permissionState);
		// call under test
		Optional<UsersEntityAccessInfo> resultOptional = EntityDeciderFunctions.DENY_IF_NOT_CERTIFIED
				.determineAccess(context);
		assertTrue(resultOptional.isPresent());
		UsersEntityAccessInfo expected = new UsersEntityAccessInfo(context,
				AuthorizationStatus.accessDenied(ERR_MSG_CERTIFIED_USER_CONTENT));
		assertEquals(expected, resultOptional.get());
	}

	@Test
	public void testDenyIfNotCertifiedWithCertification() {
		context = new AccessContext().withUser(certifiedUser).withPermissionState(permissionState);

		// call under test
		Optional<UsersEntityAccessInfo> resultOptional = EntityDeciderFunctions.DENY_IF_NOT_CERTIFIED
				.determineAccess(context);
		assertFalse(resultOptional.isPresent());
	}

	@Test
	public void testDenyIfHasNotAcceptedTermsOfUseWithNoAccept() {
		nonAdminUser.overrideAcceptsTermsOfUse(false);
		context = new AccessContext().withUser(nonAdminUser).withPermissionState(permissionState);
		// call under test
		Optional<UsersEntityAccessInfo> resultOptional = EntityDeciderFunctions.DENY_IF_HAS_NOT_ACCEPTED_TERMS_OF_USE
				.determineAccess(context);
		assertTrue(resultOptional.isPresent());
		UsersEntityAccessInfo expected = new UsersEntityAccessInfo(context,
				AuthorizationStatus.accessDenied(ERR_MSG_YOU_HAVE_NOT_YET_AGREED_TO_THE_SYNAPSE_TERMS_OF_USE));
		assertEquals(expected, resultOptional.get());
	}

	@Test
	public void testDenyIfHasNotAcceptedTermsOfUseWithAccept() {
		nonAdminUser.overrideAcceptsTermsOfUse(true);
		context = new AccessContext().withUser(nonAdminUser).withPermissionState(permissionState);

		// call under test
		Optional<UsersEntityAccessInfo> resultOptional = EntityDeciderFunctions.DENY_IF_HAS_NOT_ACCEPTED_TERMS_OF_USE
				.determineAccess(context);
		assertFalse(resultOptional.isPresent());
	}
}
