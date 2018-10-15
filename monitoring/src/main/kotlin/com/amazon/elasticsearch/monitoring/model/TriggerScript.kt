/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */

package com.amazon.elasticsearch.monitoring.model

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
        // TODO: Add other context parameters after adding a custom whitelist to expose our data model.
        @JvmField val PARAMETERS = arrayOf("_results", "_period_start", "_period_end")

        val CONTEXT = ScriptContext("trigger", Factory::class.java)
    }

    abstract fun execute(_results: List<Any>, _period_start: Long, _period_end: Long) : Boolean

    interface Factory {
        fun newInstance(scriptParams: Map<String, Any>) : TriggerScript
    }
}