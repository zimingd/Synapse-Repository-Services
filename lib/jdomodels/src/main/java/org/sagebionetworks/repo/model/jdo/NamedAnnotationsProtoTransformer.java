package org.sagebionetworks.repo.model.jdo;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.amazonaws.services.dynamodbv2.xspec.L;
import com.amazonaws.services.dynamodbv2.xspec.S;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import org.sagebionetworks.repo.model.AnnotationNameSpace;
import org.sagebionetworks.repo.model.Annotations;
import org.sagebionetworks.repo.model.NamedAnnotations;
import org.sagebionetworks.repo.model.protobuf.NamedAnnotationsProto;
import org.sagebionetworks.repo.model.protobuf.NamedAnnotationsProto.NamedAnnotationProto;
import org.sagebionetworks.repo.model.protobuf.NamedAnnotationsProto.AnnotationProto;
import org.sagebionetworks.repo.model.protobuf.NamedAnnotationsProto.AnnotationProto.AnnotationValueProto;
import org.sagebionetworks.repo.model.table.AnnotationType;
import org.springframework.util.CollectionUtils;


public class NamedAnnotationsProtoTransformer {

	///////////////////////////////////////////
	// Serialization code
	///////////////////////////////////////////

	public static byte[] toProtocolBufferBytes(NamedAnnotations namedAnnotations){
		if (namedAnnotations.isEmpty()){
			return null;
		}
		return NamedAnnotationProto.newBuilder()
				.putAnnotations(AnnotationNameSpace.PRIMARY.name(), flattenAnnotationValues(namedAnnotations.getPrimaryAnnotations()))
				.putAnnotations(AnnotationNameSpace.ADDITIONAL.name(), flattenAnnotationValues(namedAnnotations.getAdditionalAnnotations()))
				.build().toByteArray();
	}

	static AnnotationProto flattenAnnotationValues(Annotations annotations){
		Map<String, AnnotationProto.AnnotationValueProto> result = new HashMap<>(
				annotations.getStringAnnotations().size()
						+ annotations.getDoubleAnnotations().size()
						+ annotations.getLongAnnotations().size()
						+ annotations.getDateAnnotations().size()
						+ annotations.getBlobAnnotations().size()
		);

		putAnnotationValuesIntoFlattenedMap(result, annotations.getStringAnnotations(), String.class);
		putAnnotationValuesIntoFlattenedMap(result, annotations.getDoubleAnnotations(), Double.class);
		putAnnotationValuesIntoFlattenedMap(result, annotations.getLongAnnotations(), Long.class);
		putAnnotationValuesIntoFlattenedMap(result, annotations.getDateAnnotations(), Date.class);
		putAnnotationValuesIntoFlattenedMap(result, annotations.getBlobAnnotations(), byte[].class);

		return AnnotationProto.newBuilder().putAllAnnoMap(result).build();
	}

	static <T> void putAnnotationValuesIntoFlattenedMap(Map<String, AnnotationProto.AnnotationValueProto> resultMap, Map<String, List<T>> typedAnnotations, Class<T> clazz){
		final Function<T, AnnotationProto.AnnotationValueProto> protoCreatorFunction = getAnnotationValueProtoCreatorFunction(clazz);

		for(Map.Entry<String, List<T>> entry : typedAnnotations.entrySet()){
			List<T> valueList = entry.getValue();
			if(CollectionUtils.isEmpty(valueList)){
				continue;
			}
			//only save first value since multiple values is deprecated
			T value = valueList.get(0);
			if(value == null){
				continue;
			}
			//TODO: how do we want to deal with repeated keys?
			resultMap.put(entry.getKey(), protoCreatorFunction.apply(value));
		}
	}


	static <T> Function<T, AnnotationProto.AnnotationValueProto> getAnnotationValueProtoCreatorFunction(Class<T> clazz){
		if(String.class.equals(clazz)){
			return  value -> AnnotationValueProto.newBuilder()
					.setStringValue((String) value)
					.build();
		} else if (Double.class.equals(clazz)){
			return value -> AnnotationValueProto.newBuilder()
					.setDoubleValue((Double) value)
					.build();
		} else if (Long.class.equals(clazz)){
			return value -> AnnotationValueProto.newBuilder()
					.setLongValue((Long) value)
					.build();
		} else if (Date.class.equals(clazz)){
			return value -> AnnotationValueProto.newBuilder()
					.setDateValue( ((Date) value).getTime() )
					.build();
		} else if (byte[].class.equals(clazz)){
			return value -> AnnotationValueProto.newBuilder()
					.setBlobValue( ByteString.copyFrom((byte[]) value) )
					.build();
		} else {
			throw new IllegalArgumentException("unexpected type: " + clazz);
		}
	}

	///////////////////////////////////////////
	// Deserialization code
	///////////////////////////////////////////

	public static NamedAnnotations fromProtocolBufferBytes(byte[] protocolBufferBytes) throws InvalidProtocolBufferException {
		if(protocolBufferBytes == null || protocolBufferBytes.length <= 0){
			return new NamedAnnotations();
		}

		NamedAnnotationProto namedAnnotationProto = NamedAnnotationProto.parseFrom(protocolBufferBytes);

		Annotations primaryAnnotations = toAnnotations(namedAnnotationProto.getAnnotationsOrDefault(AnnotationNameSpace.PRIMARY.name(), null));
		Annotations additionalAnnotations = toAnnotations(namedAnnotationProto.getAnnotationsOrDefault(AnnotationNameSpace.ADDITIONAL.name(), null));

		return new NamedAnnotations(primaryAnnotations, additionalAnnotations);
	}

	static Annotations toAnnotations(AnnotationProto source){
		Annotations result = new Annotations();
		if (source == null){
			return result;
		}

		for(Map.Entry<String, AnnotationValueProto> entry : source.getAnnoMapMap().entrySet()){
			String key = entry.getKey();
			AnnotationValueProto value = entry.getValue();
			switch (value.getAnnotationValueUnionCase()){
				case STRINGVALUE:
					result.addAnnotation(key, value.getStringValue());
					break;
				case DOUBLEVALUE:
					result.addAnnotation(key, value.getDoubleValue());
					break;
				case LONGVALUE:
					result.addAnnotation(key, value.getLongValue());
					break;
				case DATEVALUE:
					result.addAnnotation(key, new Date(value.getDateValue()));
					break;
				case BLOBVALUE:
					result.addAnnotation(key, value.getBlobValue().toByteArray());
					break;
				default:
					throw new IllegalStateException("incorrectly serialized annotation");
			}
		}
		return result;
	}
}
