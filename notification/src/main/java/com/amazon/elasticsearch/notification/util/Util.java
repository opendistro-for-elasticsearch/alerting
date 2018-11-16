package com.amazon.elasticsearch.notification.util;

import org.elasticsearch.common.Strings;
import org.elasticsearch.common.ValidationException;

import java.util.regex.Pattern;

public class Util {

    private Util() {}

    private static final Pattern SNS_ARN_REGEX = Pattern.compile("^arn:aws(-[^:]+)?:sns:([a-zA-Z0-9-]+):\\d+:([a-zA-Z0-9-_]+)$");
    private static final Pattern IAM_ARN_REGEX = Pattern.compile("^arn:aws(-[^:]+)?:iam::([0-9]{12}):([a-zA-Z0-9-/_]+)$");

    public static String getRegion(String arn) {
        // sample topic arn arn:aws:sns:us-west-2:075315751589:test-notification
        if (isValidSNSArn(arn)) {
            return arn.split(":")[3];
        }
        throw new IllegalArgumentException("Unable to retriew region from ARN " + arn);
    }

    public static boolean isValidIAMArn(String arn) {
        return Strings.hasLength(arn) && IAM_ARN_REGEX.matcher(arn).find();
    }

    public static boolean isValidSNSArn(String arn) throws ValidationException {
        return Strings.hasLength(arn) && SNS_ARN_REGEX.matcher(arn).find();
    }
}
