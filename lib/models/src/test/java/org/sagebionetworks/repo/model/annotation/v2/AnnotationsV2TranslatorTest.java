package org.sagebionetworks.repo.model.annotation.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sagebionetworks.repo.model.Annotations;
import org.sagebionetworks.repo.model.entity.query.Value;
import org.sagebionetworks.util.ValidateArgument;

class AnnotationsV2TranslatorTest {

	AnnotationsV2 annotationsV2;
	Annotations annotationsV1;

	final String stringKey1 = "stringKey1";
	final String stringKey2 = "stringKey2";
	final String dateKey1 = "dateKey1";
	final String dateKey2 = "dateKey2";
	final String longKey1 = "longKey1";
	final String longKey2 = "longKey2";
	final String doubleKey1 = "doubleKey1";
	final String doubleKey2 = "doubleKey2";

	@BeforeEach
	void setUp() {

		//create an annotations v1 and v2 that are equivalent with all value types
		annotationsV1 = new Annotations();
		annotationsV1.addAnnotation(stringKey1, Arrays.asList("val1", "val2"));
		annotationsV1.addAnnotation(stringKey2, Arrays.asList("val3", "val4"));

		annotationsV1.addAnnotation(doubleKey1, Arrays.asList(1.2, 2.3));
		annotationsV1.addAnnotation(doubleKey2, Arrays.asList(3.4, 4.5));

		annotationsV1.addAnnotation(dateKey1, Arrays.asList(new Date(123), new Date(456)));
		annotationsV1.addAnnotation(dateKey2, Arrays.asList(new Date(789), new Date(890)));

		annotationsV1.addAnnotation(longKey1, Arrays.asList(123L, 456L));
		annotationsV1.addAnnotation(longKey2, Arrays.asList(789L, 890L));



		annotationsV2 = new AnnotationsV2();

		AnnotationV2Utils.putAnnotations(annotationsV2, stringKey1, Arrays.asList("val1", "val2"), AnnotationsV2ValueType.STRING);
		AnnotationV2Utils.putAnnotations(annotationsV2, stringKey2, Arrays.asList("val3", "val4"), AnnotationsV2ValueType.STRING);

		AnnotationV2Utils.putAnnotations(annotationsV2, doubleKey1, Arrays.asList("1.2", "2.3"), AnnotationsV2ValueType.DOUBLE);
		AnnotationV2Utils.putAnnotations(annotationsV2, doubleKey2, Arrays.asList("3.4", "4.5"), AnnotationsV2ValueType.DOUBLE);

		AnnotationV2Utils.putAnnotations(annotationsV2, dateKey1, Arrays.asList("123", "456"), AnnotationsV2ValueType.DATE);
		AnnotationV2Utils.putAnnotations(annotationsV2, dateKey2, Arrays.asList("789", "890"), AnnotationsV2ValueType.DATE);

		AnnotationV2Utils.putAnnotations(annotationsV2, longKey1, Arrays.asList("123", "456"), AnnotationsV2ValueType.LONG);
		AnnotationV2Utils.putAnnotations(annotationsV2, longKey2, Arrays.asList("789", "890"), AnnotationsV2ValueType.LONG);
	}

	@Test
	public void testToAnnotationsV1(){
		//method under test
		Annotations translated = AnnotationsV2Translator.toAnnotationsV1(annotationsV2);

		assertEquals(annotationsV1, translated);
	}

	@Test
	public void testToAnnotationsV1_skipEmptyListValues(){
		//replace stringKey1 's value with an empty list
		AnnotationsV2Value emptyValue = new AnnotationsV2Value();
		emptyValue.setType(AnnotationsV2ValueType.STRING);
		emptyValue.setValue(Collections.emptyList());
		annotationsV2.getAnnotations().put(stringKey1, emptyValue);

		//method under test
		Annotations translated = AnnotationsV2Translator.toAnnotationsV1(annotationsV2);

		//expected value should not have stringkey1 value;
		annotationsV1.getStringAnnotations().remove(stringKey1);
		assertEquals(annotationsV1, translated);
	}

	@Test
	public void testToAnnotationsV1_doubleNaN(){
		//replace stringKey1 's value with an empty list
		String nanKey = "nanKey";
		annotationsV2 = new AnnotationsV2();
		AnnotationV2Utils.putAnnotations(annotationsV2, nanKey, Arrays.asList("NaN","nan", "NAN", "nAn"), AnnotationsV2ValueType.DOUBLE);

		//method under test
		Annotations translated = AnnotationsV2Translator.toAnnotationsV1(annotationsV2);

		//expected value should not have stringkey1 value;
		annotationsV1 = new Annotations();
		annotationsV1.addAnnotation(nanKey, Arrays.asList(Double.NaN, Double.NaN, Double.NaN, Double.NaN));

		assertEquals(annotationsV1, translated);
	}

	@Test
	public void testToAnnotationsV2(){
		//method under test
		AnnotationsV2 translated = AnnotationsV2Translator.toAnnotationsV2(annotationsV1);

		assertEquals(annotationsV2, translated);
	}

	@Test
	public void testToAnnotationsV2_skipEmptyListValues(){
		//set list for stringkey1 to empty
		annotationsV1.getStringAnnotations().put(stringKey1, Collections.emptyList());
		//method under test
		AnnotationsV2 translated = AnnotationsV2Translator.toAnnotationsV2(annotationsV1);

		//should stringkey1 not have been translated to annotations key1
		annotationsV2.getAnnotations().remove(stringKey1);
		assertEquals(annotationsV2, translated);
	}

}