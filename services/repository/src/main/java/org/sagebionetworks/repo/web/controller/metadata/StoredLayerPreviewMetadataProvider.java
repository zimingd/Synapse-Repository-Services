package org.sagebionetworks.repo.web.controller.metadata;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.sagebionetworks.repo.model.DatastoreException;
import org.sagebionetworks.repo.model.StoredLayerPreview;
import org.sagebionetworks.repo.model.UserInfo;
import org.sagebionetworks.repo.web.controller.metadata.TypeSpecificMetadataProvider.EventType;

public class StoredLayerPreviewMetadataProvider implements
		TypeSpecificMetadataProvider<StoredLayerPreview> {

	@Override
	public void addTypeSpecificMetadata(StoredLayerPreview entity,
			HttpServletRequest request, UserInfo user, EventType eventType) {
		// Clear the blob and set the string
		if (entity.getPreviewBlob() != null) {
			try {
				entity.setPreviewString(new String(entity.getPreviewBlob(),
						"UTF-8"));
				entity.setPreviewBlob(null);
			} catch (UnsupportedEncodingException e) {
				throw new IllegalArgumentException(e);
			}
			try {
				createPreviewMap(entity);
			} catch (DatastoreException e) {
				throw new IllegalArgumentException(e);
			}
		}
	}

	@Override
	public void validateEntity(StoredLayerPreview entity, EventType eventType) {
		// Convert the blob value to the string value
		if (entity.getPreviewString() != null) {
			try {
				entity.setPreviewBlob(entity.getPreviewString().getBytes(
						"UTF-8"));
				entity.setPreviewString(null);
			} catch (UnsupportedEncodingException e) {
				throw new IllegalArgumentException(e);
			}
		}
	}

	/**
	 * Given a preview build the header and map.
	 * @param preview
	 * @throws DatastoreException
	 */
	public static void createPreviewMap(StoredLayerPreview preview) throws DatastoreException {
		String rawPreview = preview.getPreviewString();
		if (rawPreview == null)
			return;

		// Split the lines
		String lines[] = rawPreview.split("(?m)\n");
		String header[] = lines[0].split("\t");
		int minColumns = 2;
		int minLines = 4;

		// Confirm that we are able to interpret this as a tab-delimited file
		if ((header.length < minColumns) || (lines.length < minLines)) {
			// This means we will not set the header or rows
			return;
//			throw new DatastoreException("Unable to convert preview data to map format");
		}
		// These are our headers
		preview.setHeaders(header);
		List<Map<String, String>> results = new ArrayList<Map<String, String>>();
		for (int row = 1; row < lines.length; row++) {
			Map<String, String> result = new HashMap<String, String>();
			String values[] = lines[row].split("\t");
			// Confirm that the tab-delimited data is well-formed
			if (header.length != values.length) {
				throw new DatastoreException("Unable to convert preview data to map format");
			}
			for (int column = 0; column < values.length; column++) {
				result.put(header[column], values[column]);
			}
			results.add(result);
		}
		preview.setRows(results);
	}
	
	@Override
	public void entityDeleted(StoredLayerPreview deleted) {
		// TODO Auto-generated method stub
		
	}

}
