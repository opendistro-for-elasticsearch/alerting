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

import org.elasticsearch.action.Action;
import org.elasticsearch.common.io.stream.Writeable;

public class StopDetectorAction extends Action<StopDetectorResponse> {
    public static final StopDetectorAction INSTANCE = new StopDetectorAction();
    public static final String NAME = "cluster:admin/ad/detector/stop";

    private StopDetectorAction() {super(NAME); }

    @Override
    public StopDetectorResponse newResponse() {
        throw new UnsupportedOperationException("Usage of Streamable is to be replaced by Writeable");
    }

    @Override
    public Writeable.Reader<StopDetectorResponse> getResponseReader() {
        // return constructor method reference
        return StopDetectorResponse::new;
    }
}
