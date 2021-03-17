package com.amazon.opendistroforelasticsearch.alerting.destination.util;

import com.amazon.opendistroforelasticsearch.alerting.destination.factory.DestinationFactory;
import com.amazon.opendistroforelasticsearch.alerting.destination.message.DestinationType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

public class PropertyHelper {

    private static final Logger logger = LogManager.getLogger(PropertyHelper.class);

    private static Properties properties = new Properties();

    static {
        try {
            URL guiceUrl = Class.forName("com.amazon.opendistroforelasticsearch.alerting.destination.util.PropertyHelper")
                    .getClassLoader().getResource("di.properties");
            properties.load(guiceUrl.openStream());
        } catch (Exception e) {
            logger.error("Failed to load properties data");
        }
    }

    public static Map<DestinationType, DestinationFactory> getDestinationFactoryMap() {
        Map<DestinationType, DestinationFactory> destinationFactoryMap = new HashMap<>();
        String [] destinations = ((String) properties.get("destinations")).split(",");
        for (String destination : destinations) {
            try {
                Class destClass = Class.forName((String) properties.get(destination));
                destinationFactoryMap.put(DestinationType.valueOf(destination), (DestinationFactory) destClass.getDeclaredConstructor().newInstance());
            } catch (Exception e) {
                logger.error("Cannot create DestinationFactory, {}", destination, e);
            }
        }
        return destinationFactoryMap;
    }

    public static List<String> getBlacklistedIpRanges() {
        return Arrays.asList(((String) properties.getOrDefault("ipRanges", "")).split(",")).stream()
                .filter(ipString -> !ipString.isBlank())
                .collect(Collectors.toList());
    }
}
