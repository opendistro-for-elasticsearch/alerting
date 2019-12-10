package com.amazon.elasticsearch.ad.transport;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.amazon.elasticsearch.ad.model.FeatureData;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.common.io.stream.InputStreamStreamInput;
import org.elasticsearch.common.io.stream.OutputStreamStreamOutput;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.ToXContentObject;
import org.elasticsearch.common.xcontent.XContentBuilder;

public class AnomalyResultResponse extends ActionResponse implements ToXContentObject {
    public static final String ANOMALY_GRADE_JSON_KEY = "anomalyGrade";
    public static final String CONFIDENCE_JSON_KEY = "confidence";
    public static final String FEATURES_JSON_KEY = "features";
    public static final String FEATURE_VALUE_JSON_KEY = "value";

    private double anomalyGrade;
    private double confidence;
    private List<FeatureData> features;

    public AnomalyResultResponse(double anomalyGrade, double confidence, List<FeatureData> features) {
        this.anomalyGrade = anomalyGrade;
        this.confidence = confidence;
        this.features = features;
    }

    public AnomalyResultResponse(StreamInput in) throws IOException {
        super(in);
        anomalyGrade = in.readDouble();
        confidence = in.readDouble();
        int size = in.readVInt();
        features = new ArrayList<FeatureData>();
        for (int i=0; i<size; i++) {
            String featureId = in.readString();
            String featureName = in.readString();
            double featureValue = in.readDouble();
            features.add(new FeatureData(featureId, featureName, featureValue));
        }
    }

    public double getAnomalyGrade() {
        return anomalyGrade;
    }

    public List<FeatureData> getFeatures() {
        return features;
    }

    public double getConfidence() {
        return confidence;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeDouble(anomalyGrade);
        out.writeDouble(confidence);
        out.writeVInt(features.size());
        for(FeatureData feature : features) {
            out.writeString(feature.getFeatureId());
            out.writeString(feature.getFeatureName());
            out.writeDouble(feature.getData());
        }

    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(ANOMALY_GRADE_JSON_KEY, anomalyGrade);
        builder.field(CONFIDENCE_JSON_KEY, confidence);
        builder.startArray(FEATURES_JSON_KEY);
        for (FeatureData feature : features) {
            feature.toXContent(builder, params);
        }
        builder.endArray();
        builder.endObject();
        return builder;
    }

    public static AnomalyResultResponse fromActionResponse(final ActionResponse actionResponse) {
        if (actionResponse instanceof AnomalyResultResponse) {
            return (AnomalyResultResponse) actionResponse;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionResponse.writeTo(osso);
            try (InputStreamStreamInput input = new InputStreamStreamInput(
                    new ByteArrayInputStream(baos.toByteArray()))) {
                return new AnomalyResultResponse(input);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("failed to parse ActionResponse into AnomalyResultResponse", e);
        }
    }
}
