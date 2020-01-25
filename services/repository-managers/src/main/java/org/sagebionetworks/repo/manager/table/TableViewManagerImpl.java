package org.sagebionetworks.repo.manager.table;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.sagebionetworks.StackConfiguration;
import org.sagebionetworks.aws.SynapseS3Client;
import org.sagebionetworks.common.util.progress.ProgressCallback;
import org.sagebionetworks.repo.manager.NodeManager;
import org.sagebionetworks.repo.manager.entity.ReplicationManager;
import org.sagebionetworks.repo.model.BucketAndKey;
import org.sagebionetworks.repo.model.UserInfo;
import org.sagebionetworks.repo.model.annotation.v2.Annotations;
import org.sagebionetworks.repo.model.annotation.v2.AnnotationsValue;
import org.sagebionetworks.repo.model.dao.table.ColumnModelDAO;
import org.sagebionetworks.repo.model.dbo.dao.table.ViewScopeDao;
import org.sagebionetworks.repo.model.dbo.dao.table.ViewSnapshot;
import org.sagebionetworks.repo.model.dbo.dao.table.ViewSnapshotDao;
import org.sagebionetworks.repo.model.entity.IdAndVersion;
import org.sagebionetworks.repo.model.jdo.KeyFactory;
import org.sagebionetworks.repo.model.table.AnnotationType;
import org.sagebionetworks.repo.model.table.ColumnChange;
import org.sagebionetworks.repo.model.table.ColumnConstants;
import org.sagebionetworks.repo.model.table.ColumnModel;
import org.sagebionetworks.repo.model.table.EntityField;
import org.sagebionetworks.repo.model.table.SnapshotRequest;
import org.sagebionetworks.repo.model.table.SparseRowDto;
import org.sagebionetworks.repo.model.table.TableState;
import org.sagebionetworks.repo.model.table.ViewScope;
import org.sagebionetworks.repo.model.table.ViewTypeMask;
import org.sagebionetworks.repo.transactions.NewWriteTransaction;
import org.sagebionetworks.repo.transactions.WriteTransaction;
import org.sagebionetworks.table.cluster.SQLUtils;
import org.sagebionetworks.table.query.util.ColumnTypeListMappings;
import org.sagebionetworks.util.FileProvider;
import org.sagebionetworks.util.ValidateArgument;
import org.sagebionetworks.util.csv.CSVReaderIterator;
import org.sagebionetworks.util.csv.CSVWriterStream;
import org.sagebionetworks.workers.util.semaphore.LockUnavilableException;
import org.springframework.beans.factory.annotation.Autowired;

import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.google.common.collect.Sets;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;

public class TableViewManagerImpl implements TableViewManager {
	
	public static final String VIEW_DELTA_KEY_PREFIX = "Increment-";

	static Log log = LogFactory.getLog(TableViewManagerImpl.class);	

	public static final String DEFAULT_ETAG = "DEFAULT";
	/**
	 * See: PLFM-5456
	 */
	public static int TIMEOUT_SECONDS = 60 * 10;

	public static final String PROJECT_TYPE_CANNOT_BE_COMBINED_WITH_ANY_OTHER_TYPE = "The Project type cannot be combined with any other type.";
	public static final String ETG_COLUMN_MISSING = "The view schema must include '" + EntityField.etag.name()
			+ "' column.";
	public static final String ETAG_MISSING_MESSAGE = "The '" + EntityField.etag.name()
			+ "' must be included to update an Entity's annotations.";

	/**
	 * Max columns per view is now the same as the max per table.
	 */
	public static final int MAX_COLUMNS_PER_VIEW = ColumnConstants.MY_SQL_MAX_COLUMNS_PER_TABLE;
	/**
	 * The maximum number of view rows that can be updated in a single transaction.
	 */
	public static final long MAX_ROWS_PER_TRANSACTION = 1000;

