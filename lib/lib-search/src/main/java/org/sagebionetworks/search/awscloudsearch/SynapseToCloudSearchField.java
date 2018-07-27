package org.sagebionetworks.search.awscloudsearch;


import static org.sagebionetworks.search.awscloudsearch.CloudSearchFieldConstants.CLOUD_SEARCH_FIELD_ACL;
import static org.sagebionetworks.search.awscloudsearch.CloudSearchFieldConstants.CLOUD_SEARCH_FIELD_BOOST;
import static org.sagebionetworks.search.awscloudsearch.CloudSearchFieldConstants.CLOUD_SEARCH_FIELD_CONSORTIUM;
import static org.sagebionetworks.search.awscloudsearch.CloudSearchFieldConstants.CLOUD_SEARCH_FIELD_CREATED_BY;
import static org.sagebionetworks.search.awscloudsearch.CloudSearchFieldConstants.CLOUD_SEARCH_FIELD_CREATED_ON;
import static org.sagebionetworks.search.awscloudsearch.CloudSearchFieldConstants.CLOUD_SEARCH_FIELD_DESCRIPTION;
import static org.sagebionetworks.search.awscloudsearch.CloudSearchFieldConstants.CLOUD_SEARCH_FIELD_DISEASE;
import static org.sagebionetworks.search.awscloudsearch.CloudSearchFieldConstants.CLOUD_SEARCH_FIELD_ETAG;
import static org.sagebionetworks.search.awscloudsearch.CloudSearchFieldConstants.CLOUD_SEARCH_FIELD_ID;
import static org.sagebionetworks.search.awscloudsearch.CloudSearchFieldConstants.CLOUD_SEARCH_FIELD_MODIFIED_BY;
import static org.sagebionetworks.search.awscloudsearch.CloudSearchFieldConstants.CLOUD_SEARCH_FIELD_MODIFIED_ON;
import static org.sagebionetworks.search.awscloudsearch.CloudSearchFieldConstants.CLOUD_SEARCH_FIELD_NAME;
import static org.sagebionetworks.search.awscloudsearch.CloudSearchFieldConstants.CLOUD_SEARCH_FIELD_NODE_TYPE;
import static org.sagebionetworks.search.awscloudsearch.CloudSearchFieldConstants.CLOUD_SEARCH_FIELD_NUM_SAMPLES;
import static org.sagebionetworks.search.awscloudsearch.CloudSearchFieldConstants.CLOUD_SEARCH_FIELD_PARENT_ID;
import static org.sagebionetworks.search.awscloudsearch.CloudSearchFieldConstants.CLOUD_SEARCH_FIELD_PLATFORM;
import static org.sagebionetworks.search.awscloudsearch.CloudSearchFieldConstants.CLOUD_SEARCH_FIELD_REFERENCE;
import static org.sagebionetworks.search.awscloudsearch.CloudSearchFieldConstants.CLOUD_SEARCH_FIELD_TISSUE;
import static org.sagebionetworks.search.awscloudsearch.CloudSearchFieldConstants.CLOUD_SEARCH_FIELD_UPDATE_ACL;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import com.amazonaws.services.cloudsearchv2.model.IndexField;
import org.sagebionetworks.repo.model.search.query.SearchFieldName;
import org.sagebionetworks.util.ValidateArgument;

/**
 * This class tracks all of the fields used in CloudSearch and provides mapping from fields listed in Synapse's API
 * to their actual CloudSearch fields.
 */
public enum SynapseToCloudSearchField {
	ID(SearchFieldName.Id, CLOUD_SEARCH_FIELD_ID, false),
	NAME(SearchFieldName.Name, CLOUD_SEARCH_FIELD_NAME, true),
	ENTITY_TYPE(SearchFieldName.EntityType, CLOUD_SEARCH_FIELD_NODE_TYPE, false),
	MODIFIED_BY(SearchFieldName.ModifiedBy, CLOUD_SEARCH_FIELD_MODIFIED_BY, false),
	MODIFIED_ON(SearchFieldName.ModifiedOn, CLOUD_SEARCH_FIELD_MODIFIED_ON, false),
	CREATED_BY(SearchFieldName.CreatedBy, CLOUD_SEARCH_FIELD_CREATED_BY, false),
	CREATED_ON(SearchFieldName.CreatedOn, CLOUD_SEARCH_FIELD_CREATED_ON, true),
	DESCRIPTION(SearchFieldName.Description, CLOUD_SEARCH_FIELD_DESCRIPTION, true),

