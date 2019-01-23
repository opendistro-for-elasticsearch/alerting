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

package com.amazon.opendistro.notification.factory;

import com.amazon.opendistro.notification.message.BaseMessage;
import com.amazon.opendistro.notification.message.DestinationType;
import com.amazon.opendistro.notification.response.BaseResponse;

/**
 * Interface which enables to plug in multiple notification Channel Factories.
 *
 * @param <T> message object of type [{@link DestinationType}]
 * @param <Y> client to publish above message
 */
public interface DestinationFactory<T extends BaseMessage, Y> {
    public BaseResponse publish(T message);

    public Y getClient(T message);
}
