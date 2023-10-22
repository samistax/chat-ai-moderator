package com.samistax.application.views.files;

import com.samistax.application.service.AstraVectorService;
import com.samistax.application.service.OpenAIClient;
import com.vaadin.flow.component.Unit;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;

import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.upload.FileRejectedEvent;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import io.stargate.sdk.json.domain.JsonDocument;
import io.stargate.sdk.json.vector.JsonVectorStore;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class PDFTabsheet extends FileInputTabsheet {

    @Autowired
    private OpenAIClient aiClient;

    @Autowired
    private AstraVectorService astraVector;

    private DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyy-MM-dd HH:mm:ssZ").withZone(ZoneId.from(ZoneOffset.UTC));
    private TextArea pdfContent = new TextArea("PDF content");
    private FlexLayout statistics;

    private long duration_upload = 0;
    private long duration_tokenize = 0;
    private long duration_embedding = 0;
    private long duration_vector_store = 0;


    // JsonDocument to store embedding
    List<String> paragraphs = null;
    List<JsonDocument> embeddings = null;
    JsonVectorStore vstore = null;


    public PDFTabsheet(OpenAIClient aiClient, AstraVectorService astraVector) {
        super();

        this.aiClient = aiClient;
        this.astraVector = astraVector;
        if ( astraVector != null ){
            // Creates vector store with storeName "pdf_files". If one already exists, then old one is used.
            this.vstore = astraVector.getVectorStore("pdf_files");
        }

        statistics = new FlexLayout();
        statistics.setJustifyContentMode(JustifyContentMode.EVENLY);
        statistics.setMinWidth(150, Unit.PIXELS);
        statistics.setWidthFull();


        MemoryBuffer memoryBuffer = new MemoryBuffer();
        Upload upload = createFileImporter(memoryBuffer, "text/csv", "application/pdf");
        upload.getElement().addEventListener("file-remove", event -> {
            // Clear contents
            pdfContent.setValue("");
            statistics.removeAll();

        }).addEventData("event.detail.file.name");
        AtomicLong upload_started = new AtomicLong(0);
        upload.addStartedListener(e -> {
            upload_started.set(System.currentTimeMillis());
            System.out.println("Upload started : " + upload_started);
        });


        upload.addFinishedListener(e -> {

            try {
                if (e.getMIMEType().equals(MIME_TYPE_PDF)) {
                    // read the contents of the buffered memory inputStream
                    InputStream inputStream = memoryBuffer.getInputStream();
                    duration_upload = (System.currentTimeMillis() - upload_started.get());

                    Button prepareBtn = new Button("Prepare document");
                    prepareBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
                    prepareBtn.addClickListener(event -> {
                        // Perform your desired action here
                        long startTime = System.currentTimeMillis();
                        paragraphs = readPdfParagraphs(inputStream);
                        duration_tokenize = System.currentTimeMillis() - startTime;

                        Button embedBtn = new Button("Embed document");
                        embedBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
                        embedBtn.addClickListener(prepEvent -> {

                            long embeddingStartTime = System.currentTimeMillis();
                            //embeddings = getEmbeddings(paragraphs);
                            embeddings = getJsonDocsWithEmbeddingsMultiThread(paragraphs, "chatId1" ,e.getFileName());
                            duration_embedding = System.currentTimeMillis() - embeddingStartTime;

                            Button storeBtn = new Button("Store vectors");
                            storeBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
                            storeBtn.addClickListener( storeEvent -> {
                                long storeStartTime = System.currentTimeMillis();
                                List<String> docs = storeEmbeddingsToVectorStore(embeddings);
                                duration_vector_store = System.currentTimeMillis() - storeStartTime;
                                statistics.add(createStatComponent("Document Stored", duration_vector_store + " ms", null));

                                // TODO: Add stats for firs read after write
                                vstore.findById(docs.get(0));


                            });

                            statistics.add(createStatComponent("Document Embedded", duration_embedding + " ms", storeBtn));
                        });
                        statistics.add(createStatComponent("Document Prepared", duration_tokenize + " ms", embedBtn));
                    });
                    statistics.add(createStatComponent("File uploaded", duration_upload + " ms", prepareBtn));
                }

            } catch (Exception ex) {
                System.out.println("Ex " + ex);
                // Show error notification in UI
                fireEvent(new FileRejectedEvent(upload, "IOException: " + ex.getMessage()));
            }

            // Embed content
            //embedding = getEmbeddings(content);

            // Store embedding to vector store
            //storeEmbeddingsToVectorStore(embedding);

            // TODO: Build prompt to sumarize PDF file.
            //                  "role": "system",
            ///                    "content": "You are knowledgeable about the contents of the embedded PDF titled 'Quantum Physics Basics'."
            //             "role": "user",
            //               "content": "Can you explain the concept of superposition from the PDF?"


           /* Component newStatistics = updateStatistics("Performance", "pdf-stats",
                    new Text("File upload & prepare: " + duration_upload + " ms"),
                    new Text("Embedding paragraphs: " + duration_embedding + " ms"),
                    new Text("Writing vector store: " + duration_vector_store + " ms"));
            //this.replace(statistics, newStatistics);
            //statistics = newStatistics; // Not sure if this is needed.

            */

        });
        pdfContent.setSizeFull();
        add(upload);
        add(statistics);
        add(pdfContent);

    }

    public List<String> readPdfParagraphs(InputStream inputStream) {

        List<String> paragraphs = new ArrayList<>();
        try (PDDocument document = PDDocument.load(inputStream)) {
            PDFTextStripper pdfStripper = new PDFTextStripper();
            pdfStripper.setParagraphStart("/t");
            pdfStripper.setSortByPosition(true);

            String content = "";

            long tokenize_start = System.currentTimeMillis();
            // Chunk content and prepare for embedding
            for (String line : pdfStripper.getText(document).split(pdfStripper.getParagraphStart())) {
                System.out.println(line);
                System.out.println("********************************************************************");
                if ( line.trim().length() > 0 ) {
                    paragraphs.add(line);
                    content = content.concat(line + "\r\n **************** New Paragraph **************** \n");
                }
            }
            duration_tokenize = System.currentTimeMillis() - tokenize_start;
            pdfContent.setValue(content);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return paragraphs;
    }


    public List<JsonDocument> getJsonDocsWithEmbeddingsMultiThread(List<String> paragraphs, String chatId , String fileName) {

        List<JsonDocument> embeddings = new ArrayList<>();

        int numThreads = paragraphs.size(); // For example
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        for (int i = 0; i < numThreads; i++) {
            String chunk = paragraphs.get(i).trim();
            ChatFile chatFile = new ChatFile(i, fileName, chatId, chunk);
            String docId = ""+i;
           // if ( chunk.length() > 0 ) {
                executor.submit(() -> {
                    try {
                        long startTime = System.currentTimeMillis();
                        String embedding = aiClient.getTextEmbedding(chunk);
                        JsonDocument jsond = new JsonDocument()
                                .id(docId) // generated if not set
                                .vector(convertStringToFloatArray(embedding))
                                .put("chatId", chatId)
                                .put("fileName", fileName);
                        embeddings.add(jsond);
                        long duration = System.currentTimeMillis() - startTime;
                        System.out.println("Embedding for docid (" + docId + ") added in " + duration + " ms.");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            //} else { System.out.println("docid (" + docId + ") ise empty"); }
        }
        executor.shutdown();
        while (!executor.isTerminated()) {
        }

        System.out.println("All tasks completed!");
        return embeddings;
    }

    public List<JsonDocument> getJsonDocsWithEmbeddings(List<String> paragraphs, String chatId , String fileName) {

        List<JsonDocument> embeddings = new ArrayList<>();

        // Embed paragraphs
        int i = 0;
        for (String chunk : paragraphs) {
            // TODO: Check if JSON API can insert more than 20 documents at once.
            if (chunk.trim().length() > 0 && embeddings.size() <20) {
                //String embedding = "dummy data";
                String embedding = aiClient.getTextEmbedding(chunk);

                ChatFile chatFile = new ChatFile(++i, fileName, chatId, chunk);
                JsonDocument jsond = new JsonDocument()
                        .id(""+i) // generated if not set
                        .vector(convertStringToFloatArray(embedding))
                        .put("chatId", chatId)
                        .put("fileName", fileName);
                embeddings.add(jsond);
                i = i + 1;
            }
        }
        return embeddings;
    }
    public float[] convertStringArrayToFloatArray(String[] stringArray) {
        float[] floatArray = new float[stringArray.length];
        for (int i = 0; i < stringArray.length; i++) {
            floatArray[i] = Float.parseFloat(stringArray[i]);
        }
        return floatArray;
    }
    public static float[] convertStringToFloatArray(String input) {
        // Remove the opening and closing brackets and split by comma and space
        String[] stringValues = input.substring(1, input.length() - 1).split(", ");
        float[] floatValues = new float[stringValues.length];

        for (int i = 0; i < stringValues.length; i++) {
            floatValues[i] = Float.parseFloat(stringValues[i]);
        }

        return floatValues;
    }

    public List<String> storeEmbeddingsToVectorStore(List<JsonDocument> jsonDocuments) {

        List<String> insertedDocs = new ArrayList<>();
        String id = jsonDocuments.get(0).getId();
        String fileName = (String)jsonDocuments.get(0).getData().get("fileName");

        if ( vstore != null ){
            System.out.println("About to store doc " + id + " from file " + fileName + " to vector store: " + vstore.getName() );
            insertedDocs = vstore.insertAllJsonDocuments(jsonDocuments);
        }
        return insertedDocs;
    }
}
