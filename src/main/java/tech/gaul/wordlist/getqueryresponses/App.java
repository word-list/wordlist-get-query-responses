package tech.gaul.wordlist.getqueryresponses;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

public class App implements RequestHandler<Object, Object> {
    @Override
    public Object handleRequest(Object event, Context context) {
        // This will be a time triggered event.        
        return null;
    }
}