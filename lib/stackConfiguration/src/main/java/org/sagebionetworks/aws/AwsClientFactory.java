package org.sagebionetworks.aws;

import java.util.HashMap;
import java.util.Map;

import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;


/**
 * A factory for creating AWS clients using credential chains.
 *
 */
public class AwsClientFactory {
	
	/*
	 * AmazonS3ClientBuilder takes as a parameter a value from com.amazonaws.regions.Regions, 
	 * which has String values like US_EAST_1, US_WEST_1, CA_CENTRAL_1.
	 * 
	 * AmazonS3.getBucketLocation() returns a String representation of an instance of 
	 * com.amazonaws.services.s3.model.Region, which has String values like null (for us-east-1), 
	 * us-west-1, ca-central-1.
	 * 
	 * To make things more complicated, there is a utility to map from Regions to Region but it's 
	 * com.amazonaws.regions.Region, not com.amazonaws.services.s3.model.Region, and it has values 
	 * like us-east-1, us-west-1, ca-central-1.
	 * 
	 * So we have to map in two steps:
	 */
	public static Region getS3RegionForAWSRegions(Region awsRegion) {
		if (awsRegion==Regions.US_EAST_1) return Region.US_Standard; // string value of Region.US_Standard is null!
		com.amazonaws.regions.Region regionsRegion = com.amazonaws.regions.Region.getRegion(awsRegion);
		return Region.fromValue(regionsRegion.getName()); // this wouldn't work for us-east-1
	}

	/**
	 * Create all region-specific instances of the AmazonS3 client using a credential chain.
	 * 
	 * @return
	 */
	public static SynapseS3Client createAmazonS3Client() {
		Map<Region, S3Client> regionSpecificS3Clients = new HashMap<Region, S3Client>();
		for (Region region: Region.regions() ) {
			S3Client amazonS3 = S3Client.builder()
			.credentialsProvider(SynapseCredentialProviderChain.getInstance())
			.region(region)
			.serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
//			TODO: builder.withForceGlobalBucketAccessEnabled(true)
			.build();
			regionSpecificS3Clients.put(getS3RegionForAWSRegions(region), amazonS3);
		}
		return new SynapseS3ClientImpl(regionSpecificS3Clients);
	}

	/**
	 * Create an instance of the TransferManager using a credential chain.
	 * 
	 * @return
	 */
	public static TransferManager createTransferManager() {
		return new TransferManager(SynapseCredentialProviderChain.getInstance());
	}

	/**
	 * Create an instance of the AmazonCloudWatch using a credential chain.
	 * 
	 * @return
	 */
	public static AmazonCloudWatch createCloudWatchClient() {
		AmazonCloudWatchClientBuilder builder = AmazonCloudWatchClientBuilder.standard();
		builder.withRegion(Regions.US_EAST_1);
		builder.withCredentials(SynapseCredentialProviderChain.getInstance());
		return builder.build();
	}

	/**
	 * Create an instance of the AmazonCloudSearch using a credential chain.
	 * 
	 * @return
	 */
	public static AmazonCloudSearch createAmazonCloudSearchClient() {
		AmazonCloudSearchClientBuilder builder = AmazonCloudSearchClientBuilder.standard();
		builder.withRegion(Regions.US_EAST_1);
		builder.withCredentials(SynapseCredentialProviderChain.getInstance());
		return builder.build();
	}

	/**
	 * Create an instance of the AmazonCloudSearchDomain using a credential chain.
	 * 
	 * @return
	 */
	public static AmazonCloudSearchDomain createAmazonCloudSearchDomain(String endpoint) {
		AmazonCloudSearchDomainClientBuilder builder = AmazonCloudSearchDomainClientBuilder.standard();
		builder.withEndpointConfiguration(new EndpointConfiguration(endpoint, Regions.US_EAST_1.getName()));
		builder.withCredentials(SynapseCredentialProviderChain.getInstance());
		return builder.build();
	}

	/**
	 * Create an instance of the AmazonSQS using a credential chain.
	 * 
	 * @return
	 */
	public static AmazonSQS createAmazonSQSClient() {
		AmazonSQSClientBuilder builder = AmazonSQSClientBuilder.standard();
		builder.withRegion(Regions.US_EAST_1);
		builder.withCredentials(SynapseCredentialProviderChain.getInstance());
		return builder.build();
	}

	/**
	 * Create an instance of AmazonSNS using a credential chain.
	 * 
	 * @return
	 */
	public static AmazonSNS createAmazonSNSClient() {
		AmazonSNSClientBuilder builder = AmazonSNSClientBuilder.standard();
		builder.withRegion(Regions.US_EAST_1);
		builder.withCredentials(SynapseCredentialProviderChain.getInstance());
		return builder.build();
	}

	/**
	 * Create an instance of AmazonSimpleEmailService using a credential chain.
	 * 
	 * @return
	 */
	public static AmazonSimpleEmailService createAmazonSimpleEmailServiceClient() {
		AmazonSimpleEmailServiceClientBuilder builder = AmazonSimpleEmailServiceClientBuilder.standard();
		builder.withRegion(Regions.US_EAST_1);
		builder.withCredentials(SynapseCredentialProviderChain.getInstance());
		return builder.build();
	}

	/**
	 * Create an instance of AWSKMS client using a credential chain.
	 * 
	 * @return
	 */
	public static AWSKMS createAmazonKeyManagementServiceClient() {
		AWSKMSAsyncClientBuilder builder = AWSKMSAsyncClientBuilder.standard();
		builder.withRegion(Regions.US_EAST_1);
		builder.withCredentials(SynapseCredentialProviderChain.getInstance());
		return builder.build();
	}

}
