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

package com.amazon.opendistroforelasticsearch.alerting.destination.util;

import org.elasticsearch.common.Strings;
import org.elasticsearch.common.ValidationException;

import java.util.regex.Pattern;

public class Util {

    private Util() {}

    public static final Pattern SNS_ARN_REGEX = Pattern.compile("^arn:aws(-[^:]+)?:sns:([a-zA-Z0-9-]+):([0-9]{12}):([a-zA-Z_0-9+=,.@\\-_/]+)$");
    public static final Pattern IAM_ARN_REGEX = Pattern.compile("^arn:aws(-[^:]+)?:iam::([0-9]{12}):([a-zA-Z_0-9+=,.@\\-_/]+)$");

    public static String getRegion(String arn) {
        // sample topic arn arn:aws:sns:us-west-2:075315751589:test-notification
        if (isValidSNSArn(arn)) {
            return arn.split(":")[3];
        }
        throw new IllegalArgumentException("Unable to retrieve region from ARN " + arn);
    }

    public static boolean isValidIAMArn(String arn) {
        return Strings.hasLength(arn) && IAM_ARN_REGEX.matcher(arn).find();
    }

    public static boolean isValidSNSArn(String arn) throws ValidationException {
        return Strings.hasLength(arn) && SNS_ARN_REGEX.matcher(arn).find();
    }
}
