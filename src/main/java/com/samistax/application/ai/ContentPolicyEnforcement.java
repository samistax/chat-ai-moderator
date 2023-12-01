package com.samistax.application.ai;

import com.datastax.oss.driver.shaded.guava.common.primitives.Floats;
import com.samistax.application.Application;
import com.samistax.application.service.AstraVectorService;
import com.samistax.application.service.OpenAIClient;
import com.datastax.astra.sdk.AstraClient;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.stargate.sdk.json.domain.JsonDocument;
import io.stargate.sdk.json.domain.JsonResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.configurationprocessor.json.JSONArray;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import io.stargate.sdk.json.CollectionClient;

import javax.annotation.PostConstruct;

@Component
public class ContentPolicyEnforcement {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @JsonPropertyDescription("the proprietary content policy including rules and enforcement guidelines")
    public String category;

    public ContentPolicyEnforcement() {}


    public String getContentPolicyEnforcementActions(AstraClient astraClient,OpenAIClient aiClient,AstraVectorService astraVector,  String category) {

        JSONObject jsonResponse = new JSONObject();
        List<String> actions = new ArrayList<>();
        long startTime = System.currentTimeMillis();
        if ( AstraVectorService.JSON_API_MODE ) {
            CollectionClient collection = astraVector.getDefaultCollection();

            List<Float> embedding = aiClient.getEmbedding("text-embedding-ada-002", category);
            logger.info("getTextEmbedding took " + (System.currentTimeMillis() - startTime) + " (ms)");
            startTime = System.currentTimeMillis();
            float[] embeddingFloatArray = Floats.toArray(embedding);
            List<JsonResult> resultsSet = collection.similaritySearch(embeddingFloatArray,3);
            logger.info("Vector search request took " + (System.currentTimeMillis() - startTime) + " (ms)");
            //Sample response: "actions" : [ "Rules", "Enforcement actions", "- Asking you nicely to knock it off" ]

            resultsSet.forEach(r-> actions.add(r.getData().get("policy").toString()));

        } else {
            String embedding = aiClient.getTextEmbedding(category);

            logger.info("getTextEmbedding took " + (System.currentTimeMillis() - startTime) + " (ms)");
            startTime = System.currentTimeMillis();

            ResultSet results = astraClient.cqlSession().execute("SELECT policy FROM demo.policy ORDER BY policy_vector ANN OF " + embedding + "LIMIT 3");
            logger.info("Vector search request took " + (System.currentTimeMillis() - startTime) + " (ms)");

            if (results != null && results.getAvailableWithoutFetching() > 0) {
                // Process the results
                for (Row row : results) {
                    actions.add(row.getString("policy"));
                }
            }
        }
        try {
            jsonResponse.put("actions", actions);
        } catch (JSONException e) { }
        return jsonResponse.toString();
    }
}
