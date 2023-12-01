package com.samistax.application;

import com.datastax.astra.sdk.AstraClient;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.shaded.guava.common.primitives.Floats;
import com.samistax.application.service.AstraVectorService;
import io.stargate.sdk.json.CollectionClient;
import io.stargate.sdk.json.domain.JsonDocument;

import com.samistax.application.service.OpenAIClient;
import com.samistax.astra.entity.PulsarChatMessage;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.theme.Theme;
import com.vaadin.flow.theme.lumo.Lumo;

import org.apache.pulsar.client.api.Schema;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.pulsar.core.DefaultSchemaResolver;
import org.springframework.pulsar.core.SchemaResolver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;


/**
 * The entry point of the Spring Boot application.
 *
 * Use the @PWA annotation make the application installable on phones, tablets
 * and some desktop browsers.
 *
 */
@SpringBootApplication
@Theme(value = "chataimoderator", variant = Lumo.DARK)
@Push
public class Application implements AppShellConfigurator {

    @Autowired
    private AstraClient astraClient;

    @Autowired
    private OpenAIClient aiClient;

    @Autowired
    private AstraVectorService astraVector;

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
    @Bean
    public SchemaResolver.SchemaResolverCustomizer<DefaultSchemaResolver> schemaResolverCustomizer() {
        return (schemaResolver) -> schemaResolver.addCustomSchemaMapping(PulsarChatMessage.class, Schema.JSON(PulsarChatMessage.class));
    }

    @Bean
    public String loadPolicyDataCQL() {
        if ( aiClient != null ) {
            String dataCheck = "select * from policy WHERE id = 1;";
            ResultSet results = astraClient.cqlSession().execute(dataCheck);
            System.out.println("Policy table empty?: " + results.iterator().hasNext());
            // In case demo data does not exist, read policy file and vectorize and persist content.
            if (!results.iterator().hasNext()) {
                try {
                    String resourcePath = "META-INF/resources/local-content-policy.txt";
                    // Open the resource file using the class loader
                    InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath);
                    // Check if the resource file was found
                    if (inputStream != null) {
                        // Read the contents of the resource file
                        // You can use BufferedReader or any other class depending on your requirements
                        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                        String line;
                        int id = 1;

                        while ((line = reader.readLine()) != null) {
                            List<Float> embedding = aiClient.getEmbedding("text-embedding-ada-002", line);

                            String query = "INSERT INTO demo.policy(id, policy, policy_vector) VALUES (" + id + ", '" + line.replace("'", "") + "', " + embedding + ")";
                            System.out.println("Query : " + id);
                            astraClient.cqlSession().execute(query);

                            id++;
                        }
                        // Close the reader
                        reader.close();

                    } else {
                        // Resource file not found
                        System.out.println("Resource file not found: " + resourcePath);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return "loaded";
        }

        return "loading failed";
    }
    @Bean
    public String loadPolicyDataJSON() {

        CollectionClient collection = astraVector.getDefaultCollection();

        if ( collection != null) {
            // In case policy file not embedded yet then vectorize and persist the content.
            if ( collection.countDocuments() == 0 ) {
                try {
                    String resourcePath = "META-INF/resources/local-content-policy.txt";
                    // Open the resource file using the class loader
                    InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath);

                    // Check if the resource file was found
                    if (inputStream != null) {
                        // Read the contents of the resource file
                        // You can use BufferedReader or any other class depending on your requirements
                        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

                        String line;
                        int id = 1;

                        while ((line = reader.readLine()) != null) {
                            List<Float> embedding = aiClient.getEmbedding("text-embedding-ada-002", line);
                            collection.insertOne(new JsonDocument()
                                    .id(""+id)
                                    .put("policy", line)
                                    .vector(Floats.toArray(embedding))
                            );
                            id++;
                        }
                        // Close the reader
                        reader.close();

                    } else {
                        // Resource file not found
                        System.out.println("Resource file not found: " + resourcePath);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return "loaded";
        }

        return "loading failed";
    }
}
