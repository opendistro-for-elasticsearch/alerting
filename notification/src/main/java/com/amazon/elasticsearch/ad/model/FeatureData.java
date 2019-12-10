package com.amazon.elasticsearch.ad.model;

import com.amazon.elasticsearch.ad.annotation.Generated;
import org.elasticsearch.common.xcontent.ToXContentObject;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;

import java.io.IOException;

import static org.elasticsearch.common.xcontent.XContentParserUtils.ensureExpectedToken;

/**
 * Feature data used by RCF model.
 */
public class FeatureData implements ToXContentObject {

    public static final String FEATURE_ID_FIELD = "feature_id";
    public static final String FEATURE_NAME_FIELD = "feature_name";
    public static final String DATA_FIELD = "data";

    private final String featureId;
    private final String featureName;
    private final Double data;

    public FeatureData(String featureId, String featureName, Double data) {
        this.featureId = featureId;
        this.featureName = featureName;
        this.data = data;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        XContentBuilder xContentBuilder = builder.startObject()
                .field(FEATURE_ID_FIELD, featureId)
                .field(FEATURE_NAME_FIELD, featureName)
                .field(DATA_FIELD, data);
        return xContentBuilder.endObject();
    }

    public static FeatureData parse(XContentParser parser) throws IOException {
        String featureId = null;
        Double data = null;
        String parsedFeatureName = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser::getTokenLocation);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case FEATURE_ID_FIELD:
                    featureId = parser.text();
                    break;
                case FEATURE_NAME_FIELD:
                    parsedFeatureName = parser.text();
                    break;
                case DATA_FIELD:
                    data = parser.doubleValue();
                    break;
                default:
                    break;
            }
        }
        return new FeatureData(featureId, parsedFeatureName, data);
    }

    @Generated
    public String getFeatureId() {
        return featureId;
    }

    @Generated
    public Double getData() {
        return data;
    }

    @Generated
    public String getFeatureName() {
        return featureName;
    }
}
