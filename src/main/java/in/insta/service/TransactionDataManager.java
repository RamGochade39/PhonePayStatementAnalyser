package in.insta.service;

import in.insta.entity.Transaction;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class TransactionDataManager {
    private static final Map<String, List<Transaction>> sessionData = Collections.synchronizedMap(new LinkedHashMap<>());
    
    public void storeTransactions(String sessionId, List<Transaction> transactions) {
        sessionData.put(sessionId, new ArrayList<>(transactions));
    }
    
    public List<Transaction> getTransactions(String sessionId) {
        return sessionData.getOrDefault(sessionId, new ArrayList<>());
    }
    
    public boolean hasTransactions(String sessionId) {
        return sessionData.containsKey(sessionId) && !sessionData.get(sessionId).isEmpty();
    }
    
    public void clearTransactions(String sessionId) {
        sessionData.remove(sessionId);
    }
    
    public int getTransactionCount(String sessionId) {
        return sessionData.getOrDefault(sessionId, new ArrayList<>()).size();
    }
}