	@Autowired
	ViewScopeDao viewScopeDao;
	@Autowired
	ColumnModelManager columModelManager;
	@Autowired
	TableManagerSupport tableManagerSupport;
	@Autowired
	ColumnModelDAO columnModelDao;
	@Autowired
	NodeManager nodeManager;
	@Autowired
	ReplicationManager replicationManager;
	@Autowired
	TableIndexConnectionFactory connectionFactory;
	@Autowired
	FileProvider fileProvider;
	@Autowired
	SynapseS3Client s3Client;
	@Autowired
	StackConfiguration config;
	@Autowired
	ViewSnapshotDao viewSnapshotDao;

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.sagebionetworks.repo.manager.table.TableViewManager#setViewSchemaAndScope
	 * (org.sagebionetworks.repo.model.UserInfo, java.util.List, java.util.List,
	 * java.lang.String)
	 */
	@WriteTransaction
	@Override
	public void setViewSchemaAndScope(UserInfo userInfo, List<String> schema, ViewScope scope, String viewIdString) {
		ValidateArgument.required(userInfo, "userInfo");
		ValidateArgument.required(scope, "scope");
		validateViewSchemaSize(schema);
		Long viewId = KeyFactory.stringToKey(viewIdString);
		IdAndVersion idAndVersion = IdAndVersion.parse(viewIdString);
		Set<Long> scopeIds = null;
		if (scope.getScope() != null) {
			scopeIds = new HashSet<Long>(KeyFactory.stringToKey(scope.getScope()));
		}
		Long viewTypeMaks = ViewTypeMask.getViewTypeMask(scope);
		if ((viewTypeMaks & ViewTypeMask.Project.getMask()) > 0) {
			if (viewTypeMaks != ViewTypeMask.Project.getMask()) {
				throw new IllegalArgumentException(PROJECT_TYPE_CANNOT_BE_COMBINED_WITH_ANY_OTHER_TYPE);
			}
		}
		// validate the scope size
		tableManagerSupport.validateScopeSize(scopeIds, viewTypeMaks);

		// Define the scope of this view.
		viewScopeDao.setViewScopeAndType(viewId, scopeIds, viewTypeMaks);
		// Define the schema of this view.
		columModelManager.bindColumnsToDefaultVersionOfObject(schema, viewIdString);
		// trigger an update
		tableManagerSupport.setTableToProcessingAndTriggerUpdate(idAndVersion);
	}

	@Override
	public Set<Long> findViewsContainingEntity(String entityId) {
		IdAndVersion idAndVersion = IdAndVersion.parse(entityId);
		List<Long> entityPath = tableManagerSupport.getEntityPath(idAndVersion);
		return viewScopeDao.findViewScopeIntersectionWithPath(entityPath);
	}

	@Override
	public List<ColumnModel> getViewSchema(IdAndVersion idAndVersion) {
		return columModelManager.getColumnModelsForObject(idAndVersion);
	}

	@Override
	public List<String> getViewSchemaIds(IdAndVersion idAndVersion) {
		return columModelManager.getColumnIdsForTable(idAndVersion);
	}

	@WriteTransaction
	@Override
	public List<ColumnModel> applySchemaChange(UserInfo user, String viewId, List<ColumnChange> changes,
			List<String> orderedColumnIds) {
		// first determine what the new Schema will be
		List<String> newSchemaIds = columModelManager.calculateNewSchemaIdsAndValidate(viewId, changes,
				orderedColumnIds);
		validateViewSchemaSize(newSchemaIds);
		List<ColumnModel> newSchema = columModelManager.bindColumnsToDefaultVersionOfObject(newSchemaIds, viewId);
		IdAndVersion idAndVersion = IdAndVersion.parse(viewId);
		// trigger an update.
		tableManagerSupport.setTableToProcessingAndTriggerUpdate(idAndVersion);
		return newSchema;
	}

	/**
	 * Validate that the new schema is within the allowed size for views.
	 * 
	 * @param newSchema
	 */
	public static void validateViewSchemaSize(List<String> newSchema) {
		if (newSchema != null) {
			if (newSchema.size() > MAX_COLUMNS_PER_VIEW) {
				throw new IllegalArgumentException("A view cannot have " + newSchema.size() + " columns.  It must have "
						+ MAX_COLUMNS_PER_VIEW + " columns or less.");
			}
		}
	}

