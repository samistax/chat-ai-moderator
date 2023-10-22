package com.samistax.application.ai;

import com.datastax.astra.sdk.AstraClient;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.theokanning.openai.completion.chat.ChatMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

@Component
public class ChatHistory {

    public String getChatHistory(AstraClient astraClient, String userId, int memorySize) {
        String jsonResponse = "";
System.out.println("Retrieving messages for user: "+ userId);
        String query = "SELECT text, time, userid, username from chat_message";
        if ( userId != null ) {
            query = query.concat(" WHERE userid = '"+userId+"'");
        }
        if ( memorySize > 0  ) {
            query = query.concat(" LIMIT "+memorySize);
        }
        // Retrieve last messages from database ( fetch memory items for LLM)
        ResultSet results = astraClient.cqlSession().execute(query);
        if (results != null && results.getAvailableWithoutFetching() > 0) {
            for ( Row r: results.all()) {
                String username = r.getString("username");
                String usermsg = r.getString("text");
                jsonResponse = jsonResponse.concat("{\"role\": \"Assistant\", \"content\": \""+username+" said: "+usermsg+"\"},");
                System.out.println(username+" -> "+usermsg);
            }
            jsonResponse = jsonResponse.replaceAll(",$","") + "]}";
            // Process the results
            System.out.println("Result : " + jsonResponse);

            return jsonResponse;
        } else {
            System.out.println("No results found.");
            return "";
        }
    }

}
