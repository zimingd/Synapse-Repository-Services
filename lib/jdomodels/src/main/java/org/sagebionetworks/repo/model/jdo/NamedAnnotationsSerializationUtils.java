package org.sagebionetworks.repo.model.jdo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.sagebionetworks.repo.model.NamedAnnotations;

public class NamedAnnotationsSerializationUtils {
	//should not be constructed every time because it caches mappings
	private static final ObjectMapper objectMapper = new ObjectMapper();

	static{
	objectMapper.setVisibility(objectMapper.getSerializationConfig().getDefaultVisibilityChecker()
                .withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withCreatorVisibility(JsonAutoDetect.Visibility.NONE));
	objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
	objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	objectMapper.readerFor(NamedAnnotations.class);
	objectMapper.writerFor(NamedAnnotations.class);
	}

	public static byte[] toBytes(NamedAnnotations namedAnnotations) throws IOException {
		if(namedAnnotations == null || namedAnnotations.isEmpty()){
			return null;
		}
		ObjectWriter objectWriter = objectMapper.writerFor(NamedAnnotations.class);
		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		objectWriter.writeValue(byteArrayOutputStream, namedAnnotations);
		return byteArrayOutputStream.toByteArray();
	}

	public static NamedAnnotations fromBytes(byte[] bytes) throws IOException {
		if(bytes == null || bytes.length == 0){
			return new NamedAnnotations();
		}
		ObjectReader objectReader = objectMapper.readerFor(NamedAnnotations.class);
		return objectReader.readValue(bytes);
	}

}
