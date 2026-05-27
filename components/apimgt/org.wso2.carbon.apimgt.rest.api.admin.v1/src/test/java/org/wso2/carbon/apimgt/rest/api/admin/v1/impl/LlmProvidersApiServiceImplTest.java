/*
 * Copyright (c) 2026, WSO2 LLC. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.apimgt.rest.api.admin.v1.impl;

import org.junit.Assert;
import org.junit.Test;
import org.wso2.carbon.apimgt.api.model.LLMModel;
import org.wso2.carbon.apimgt.api.model.LLMProvider;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class LlmProvidersApiServiceImplTest {

    private static final String PROVIDER_ID = "test-provider-id";
    private static final String EXISTING_API_DEF = "{\"openapi\":\"3.0.0\"}";
    private static final String EXISTING_DESCRIPTION = "existing description";
    private static final String EXISTING_CONFIGURATIONS =
            "{\"connectorType\":\"awsBedrock_1.0.0\","
            + "\"metadata\":[{\"attributeName\":\"promptTokenCount\"}],"
            + "\"authenticationConfiguration\":{\"enabled\":true,\"type\":\"aws\","
            + "\"parameters\":{\"awsServiceName\":\"bedrock\"}}}";

    private LLMProvider buildProvider(boolean builtIn, String apiDef, String description,
            String configurations, List<LLMModel> models) {
        LLMProvider provider = new LLMProvider();
        provider.setBuiltInSupport(builtIn);
        provider.setName("testProvider");
        provider.setApiVersion("1.0");
        provider.setApiDefinition(apiDef);
        provider.setDescription(description);
        provider.setConfigurations(configurations);
        provider.setModelList(models);
        return provider;
    }

    private LLMProvider invokeBuildUpdatedLLMProvider(LLMProvider retrievedProvider, String id,
            String description, String configurations, InputStream stream, List<String> modelList)
            throws Exception {
        Method method = LlmProvidersApiServiceImpl.class.getDeclaredMethod(
                "buildUpdatedLLMProvider", LLMProvider.class, String.class, String.class,
                String.class, InputStream.class, List.class);
        method.setAccessible(true);
        try {
            return (LLMProvider) method.invoke(new LlmProvidersApiServiceImpl(),
                    retrievedProvider, id, description, configurations, stream, modelList);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            }
            throw e;
        }
    }

    // ── Built-in provider tests ──────────────────────────────────────────────

    @Test
    public void testBuiltInNullConfigurationsReturnsNull() throws Exception {
        LLMProvider retrieved = buildProvider(true, EXISTING_API_DEF, EXISTING_DESCRIPTION,
                EXISTING_CONFIGURATIONS, Collections.singletonList(new LLMModel("v", Arrays.asList("m1"))));

        LLMProvider result = invokeBuildUpdatedLLMProvider(retrieved, PROVIDER_ID,
                "new desc", null, null, null);

        Assert.assertNull("Expected null when built-in configurations is null", result);
    }

    @Test
    public void testBuiltInUpdatesOnlyAuthConfig() throws Exception {
        String updatedConfigurations =
                "{\"connectorType\":\"shouldBeIgnored\","
                + "\"authenticationConfiguration\":{\"enabled\":true,\"type\":\"apikey\","
                + "\"parameters\":{\"headerEnabled\":true,\"headerName\":\"x-api-key\"}}}";
        LLMProvider retrieved = buildProvider(true, EXISTING_API_DEF, EXISTING_DESCRIPTION,
                EXISTING_CONFIGURATIONS, Collections.singletonList(new LLMModel("v", Arrays.asList("m1"))));

        LLMProvider result = invokeBuildUpdatedLLMProvider(retrieved, PROVIDER_ID,
                "new desc", updatedConfigurations, null, null);

        Assert.assertNotNull(result);
        // connectorType from existing must be preserved
        Assert.assertTrue(result.getConfigurations().contains("awsBedrock_1.0.0"));
        // authConfig must be updated
        Assert.assertTrue(result.getConfigurations().contains("apikey"));
        Assert.assertTrue(result.getConfigurations().contains("x-api-key"));
    }

    @Test
    public void testBuiltInPreservesApiDefinitionEvenIfStreamProvided() throws Exception {
        String updatedConfigurations = "{\"authenticationConfiguration\":{\"type\":\"apikey\"}}";
        InputStream newApiDefStream = new ByteArrayInputStream(
                "{\"openapi\":\"3.1.0\"}".getBytes(StandardCharsets.UTF_8));
        LLMProvider retrieved = buildProvider(true, EXISTING_API_DEF, EXISTING_DESCRIPTION,
                EXISTING_CONFIGURATIONS, null);

        LLMProvider result = invokeBuildUpdatedLLMProvider(retrieved, PROVIDER_ID,
                null, updatedConfigurations, newApiDefStream, null);

        Assert.assertNotNull(result);
        Assert.assertEquals(EXISTING_API_DEF, result.getApiDefinition());
    }

    @Test
    public void testBuiltInPreservesDescriptionEvenIfNewDescriptionProvided() throws Exception {
        String updatedConfigurations = "{\"authenticationConfiguration\":{\"type\":\"apikey\"}}";
        LLMProvider retrieved = buildProvider(true, EXISTING_API_DEF, EXISTING_DESCRIPTION,
                EXISTING_CONFIGURATIONS, null);

        LLMProvider result = invokeBuildUpdatedLLMProvider(retrieved, PROVIDER_ID,
                "ignored new description", updatedConfigurations, null, null);

        Assert.assertNotNull(result);
        Assert.assertEquals(EXISTING_DESCRIPTION, result.getDescription());
    }

    @Test
    public void testBuiltInPreservesModelListEvenIfNewModelListProvided() throws Exception {
        String updatedConfigurations = "{\"authenticationConfiguration\":{\"type\":\"apikey\"}}";
        LLMModel existingModel = new LLMModel("vendor", Arrays.asList("model-a", "model-b"));
        LLMProvider retrieved = buildProvider(true, EXISTING_API_DEF, EXISTING_DESCRIPTION,
                EXISTING_CONFIGURATIONS, Collections.singletonList(existingModel));

        LLMProvider result = invokeBuildUpdatedLLMProvider(retrieved, PROVIDER_ID,
                null, updatedConfigurations, null, Arrays.asList("new-model-x"));

        Assert.assertNotNull(result);
        Assert.assertEquals(retrieved.getModelList(), result.getModelList());
    }

    @Test
    public void testBuiltInNoAuthConfigInIncomingPreservesExistingConfig() throws Exception {
        String updatedConfigurations = "{\"connectorType\":\"shouldBeIgnored\"}";
        LLMProvider retrieved = buildProvider(true, EXISTING_API_DEF, EXISTING_DESCRIPTION,
                EXISTING_CONFIGURATIONS, null);

        LLMProvider result = invokeBuildUpdatedLLMProvider(retrieved, PROVIDER_ID,
                null, updatedConfigurations, null, null);

        Assert.assertNotNull(result);
        Assert.assertEquals(EXISTING_CONFIGURATIONS, result.getConfigurations());
    }

    @Test
    public void testBuiltInMalformedJsonConfigurationsThrowsIOException() throws Exception {
        LLMProvider retrieved = buildProvider(true, EXISTING_API_DEF, EXISTING_DESCRIPTION,
                EXISTING_CONFIGURATIONS, null);
        try {
            invokeBuildUpdatedLLMProvider(retrieved, PROVIDER_ID, null, "NOT_VALID_JSON{{", null, null);
            Assert.fail("Expected IOException for malformed JSON");
        } catch (IOException e) {
            // expected
        }
    }

    @Test
    public void testBuiltInSetsCorrectId() throws Exception {
        String updatedConfigurations = "{\"authenticationConfiguration\":{\"type\":\"apikey\"}}";
        LLMProvider retrieved = buildProvider(true, EXISTING_API_DEF, EXISTING_DESCRIPTION,
                EXISTING_CONFIGURATIONS, null);

        LLMProvider result = invokeBuildUpdatedLLMProvider(retrieved, PROVIDER_ID,
                null, updatedConfigurations, null, null);

        Assert.assertNotNull(result);
        Assert.assertEquals(PROVIDER_ID, result.getId());
        Assert.assertTrue(result.isBuiltInSupport());
    }

    // ── Custom provider tests ────────────────────────────────────────────────

    @Test
    public void testCustomReplacesConfigurations() throws Exception {
        String updatedConfigurations = "{\"connectorType\":\"newConnector\"}";
        LLMProvider retrieved = buildProvider(false, EXISTING_API_DEF, EXISTING_DESCRIPTION,
                "{\"connectorType\":\"oldConnector\"}", null);

        LLMProvider result = invokeBuildUpdatedLLMProvider(retrieved, PROVIDER_ID,
                null, updatedConfigurations, null, null);

        Assert.assertNotNull(result);
        Assert.assertEquals(updatedConfigurations, result.getConfigurations());
    }

    @Test
    public void testCustomNullConfigurationsFallsBackToExisting() throws Exception {
        String existingConfig = "{\"connectorType\":\"oldConnector\"}";
        LLMProvider retrieved = buildProvider(false, EXISTING_API_DEF, EXISTING_DESCRIPTION,
                existingConfig, null);

        LLMProvider result = invokeBuildUpdatedLLMProvider(retrieved, PROVIDER_ID,
                null, null, null, null);

        Assert.assertNotNull(result);
        Assert.assertEquals(existingConfig, result.getConfigurations());
    }

    @Test
    public void testCustomUpdatesApiDefinitionFromStream() throws Exception {
        String newApiDef = "{\"openapi\":\"3.1.0\",\"info\":{\"title\":\"New\"}}";
        InputStream stream = new ByteArrayInputStream(newApiDef.getBytes(StandardCharsets.UTF_8));
        LLMProvider retrieved = buildProvider(false, EXISTING_API_DEF, EXISTING_DESCRIPTION,
                "{\"connectorType\":\"c\"}", null);

        LLMProvider result = invokeBuildUpdatedLLMProvider(retrieved, PROVIDER_ID,
                null, "{\"connectorType\":\"c\"}", stream, null);

        Assert.assertNotNull(result);
        Assert.assertEquals(newApiDef, result.getApiDefinition());
    }

    @Test
    public void testCustomNullApiDefinitionStreamPreservesExisting() throws Exception {
        LLMProvider retrieved = buildProvider(false, EXISTING_API_DEF, EXISTING_DESCRIPTION,
                "{\"connectorType\":\"c\"}", null);

        LLMProvider result = invokeBuildUpdatedLLMProvider(retrieved, PROVIDER_ID,
                null, "{\"connectorType\":\"c\"}", null, null);

        Assert.assertNotNull(result);
        Assert.assertEquals(EXISTING_API_DEF, result.getApiDefinition());
    }

    @Test
    public void testCustomUpdatesDescription() throws Exception {
        String newDescription = "updated description";
        LLMProvider retrieved = buildProvider(false, EXISTING_API_DEF, EXISTING_DESCRIPTION,
                "{\"connectorType\":\"c\"}", null);

        LLMProvider result = invokeBuildUpdatedLLMProvider(retrieved, PROVIDER_ID,
                newDescription, "{\"connectorType\":\"c\"}", null, null);

        Assert.assertNotNull(result);
        Assert.assertEquals(newDescription, result.getDescription());
    }

    @Test
    public void testCustomNullDescriptionPreservesExisting() throws Exception {
        LLMProvider retrieved = buildProvider(false, EXISTING_API_DEF, EXISTING_DESCRIPTION,
                "{\"connectorType\":\"c\"}", null);

        LLMProvider result = invokeBuildUpdatedLLMProvider(retrieved, PROVIDER_ID,
                null, "{\"connectorType\":\"c\"}", null, null);

        Assert.assertNotNull(result);
        Assert.assertEquals(EXISTING_DESCRIPTION, result.getDescription());
    }

    @Test
    public void testCustomUpdatesModelList() throws Exception {
        List<String> newModels = Arrays.asList("gpt-4", "gpt-3.5");
        LLMModel existingModel = new LLMModel("oldVendor", Arrays.asList("old-model"));
        LLMProvider retrieved = buildProvider(false, EXISTING_API_DEF, EXISTING_DESCRIPTION,
                "{\"connectorType\":\"c\"}", Collections.singletonList(existingModel));
        retrieved.setName("testProvider");

        LLMProvider result = invokeBuildUpdatedLLMProvider(retrieved, PROVIDER_ID,
                null, "{\"connectorType\":\"c\"}", null, newModels);

        Assert.assertNotNull(result);
        Assert.assertEquals(1, result.getModelList().size());
        Assert.assertEquals(newModels, result.getModelList().get(0).getValues());
    }

    @Test
    public void testCustomNullModelListPreservesExisting() throws Exception {
        LLMModel existingModel = new LLMModel("vendor", Arrays.asList("model-a"));
        LLMProvider retrieved = buildProvider(false, EXISTING_API_DEF, EXISTING_DESCRIPTION,
                "{\"connectorType\":\"c\"}", Collections.singletonList(existingModel));

        LLMProvider result = invokeBuildUpdatedLLMProvider(retrieved, PROVIDER_ID,
                null, "{\"connectorType\":\"c\"}", null, null);

        Assert.assertNotNull(result);
        Assert.assertEquals(retrieved.getModelList(), result.getModelList());
    }

    @Test
    public void testCustomSetsCorrectId() throws Exception {
        LLMProvider retrieved = buildProvider(false, EXISTING_API_DEF, EXISTING_DESCRIPTION,
                "{\"connectorType\":\"c\"}", null);

        LLMProvider result = invokeBuildUpdatedLLMProvider(retrieved, PROVIDER_ID,
                null, "{\"connectorType\":\"c\"}", null, null);

        Assert.assertNotNull(result);
        Assert.assertEquals(PROVIDER_ID, result.getId());
        Assert.assertFalse(result.isBuiltInSupport());
    }
}
