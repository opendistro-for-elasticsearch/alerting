package com.amazon.elasticsearch.notification.factory;

import com.amazon.elasticsearch.notification.message.DestinationType;

import java.util.HashMap;
import java.util.Map;

/*
 * This class helps in fetching the right Channel Factory based on the
 * type of the channel.
 * A channel could be SNS, Email, Webhook etc
 */
public class DestinationFactoryProvider {

    private static Map<DestinationType, DestinationFactory> destinationFactoryMap = new HashMap<>();

    static {
        destinationFactoryMap.put(DestinationType.SNS, new SNSDestinationFactory());
        destinationFactoryMap.put(DestinationType.CHIME, new ChimeDestinationFactory());
        destinationFactoryMap.put(DestinationType.SLACK, new SlackDestinationFactory());
        destinationFactoryMap.put(DestinationType.CUSTOMWEBHOOK, new CustomWebhookDestinationFactory());
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
