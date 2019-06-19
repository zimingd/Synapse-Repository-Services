package org.sagebionetworks.search.elasticsearch;

import java.io.IOException;

import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.client.ElasticsearchClient;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetMappingsRequest;
import org.elasticsearch.index.IndexNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;

public class ElasticSearchDaoImpl {

	@Autowired
	RestHighLevelClient client;



	@Override
	boolean doesSearchIndexExists(String indexName){

		try{
			GetMappingsRequest getMappingsRequest = new GetMappingsRequest()
					//fetch information from master node
					.local(false);
			client.indices().getMapping(getMappingsRequest, RequestOptions.DEFAULT);

		} catch (IndexNotFoundException e){ //TODO: find exeption to catch and handle correctly
			return false;
		} catch (IOException e) {
			e.printStackTrace(); //
		}
	}
}
