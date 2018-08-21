package com.amazon.elasticsearch.policy;

import org.elasticsearch.common.settings.Settings;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class Policy {

    private String rawPolicy;
    private String name;
    private boolean enabled;
    private String schedule;
    private String search;
    private List<String> actions;

    private final String NAME = "name";
    private final String ENABLED = "enabled";
    private final String SCHEDULE = "schedule";
    private final String EXPRESSION = "expression";
    private final String SEARCH = "search";
    private final String ACTIONS = "actions";

    public Policy(Settings settings, String policy) {
        rawPolicy = policy;
        parsePolicy();
    }

    private void parsePolicy() {
        JSONObject policyObject = new JSONObject(rawPolicy);
        name = policyObject.getString(NAME);
        enabled = policyObject.getBoolean(ENABLED);
        schedule = policyObject.getJSONObject(SCHEDULE).getString(EXPRESSION);
        search = policyObject.getJSONObject(SEARCH).toString();
        if (policyObject.has(ACTIONS)) {
            actions = new ArrayList<String>();
            JSONArray actions = policyObject.getJSONArray(ACTIONS);
            for (int i = 0; i < actions.length(); i++) {
                this.actions.add(actions.getJSONObject(i).toString());
            }
        }
    }

    @Override
    public String toString() {
        return rawPolicy;
    }
}
