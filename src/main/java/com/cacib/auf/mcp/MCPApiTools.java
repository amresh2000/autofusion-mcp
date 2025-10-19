package com.cacib.auf.mcp;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchema;

/**
 * Utility class for making HTTP API requests and validating responses with JSON schemas.
 * Supports various authentication types and request configurations.
 */
public class MCPApiTools {

    private static final Logger log = LoggerFactory.getLogger(MCPApiTools.class);

    public enum AuthType {
        BEARER, BASIC, API_KEY
    }

    public static class ValidationResult {
        public final boolean isValid;
        public final String message;
        public final int statusCode;
        public final String responseBody;

        public ValidationResult(boolean isValid, String message, int statusCode, String responseBody) {
            this.isValid = isValid;
            this.message = message;
            this.statusCode = statusCode;
            this.responseBody = responseBody;
        }
    }

    /**
     * Send an HTTP request with specified parameters.
     */
    public static Response sendRequest(String method, String url, Map<String, String> headers,
                                     Map<String, String> queryParams, String body,
                                     AuthType authType, String authValue) {

        log.info("Sending {} request to: {}", method, url);

        RequestSpecification request = RestAssured.given();

        // Add headers
        if (headers != null && !headers.isEmpty()) {
            headers.forEach(request::header);
        }

        // Add query parameters
        if (queryParams != null && !queryParams.isEmpty()) {
            queryParams.forEach(request::queryParam);
        }

        // Add authentication
        if (authType != null && authValue != null) {
            switch (authType) {
                case BEARER:
                    request.header("Authorization", "Bearer " + authValue);
                    break;
                case BASIC:
                    String[] credentials = authValue.split(":");
                    if (credentials.length == 2) {
                        request.auth().basic(credentials[0], credentials[1]);
                    }
                    break;
                case API_KEY:
                    request.header("X-API-Key", authValue);
                    break;
            }
        }

        // Add body if present
        if (body != null && !body.trim().isEmpty()) {
            request.contentType(ContentType.JSON).body(body);
        }

        // Execute request based on method
        Response response;
        switch (method.toUpperCase()) {
            case "GET":
                response = request.get(url);
                break;
            case "POST":
                response = request.post(url);
                break;
            case "PUT":
                response = request.put(url);
                break;
            case "DELETE":
                response = request.delete(url);
                break;
            case "PATCH":
                response = request.patch(url);
                break;
            default:
                throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        }

        log.info("Request completed with status: {}", response.getStatusCode());
        return response;
    }

    /**
     * Send an API request and validate the response against a JSON schema.
     */
    public static ValidationResult validateApiResponseWithSchema(String method, String url,
                                                               Map<String, String> headers,
                                                               Map<String, String> queryParams,
                                                               String body, AuthType authType,
                                                               String authValue, String jsonSchemaRaw,
                                                               String jsonSchemaFilePath) {

        try {
            // Send the request
            Response response = sendRequest(method, url, headers, queryParams, body, authType, authValue);

            String responseBody = response.getBody().asString();
            int statusCode = response.getStatusCode();

            // Get schema content
            String schemaContent;
            if (jsonSchemaRaw != null && !jsonSchemaRaw.trim().isEmpty()) {
                schemaContent = jsonSchemaRaw;
                log.info("Using raw JSON schema for validation");
            } else if (jsonSchemaFilePath != null && !jsonSchemaFilePath.trim().isEmpty()) {
                File schemaFile = new File(jsonSchemaFilePath);
                if (!schemaFile.exists()) {
                    return new ValidationResult(false, "Schema file not found: " + jsonSchemaFilePath,
                                              statusCode, responseBody);
                }
                schemaContent = Files.readString(schemaFile.toPath());
                log.info("Using JSON schema from file: {}", jsonSchemaFilePath);
            } else {
                return new ValidationResult(false, "No JSON schema provided (raw or file path)",
                                          statusCode, responseBody);
            }

            // Validate response against schema
            try {
                response.then().body(matchesJsonSchema(schemaContent));
                return new ValidationResult(true, "Response matches schema", statusCode, responseBody);
            } catch (AssertionError e) {
                return new ValidationResult(false, "Schema validation failed: " + e.getMessage(),
                                          statusCode, responseBody);
            }

        } catch (IOException e) {
            return new ValidationResult(false, "Error reading schema file: " + e.getMessage(), 0, "");
        } catch (Exception e) {
            return new ValidationResult(false, "Request failed: " + e.getMessage(), 0, "");
        }
    }
}