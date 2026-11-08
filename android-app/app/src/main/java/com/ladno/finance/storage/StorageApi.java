package com.ladno.finance.storage;

import org.json.JSONObject;
import com.ladno.finance.core.ImportCandidate;

public interface StorageApi {
    JSONObject state(String month, String currency);
    JSONObject execute(String action, JSONObject payload);
    JSONObject ingestCandidate(JSONObject source, ImportCandidate candidate);
}
