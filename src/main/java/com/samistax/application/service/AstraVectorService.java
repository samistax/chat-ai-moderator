package com.samistax.application.service;

import com.dtsx.astra.sdk.vector.AstraVectorClient;
import com.dtsx.astra.sdk.vector.AstraVectorDatabaseClient;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.embedding.Embedding;
import com.theokanning.openai.embedding.EmbeddingRequest;
import com.theokanning.openai.moderation.ModerationRequest;
import com.theokanning.openai.moderation.ModerationResult;
import com.theokanning.openai.service.OpenAiService;
import io.stargate.sdk.json.vector.JsonVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

@Service
public class AstraVectorService {

    @Value( "${astra.api.application-token}" )
    private String ASTRA_DB_TOKEN;
    @Value( "${astra.vector.db-name:aidemo}" )
    private String VECTOR_DB_NAME;

    private AstraVectorDatabaseClient  vectorDb;

    public AstraVectorService() {}

    @PostConstruct
    private void init() {
        if ( vectorDb == null) {
            // Get handle to Astra Vector
            AstraVectorClient vectorClient = new AstraVectorClient(ASTRA_DB_TOKEN);
            vectorDb = vectorClient.database(VECTOR_DB_NAME);
            vectorDb.findAllStores().forEach(s -> System.out.println(s));
        }
    }
    public AstraVectorDatabaseClient getVectorClient() {
        return vectorDb;
    }
    public JsonVectorStore getVectorStore( String store) {
        return vectorDb.vectorStore(store);
    }
}
