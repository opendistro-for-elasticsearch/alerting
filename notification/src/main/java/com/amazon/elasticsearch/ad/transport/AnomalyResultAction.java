package com.amazon.elasticsearch.ad.transport;

import org.elasticsearch.action.Action;
import org.elasticsearch.common.io.stream.Writeable;

public class AnomalyResultAction extends Action<AnomalyResultResponse> {
    public static final AnomalyResultAction INSTANCE = new AnomalyResultAction();
    public static final String NAME = "cluster:admin/ad/result";

    private AnomalyResultAction() {super(NAME); }

    @Override
    public AnomalyResultResponse newResponse() {
        throw new UnsupportedOperationException("Usage of Streamable is to be replaced by Writeable");
    }

    @Override
    public Writeable.Reader<AnomalyResultResponse> getResponseReader() {
        // return constructor method reference
        return AnomalyResultResponse::new;
    }
}