/*
 *   Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License").
 *   You may not use this file except in compliance with the License.
 *   A copy of the License is located at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   or in the "license" file accompanying this file. This file is distributed
 *   on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *   express or implied. See the License for the specific language governing
 *   permissions and limitations under the License.
 */

package com.amazon.opendistroforelasticsearch.alerting.script

import org.elasticsearch.script.Script
import org.elasticsearch.script.ScriptContext

abstract class TriggerScript(_scriptParams: Map<String, Any>) {

    /**
     * [scriptParams] are the [user-defined parameters][Script.getParams] specified in the script definition.
     * The [scriptParams] are defined when the script is compiled and DON'T change every time the script executes. This field
     * is named **script**Params to avoid confusion with the [PARAMETERS] field. However to remain consistent with every other
     * painless script context we surface it to the painless script as just `params` using a custom getter name.
     */
    val scriptParams: Map<String, Any> = _scriptParams
        @JvmName("getParams") get

    companion object {
        /**
         * [PARAMETERS] contains the names of the formal arguments to the [execute] method which define the
         * script's execution context. These argument names (`_results` etc.)  are available as named parameters
         * in the painless script. These arguments passed to the [execute] method change every time the trigger is executed.
         * In a sane world this would have been named `ARGUMENTS` to avoid confusing the hell out of everyone who has to
         * work with this code.
         */
        @JvmField val PARAMETERS = arrayOf("ctx")

        val CONTEXT = ScriptContext("trigger", Factory::class.java)
    }

    /**
     * Run a trigger script with the given context.
     *
     * @param ctx - the trigger execution context
     */
    abstract fun execute(ctx: TraditionalTriggerExecutionContext): Boolean

    interface Factory {
        fun newInstance(scriptParams: Map<String, Any>): TriggerScript
    }
}
