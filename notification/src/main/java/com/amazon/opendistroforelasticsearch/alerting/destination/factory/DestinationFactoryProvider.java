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

package com.amazon.opendistroforelasticsearch.alerting.destination.factory;

import com.amazon.opendistroforelasticsearch.alerting.destination.message.DestinationType;

import java.util.HashMap;
import java.util.Map;

/*
 * This class helps in fetching the right Channel Factory based on the
 * type of the channel.
 * A channel could be  Email, Webhook etc
 */
public class DestinationFactoryProvider {

    private static Map<DestinationType, DestinationFactory> destinationFactoryMap = new HashMap<>();

    static {
        destinationFactoryMap.put(DestinationType.CHIME, new ChimeDestinationFactory());
        destinationFactoryMap.put(DestinationType.SLACK, new SlackDestinationFactory());
        destinationFactoryMap.put(DestinationType.CUSTOMWEBHOOK, new CustomWebhookDestinationFactory());
        destinationFactoryMap.put(DestinationType.MAIL, new MailDestinationFactory());
    }

    /**
     * Fetches the right channel factory based on the type of the channel
     *
     * @param destinationType [{@link DestinationType}]
     * @return DestinationFactory factory object for above destination type
     */
    public static DestinationFactory getFactory(DestinationType destinationType) {
        if (!destinationFactoryMap.containsKey(destinationType)) {
            throw new IllegalArgumentException("Invalid channel type");
        }
        return destinationFactoryMap.get(destinationType);
    }

    /*
     *  This function is to mock hooks for the unit test
     */
     public static void setFactory(DestinationType type, DestinationFactory factory) {
        destinationFactoryMap.put(type, factory);
    }
}
