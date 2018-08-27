/*
 * Copyright 2013-2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except in compliance with
 * the License. A copy of the License is located at
 *
 * http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package com.amazon.elasticsearch.monitor;

import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.painless.PainlessScriptEngine;
import org.elasticsearch.painless.spi.Whitelist;
import org.elasticsearch.script.ExecutableScript;
import org.elasticsearch.script.ScriptContext;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PainlessPolicy {
    private PainlessScriptEngine painlessScriptEngine;
    private Map<String, Object> context;
    private Map<String, Object> vars;
    private ExecutableScript.Factory factory;

    public PainlessPolicy(String scriptName, String scriptSource, Map<String, Object> context, Map<String, Object> vars) {
        this.painlessScriptEngine = new PainlessScriptEngine(Settings.EMPTY, scriptContexts());
        this.factory = painlessScriptEngine.compile(scriptName,
            scriptSource, ExecutableScript.CONTEXT, Collections.emptyMap());
        this.context = context;
        this.vars = vars;
    }

    //Set the script contexts for whitelisting.
    private Map<ScriptContext<?>, List<Whitelist>> scriptContexts() {
        Map<ScriptContext<?>, List<Whitelist>> contexts = new HashMap<>();
        contexts.put(ExecutableScript.CONTEXT, Whitelist.BASE_WHITELISTS);
        return contexts;
    }

    public String getResult() {
        ExecutableScript script = this.factory.newInstance(vars);
        script.setNextVar("ctx", this.context);
        return script.run().toString();
    }
}
