package com.samistax.application;

import com.datastax.astra.sdk.AstraClient;
import com.datastax.oss.driver.api.core.cql.ResultSet;
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

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
    @Bean
    public SchemaResolver.SchemaResolverCustomizer<DefaultSchemaResolver> schemaResolverCustomizer() {
        return (schemaResolver) -> schemaResolver.addCustomSchemaMapping(PulsarChatMessage.class, Schema.JSON(PulsarChatMessage.class));
    }

    @Bean
    public String loadPolicyData() {

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
                            String embedding = aiClient.getTextEmbedding(line);
                            String query = "insert into demo.policy(id, policy, policy_vector) values (" + id + ", '" + line.replace("'", "") + "', " + embedding + ")";
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
}
