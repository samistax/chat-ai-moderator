package com.samistax.application.service;

import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.embedding.Embedding;
import com.theokanning.openai.embedding.EmbeddingRequest;
import com.theokanning.openai.moderation.ModerationRequest;
import com.theokanning.openai.moderation.ModerationResult;
import com.theokanning.openai.service.OpenAiService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

@Service
public class OpenAIClient {

    @Value( "${openai.apikey}" )
    private String OPENAI_API_KEY;


    private OpenAiService service;

    public OpenAIClient() {}

    private void initService() {
        if ( service == null) {
            service = new OpenAiService(OPENAI_API_KEY, Duration.ofSeconds(60L));
        }
    }
    public String getTextEmbedding(String text) {
        return getEmbedding("text-embedding-ada-002", text);
    }
    public String getEmbedding(String model,String text) {
        initService();

        EmbeddingRequest embeddingRequest = EmbeddingRequest.builder()
                .model(model)
                .input(Arrays.asList(text))
                .build();

        int maxRetries = 3;
        int retryCount = 0;

        while (retryCount < maxRetries) {
            try {
                List<Embedding> embeddings = service.createEmbeddings(embeddingRequest).getData();
                return embeddings.get(0).getEmbedding().toString();
            } catch (Exception e) {
                System.out.println("SocketTimeoutException occurred. Retrying...");
                retryCount++;
            }
        }
        return "";
    }
    public ChatCompletionResult createChatCompletion(ChatCompletionRequest completionRequest) {
        initService();
        return service.createChatCompletion(completionRequest);
    }

    public ModerationResult moderateContent(String testToBeModarated) {
        initService();
        ModerationRequest req = ModerationRequest.builder()
                .model("text-moderation-latest")
                .input(testToBeModarated).build();
        return service.createModeration(req);
    }
    public OpenAiService getService() {
        // Ensure service handle is created
        initService();
        return service;
    }

}
