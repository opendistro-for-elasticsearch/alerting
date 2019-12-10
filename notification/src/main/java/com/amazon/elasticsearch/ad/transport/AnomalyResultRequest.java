package com.amazon.elasticsearch.ad.transport;

import static org.elasticsearch.action.ValidateActions.addValidationError;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;

import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionRequestValidationException;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.io.stream.InputStreamStreamInput;
import org.elasticsearch.common.io.stream.OutputStreamStreamOutput;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.ToXContentObject;
import org.elasticsearch.common.xcontent.XContentBuilder;

import com.amazon.elasticsearch.ad.constant.CommonErrorMessages;
import com.amazon.elasticsearch.ad.constant.CommonMessageAttributes;

public class AnomalyResultRequest extends ActionRequest implements ToXContentObject {
    static final String INVALID_TIMESTAMP_ERR_MSG = "timestamp is invalid";
    static final String START_JSON_KEY = "start";
    static final String END_JSON_KEY = "end";

    private String adID;
    // time range start and end. Unit: epoch milliseconds
    private long start;
    private long end;

    public AnomalyResultRequest(StreamInput in) throws IOException {
        super(in);
        adID = in.readString();
        start = in.readLong();
        end = in.readLong();
    }

    public AnomalyResultRequest(String adID, long start, long end) {
        super();
        this.adID = adID;
        this.start = start;
        this.end = end;
    }

    public long getStart() {
        return start;
    }

    public long getEnd() {
        return end;
    }

    public String getAdID() {
        return adID;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(adID);
        out.writeLong(start);
        out.writeLong(end);
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException validationException = null;
        if (Strings.isEmpty(adID)) {
            validationException = addValidationError(CommonErrorMessages.AD_ID_MISSING_MSG, validationException);
        }
        if (start <= 0 || end <= 0 || start > end) {
            validationException = addValidationError(
                    String.format(Locale.ROOT, "%s: start %d, end %d", INVALID_TIMESTAMP_ERR_MSG, start, end),
                    validationException);
        }
        return validationException;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(CommonMessageAttributes.ID_JSON_KEY, adID);
        builder.field(START_JSON_KEY, start);
        builder.field(END_JSON_KEY, end);
        builder.endObject();
        return builder;
    }

    public static AnomalyResultRequest fromActionRequest(final ActionRequest actionRequest) {
        if (actionRequest instanceof AnomalyResultRequest) {
            return (AnomalyResultRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new AnomalyResultRequest(input);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("failed to parse ActionRequest into AnomalyResultRequest", e);
        }
    }
}
