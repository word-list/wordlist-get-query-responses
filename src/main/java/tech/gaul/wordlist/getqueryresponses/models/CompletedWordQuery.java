package tech.gaul.wordlist.getqueryresponses.models;

import java.util.Date;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CompletedWordQuery extends ActiveWordQuery {

    public Date completedAt;

    public CompletedWordQuery(ActiveWordQuery activeWordQuery) {
        super(activeWordQuery.getId(), activeWordQuery.getWord(), activeWordQuery.getBatchRequestCustomId(),
                activeWordQuery.getBatchRequestId(), activeWordQuery.getUploadedFileId(),
                activeWordQuery.getCreatedAt(),
                activeWordQuery.getUpdatedAt(), activeWordQuery.getStatus());

        
    }

}
