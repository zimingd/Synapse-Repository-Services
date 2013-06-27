package org.sagebionetworks.javadoc.velocity.controller;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.sagebionetworks.javadoc.web.services.FilterUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import com.sun.javadoc.AnnotationDesc;
import com.sun.javadoc.AnnotationDesc.ElementValuePair;
import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.MethodDoc;
import com.sun.javadoc.Parameter;
import com.sun.javadoc.Type;

public class ControllerUtils {

	public static String REQUEST_MAPPING_VALUE = RequestMapping.class.getName()+".value";
	public static String REQUEST_MAPPING_METHOD = RequestMapping.class.getName()+".method";
	
	
	/**
	 * Translate from a a controller class to a Controller model.
	 * 
	 * @param classDoc
	 * @return
	 */
	public static ControllerModel translateToModel(ClassDoc classDoc){
		ControllerModel model = new ControllerModel();
		// Setup the basic data
		model.setName(classDoc.name());
		model.setClassDescription(classDoc.getRawCommentText());
    	Iterator<MethodDoc> methodIt = FilterUtils.requestMappingIterator(classDoc.methods());
    	List<MethodModel> methods = new LinkedList<MethodModel>();
    	model.setMethods(methods);
    	while(methodIt.hasNext()){
    		MethodDoc methodDoc = methodIt.next();
    		System.out.println(methodDoc.qualifiedName());
    		System.out.println(methodDoc.name());
    		MethodModel methodModel = translateMethod(methodDoc);
    		methods.add(methodModel);
    	}
		return model;
	}
	
	public static MethodModel translateMethod(MethodDoc methodDoc){
		MethodModel methodModel = new MethodModel();
		//Process the method annotations.
        processMethodAnnotations(methodDoc, methodModel);
        // Now process the parameters
        processParameterAnnotations(methodDoc, methodModel);
		methodModel.setDescription(methodDoc.commentText());
		// Create the Link to this method
		String niceUrl = methodModel.getUrl().replaceAll("\\{", "");
		niceUrl = niceUrl.replaceAll("\\}", "");
		niceUrl = niceUrl.replaceAll("/", ".");
		String fullName = methodModel.getHttpType()+niceUrl;
		methodModel.setFullMethodName(fullName);
		Link methodLink = new Link("${"+fullName+"}", methodModel.getHttpType()+" "+methodModel.getUrl());
		methodModel.setMethodLink(methodLink);
		return methodModel;
	}

	private static void processParameterAnnotations(MethodDoc methodDoc, MethodModel methodModel) {
		Parameter[] params = methodDoc.parameters();
        if(params != null){
        	for(Parameter param: params){
        		AnnotationDesc[] paramAnnos = param.annotations();
        		if(paramAnnos != null){
        			for(AnnotationDesc ad: paramAnnos){
        				String qualifiedName = ad.annotationType().qualifiedName();
        				if(RequestBody.class.getName().equals(qualifiedName)){
        					// Request body
        					Link link = new Link("${"+param.type().qualifiedTypeName()+"}", param.typeName());
        					methodModel.setRequestBody(link);
        				}else if(PathVariable.class.getName().equals(qualifiedName)){
        					// Path parameter
        					ParameterModel paramModel = new ParameterModel();
        					paramModel.setName("{"+param.name()+"}");
//        					paramModel.set("{"+param.name()+"}");
        				}
        			}
        		}
        	}
        }
	}

	private static void processMethodAnnotations(MethodDoc methodDoc,
			MethodModel methodModel) {
		AnnotationDesc[] annos = methodDoc.annotations();
        if(annos != null){
        	for(AnnotationDesc ad: annos){
        		String qualifiedName = ad.annotationType().qualifiedName();
        		System.out.println(qualifiedName);
        		if(RequestMapping.class.getName().equals(qualifiedName)){
        			extractRequestMapping(methodModel, ad);
        		}else if(ResponseBody.class.getName().equals(qualifiedName)){
        			Link link = extractResponseLink(methodDoc);
        			methodModel.setResponseBody(link);
        		}
        	}
        }
	}

	private static Link extractResponseLink(MethodDoc methodDoc) {
		// this means there is a response body for this method.
		Type returnType = methodDoc.returnType();
		Link reponseLink = new Link();
		StringBuilder builder = new StringBuilder();
		builder.append("${").append(returnType.qualifiedTypeName()).append("}");
		reponseLink.setHref(builder.toString());
		reponseLink.setDisplay(returnType.simpleTypeName());
		return reponseLink;
	}

	/**
	 * Extract the request mapping data from the annotation.
	 * @param methodModel
	 * @param ad
	 */
	private static void extractRequestMapping(MethodModel methodModel, AnnotationDesc ad) {
		for(ElementValuePair pair: ad.elementValues()){
			String pairName = pair.element().qualifiedName();
			if(REQUEST_MAPPING_VALUE.equals(pairName)){
				String rawValue = pair.value().toString();
				if(rawValue!= null){
		    		methodModel.setUrl(rawValue.substring(1, rawValue.length()-1));
				}
			}else if(REQUEST_MAPPING_METHOD.equals(pairName)){
				String value = pair.value().toString();
				if(value != null){
					int inxed = RequestMethod.class.getName().length();
					methodModel.setHttpType(value.substring(inxed+1));
				}
			}
		}
	}
}