	//indexes of annotations
	CONSORTIUM(SearchFieldName.Consortium, CLOUD_SEARCH_FIELD_CONSORTIUM, true),
	DISEASE(SearchFieldName.Disease, CLOUD_SEARCH_FIELD_DISEASE, true),
	NUM_SAMPLES(SearchFieldName.NumSamples, CLOUD_SEARCH_FIELD_NUM_SAMPLES, true),
	TISSUE(SearchFieldName.Tissue, CLOUD_SEARCH_FIELD_TISSUE, true),

	//The ones below are not exposed in our API currently (and probably never will be)
	ETAG(null, CLOUD_SEARCH_FIELD_ETAG, false),
	BOOST(null, CLOUD_SEARCH_FIELD_BOOST, false),
	PARENT_ID(null, CLOUD_SEARCH_FIELD_PARENT_ID, false),
	PLATFORM(null, CLOUD_SEARCH_FIELD_PLATFORM, false),
	REFERENCE(null, CLOUD_SEARCH_FIELD_REFERENCE, false),
	ACL(null, CLOUD_SEARCH_FIELD_ACL, false),
	UPDATE_ACL(null, CLOUD_SEARCH_FIELD_UPDATE_ACL, false);

	private final SearchFieldName synapseSearchFieldName;
	private final CloudSearchField cloudSearchField;
	private boolean includeInSearchedFields;

	SynapseToCloudSearchField(SearchFieldName synapseSearchFieldName, CloudSearchField cloudSearchField, boolean includeInSearchedFields){
		this.synapseSearchFieldName = synapseSearchFieldName;
		this.cloudSearchField = cloudSearchField;
		this.includeInSearchedFields = includeInSearchedFields;
	}

	/**
	 * Returns the CloudSearchField corresponding to the SearchFieldName
	 * @param synapseSearchFieldName the SearchFieldName used to find its corresponding CloudSearchField
	 * @return CloudSearchField corresponding to the SearchFieldName or null if no match is found.
	 */
	public static CloudSearchField cloudSearchFieldFor(SearchFieldName synapseSearchFieldName){
		ValidateArgument.required(synapseSearchFieldName, "synapseSearchFieldName");

		for (SynapseToCloudSearchField synapseToCloudSearchField : values()){
			if(synapseSearchFieldName == synapseToCloudSearchField.synapseSearchFieldName){
				return synapseToCloudSearchField.cloudSearchField;
			}
		}
		throw new IllegalArgumentException("Unknown SearchField");
	}

	/**
	 * Returns a List of all IndexFields needed for initialization of the Cloud Search Domain.
	 * @return a List of all IndexFields needed for initialization of the Cloud Search Domain,
	 */
	public static List<IndexField> loadSearchDomainSchema() {
		List<IndexField> indexFields = new ArrayList<>();
		for(SynapseToCloudSearchField fieldEnum : values()){
			CloudSearchField cloudSearchIndexField = fieldEnum.cloudSearchField;
			if(cloudSearchIndexField instanceof SynapseCreatedCloudSearchField){
				indexFields.add( ((SynapseCreatedCloudSearchField) cloudSearchIndexField).getIndexField() );
			}
		}
		return indexFields;
	}

	public static List<String> getQueriedFieldNames(){
		List<String> searchedFields = new ArrayList<>();
		for(SynapseToCloudSearchField fieldEnum : values()){
			if (fieldEnum.includeInSearchedFields){
				searchedFields.add(fieldEnum.cloudSearchField.getFieldName());
			}
		}
		return searchedFields;
	}
}
