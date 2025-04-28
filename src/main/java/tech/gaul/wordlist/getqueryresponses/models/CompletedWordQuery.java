package tech.gaul.wordlist.getqueryresponses.models;

import lombok.Builder;
import lombok.Getter;

@Getter
@Setter
public class CompletedWordQuery extends ActiveWordQuery {
    
    public CompletedWordQuery(ActiveWordQuery activeWordQuery) {
        super(activeWordQuery.getId(), activeWordQuery.getWord(), activeWordQuery.getBatchRequestCustomId(),
                activeWordQuery.getBatchRequestId(), activeWordQuery.getUploadedFileId(), activeWordQuery.getCreatedAt(),
                activeWordQuery.getUpdatedAt(), activeWordQuery.getStatus());
                
        
    }

}