	/**
	 * Update an Entity using data form a view.
	 * 
	 * NOTE: Each entity is updated in a separate transaction to prevent locking the
	 * entity tables for long periods of time. This also prevents deadlock.
	 * 
	 * @return The EntityId.
	 * 
	 */
	@NewWriteTransaction
	@Override
	public void updateEntityInView(UserInfo user, List<ColumnModel> tableSchema, SparseRowDto row) {
		ValidateArgument.required(row, "SparseRowDto");
		ValidateArgument.required(row.getRowId(), "row.rowId");
		if (row.getValues() == null || row.getValues().isEmpty()) {
			// nothing to do for this row.
			return;
		}
		String entityId = KeyFactory.keyToString(row.getRowId());
		Map<String, String> values = row.getValues();
		String etag = row.getEtag();
		if (etag == null) {
			/*
			 * Prior to PLFM-4249, users provided the etag as a column on the table. View
			 * query results will now include the etag if requested, even if the view does
			 * not have an etag column. However, if this etag is null, then for backwards
			 * compatibility we still need to look for an etag column in the view.
			 */
			ColumnModel etagColumn = getEtagColumn(tableSchema);
			etag = values.get(etagColumn.getId());
			if (etag == null) {
				throw new IllegalArgumentException(ETAG_MISSING_MESSAGE);
			}
		}
		// Get the current annotations for this entity.
		Annotations userAnnotations = nodeManager.getUserAnnotations(user, entityId);
		userAnnotations.setEtag(etag);
		boolean updated = updateAnnotationsFromValues(userAnnotations, tableSchema, values);
		if (updated) {
			// save the changes. validation of updated values will occur in this call
			nodeManager.updateUserAnnotations(user, entityId, userAnnotations);
			// Replicate the change
			replicationManager.replicate(entityId);
		}
	}

	/**
	 * Lookup the etag column from the given schema.
	 * 
	 * @param schema
	 * @return
	 */
	public static ColumnModel getEtagColumn(List<ColumnModel> schema) {
		for (ColumnModel cm : schema) {
			if (EntityField.etag.name().equals(cm.getName())) {
				return cm;
			}
		}
		throw new IllegalArgumentException(ETG_COLUMN_MISSING);
	}

	/**
	 * Update the passed Annotations using the given schema and values map.
	 * 
	 * @param additional
	 * @param tableSchema
	 * @param values
	 * @return
	 */
	public static boolean updateAnnotationsFromValues(Annotations additional, List<ColumnModel> tableSchema,
			Map<String, String> values) {
		boolean updated = false;
		// process each column of the view
		for (ColumnModel column : tableSchema) {
			EntityField matchedField = EntityField.findMatch(column);
			// Ignore all entity fields.
			if (matchedField == null) {
				// is this column included in the row?
				if (values.containsKey(column.getId())) {
					updated = true;
					// Match the column type to an annotation type.
					AnnotationType type = SQLUtils.translateColumnTypeToAnnotationType(column.getColumnType());
					String value = values.get(column.getId());
					// Unconditionally remove a current annotation.
					Map<String, AnnotationsValue> annotationsMap = additional.getAnnotations();
					annotationsMap.remove(column.getName());
					// Add back the annotation if the value is not null
					if(value != null){
						List<String> annoStringValues = toAnnotationValuesList(column, value);
						AnnotationsValue annotationsV2Value = new AnnotationsValue();
						annotationsV2Value.setValue(annoStringValues);
						annotationsV2Value.setType(type.getAnnotationsV2ValueType());
						annotationsMap.put(column.getName(), annotationsV2Value);
					}
				}
			}
		}
		return updated;
	}

	static List<String> toAnnotationValuesList(ColumnModel column, String value) {
		if(ColumnTypeListMappings.isList(column.getColumnType())){
			//try to parse as JSON array and extract values as string
			try {
				JSONArray jsonArray = new JSONArray(value);
				List<String> annoStringValues = new ArrayList<>(jsonArray.length());
				for(Object o : jsonArray){
					if(JSONObject.NULL.equals(o)){ //null values are parsed as JSONObject.NULL
						throw new IllegalArgumentException("null value is not allowed");
					}
					annoStringValues.add(o.toString());
				}
				return annoStringValues;
			}catch (JSONException e){
				throw new IllegalArgumentException("Value is not correctly formatted as a JSON Array: " + value);
			}
		} else{
			//column type is not list, take as is
			return Collections.singletonList(value);
		}
	}

	@Override
	public void deleteViewIndex(IdAndVersion idAndVersion) {
		TableIndexManager indexManager = connectionFactory.connectToTableIndex(idAndVersion);
		indexManager.deleteTableIndex(idAndVersion);
	}

