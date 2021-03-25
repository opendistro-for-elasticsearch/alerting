package com.amazon.opendistroforelasticsearch.alerting.destination;

import com.amazon.opendistroforelasticsearch.alerting.destination.factory.DestinationFactory;
import com.amazon.opendistroforelasticsearch.alerting.destination.message.DestinationType;
import com.amazon.opendistroforelasticsearch.alerting.destination.util.PropertyHelper;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PropertyHelperTest {

    @Test
    public void testDestinationFactoryList() {
        Map<DestinationType, DestinationFactory> destMap = PropertyHelper.getDestinationFactoryMap();
        assertEquals(5, destMap.size());
        assertTrue(destMap.containsKey(DestinationType.CHIME));
        assertTrue(destMap.containsKey(DestinationType.SLACK));
        assertTrue(destMap.containsKey(DestinationType.SNS));
        assertTrue(destMap.containsKey(DestinationType.CUSTOMWEBHOOK));
        assertTrue(destMap.containsKey(DestinationType.EMAIL));
    }
}
