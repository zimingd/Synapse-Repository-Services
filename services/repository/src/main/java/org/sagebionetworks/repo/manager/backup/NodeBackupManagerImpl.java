package org.sagebionetworks.repo.manager.backup;

import java.util.Date;

import org.sagebionetworks.repo.model.AccessControlList;
import org.sagebionetworks.repo.model.AccessControlListDAO;
import org.sagebionetworks.repo.model.ConflictingUpdateException;
import org.sagebionetworks.repo.model.DatastoreException;
import org.sagebionetworks.repo.model.InvalidModelException;
import org.sagebionetworks.repo.model.NodeBackup;
import org.sagebionetworks.repo.model.NodeConstants;
import org.sagebionetworks.repo.model.NodeDAO;
import org.sagebionetworks.repo.model.NodeInheritanceDAO;
import org.sagebionetworks.repo.model.NodeRevision;
import org.sagebionetworks.repo.model.ResourceAccess;
import org.sagebionetworks.repo.model.UserGroup;
import org.sagebionetworks.repo.model.UserGroupDAO;
import org.sagebionetworks.repo.model.jdo.KeyFactory;
import org.sagebionetworks.repo.web.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;


/**
 * The class acts as the source and destination for backups and restoration.
 * 
 * @author jmhill
 *
 */
@Transactional(readOnly = true)
public class NodeBackupManagerImpl implements NodeBackupManager {
	
	@Autowired
	NodeDAO nodeDao;
	
	@Autowired
	AccessControlListDAO aclDAO;
	
	@Autowired
	UserGroupDAO userGroupDAO;
	
	@Autowired
	private NodeInheritanceDAO inheritanceDAO;

	@Transactional(readOnly = true)
	@Override
	public NodeBackup getRoot() throws DatastoreException, NotFoundException {
		// First look up the ID of the root
		String id = nodeDao.getNodeIdForPath(NodeConstants.ROOT_FOLDER_PATH);
		if(id == null) throw new NotFoundException("Cannot find the root node: "+NodeConstants.ROOT_FOLDER_PATH);
		return getNode(id);
	}

	@Transactional(readOnly = true)
	@Override
	public NodeBackup getNode(String id) throws NotFoundException, DatastoreException {
		// Build up the node from the id
		NodeBackup backup = new NodeBackup();
		// First get the node
		backup.setNode(nodeDao.getNode(id));
		String benefactor = inheritanceDAO.getBenefactor(id);
		backup.setBenefactor(benefactor);
		// This node only has an ACL if it is its own benefactor
		if(id.equals(benefactor)){
			backup.setAcl(aclDAO.get(id));
		}
		backup.setChildren(nodeDao.getChildrenIdsAsList(id));
		backup.setRevisions(nodeDao.getVersionNumbers(id));
		return backup;
	}

	@Override
	public NodeRevision getNodeRevision(String nodeId, Long revisionId) throws NotFoundException, DatastoreException {
		// Pass it along
		return nodeDao.getNodeRevision(nodeId,revisionId);
	}

	@Override
	public long getTotalNodeCount() {
		return nodeDao.getTotalNodeCount();
	}

	/**
	 * Create this node in a single transaction.  This is important. We do not want a transaction
	 * around an entire system restoration call.  Such a transaction will not scale, and a partial
	 * restore is better than restoring nothing if there is a single bad node.
	 * @throws DatastoreException 
	 * @throws NotFoundException 
	 * @throws ConflictingUpdateException 
	 * @throws InvalidModelException 
	 */
	@Transactional(readOnly = false, propagation = Propagation.REQUIRES_NEW)
	@Override
	public void createOrUpdateNode(NodeBackup backup) {
		if(backup == null) throw new IllegalArgumentException("NodeBackup cannot be null");
		if(backup.getNode() == null) throw new IllegalArgumentException("NodeBackup.node cannot be null");
		if(backup.getNode().getId() == null) throw new IllegalArgumentException("NodeBackup.node.id cannot be null");
		if(backup.getBenefactor() == null) throw new IllegalArgumentException("NodeBackup.benefactor cannot be null");
		String nodeId = backup.getNode().getId();
		// Does this node already exist
		try {
			// Before we update make sure the users we need exist
			createUsersAsNeeded(backup.getAcl());
			// Now process the node
			if (nodeDao.doesNodeExist(KeyFactory.stringToKey(nodeId))) {
				// Update the node
				nodeDao.updateNode(backup.getNode());
				if (backup.getAcl() != null) {
					aclDAO.update(backup.getAcl());
				}
			} else {
				// Update the node
				nodeDao.createNew(backup.getNode());
				if (backup.getAcl() != null) {
					aclDAO.create(backup.getAcl());
				}
			}
			// Set the benefactor
			inheritanceDAO.addBeneficiary(nodeId, backup.getBenefactor());
		} catch (Exception e) {
			// Convert all exceptions to runtimes to force a rollback on this
			// node.
			throw new RuntimeException(e);
		}
	}

	/**
	 * Make sure all of the groups listed in the ACL actually exist
	 * @param acl
	 * @throws DatastoreException
	 * @throws InvalidModelException
	 */
	public void createUsersAsNeeded(AccessControlList acl)	throws DatastoreException, InvalidModelException {
		if(acl != null && acl.getResourceAccess() != null){
			for(ResourceAccess access: acl.getResourceAccess()){
				String groupName = access.getGroupName();
				if(groupName != null){
					if(!userGroupDAO.doesPrincipalExist(groupName)){
						UserGroup principal = createUserGroupForName(groupName);
						userGroupDAO.create(principal);
					}
				}
			}
		}
	}

	/**
	 * Create a UserGroup for a given name.
	 * @param groupName
	 * @return
	 */
	public static UserGroup createUserGroupForName(String groupName) {
		UserGroup principal = new UserGroup();
		principal.setName(groupName);
		// Users must have an email address as a name
		// and groups are not allowed to have an email address as name
		principal.setIndividual(UserGroup.isEmailAddress(groupName));
		principal.setCreationDate(new Date());
		return principal;
	}
	/**
	 * Create this revisoin in a single transaction.  This is important. We do not want a transaction
	 * around an entire system restoration call.  Such a transaction will not scale, and a partial
	 * restore is better than restoring nothing if there is a single bad node.
	 */
	@Transactional(readOnly = false, propagation = Propagation.REQUIRES_NEW)
	@Override
	public void createOrUpdateRevision(NodeRevision rev) {
		if(rev == null) throw new IllegalArgumentException("NodeRevision cannot be null");
		if(rev.getNodeId() == null) throw new IllegalArgumentException("NodeRevision.nodeId cannot be null");
		if(rev.getRevisionNumber() == null) throw new IllegalArgumentException("NodeRevision.revisionNumber cannot be null");
		if(rev.getLabel() == null) throw new IllegalArgumentException("NodeRevision.revisionNumber cannot be null");
		try{
			if(nodeDao.doesNodeRevisionExist(rev.getNodeId(), rev.getRevisionNumber())){
				// This is an update.
				nodeDao.updateRevision(rev);
			}else{
				// This is a create
				nodeDao.createNewRevision(rev);
			}
			
		}catch(Exception e ){
			// Convert all exceptions to runtimes to force a rollback on this node.
			throw new RuntimeException(e);
		}
	}

}