	@Override
	public void createOrUpdateViewIndex(IdAndVersion idAndVersion, ProgressCallback outerProgressCallback)
			throws Exception {
		Optional<TableState> optionalState = tableManagerSupport.getTableStatusState(idAndVersion);
		if (optionalState.isPresent() && optionalState.get() == TableState.AVAILABLE
				&& !idAndVersion.getVersion().isPresent()) {
			/*
			 * The view is currently available and this is not a "snapshot". This route will
			 * attempt to apply any changes to an existing view while the view status
			 * remains AVAILABLE. Users will be able to query the view during this
			 * operation.
			 */
			applyChangesToAvailableView(idAndVersion, outerProgressCallback);
		}else {
			/*
			 * The view is not currently available or this is a "snapshot". This route will
			 * create or rebuild the table from scratch with the view status set to
			 * PROCESSING. Users will not be able to query the view during this operation.
			 */
			createOrRebuildView(idAndVersion, outerProgressCallback);
		}
	}
	
	/**
	 * Attempt to apply any changes to a view that will remain available for query
	 * during this operation.
	 * 
	 * @param idAndVersion
	 * @param outerProgressCallback
	 * @throws Exception
	 */
	void applyChangesToAvailableView(IdAndVersion idAndVersion, ProgressCallback outerProgressCallback)
			throws Exception {
		/*
		 * By getting a read lock on the view, we ensure no other process is able to do
		 * a full rebuild of the view while this runs.  The read lock also allows users
		 * to query the view while this process runs.
		 */
		try {
			tableManagerSupport.tryRunWithTableNonexclusiveLock(outerProgressCallback, idAndVersion, TIMEOUT_SECONDS,
					(ProgressCallback callback) -> {
						/*
						 * A special exclusive lock is used to prevent more then one instance
						 * from applying deltas to a view at a time.
						 */
						String key = VIEW_DELTA_KEY_PREFIX + idAndVersion.toString();
						tableManagerSupport.tryRunWithTableExclusiveLock(outerProgressCallback, key, TIMEOUT_SECONDS,
								(ProgressCallback innerCallback) -> {
									// while holding both locks do the work.
									applyChangesToAvailableViewHoldingLock(idAndVersion);
									return null;
								});
						return null;
					});
		} catch (LockUnavilableException e1) {
			log.warn("Unable to aquire lock: " + idAndVersion + " so the message will be ignored.");
		}
	}
	
	/**
	 * Attempt to apply any changes to a view that will remain available for query during this operation.
	 * The caller must hold an exclusive lock on the view-change during this operation.
	 * @param viewId
	 */
	void applyChangesToAvailableViewHoldingLock(IdAndVersion viewId) {
		try {
			TableIndexManager indexManager = connectionFactory.connectToTableIndex(viewId);
			Long viewTypeMask = tableManagerSupport.getViewTypeMask(viewId);
			Set<Long> allContainersInScope = tableManagerSupport.getAllContainerIdsForViewScope(viewId, viewTypeMask);
			List<ColumnModel> currentSchema = tableManagerSupport.getTableSchema(viewId);
			Set<Long> rowsIdsWithChanges = null;
			Set<Long> previousPageRowIdsWithChanges = Collections.emptySet();
			// Continue applying change to the view until none remain.
			do {
				Optional<TableState> optionalState = tableManagerSupport.getTableStatusState(viewId);
				if(!optionalState.isPresent() || optionalState.get() != TableState.AVAILABLE) {
					// no point in continuing if the table is no longer available.
					return;
				}
				rowsIdsWithChanges = indexManager.getOutOfDateRowsForView(viewId, viewTypeMask, allContainersInScope,  MAX_ROWS_PER_TRANSACTION);
				// Are thrashing on the same Ids?
				Set<Long> intersectionWithPreviousPage = Sets.intersection(rowsIdsWithChanges,
						previousPageRowIdsWithChanges);
				if (intersectionWithPreviousPage.size() > 0) {
					log.warn("Found " + intersectionWithPreviousPage.size()
							+ " rows that were just updated but are still out-of-date for view:" + viewId.toString()
							+ " View update will terminate.");
					return;
				}
				
				if (!rowsIdsWithChanges.isEmpty()) {
					// update these rows in a new transaction.
					indexManager.updateViewRowsInTransaction(viewId, rowsIdsWithChanges, viewTypeMask, allContainersInScope,
							currentSchema);
					previousPageRowIdsWithChanges = rowsIdsWithChanges;
					tableManagerSupport.updateChangedOnIfAvailable(viewId);
				}
			} while (!rowsIdsWithChanges.isEmpty());
		} catch (Exception e) {
			// failed.
			tableManagerSupport.attemptToSetTableStatusToFailed(viewId, e);
			throw e;
		}
	}

