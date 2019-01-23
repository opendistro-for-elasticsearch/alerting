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

package com.amazon.opendistro.notification;

import com.amazon.opendistro.notification.factory.DestinationFactory;
import com.amazon.opendistro.notification.factory.DestinationFactoryProvider;
import com.amazon.opendistro.notification.message.BaseMessage;
import com.amazon.opendistro.notification.response.BaseResponse;

import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * This is a client facing Notification class to publish the messages
 * to the Notification channel like SNS, Email, Webhooks etc
 */
public class Notification {
    private DestinationFactoryProvider factoryProvider;

    private BaseResponse run(BaseMessage notificationMessage) throws Exception {
        DestinationFactory destinationFactory = DestinationFactoryProvider.getFactory(notificationMessage.getChannelType());
        return destinationFactory.publish(notificationMessage);
    }

    /**
     * Publishes the notification message to the corresponding notification
     * channel
     *
     * @param notificationMessage
     * @return BaseResponse
     */
    public static BaseResponse publish(BaseMessage notificationMessage) {
            return AccessController.doPrivileged((PrivilegedAction<BaseResponse>) () -> {
                DestinationFactory destinationFactory = DestinationFactoryProvider.getFactory(notificationMessage.getChannelType());
                return destinationFactory.publish(notificationMessage);
            });
    }
}

