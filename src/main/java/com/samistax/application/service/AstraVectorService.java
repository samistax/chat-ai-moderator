package com.samistax.application.service;

import com.datastax.astra.sdk.AstraClient;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.data.CqlVector;
import com.datastax.oss.driver.api.core.metadata.Metadata;
import com.dtsx.astra.sdk.AstraDB;
import io.stargate.sdk.json.CollectionClient;
import io.stargate.sdk.json.domain.CollectionDefinition;
import io.stargate.sdk.json.domain.SimilarityMetric;
import io.stargate.sdk.json.exception.CollectionNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


@Service
public class AstraVectorService {

    @Value( "${astra.api.application-token}" )
    private String ASTRA_DB_TOKEN;
    @Value( "${astra.api.endpoint}" )
    private String ASTRA_API_ENDPOINT;
    @Value( "${astra.vector.db-name:aidemo}" )
    private String VECTOR_DB_NAME;
    @Value( "${spring.cassandra.keyspace-name}" )
    private String VECTOR_KEYSPACE;
    public static boolean JSON_API_MODE = true;
    private AstraClient astraClient;
    private AstraDB astraDB;

    private static final String DEFAULT_COLLECTION_ID = "policyCollection";

    public AstraVectorService(AstraClient astraClient) {
        this.astraClient = astraClient;
    }

    public void setJsonAPIMode(boolean state) {
        JSON_API_MODE = state;
    }
    public CollectionClient getCollection(String id){
        CollectionClient col = null;
        try {
            col = astraDB.collection(id);
        } catch (CollectionNotFoundException cnfe ) {
            // Collection did not exist, create one and return it to client
            col = astraDB.createCollection(id);
        }
        return col;
    }
    public CollectionClient getDefaultCollection(){
        return getCollection(DEFAULT_COLLECTION_ID);
    }

    @PostConstruct
    private void init() {
        // Initialization
        try {
            astraDB = new AstraDB(ASTRA_DB_TOKEN, ASTRA_API_ENDPOINT);
            CollectionDefinition colDefinition = CollectionDefinition.builder()
                    .name(DEFAULT_COLLECTION_ID)
                    .vector(1536, SimilarityMetric.cosine)
                    .build();

            astraDB.createCollection(colDefinition);
        } catch (Exception ex) {
            System.out.println("Failed to initialise Astra Collection : " + ex);
        }
        if ( astraClient != null ) {
            CqlSession session = astraClient.cqlSession();
            Metadata metadata = session.getMetadata();
            boolean tableExists = metadata.getKeyspace(VECTOR_KEYSPACE)
                    .flatMap(ks -> ks.getTable("document"))
                    .isPresent();

            // Create table if not exist.
            if ( ! tableExists ) {
                String createStatement = "CREATE TABLE IF NOT EXISTS demo.document (\n" +
                        "  filename text,\n" +
                        "  chunkid int,\n" +
                        "  content text,\n" +
                        "  vector VECTOR <FLOAT, 1536>,\n" +
                        "  PRIMARY KEY (filename, chunkid)\n" +
                        ") WITH CLUSTERING ORDER BY (chunkid ASC)";

                // Create SAI index if table creation succeeded
                ResultSet result = session.execute(createStatement);
                if ( result.getExecutionInfo().getErrors().size() == 0 ) {
                    String addIndexStatement = "CREATE CUSTOM INDEX ON document(vector) USING 'org.apache.cassandra.index.sai.StorageAttachedIndex'";
                    session.execute(addIndexStatement);
                }
            }
        }
    }

    // Document functions
    public List<String> savedDocuments() {
        String query = "SELECT filename from document";
        Set<String> files = new HashSet<String>();
        ResultSet results = astraClient.cqlSession().execute(query);
        if ( results != null && results.isFullyFetched() ) {
            results.forEach(row-> files.add(row.getString("filename")));
        }
        return files.stream().toList();
    }

    public ResultSet storeDocumentChunk(String filename, int chunkid, String text, List<Double> embedding){
        if ( astraClient != null ) {
            CqlVector<Float> vector = CqlVector.newInstance(embedding.stream().map(d -> d.floatValue()).collect(Collectors.toList()));
            String insertQuery = "INSERT INTO document (filename, chunkid, content, vector) VALUES ('" + filename + "', " + chunkid + ", '" + text + "', " + embedding + ")";
            return astraClient.cqlSession().execute(insertQuery);
        }
        return null;
    }
    public ResultSet searchDocument(String filename, List<Double> ann, int chunkid, int topK) {
        String query = "SELECT filename, chunkid, content, vector from document";
        if ( filename != null ) {
            query = query.concat(" WHERE filename = '"+filename+"'");
        }
        if ( chunkid > 0  ) {
            query = query.concat(", chunkid = "+chunkid);
        }
        if ( false ) {
            query = query.concat(", similarity_cosine(item_vector, ["+ann.toString()+"]");
        }
        if ( ann != null  ) {
            query = query.concat(" ANN OF ["+ann.toString()+"]");
        }
        if ( topK > 0  ) {
            query = query.concat(" LIMIT = "+topK);
        }
        return astraClient.cqlSession().execute(query);
    }
    public ResultSet fetchAllDocumentChunks(String filename) {

        String query = "SELECT filename, chunkid, content, vector from document";
        if ( filename != null ) {
            query = query.concat(" WHERE filename = '"+filename+"'");
        }
        // Retrieve last messages from database ( fetch memory items for LLM)
        return astraClient.cqlSession().execute(query);
    }
}