	/**
	 * Create or rebuild a view from scratch. Users will not be able to query the
	 * view during this build.
	 * 
	 * @param idAndVersion
	 * @param outerProgressCallback
	 * @throws Exception
	 */
	void createOrRebuildView(IdAndVersion idAndVersion, ProgressCallback outerProgressCallback) throws Exception {
		tableManagerSupport.tryRunWithTableExclusiveLock(outerProgressCallback, idAndVersion, TIMEOUT_SECONDS,
				(ProgressCallback innerCallback) -> {
					createOrRebuildViewHoldingLock(idAndVersion);
					return null;
				});
	}

	/**
	 * Create or rebuild a view from scratch. Users will not be able to query the
	 * view during this build. The caller must hold an exclusive lock on the view
	 * during this call.
	 * 
	 * @param idAndVersion
	 */
	void createOrRebuildViewHoldingLock(IdAndVersion idAndVersion) {
		try {
			// Is the index out-of-synch?
			if (!tableManagerSupport.isIndexWorkRequired(idAndVersion)) {
				// nothing to do
				return;
			}
			// Start the worker
			final String token = tableManagerSupport.startTableProcessing(idAndVersion);
			TableIndexManager indexManager = connectionFactory.connectToTableIndex(idAndVersion);
			// Since this worker re-builds the index, start by deleting it.
			indexManager.deleteTableIndex(idAndVersion);
			// Need the MD5 for the original schema.
			String originalSchemaMD5Hex = tableManagerSupport.getSchemaMD5Hex(idAndVersion);
			List<ColumnModel> viewSchema = getViewSchema(idAndVersion);

			// create the table in the index.
			boolean isTableView = true;
			indexManager.setIndexSchema(idAndVersion, isTableView, viewSchema);
			tableManagerSupport.attemptToUpdateTableProgress(idAndVersion, token, "Copying data to view...", 0L, 1L);
			
			Long viewCRC = null;
			if(idAndVersion.getVersion().isPresent()) {
				viewCRC = populateViewFromSnapshot(idAndVersion, indexManager);
			}else {
				viewCRC = populateViewIndexFromReplication(idAndVersion, indexManager, viewSchema);
			}
			// now that table is created and populated the indices on the table can be
			// optimized.
			indexManager.optimizeTableIndices(idAndVersion);

			//for any list columns, build separate tables that serve as an index
			indexManager.populateListColumnIndexTables(idAndVersion, viewSchema);

			// both the CRC and schema MD5 are used to determine if the view is up-to-date.
			indexManager.setIndexVersionAndSchemaMD5Hex(idAndVersion, viewCRC, originalSchemaMD5Hex);
			// Attempt to set the table to complete.
			tableManagerSupport.attemptToSetTableStatusToAvailable(idAndVersion, token, DEFAULT_ETAG);
		} catch (Exception e) {
			// failed.
			tableManagerSupport.attemptToSetTableStatusToFailed(idAndVersion, e);
			throw e;
		}
	}

	/**
	 * Populate the view table using entity replication data.
	 * @param idAndVersion
	 * @param indexManager
	 * @param viewSchema
	 */
	long populateViewIndexFromReplication(IdAndVersion idAndVersion, TableIndexManager indexManager,
			List<ColumnModel> viewSchema) {
		// Look-up the type for this table.
		Long viewTypeMask = tableManagerSupport.getViewTypeMask(idAndVersion);
		// Get the containers for this view.
		Set<Long> allContainersInScope = tableManagerSupport.getAllContainerIdsForViewScope(idAndVersion,
				viewTypeMask);
		return indexManager.populateViewFromEntityReplication(idAndVersion.getId(), viewTypeMask,
				allContainersInScope, viewSchema);
	}

