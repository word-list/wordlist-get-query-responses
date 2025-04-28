package tech.gaul.wordlist.getqueryresponses.models;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
@Setter
public class UpdateWordMessage {
    
    private String word;
    private int offensiveness;
    private int commonness;
    private int sentiment;
    private String[] types;
    
}
