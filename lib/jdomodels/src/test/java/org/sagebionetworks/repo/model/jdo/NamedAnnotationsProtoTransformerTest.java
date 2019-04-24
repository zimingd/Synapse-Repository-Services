package org.sagebionetworks.repo.model.jdo;

import static org.junit.jupiter.api.Assertions.*;

import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.Date;

import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.sagebionetworks.repo.model.NamedAnnotations;

class NamedAnnotationsProtoTransformerTest {

	@Test
	public void testRoundTrip() throws IOException {
		long entityId = 123;
		int maxAnnotationChars = 6;
		NamedAnnotations annos = new NamedAnnotations();
		annos.getAdditionalAnnotations().addAnnotation("aString", "someString");
		annos.getAdditionalAnnotations().addAnnotation("aLong", 123L);
		annos.getAdditionalAnnotations().addAnnotation("aDouble", 1.22);
		annos.getAdditionalAnnotations().addAnnotation("aDate", new Date(444L));
		annos.getAdditionalAnnotations().addAnnotation("aBlob", new byte[]{1,2,3,4,5});
		annos.getPrimaryAnnotations().addAnnotation("aString", "someString");
		annos.getPrimaryAnnotations().addAnnotation("aLong", 123L);
		annos.getPrimaryAnnotations().addAnnotation("aDouble", 1.22);
		annos.getPrimaryAnnotations().addAnnotation("aDate", new Date(444L));
		annos.getPrimaryAnnotations().addAnnotation("aBlob", new byte[]{1,2,3,4,5});



		long xmlStart = System.currentTimeMillis();
		byte[] xmlBytes = JDOSecondaryPropertyUtils.compressAnnotations(annos);
		System.out.println(System.currentTimeMillis() - xmlStart+ " size = " + xmlBytes.length);


		xmlStart = System.currentTimeMillis();
		NamedAnnotations xmlannos = JDOSecondaryPropertyUtils.decompressedAnnotations(xmlBytes);
		System.out.println("deserialize time " + (System.currentTimeMillis() - xmlStart));

		System.out.println("--------------------------------------------------------------");

		long start = System.currentTimeMillis();
		byte[] protocolBufferBytes = NamedAnnotationsProtoTransformer.toProtocolBufferBytes(annos);
		System.out.println(System.currentTimeMillis() - start + " size = " + protocolBufferBytes.length);

		start = System.currentTimeMillis();
		NamedAnnotations deserializedAnnos = NamedAnnotationsProtoTransformer.fromProtocolBufferBytes(protocolBufferBytes);
		System.out.println("deserialize time " + (System.currentTimeMillis() - start));




		assertEquals(annos, deserializedAnnos);
	}
}