	/**
	 * Populate the view table from csv snapshot.
	 * @param idAndVersion
	 * @param indexManager
	 */
	long populateViewFromSnapshot(IdAndVersion idAndVersion, TableIndexManager indexManager) {
		ViewSnapshot snapshot = viewSnapshotDao.getSnapshot(idAndVersion);
		File tempFile = null;
		try {
			tempFile = fileProvider.createTempFile("ViewSnapshotDownload", ".csv.gzip");
			// download the snapshot file to a temp file.
			s3Client.getObject(new GetObjectRequest(snapshot.getBucket(), snapshot.getKey()), tempFile);
			try (CSVReaderIterator reader = new CSVReaderIterator(new CSVReader(fileProvider.createReader(
					fileProvider.createGZIPInputStream(fileProvider.createFileInputStream(tempFile)),
					StandardCharsets.UTF_8)))) {
				indexManager.populateViewFromSnapshot(idAndVersion, reader);
			}
			return snapshot.getSnapshotId();
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			if (tempFile != null) {
				tempFile.delete();
			}
		}
	}

	/**
	 * Create a view snapshot file and upload it to S3.
	 * 
	 * @param idAndVersion
	 * @param viewTypeMask
	 * @param viewSchema
	 * @param allContainersInScope
	 * @return
	 * @throws IOException
	 * @throws FileNotFoundException
	 * @throws UnsupportedEncodingException
	 */
	BucketAndKey createViewSnapshotAndUploadToS3(IdAndVersion idAndVersion, Long viewTypeMask,
			List<ColumnModel> viewSchema, Set<Long> allContainersInScope) {
		ValidateArgument.required(idAndVersion, "idAndVersion");
		ValidateArgument.required(viewTypeMask, "viewTypeMask");
		ValidateArgument.required(viewSchema, "viewSchema");
		ValidateArgument.required(allContainersInScope, "allContainersInScope");

		TableIndexManager indexManager = connectionFactory.connectToTableIndex(idAndVersion);

		File tempFile = null;
		try {
			tempFile = fileProvider.createTempFile("ViewSnapshot", ".csv");
			// Stream view data from the replication database to a local CSV file.
			try (CSVWriter writer = new CSVWriter(fileProvider.createWriter(
					fileProvider.createGZIPOutputStream(fileProvider.createFileOutputStream(tempFile)),
					StandardCharsets.UTF_8))) {
				CSVWriterStream writerAdapter = (String[] nextLine) -> {
					writer.writeNext(nextLine);
				};
				// write the snapshot to the temp file.
				indexManager.createViewSnapshot(idAndVersion.getId(), viewTypeMask, allContainersInScope, viewSchema,
						writerAdapter);
			}
			// upload the resulting CSV to S3.
			String key = idAndVersion.getId() + "/" + UUID.randomUUID().toString() + ".csv.gzip";
			String bucket = config.getViewSnapshotBucketName();
			s3Client.putObject(new PutObjectRequest(bucket, key, tempFile));
			return new BucketAndKey().withBucket(bucket).withtKey(key);
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			// unconditionally delete the temporary file.
			if (tempFile != null) {
				tempFile.delete();
			}
		}
	}

	@WriteTransaction
	@Override
	public long createSnapshot(UserInfo userInfo, String tableId, SnapshotRequest snapshotOptions) {
		IdAndVersion idAndVersion = IdAndVersion.parse(tableId);
		Long viewTypeMask = tableManagerSupport.getViewTypeMask(idAndVersion);
		List<ColumnModel> viewSchema = getViewSchema(idAndVersion);
		Set<Long> allContainersInScope = tableManagerSupport.getAllContainerIdsForViewScope(idAndVersion, viewTypeMask);
		BucketAndKey bucketAndKey = createViewSnapshotAndUploadToS3(idAndVersion, viewTypeMask, viewSchema,
				allContainersInScope);
		// create a new version
		long snapshotVersion = nodeManager.createSnapshotAndVersion(userInfo, tableId, snapshotOptions);
		IdAndVersion resultingIdAndVersion = IdAndVersion.newBuilder().setId(idAndVersion.getId())
				.setVersion(snapshotVersion).build();
		// bind the current schema to the version
		columModelManager.bindCurrentColumnsToVersion(resultingIdAndVersion);
		// save the snapshot data.
		viewSnapshotDao.createSnapshot(new ViewSnapshot().withBucket(bucketAndKey.getBucket())
				.withKey(bucketAndKey.getKey()).withCreatedBy(userInfo.getId()).withCreatedOn(new Date())
				.withVersion(snapshotVersion).withViewId(idAndVersion.getId()));
		// trigger an update (see: PLFM-5957)
		tableManagerSupport.setTableToProcessingAndTriggerUpdate(resultingIdAndVersion);
		return snapshotVersion;
	}

}
