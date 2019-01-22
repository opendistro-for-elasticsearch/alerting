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

package com.amazon.opendistro.notification.credentials;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.logging.Loggers;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * This class handles the connections to AWS ES internal service endpoint, to
 * fetch the temporary credentials to assume the role.
 */
class InternalAuthCredentialsApiRequest {

    private static final Logger logger = Loggers.getLogger(InternalAuthCredentialsApiRequest.class);
    private static final InternalAwsCredentials EMPTY_CREDENTIALS = new InternalAwsCredentials();
    private static final String ENDPOINT = "http://localhost:9200/_internal/auth";
    private final CloseableHttpClient httpClient;
    private final String policyType;

    private static ObjectMapper JSON_MAPPER = new ObjectMapper();
    static {
        JSON_MAPPER.setPropertyNamingStrategy(new PropertyNamingStrategy.LowerCaseWithUnderscoresStrategy());
    }

    InternalAuthCredentialsApiRequest(CloseableHttpClient httpClient, String policyType) {
        this.httpClient = httpClient;
        this.policyType = policyType;
    }

    InternalAwsCredentials execute() throws IOException {
        HttpResponse response = getHttpResponse();
        validateResponseStatus(response);
        String responseString = getResponseString(response);
        return httpResponseAsCredentialsObject(responseString);
    }

    private HttpResponse getHttpResponse() throws IOException {
        HttpGet internalAuthGetRequest = new HttpGet(internalAuthUri());

        return httpClient.execute(internalAuthGetRequest);
    }

    private URI internalAuthUri() {
        try {
            return new URIBuilder(ENDPOINT)
                    .addParameter("policy_id", policyType)
                    .build();
        } catch (URISyntaxException exception) {
            logger.error(exception);
            throw new IllegalStateException("Error creating URI");
        }
    }

    private String getResponseString(HttpResponse response) throws IOException {
        HttpEntity entity = response.getEntity();
        if (entity == null)
            return "{}";

        String responseString = EntityUtils.toString(entity);
        logger.debug("Internal Auth response: " + responseString);

        return responseString;
    }

    private String getResponseString(HttpEntity entity) throws IOException {
        if (entity == null)
            return "{}";

        String responseString = EntityUtils.toString(entity);
        logger.debug("Internal Auth response: " + responseString);

        return responseString;
    }

    private void validateResponseStatus(HttpResponse response) throws IOException {
        int statusCode = response.getStatusLine().getStatusCode();

        if (statusCode != 200) {
            throw new IOException("Request to internal auth failed with not OK response");
        }
    }

    private InternalAwsCredentials httpResponseAsCredentialsObject(String responseString) throws IOException {
        try {
            return JSON_MAPPER.readValue(responseString, InternalAwsCredentials.class);
        } catch (JsonParseException e) {
            logger.error("Error in parsing internal aws credentials response", e);
            return EMPTY_CREDENTIALS;
        } catch (JsonMappingException e) {
            logger.error("Error in parsing internal aws credentials response", e);
            return EMPTY_CREDENTIALS;
        } catch (IOException e) {
            logger.error("Error in parsing internal aws credentials response", e);
            return EMPTY_CREDENTIALS;
        }
    }
}
