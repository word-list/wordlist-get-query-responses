package tech.gaul.wordlist.getqueryresponses;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.core.http.HttpResponse;
import com.openai.models.batches.Batch;
import com.openai.models.batches.BatchRetrieveParams;
import com.openai.models.files.FileContentParams;
import com.openai.models.files.FileObject;
import com.openai.models.files.FileRetrieveParams;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.sqs.SqsClient;
import tech.gaul.wordlist.getqueryresponses.models.ActiveWordQuery;
import tech.gaul.wordlist.getqueryresponses.models.BatchResultModel;
import tech.gaul.wordlist.getqueryresponses.models.CompletedWordQuery;
import tech.gaul.wordlist.getqueryresponses.models.UpdateWordMessage;

public class BatchStatusUpdater {
    TableSchema<ActiveWordQuery> activeWordQuerySchema = TableSchema.fromBean(ActiveWordQuery.class);
    TableSchema<CompletedWordQuery> completedWordQuerySchema = TableSchema.fromBean(CompletedWordQuery.class);

    // This class is responsible for retrieving active batch jobs from DynamoDB,
    // and checking their status with OpenAI.
    // It will also be responsible for updating the status of the batch jobs in
    // DynamoDB.

    public void updateBatchStatus() {

        DynamoDbEnhancedClient dynamoDbClient = DependencyFactory.dynamoDbClient();
        OpenAIClient openAIClient = DependencyFactory.getOpenAIClient();

        HashMap<String, String> attrNames = new HashMap<>();
        attrNames.put("#status", "status");

        HashMap<String, AttributeValue> attrValues = new HashMap<>();
        attrValues.put(":statusValue", AttributeValue.builder().s("Awaiting Response").build());

        // Collect active queries related to batch requests
        Map<String, List<ActiveWordQuery>> activeQueries = dynamoDbClient.table("active-queries", activeWordQuerySchema)
                .scan()
                .items()
                .stream()
                .collect(Collectors.groupingBy(ActiveWordQuery::getBatchRequestId));

        for (String batchRequestId : activeQueries.keySet()) {

            List<ActiveWordQuery> activeQueriesList = activeQueries.get(batchRequestId);

            // Check the status of the batch job with OpenAI
            BatchRetrieveParams batchRetrieveParams = BatchRetrieveParams.builder()
                    .batchId(batchRequestId)
                    .build();

            Batch batch = openAIClient.batches().retrieve(batchRetrieveParams);

            if (batch == null) {
                // TODO: This should time out eventually.
                continue;
            }

            Batch.Status status = batch.status();
            String statusText = status.toString();

            boolean isCompleted = status.equals(Batch.Status.COMPLETED) ||
                    status.equals(Batch.Status.FAILED) ||
                    status.equals(Batch.Status.CANCELLED);

            activeQueriesList.forEach(activeQuery -> {
                activeQuery.setStatus(statusText);
            });

            if (isCompleted) {
                if (status.equals(Batch.Status.COMPLETED)) {
                    updateWordsFromBatchResult(openAIClient, batch, activeQueriesList);
                }

                for (ActiveWordQuery activeQuery : activeQueriesList) {

                    CompletedWordQuery completedQuery = new CompletedWordQuery(activeQuery);
                    completedQuery.setCompletedAt(new Date());

                    dynamoDbClient.table("completed-queries", completedWordQuerySchema)
                            .putItem(completedQuery);

                    dynamoDbClient.table("active-queries", activeWordQuerySchema)
                            .deleteItem(activeQuery);
                }
            } else {
                for (ActiveWordQuery activeQuery : activeQueriesList) {
                    dynamoDbClient.table("active-queries", activeWordQuerySchema)
                            .updateItem(activeQuery);
                }
            }
        }
    }

    private void updateWordsFromBatchResult(OpenAIClient openAIClient, Batch batch,
            List<ActiveWordQuery> activeQueriesList) throws JsonProcessingException {

        if (!batch.outputFileId().isPresent()) {
            throw new RuntimeException("Batch result does not contain output file ID");
        }

        String outputFileId = batch.outputFileId().get();

        // Retrieve the output file from OpenAI
        FileContentParams fileRetrieveParams = FileContentParams.builder()
                .fileId(outputFileId)
                .build();
        HttpResponse contentResponse = openAIClient.files().content(fileRetrieveParams);

        if (contentResponse.statusCode() != 200) {
            throw new RuntimeException("Failed to retrieve output file");
        }

        // Parse the output file to get the results
        BufferedReader reader = new BufferedReader(new InputStreamReader(contentResponse.body()));

        List<BatchResultModel> batchResults = reader.lines().map(line -> {
            try {
                return new ObjectMapper().readValue(line, BatchResultModel.class);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).collect(Collectors.toList());

        try {
            reader.close();
        } catch (IOException e) {
            // TODO: Log warning
        }

        Map<String, ActiveWordQuery> activeQueriesMap = activeQueriesList.stream()
                .collect(Collectors.toMap(ActiveWordQuery::getWord, activeQuery -> activeQuery));

        SqsClient sqsClient = DependencyFactory.sqsClient();

        for (BatchResultModel batchResult : batchResults) {

            // Make sure we have a query for this word
            String word = batchResult.getWord();

            if (!activeQueriesMap.containsKey(word)) {
                // TODO: Log warning
                continue;
            }

            UpdateWordMessage message = UpdateWordMessage.builder()
                    .word(word)
                    .build();

            String messageBody = new ObjectMapper().writeValueAsString(message);

            sqsClient.sendMessage(request -> request
                    .queueUrl(System.getenv("SQS_URL"))
                    .messageBody(messageBody)
                    .build());
                    
            activeQueriesMap.remove(word);
        }

        // TODO: Requeue any missed words
    }

}