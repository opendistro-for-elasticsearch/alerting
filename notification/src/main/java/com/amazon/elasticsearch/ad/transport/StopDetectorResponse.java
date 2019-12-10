/*
 * Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.amazon.elasticsearch.ad.transport;

import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.common.io.stream.InputStreamStreamInput;
import org.elasticsearch.common.io.stream.OutputStreamStreamOutput;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.ToXContentObject;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class StopDetectorResponse extends ActionResponse implements ToXContentObject {
    public static final String SUCCESS_JSON_KEY = "success";
    private boolean success;

    public StopDetectorResponse(boolean success) {
        this.success = success;
    }

    public StopDetectorResponse(StreamInput in) throws IOException {
        super(in);
        success = in.readBoolean();
    }

    public boolean success() {
        return success;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeBoolean(success);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(SUCCESS_JSON_KEY, success);
        builder.endObject();
        return builder;
    }

    public static StopDetectorResponse fromActionResponse(final ActionResponse actionResponse) {
        if (actionResponse instanceof StopDetectorResponse) {
            return (StopDetectorResponse) actionResponse;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionResponse.writeTo(osso);
            try (InputStreamStreamInput input = new InputStreamStreamInput(
                    new ByteArrayInputStream(baos.toByteArray()))) {
                return new StopDetectorResponse(input);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("failed to parse ActionResponse into StopDetectorResponse", e);
        }
    }
}
