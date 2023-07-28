package com.samistax.application.ai;

import com.samistax.application.service.OpenAIClient;
import com.datastax.astra.sdk.AstraClient;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ContentPolicyEnforcement {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @JsonPropertyDescription("the proprietary content policy including rules and enforcement guidelines")
    public String category;

    public ContentPolicyEnforcement() {}

    public String getContentPolicyEnforcementActions(AstraClient astraClient,OpenAIClient aiClient,  String category) {

        long startTime = System.currentTimeMillis();
        String embedding = aiClient.getTextEmbedding(category);
        logger.info("getTextEmbedding took " + (System.currentTimeMillis() - startTime) + " (ms)");

        startTime = System.currentTimeMillis();
        ResultSet results = astraClient.cqlSession().execute("SELECT policy FROM demo.policy ORDER BY policy_vector ANN OF " + embedding + "LIMIT 3");
        logger.info("Vector search request took " + (System.currentTimeMillis() - startTime) + " (ms)");

        if (results != null && results.getAvailableWithoutFetching() > 0) {
            // Process the results
            String jsonResponse =  "{\"actions\":[";
            for (Row row : results) {
                jsonResponse = jsonResponse + "\"" + row.getString("policy") + "\",";
            }
            jsonResponse = jsonResponse.replaceAll(",$","") + "]}";
            System.out.println("Result : " + jsonResponse);
            return jsonResponse;
        } else {
            System.out.println("No results found.");
            return "";
        }
    }
}
