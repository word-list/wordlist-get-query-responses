package tech.gaul.wordlist.getqueryresponses;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.openai.client.OpenAIClient;
import com.openai.models.batches.Batch;
import com.openai.models.batches.BatchRetrieveParams;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import tech.gaul.wordlist.getqueryresponses.models.ActiveWordQuery;

public class BatchStatusRetriever {
    TableSchema<ActiveWordQuery> activeWordQuerySchema = TableSchema.fromBean(ActiveWordQuery.class);
    TableSchema<CompletedWordQuery> completedWordQuerySchema = TableSchema.fromBean(CompletedWordQuery.class);

    // This class is responsible for retrieving active batch jobs from DynamoDB,
    // and checking their status with OpenAI.
    // It will also be responsible for updating the status of the batch jobs in
    // DynamoDB.

    public void retrieveBatchStatus() {

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
                    updateWordsFromBatchResult(batch, activeQueriesList);
                }

                for (ActiveWordQuery activeQuery : activeQueriesList) {
                    dynamoDbClient.table("completed-queries", activeWordQuerySchema)
                            .putItem(activeQuery);

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

    private void updateWordsFromBatchResult(Batch batch, List<ActiveWordQuery> activeQueriesList) {
        
    }

}