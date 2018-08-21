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
package com.amazon.elasticsearch.resthandler;

import org.apache.logging.log4j.Level;
import org.elasticsearch.test.ESIntegTestCase;
import org.elasticsearch.test.ESTestCase;
import org.junit.Test;

public class PainlessPolicyTests extends ESTestCase {

    public PainlessPolicyTests() {
        logger.log(Level.INFO, "Created testdeletepolicyclass");
    }

    @Test
    public void testFooBar() {
        logger.log(Level.INFO, "Running fun :)");
    }
}
