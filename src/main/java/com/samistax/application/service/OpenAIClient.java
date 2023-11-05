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

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OpenAIClient {

    @Value( "${openai.apikey}" )
    private String OPENAI_API_KEY;

    private OpenAiService service;

    public OpenAIClient() {}

    @PostConstruct
    private void init() {
        service = new OpenAiService(OPENAI_API_KEY, Duration.ofSeconds(60L));
    }
    public String getTextEmbedding(String text) {
        List<Float> embedding = getEmbedding("text-embedding-ada-002", text);
        return  embedding.toString();
    }
    public List<Embedding> getEmbeddings(List<String> textArray) {
        EmbeddingRequest embeddingRequest = EmbeddingRequest.builder()
                .model("text-embedding-ada-002")
                .input(textArray)
                .build();
        return service.createEmbeddings(embeddingRequest).getData();
    }

    public List<Float> getEmbedding(String model,String text) {

        EmbeddingRequest embeddingRequest = EmbeddingRequest.builder()
                .model(model)
                .input(Arrays.asList(text))
                .build();

        int maxRetries = 3;
        int retryCount = 0;

        while (retryCount < maxRetries) {
            try {
                List<Embedding> embeddings = service.createEmbeddings(embeddingRequest).getData();
                List<Double> vector =  embeddings.get(0).getEmbedding();
                return vector.stream().map(d -> d.floatValue()).collect(Collectors.toList());
            } catch (Exception e) {
                System.out.println("SocketTimeoutException occurred. Retrying...");
                retryCount++;
            }
        }
        return new ArrayList<>();
    }
    public ChatCompletionResult createChatCompletion(ChatCompletionRequest completionRequest) {
        return service.createChatCompletion(completionRequest);
    }

    public ModerationResult moderateContent(String testToBeModarated) {
        ModerationRequest req = ModerationRequest.builder()
                .model("text-moderation-latest")
                .input(testToBeModarated).build();
        return service.createModeration(req);
    }
    public OpenAiService getService() {
        // Ensure service handle is created
        return service;
    }

}
