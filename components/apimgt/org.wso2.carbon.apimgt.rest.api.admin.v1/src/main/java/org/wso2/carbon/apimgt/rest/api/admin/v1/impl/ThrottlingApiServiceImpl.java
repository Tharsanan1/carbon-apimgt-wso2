/*
 *  Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.wso2.carbon.apimgt.rest.api.admin.v1.impl;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.base.Splitter;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.cxf.jaxrs.ext.MessageContext;
import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.apache.cxf.message.Message;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.wso2.carbon.apimgt.api.*;
import org.wso2.carbon.apimgt.api.model.BlockConditionsDTO;
import org.wso2.carbon.apimgt.api.model.policy.*;
import org.wso2.carbon.apimgt.impl.APIAdminImpl;
import org.wso2.carbon.apimgt.impl.APIConstants;
import org.wso2.carbon.apimgt.impl.dto.TierPermissionDTO;
import org.wso2.carbon.apimgt.impl.importexport.APIImportExportException;
import org.wso2.carbon.apimgt.impl.importexport.ImportExportConstants;
import org.wso2.carbon.apimgt.impl.importexport.utils.CommonUtil;
import org.wso2.carbon.apimgt.impl.utils.APIUtil;
import org.wso2.carbon.apimgt.rest.api.admin.v1.ThrottlingApiService;
import org.wso2.carbon.apimgt.rest.api.admin.v1.dto.*;
import org.wso2.carbon.apimgt.rest.api.admin.v1.dto.BlockingConditionDTO.ConditionTypeEnum;
import org.wso2.carbon.apimgt.rest.api.admin.v1.utils.RestApiAdminUtils;
import org.wso2.carbon.apimgt.rest.api.admin.v1.utils.mappings.throttling.*;
import org.wso2.carbon.apimgt.rest.api.common.RestApiCommonUtil;
import org.wso2.carbon.apimgt.rest.api.common.RestApiConstants;
import org.wso2.carbon.apimgt.rest.api.util.exception.ConflictException;
import org.wso2.carbon.apimgt.rest.api.util.exception.ForbiddenException;
import org.wso2.carbon.apimgt.rest.api.util.utils.RestApiUtil;
import org.wso2.carbon.utils.multitenancy.MultitenantConstants;

import javax.ws.rs.core.Response;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.wso2.carbon.apimgt.impl.importexport.ImportExportConstants.APIM_VERSION;

/**
 * This is the service implementation class for Admin Portal Throttling related operations
 */
public class ThrottlingApiServiceImpl implements ThrottlingApiService {

    private static final Log log = LogFactory.getLog(ThrottlingApiServiceImpl.class);

    /**
     * Retrieves all Advanced level policies
     *
     * @param accept Accept header value
     * @return All matched Advanced Throttle policies to the given request
     */
    @Override public Response throttlingPoliciesAdvancedGet(String accept, MessageContext messageContext) {
        try {
            APIAdmin apiAdmin = new APIAdminImpl();
            String userName = RestApiCommonUtil.getLoggedInUsername();
            int tenantId = APIUtil.getTenantId(userName);
            Policy[] apiPolicies = apiAdmin.getPolicies(tenantId, PolicyConstants.POLICY_LEVEL_API);
            List<APIPolicy> policies = new ArrayList<>();

            for (Policy policy : apiPolicies) {
                policies.add((APIPolicy) policy);
            }
            AdvancedThrottlePolicyListDTO listDTO = AdvancedThrottlePolicyMappingUtil.fromAPIPolicyArrayToListDTO(
                    policies.toArray(new APIPolicy[policies.size()]));
            return Response.ok().entity(listDTO).build();
        } catch (APIManagementException e) {
            String errorMessage = "Error while retrieving Advanced level policies";
            RestApiUtil.handleInternalServerError(errorMessage, e, log);
        }
        return null;
    }

    /**
     * Add an Advanced Level Throttle Policy
     *
     * @param body        DTO of new policy to be created
     * @param contentType Content-Type header
     * @return Created policy along with the location of it with Location header
     */
    @Override public Response throttlingPoliciesAdvancedPost(String contentType, AdvancedThrottlePolicyDTO body,
            MessageContext messageContext) throws APIManagementException {

        RestApiAdminUtils.validateThrottlePolicyNameProperty(body.getPolicyName());

        try {
            APIProvider apiProvider = RestApiCommonUtil.getLoggedInUserProvider();
            String userName = RestApiCommonUtil.getLoggedInUsername();
            APIPolicy apiPolicy = AdvancedThrottlePolicyMappingUtil.fromAdvancedPolicyDTOToPolicy(body);

            //Check if there's a policy exists before adding the new policy
            try {
                Policy policyIfExists = apiProvider.getAPIPolicy(userName, apiPolicy.getPolicyName());
                if (policyIfExists != null) {
                    RestApiUtil.handleResourceAlreadyExistsError(
                            "Advanced Policy with name " + apiPolicy.getPolicyName() + " already exists", log);
                }
            } catch (PolicyNotFoundException ignore) {
            }
            //Add the policy
            apiProvider.addPolicy(apiPolicy);

            //retrieve the new policy and send back as the response
            APIPolicy newApiPolicy = apiProvider.getAPIPolicy(userName, body.getPolicyName());
            AdvancedThrottlePolicyDTO policyDTO = AdvancedThrottlePolicyMappingUtil.fromAdvancedPolicyToDTO(
                    newApiPolicy);
            return Response.created(new URI(RestApiConstants.RESOURCE_PATH_THROTTLING_POLICIES_ADVANCED + "/"
                    + policyDTO.getPolicyId())).entity(policyDTO).build();
        } catch (APIManagementException e) {
            String errorMessage = "Error while adding an Advanced level policy: " + body.getPolicyName();
            RestApiUtil.handleInternalServerError(errorMessage, e, log);
        } catch (URISyntaxException e) {
            String errorMessage = "Error while retrieving Advanced Throttle policy location : " + body.getPolicyName();
            RestApiUtil.handleInternalServerError(errorMessage, e, log);
        }
        return null;
    }

    /**
     * Get a specific Advanced Level Policy
     *
     * @param policyId uuid of the policy
     * @return Required policy specified by name
     */
    @Override public Response throttlingPoliciesAdvancedPolicyIdGet(String policyId, MessageContext messageContext) {
        try {
            APIProvider apiProvider = RestApiCommonUtil.getLoggedInUserProvider();
            String username = RestApiCommonUtil.getLoggedInUsername();

            //This will give PolicyNotFoundException if there's no policy exists with UUID
            APIPolicy apiPolicy = apiProvider.getAPIPolicyByUUID(policyId);
            if (!RestApiAdminUtils.isPolicyAccessibleToUser(username, apiPolicy)) {
                RestApiUtil.handleAuthorizationFailure(RestApiConstants.RESOURCE_ADVANCED_POLICY, policyId, log);
            }
            AdvancedThrottlePolicyDTO policyDTO = AdvancedThrottlePolicyMappingUtil.fromAdvancedPolicyToDTO(apiPolicy);
            return Response.ok().entity(policyDTO).build();
        } catch (APIManagementException e) {
            if (RestApiUtil.isDueToResourceNotFound(e)) {
                RestApiUtil.handleResourceNotFoundError(RestApiConstants.RESOURCE_ADVANCED_POLICY, policyId, e, log);
            } else {
                String errorMessage = "Error while retrieving Advanced level policy : " + policyId;
                RestApiUtil.handleInternalServerError(errorMessage, e, log);
            }
        }
        return null;
    }

    /**
     * Updates a given Advanced level policy specified by uuid
     *
     * @param policyId    uuid of the policy
     * @param body        DTO of policy to be updated
     * @param contentType Content-Type header
     * @return Updated policy
     */
    @Override public Response throttlingPoliciesAdvancedPolicyIdPut(String policyId, String contentType,
            AdvancedThrottlePolicyDTO body, MessageContext messageContext) {
        try {
            APIProvider apiProvider = RestApiCommonUtil.getLoggedInUserProvider();
            String username = RestApiCommonUtil.getLoggedInUsername();

            //will give PolicyNotFoundException if there's no policy exists with UUID
            APIPolicy existingPolicy = apiProvider.getAPIPolicyByUUID(policyId);
            if (!RestApiAdminUtils.isPolicyAccessibleToUser(username, existingPolicy)) {
                RestApiUtil.handleAuthorizationFailure(RestApiConstants.RESOURCE_ADVANCED_POLICY, policyId, log);
            }

            //overridden parameters
            body.setPolicyId(policyId);
            body.setPolicyName(existingPolicy.getPolicyName());

            //update the policy
            APIPolicy apiPolicy = AdvancedThrottlePolicyMappingUtil.fromAdvancedPolicyDTOToPolicy(body);
            apiProvider.updatePolicy(apiPolicy);

            //retrieve the new policy and send back as the response
            APIPolicy newApiPolicy = apiProvider.getAPIPolicyByUUID(policyId);
            AdvancedThrottlePolicyDTO policyDTO = AdvancedThrottlePolicyMappingUtil.fromAdvancedPolicyToDTO(
                    newApiPolicy);
            return Response.ok().entity(policyDTO).build();
        } catch (APIManagementException e) {
            if (RestApiUtil.isDueToResourceNotFound(e)) {
                RestApiUtil.handleResourceNotFoundError(RestApiConstants.RESOURCE_ADVANCED_POLICY, policyId, e, log);
            } else {
                String errorMessage = "Error while updating Advanced level policy: " + body.getPolicyName();
                RestApiUtil.handleInternalServerError(errorMessage, e, log);
            }
        }
        return null;
    }

    /**
     * Delete an Advanced level policy specified by uuid
     *
     * @param policyId uuid of the policy
     * @return 200 OK response if successfully deleted the policy
     */
    @Override public Response throttlingPoliciesAdvancedPolicyIdDelete(String policyId, MessageContext messageContext)
            throws APIManagementException {
        APIProvider apiProvider = RestApiCommonUtil.getLoggedInUserProvider();
        String username = RestApiCommonUtil.getLoggedInUsername();
        String organization = RestApiUtil.getValidatedOrganization(messageContext);
        //This will give PolicyNotFoundException if there's no policy exists with UUID
        APIPolicy existingPolicy = null;
        try {
            existingPolicy = apiProvider.getAPIPolicyByUUID(policyId);
        } catch (APIManagementException e) {
            RestApiUtil.handleResourceNotFoundError(RestApiConstants.RESOURCE_ADVANCED_POLICY, policyId, e, log);
        }
        if (!RestApiAdminUtils.isPolicyAccessibleToUser(username, existingPolicy)) {
            RestApiUtil.handleAuthorizationFailure(RestApiConstants.RESOURCE_ADVANCED_POLICY, policyId, log);
        }
        if (apiProvider.hasAttachments(username, existingPolicy.getPolicyName(), PolicyConstants.POLICY_LEVEL_API,
                organization)) {
            String message = "Advanced Throttling Policy " + existingPolicy.getPolicyName() + ": " + policyId
                    + " already attached to API/Resource";
            throw new APIManagementException(message,
                    ExceptionCodes.from(ExceptionCodes.ALREADY_ASSIGNED_ADVANCED_POLICY_DELETE_ERROR,
                            existingPolicy.getPolicyName()));
        }
        apiProvider.deletePolicy(username, PolicyConstants.POLICY_LEVEL_API, existingPolicy.getPolicyName());
        return Response.ok().build();
    }

    /**
     * Retrieves all Application Throttle Policies
     *
     * @param accept Accept header value
     * @return Retrieves all Application Throttle Policies
     */
    @Override public Response throttlingPoliciesApplicationGet(String accept, MessageContext messageContext) {
        try {
            APIAdmin apiAdmin = new APIAdminImpl();
            String userName = RestApiCommonUtil.getLoggedInUsername();
            int tenantId = APIUtil.getTenantId(userName);
            Policy[] appPolicies = apiAdmin.getPolicies(tenantId, PolicyConstants.POLICY_LEVEL_APP);
            List<ApplicationPolicy> policies = new ArrayList<>();
            for (Policy policy : appPolicies) {
                policies.add((ApplicationPolicy) policy);
            }
            ApplicationThrottlePolicyListDTO listDTO = ApplicationThrottlePolicyMappingUtil.fromApplicationPolicyArrayToListDTO(
                    policies.toArray(new ApplicationPolicy[policies.size()]));
            return Response.ok().entity(listDTO).build();
        } catch (APIManagementException e) {
            String errorMessage = "Error while retrieving Application level policies";
            RestApiUtil.handleInternalServerError(errorMessage, e, log);
        }
        return null;
    }

    /**
     * Add an Application Level Throttle Policy
     *
     * @param body        DTO of the Application Policy to add
     * @param contentType Content-Type header
     * @return Newly created Application Throttle Policy with the location with the Location header
     */
    @Override public Response throttlingPoliciesApplicationPost(String contentType, ApplicationThrottlePolicyDTO body,
            MessageContext messageContext) throws APIManagementException {

        RestApiAdminUtils.validateThrottlePolicyNameProperty(body.getPolicyName());

        try {
            APIProvider apiProvider = RestApiCommonUtil.getLoggedInUserProvider();
            String username = RestApiCommonUtil.getLoggedInUsername();
            ApplicationPolicy appPolicy = ApplicationThrottlePolicyMappingUtil.fromApplicationThrottlePolicyDTOToModel(
                    body);

            //Check if there's a policy exists before adding the new policy
            try {
                Policy policyIfExists = apiProvider.getApplicationPolicy(username, appPolicy.getPolicyName());
                if (policyIfExists != null) {
                    RestApiUtil.handleResourceAlreadyExistsError(
                            "Application Policy with name " + appPolicy.getPolicyName() + " already exists", log);
                }
            } catch (PolicyNotFoundException ignore) {
            }
            //Add the policy
            apiProvider.addPolicy(appPolicy);

            //retrieve the new policy and send back as the response
            ApplicationPolicy newAppPolicy = apiProvider.getApplicationPolicy(username, body.getPolicyName());
            ApplicationThrottlePolicyDTO policyDTO = ApplicationThrottlePolicyMappingUtil.fromApplicationThrottlePolicyToDTO(
                    newAppPolicy);
            return Response.created(new URI(RestApiConstants.RESOURCE_PATH_THROTTLING_POLICIES_APPLICATION + "/"
                    + policyDTO.getPolicyId())).entity(policyDTO).build();
        } catch (APIManagementException e) {
            String errorMessage = "Error while adding an Application level policy: " + body.getPolicyName();
            RestApiUtil.handleInternalServerError(errorMessage, e, log);
        } catch (URISyntaxException e) {
            String errorMessage =
                    "Error while retrieving Application Throttle policy location : " + body.getPolicyName();
            RestApiUtil.handleInternalServerError(errorMessage, e, log);
        }
        return null;
    }

    /**
     * Get a specific Application Policy by its uuid
     *
     * @param policyId uuid of the policy
     * @return Matched Application Throttle Policy by the given name
     */
    @Override public Response throttlingPoliciesApplicationPolicyIdGet(String policyId, MessageContext messageContext) {
        try {
            APIProvider apiProvider = RestApiCommonUtil.getLoggedInUserProvider();
            String username = RestApiCommonUtil.getLoggedInUsername();

            //This will give PolicyNotFoundException if there's no policy exists with UUID
            ApplicationPolicy appPolicy = apiProvider.getApplicationPolicyByUUID(policyId);
            if (!RestApiAdminUtils.isPolicyAccessibleToUser(username, appPolicy)) {
                RestApiUtil.handleAuthorizationFailure(RestApiConstants.RESOURCE_APP_POLICY, policyId, log);
            }
            ApplicationThrottlePolicyDTO policyDTO = ApplicationThrottlePolicyMappingUtil.fromApplicationThrottlePolicyToDTO(
                    appPolicy);
            return Response.ok().entity(policyDTO).build();
        } catch (APIManagementException e) {
            if (RestApiUtil.isDueToResourceNotFound(e)) {
                RestApiUtil.handleResourceNotFoundError(RestApiConstants.RESOURCE_APP_POLICY, policyId, e, log);
            } else {
                String errorMessage = "Error while retrieving Application level policy: " + policyId;
                RestApiUtil.handleInternalServerError(errorMessage, e, log);
            }
        }
        return null;
    }

    /**
     * Updates a given Application level policy specified by uuid
     *
     * @param policyId    uuid of the policy
     * @param body        DTO of policy to be updated
     * @param contentType Content-Type header
     * @return Updated policy
     */
    @Override public Response throttlingPoliciesApplicationPolicyIdPut(String policyId, String contentType,
            ApplicationThrottlePolicyDTO body, MessageContext messageContext) {
        try {
            APIProvider apiProvider = RestApiCommonUtil.getLoggedInUserProvider();
            String username = RestApiCommonUtil.getLoggedInUsername();

            //will give PolicyNotFoundException if there's no policy exists with UUID
            ApplicationPolicy existingPolicy = apiProvider.getApplicationPolicyByUUID(policyId);
            if (!RestApiAdminUtils.isPolicyAccessibleToUser(username, existingPolicy)) {
                RestApiUtil.handleAuthorizationFailure(RestApiConstants.RESOURCE_APP_POLICY, policyId, log);
            }
            //overridden properties
            body.setPolicyId(policyId);
            body.setPolicyName(existingPolicy.getPolicyName());

            //update the policy
            ApplicationPolicy appPolicy = ApplicationThrottlePolicyMappingUtil.fromApplicationThrottlePolicyDTOToModel(
                    body);
            apiProvider.updatePolicy(appPolicy);

            //retrieve the new policy and send back as the response
            ApplicationPolicy newAppPolicy = apiProvider.getApplicationPolicyByUUID(policyId);
            ApplicationThrottlePolicyDTO policyDTO = ApplicationThrottlePolicyMappingUtil.fromApplicationThrottlePolicyToDTO(
                    newAppPolicy);
            return Response.ok().entity(policyDTO).build();
        } catch (APIManagementException e) {
            if (RestApiUtil.isDueToResourceNotFound(e)) {
                RestApiUtil.handleResourceNotFoundError(RestApiConstants.RESOURCE_APP_POLICY, policyId, e, log);
            } else {
                String errorMessage = "Error while updating Application level policy: " + body.getPolicyName();
                RestApiUtil.handleInternalServerError(errorMessage, e, log);
            }
        }
        return null;
    }

    /**
     * Delete an Application level policy specified by uuid
     *
     * @param policyId uuid of the policy
     * @return 200 OK response if successfully deleted the policy
     */
    @Override public Response throttlingPoliciesApplicationPolicyIdDelete(String policyId,
            MessageContext messageContext) {
        try {
            APIProvider apiProvider = RestApiCommonUtil.getLoggedInUserProvider();
            String username = RestApiCommonUtil.getLoggedInUsername();
            String organization = RestApiUtil.getValidatedOrganization(messageContext);
            //This will give PolicyNotFoundException if there's no policy exists with UUID
            ApplicationPolicy existingPolicy = apiProvider.getApplicationPolicyByUUID(policyId);
            if (!RestApiAdminUtils.isPolicyAccessibleToUser(username, existingPolicy)) {
                RestApiUtil.handleAuthorizationFailure(RestApiConstants.RESOURCE_APP_POLICY, policyId, log);
            }
            if (apiProvider.hasAttachments(organization, existingPolicy.getPolicyName(),
                    PolicyConstants.POLICY_LEVEL_APP, organization)) {
                String message = "Policy " + policyId + " already attached to an application";
                log.error(message);
                throw new APIManagementException(message);
            }
            apiProvider.deletePolicy(username, PolicyConstants.POLICY_LEVEL_APP, existingPolicy.getPolicyName());
            return Response.ok().build();
        } catch (APIManagementException e) {
            if (RestApiUtil.isDueToResourceNotFound(e)) {
                RestApiUtil.handleResourceNotFoundError(RestApiConstants.RESOURCE_APP_POLICY, policyId, e, log);
            } else {
                String errorMessage = "Error while deleting Application level policy : " + policyId;
                RestApiUtil.handleInternalServerError(errorMessage, e, log);
            }
        }
        return null;
    }

    /**
     * Retrieves all Subscription level policies
     *
     * @param accept Accept header value
     * @return All matched Subscription Throttle policies to the given request
     */
    @Override public Response throttlingPoliciesSubscriptionGet(String accept, MessageContext messageContext) {
        try {
            APIAdmin apiAdmin = new APIAdminImpl();
            String userName = RestApiCommonUtil.getLoggedInUsername();
            int tenantId = APIUtil.getTenantId(userName);
            Policy[] subscriptionPolicies = apiAdmin.getPolicies(tenantId, PolicyConstants.POLICY_LEVEL_SUB);
            List<SubscriptionPolicy> policies = new ArrayList<>();
            for (Policy policy : subscriptionPolicies) {
                policies.add((SubscriptionPolicy) policy);
            }
            SubscriptionThrottlePolicyListDTO listDTO = SubscriptionThrottlePolicyMappingUtil.fromSubscriptionPolicyArrayToListDTO(
                    policies.toArray(new SubscriptionPolicy[policies.size()]));
            return Response.ok().entity(listDTO).build();
        } catch (APIManagementException | ParseException e) {
            String errorMessage = "Error while retrieving Subscription level policies";
            RestApiUtil.handleInternalServerError(errorMessage, e, log);
        }
        return null;
    }

    /**
     * Add a Subscription Level Throttle Policy
     *
     * @param body        DTO of new policy to be created
     * @param contentType Content-Type header
     * @return Created policy along with the location of it with Location header
     */
    @Override public Response throttlingPoliciesSubscriptionPost(String contentType, SubscriptionThrottlePolicyDTO body,
            MessageContext messageContext) throws APIManagementException {

        RestApiAdminUtils.validateThrottlePolicyNameProperty(body.getPolicyName());

        try {
            APIProvider apiProvider = RestApiCommonUtil.getLoggedInUserProvider();
            String username = RestApiCommonUtil.getLoggedInUsername();
            SubscriptionPolicy subscriptionPolicy = SubscriptionThrottlePolicyMappingUtil.fromSubscriptionThrottlePolicyDTOToModel(
                    body);
            //Check if there's a policy exists before adding the new policy
            try {
                Policy policyIfExists = apiProvider.getSubscriptionPolicy(username, subscriptionPolicy.getPolicyName());
                if (policyIfExists != null) {
                    RestApiUtil.handleResourceAlreadyExistsError(
                            "Subscription Policy with name " + subscriptionPolicy.getPolicyName() + " already exists",
                            log);
                }
            } catch (PolicyNotFoundException ignore) {
            }

            // validate if permission info exists and halt the execution in case of an error
            validatePolicyPermissions(body);

            //Add the policy
            apiProvider.addPolicy(subscriptionPolicy);

            //update policy permissions
            updatePolicyPermissions(body);

            //retrieve the new policy and send back as the response
            SubscriptionPolicy newSubscriptionPolicy = apiProvider.getSubscriptionPolicy(username,
                    body.getPolicyName());
            SubscriptionThrottlePolicyDTO policyDTO = SubscriptionThrottlePolicyMappingUtil.fromSubscriptionThrottlePolicyToDTO(
                    newSubscriptionPolicy);

            //setting policy permissions
            setPolicyPermissionsToDTO(policyDTO);
            return Response.created(new URI(RestApiConstants.RESOURCE_PATH_THROTTLING_POLICIES_SUBSCRIPTION + "/"
                    + policyDTO.getPolicyId())).entity(policyDTO).build();
        } catch (ParseException e) {
            String errorMessage = "Error while adding a Subscription level policy: " + body.getPolicyName();
            RestApiUtil.handleInternalServerError(errorMessage, e, log);
        } catch (URISyntaxException e) {
            String errorMessage =
                    "Error while retrieving Subscription Throttle policy location : " + body.getPolicyName();
            RestApiUtil.handleInternalServerError(errorMessage, e, log);
        }
        return null;
    }

    /**
     * Returns list of throttling policy details filtered using query parameters
     *
     * @param query          filtering parameters
     * @return Retrieves Throttle Policies List
     */
    @Override public Response throttlingPolicySearch(String query, MessageContext messageContext)
            throws APIManagementException {
        ThrottlePolicyDetailsListDTO resultListDTO = new ThrottlePolicyDetailsListDTO();

        String policyType;
        Map<String, String> filters;
        List<ThrottlePolicyDetailsDTO> result = null;
        if (log.isDebugEnabled()) {
            log.debug("Extracting query info...");
        }
        query=(query==null)?"type:all":query;
        filters = Splitter.on(" ").withKeyValueSeparator(":").split(query);
        try {
            policyType = filters.get("type");
            result = getThrottlingPolicies(policyType);
        } catch (NullPointerException e) {
            String errorMessage = "Error while resolving policy type";
            RestApiUtil.handleInternalServerError(errorMessage, e, log);
        }
        assert result != null;
        resultListDTO.setCount(result.size());
        resultListDTO.setList(result);
        return Response.ok().entity(resultListDTO).build();
    }

    /**
     * Validates the permission element of the subscription throttle policy
     *
     * @param body subscription throttle policy
     * @throws APIManagementException when there are validation errors
     */
    private void validatePolicyPermissions(SubscriptionThrottlePolicyDTO body) throws APIManagementException {
        SubscriptionThrottlePolicyPermissionDTO policyPermissions = body.getPermissions();
        if (policyPermissions != null && policyPermissions.getRoles().size() == 0) {
            throw new APIManagementException(ExceptionCodes.ROLES_CANNOT_BE_EMPTY);
        }
    }

    /**
     * Update APIM with the subscription throttle policy permission
     *
     * @param body subscription throttle policy
     * @throws APIManagementException when there are validation errors or error while updating the permissions
     */
    private void updatePolicyPermissions(SubscriptionThrottlePolicyDTO body) throws APIManagementException {
        APIProvider apiProvider = RestApiCommonUtil.getLoggedInUserProvider();
        SubscriptionThrottlePolicyPermissionDTO policyPermissions = body.getPermissions();
        if (policyPermissions != null) {
            if (policyPermissions.getRoles().size() > 0) {
                String roles = StringUtils.join(policyPermissions.getRoles(), ",");
                String permissionType;
                if (policyPermissions.getPermissionType()
                        == SubscriptionThrottlePolicyPermissionDTO.PermissionTypeEnum.ALLOW) {
                    permissionType = APIConstants.TIER_PERMISSION_ALLOW;
                } else {
                    permissionType = APIConstants.TIER_PERMISSION_DENY;
                }
                apiProvider.updateThrottleTierPermissions(body.getPolicyName(), permissionType, roles);
            } else {
                throw new APIManagementException(ExceptionCodes.ROLES_CANNOT_BE_EMPTY);
            }
        } else {
            apiProvider.deleteTierPermissions(body.getPolicyName());
        }
    }

    /**
     * Set subscription throttle policy permission info into the DTO
     *
     * @param policyDTO subscription throttle policy DTO
     * @throws APIManagementException error while setting/retrieve the permissions to the DTO
     */
    private void setPolicyPermissionsToDTO(SubscriptionThrottlePolicyDTO policyDTO) throws APIManagementException {
        APIProvider apiProvider = RestApiCommonUtil.getLoggedInUserProvider();
        TierPermissionDTO addedPolicyPermission = (TierPermissionDTO) apiProvider.getThrottleTierPermission(
                policyDTO.getPolicyName());
        if (addedPolicyPermission != null) {
            SubscriptionThrottlePolicyPermissionDTO addedPolicyPermissionDTO = SubscriptionThrottlePolicyMappingUtil.fromSubscriptionThrottlePolicyPermissionToDTO(
                    addedPolicyPermission);
            policyDTO.setPermissions(addedPolicyPermissionDTO);
        }
    }

    /**
     * Get a specific Subscription Policy by its uuid
     *
     * @param policyId uuid of the policy
     * @return Matched Subscription Throttle Policy by the given name
     */
    @Override public Response throttlingPoliciesSubscriptionPolicyIdGet(String policyId,
            MessageContext messageContext) {
        try {
            APIProvider apiProvider = RestApiCommonUtil.getLoggedInUserProvider();
            String username = RestApiCommonUtil.getLoggedInUsername();

            //This will give PolicyNotFoundException if there's no policy exists with UUID
            SubscriptionPolicy subscriptionPolicy = apiProvider.getSubscriptionPolicyByUUID(policyId);
            if (!RestApiAdminUtils.isPolicyAccessibleToUser(username, subscriptionPolicy)) {
                RestApiUtil.handleAuthorizationFailure(RestApiConstants.RESOURCE_SUBSCRIPTION_POLICY, policyId, log);
            }
            SubscriptionThrottlePolicyDTO policyDTO = SubscriptionThrottlePolicyMappingUtil.fromSubscriptionThrottlePolicyToDTO(
                    subscriptionPolicy);

            //setting policy permissions
            setPolicyPermissionsToDTO(policyDTO);
            return Response.ok().entity(policyDTO).build();
        } catch (APIManagementException | ParseException e) {
            if (RestApiUtil.isDueToResourceNotFound(e)) {
                RestApiUtil.handleResourceNotFoundError(RestApiConstants.RESOURCE_SUBSCRIPTION_POLICY, policyId, e,
                        log);
            } else {
                String errorMessage = "Error while retrieving Subscription level policy: " + policyId;
                RestApiUtil.handleInternalServerError(errorMessage, e, log);
            }
        }
        return null;
    }

    /**
     * Updates a given Subscription level policy specified by uuid
     *
     * @param policyId    u
     * @param body        DTO of policy to be updated
     * @param contentType Content-Type header
     * @return Updated policy
     */
    @Override public Response throttlingPoliciesSubscriptionPolicyIdPut(String policyId, String contentType,
            SubscriptionThrottlePolicyDTO body, MessageContext messageContext) throws APIManagementException {
        try {
            APIProvider apiProvider = RestApiCommonUtil.getLoggedInUserProvider();
            String username = RestApiCommonUtil.getLoggedInUsername();

            //will give PolicyNotFoundException if there's no policy exists with UUID
            SubscriptionPolicy existingPolicy = apiProvider.getSubscriptionPolicyByUUID(policyId);
            if (!RestApiAdminUtils.isPolicyAccessibleToUser(username, existingPolicy)) {
                RestApiUtil.handleAuthorizationFailure(RestApiConstants.RESOURCE_SUBSCRIPTION_POLICY, policyId, log);
            }

            //overridden properties
            body.setPolicyId(policyId);
            body.setPolicyName(existingPolicy.getPolicyName());

            // validate if permission info exists and halt the execution in case of an error
            validatePolicyPermissions(body);

            //update the policy
            SubscriptionPolicy subscriptionPolicy = SubscriptionThrottlePolicyMappingUtil.fromSubscriptionThrottlePolicyDTOToModel(
                    body);
            apiProvider.updatePolicy(subscriptionPolicy);

            //update policy permissions
            updatePolicyPermissions(body);

            //retrieve the new policy and send back as the response
            SubscriptionPolicy newSubscriptionPolicy = apiProvider.getSubscriptionPolicy(username,
                    body.getPolicyName());
            SubscriptionThrottlePolicyDTO policyDTO = SubscriptionThrottlePolicyMappingUtil.fromSubscriptionThrottlePolicyToDTO(
                    newSubscriptionPolicy);
            //setting policy permissions
            setPolicyPermissionsToDTO(policyDTO);
            return Response.ok().entity(policyDTO).build();
        } catch (APIManagementException | ParseException e) {
            if (RestApiUtil.isDueToResourceNotFound(e)) {
                RestApiUtil.handleResourceNotFoundError(RestApiConstants.RESOURCE_SUBSCRIPTION_POLICY, policyId, e,
                        log);
            } else {
                String errorMessage = "Error while updating Subscription level policy: " + body.getPolicyName();
                throw new APIManagementException(errorMessage, e);
            }
        }
        return null;
    }

    /**
     * Delete a Subscription level policy specified by uuid
     *
     * @param policyId uuid of the policyu
     * @return 200 OK response if successfully deleted the policy
     */
    @Override public Response throttlingPoliciesSubscriptionPolicyIdDelete(String policyId,
            MessageContext messageContext) {
        try {
            APIProvider apiProvider = RestApiCommonUtil.getLoggedInUserProvider();
            String username = RestApiCommonUtil.getLoggedInUsername();
            String organization = RestApiUtil.getValidatedOrganization(messageContext);
            //This will give PolicyNotFoundException if there's no policy exists with UUID
            SubscriptionPolicy existingPolicy = apiProvider.getSubscriptionPolicyByUUID(policyId);
            if (!RestApiAdminUtils.isPolicyAccessibleToUser(username, existingPolicy)) {
                RestApiUtil.handleAuthorizationFailure(RestApiConstants.RESOURCE_SUBSCRIPTION_POLICY, policyId, log);
            }
            if (apiProvider.hasAttachments(username, existingPolicy.getPolicyName(), PolicyConstants.POLICY_LEVEL_SUB,
                    organization)) {
                String message = "Policy " + policyId + " already has subscriptions";
                log.error(message);
                throw new APIManagementException(message);
            }
            apiProvider.deletePolicy(username, PolicyConstants.POLICY_LEVEL_SUB, existingPolicy.getPolicyName());
            return Response.ok().build();
        } catch (APIManagementException e) {
            if (RestApiUtil.isDueToResourceNotFound(e)) {
                RestApiUtil.handleResourceNotFoundError(RestApiConstants.RESOURCE_SUBSCRIPTION_POLICY, policyId, e,
                        log);
            } else {
                String errorMessage = "Error while deleting Subscription level policy : " + policyId;
                RestApiUtil.handleInternalServerError(errorMessage, e, log);
            }
        }
        return null;
    }

    /**
     * Retrieves all Global level policies
     *
     * @param accept Accept header value
     * @return All matched Global Throttle policies to the given request
     */
    @Override public Response throttlingPoliciesCustomGet(String accept, MessageContext messageContext) {
        try {
            APIAdmin apiAdmin = new APIAdminImpl();
            String userName = RestApiCommonUtil.getLoggedInUsername();
            int tenantId = APIUtil.getTenantId(userName);

            //only super tenant is allowed to access global policies/custom rules
            checkTenantDomainForCustomRules();

            Policy[] globalPolicies = apiAdmin.getPolicies(tenantId, PolicyConstants.POLICY_LEVEL_GLOBAL);
            List<GlobalPolicy> policies = new ArrayList<>();
            for (Policy policy : globalPolicies) {
                policies.add((GlobalPolicy) policy);
            }
            CustomRuleListDTO listDTO = GlobalThrottlePolicyMappingUtil.fromGlobalPolicyArrayToListDTO(
                    policies.toArray(new GlobalPolicy[policies.size()]));
            return Response.ok().entity(listDTO).build();
        } catch (APIManagementException e) {
            String errorMessage = "Error while retrieving Global level policies";
            RestApiUtil.handleInternalServerError(errorMessage, e, log);
        }
        return null;
    }

    /**
     * Add an Global Level Throttle Policy
     *
     * @param body        DTO of new policy to be created
     * @param contentType Content-Type header
     * @return Created policy along with the location of it with Location header
     */
    @Override public Response throttlingPoliciesCustomPost(String contentType, CustomRuleDTO body,
            MessageContext messageContext) throws APIManagementException {

        RestApiAdminUtils.validateCustomRuleRequiredProperties(body,
                (String) messageContext.get(Message.HTTP_REQUEST_METHOD));

        try {
            APIProvider apiProvider = RestApiCommonUtil.getLoggedInUserProvider();

            //only super tenant is allowed to access global policies/custom rules
            checkTenantDomainForCustomRules();

            GlobalPolicy globalPolicy = GlobalThrottlePolicyMappingUtil.fromGlobalThrottlePolicyDTOToModel(body);
            //Check if there's a policy exists before adding the new policy
            try {
                Policy policyIfExists = apiProvider.getGlobalPolicy(globalPolicy.getPolicyName());
                if (policyIfExists != null) {
                    RestApiUtil.handleResourceAlreadyExistsError(
                            "Custom rule with name " + globalPolicy.getPolicyName() + " already exists", log);
                }
            } catch (PolicyNotFoundException ignore) {
            }
            //Add the policy
            apiProvider.addPolicy(globalPolicy);

            //retrieve the new policy and send back as the response
            GlobalPolicy newGlobalPolicy = apiProvider.getGlobalPolicy(body.getPolicyName());
            CustomRuleDTO policyDTO = GlobalThrottlePolicyMappingUtil.fromGlobalThrottlePolicyToDTO(newGlobalPolicy);
            return Response.created(
                            new URI(RestApiConstants.RESOURCE_PATH_THROTTLING_POLICIES_GLOBAL + "/" + policyDTO.getPolicyId()))
                    .entity(policyDTO).build();
        } catch (APIManagementException e) {
            String errorMessage = "Error while adding a custom rule: " + body.getPolicyName();
            RestApiUtil.handleInternalServerError(errorMessage, e, log);
        } catch (URISyntaxException e) {
            String errorMessage = "Error while retrieving Global Throttle policy location : " + body.getPolicyName();
            RestApiUtil.handleInternalServerError(errorMessage, e, log);
        }
        return null;
    }

    /**
     * Get a specific custom rule by its name
     *
     * @param ruleId uuid of the policy
     * @return Matched Global Throttle Policy by the given name
     */
    @Override public Response throttlingPoliciesCustomRuleIdGet(String ruleId, MessageContext messageContext) {
        try {
            APIProvider apiProvider = RestApiCommonUtil.getLoggedInUserProvider();
            String username = RestApiCommonUtil.getLoggedInUsername();

            //only super tenant is allowed to access global policies/custom rules
            checkTenantDomainForCustomRules();

            //This will give PolicyNotFoundException if there's no policy exists with UUID
            GlobalPolicy globalPolicy = apiProvider.getGlobalPolicyByUUID(ruleId);
            if (!RestApiAdminUtils.isPolicyAccessibleToUser(username, globalPolicy)) {
                RestApiUtil.handleAuthorizationFailure(RestApiConstants.RESOURCE_CUSTOM_RULE, ruleId, log);
            }
            CustomRuleDTO policyDTO = GlobalThrottlePolicyMappingUtil.fromGlobalThrottlePolicyToDTO(globalPolicy);
            return Response.ok().entity(policyDTO).build();
        } catch (APIManagementException e) {
            if (RestApiUtil.isDueToResourceNotFound(e)) {
                RestApiUtil.handleResourceNotFoundError(RestApiConstants.RESOURCE_CUSTOM_RULE, ruleId, e, log);
            } else {
                String errorMessage = "Error while retrieving Custom Rule: " + ruleId;
                RestApiUtil.handleInternalServerError(errorMessage, e, log);
            }
        }
        return null;
    }

    /**
     * Updates a given Global level policy/custom rule specified by uuid
     *
     * @param ruleId      uuid of the policy
     * @param body        DTO of policy to be updated
     * @param contentType Content-Type header
     * @return Updated policy
     */
    @Override public Response throttlingPoliciesCustomRuleIdPut(String ruleId, String contentType, CustomRuleDTO body,
            MessageContext messageContext) throws APIManagementException {

        RestApiAdminUtils.validateCustomRuleRequiredProperties(body,
                (String) messageContext.get(Message.HTTP_REQUEST_METHOD));

        try {
            APIProvider apiProvider = RestApiCommonUtil.getLoggedInUserProvider();
            String username = RestApiCommonUtil.getLoggedInUsername();

            //only super tenant is allowed to access global policies/custom rules
            checkTenantDomainForCustomRules();

            //will give PolicyNotFoundException if there's no policy exists with UUID
            GlobalPolicy existingPolicy = apiProvider.getGlobalPolicyByUUID(ruleId);
            if (!RestApiAdminUtils.isPolicyAccessibleToUser(username, existingPolicy)) {
                RestApiUtil.handleAuthorizationFailure(RestApiConstants.RESOURCE_CUSTOM_RULE, ruleId, log);
            }

            //overridden properties
            body.setPolicyId(ruleId);
            body.setPolicyName(existingPolicy.getPolicyName());

            //update the policy
            GlobalPolicy globalPolicy = GlobalThrottlePolicyMappingUtil.fromGlobalThrottlePolicyDTOToModel(body);
            apiProvider.updatePolicy(globalPolicy);

            //retrieve the new policy and send back as the response
            GlobalPolicy newGlobalPolicy = apiProvider.getGlobalPolicyByUUID(ruleId);
            CustomRuleDTO policyDTO = GlobalThrottlePolicyMappingUtil.fromGlobalThrottlePolicyToDTO(newGlobalPolicy);
            return Response.ok().entity(policyDTO).build();
        } catch (APIManagementException e) {
            if (RestApiUtil.isDueToResourceNotFound(e)) {
                RestApiUtil.handleResourceNotFoundError(RestApiConstants.RESOURCE_CUSTOM_RULE, ruleId, e, log);
            } else {
                String errorMessage = "Error while updating custom rule: " + body.getPolicyName();
                RestApiUtil.handleInternalServerError(errorMessage, e, log);
            }
        }
        return null;
    }

    /**
     * Delete a Global level policy/custom rule specified by uuid
     *
     * @param ruleId uuid of the policy
     * @return 200 OK response if successfully deleted the policy
     */
    @Override public Response throttlingPoliciesCustomRuleIdDelete(String ruleId, MessageContext messageContext) {
        try {
            APIProvider apiProvider = RestApiCommonUtil.getLoggedInUserProvider();

            //only super tenant is allowed to access global policies/custom rules
            checkTenantDomainForCustomRules();

            String username = RestApiCommonUtil.getLoggedInUsername();

            //This will give PolicyNotFoundException if there's no policy exists with UUID
            GlobalPolicy existingPolicy = apiProvider.getGlobalPolicyByUUID(ruleId);
            if (!RestApiAdminUtils.isPolicyAccessibleToUser(username, existingPolicy)) {
                RestApiUtil.handleAuthorizationFailure(RestApiConstants.RESOURCE_CUSTOM_RULE, ruleId, log);
            }
            apiProvider.deletePolicy(username, PolicyConstants.POLICY_LEVEL_GLOBAL, existingPolicy.getPolicyName());
            return Response.ok().build();
        } catch (APIManagementException e) {
            if (RestApiUtil.isDueToResourceNotFound(e)) {
                RestApiUtil.handleResourceNotFoundError(RestApiConstants.RESOURCE_CUSTOM_RULE, ruleId, e, log);
            } else {
                String errorMessage = "Error while deleting custom rule : " + ruleId;
                RestApiUtil.handleInternalServerError(errorMessage, e, log);
            }
        }
        return null;
    }

    /**
     * Export a Throttling Policy by the policy name with/without specifying the policy type
     * If policy type is not specified first found throttling policy is returned
     *
     * @param policyId       UUID of the throttling policy to be exported(for future use)
     * @param policyName     Name of the policy to be exported
     * @param type           type of the policy to be exported
     * @param format         format of the policy details
     * @return required throttling policy
     */
    @Override public Response exportThrottlingPolicy(String policyId, String policyName, String type, String format,
            MessageContext messageContext) {
        try {
            APIProvider apiProvider = RestApiCommonUtil.getLoggedInUserProvider();
            String userName = RestApiCommonUtil.getLoggedInUsername();
            boolean policyFound = false;
            ExportThrottlePolicyDTO exportPolicy = new ExportThrottlePolicyDTO();
            exportPolicy.type(RestApiConstants.RESOURCE_THROTTLING_POLICY);
            exportPolicy.version(APIM_VERSION);
            type = (type== null) ? StringUtils.EMPTY : type;
            if ((type.equals(PolicyConstants.POLICY_LEVEL_APP) || type.equals(StringUtils.EMPTY))) {
                try {
                    ApplicationPolicy appPolicy = apiProvider.getApplicationPolicy(userName, policyName);
                    if (appPolicy != null) {
                        policyId = appPolicy.getUUID();
                        if (!RestApiAdminUtils.isPolicyAccessibleToUser(userName, appPolicy)) {
                            RestApiUtil.handleAuthorizationFailure(RestApiConstants.RESOURCE_APP_POLICY, policyId, log);
                        }
                        ApplicationThrottlePolicyDTO policyDTO = ApplicationThrottlePolicyMappingUtil.fromApplicationThrottlePolicyToDTO(
                                appPolicy);
                        exportPolicy.data(policyDTO);
                        exportPolicy.subtype(RestApiConstants.RESOURCE_APP_POLICY);
                        policyFound = true;
                    } else if (!type.equals(StringUtils.EMPTY)) {
                        RestApiUtil.handleResourceNotFoundError(
                                RestApiConstants.RESOURCE_APP_POLICY + " not found by the name " + policyName, log);
                    }
                } catch (APIManagementException e) {
                    String errorMessage = "Error while retrieving Application policy: " + policyName;
                    RestApiUtil.handleInternalServerError(errorMessage, e, log);
                }
            }
            if ((type.equals(PolicyConstants.POLICY_LEVEL_SUB) || type.equals(StringUtils.EMPTY)) && !policyFound) {
                try {
                    SubscriptionPolicy subPolicy = apiProvider.getSubscriptionPolicy(userName, policyName);
                    if (subPolicy != null) {
                        policyId = subPolicy.getUUID();
                        if (!RestApiAdminUtils.isPolicyAccessibleToUser(userName, subPolicy)) {
                            RestApiUtil.handleAuthorizationFailure(RestApiConstants.RESOURCE_SUBSCRIPTION_POLICY,
                                    policyId, log);
                        }
                        SubscriptionThrottlePolicyDTO policyDTO = SubscriptionThrottlePolicyMappingUtil.fromSubscriptionThrottlePolicyToDTO(
                                subPolicy);
                        //setting policy permissions
                        setPolicyPermissionsToDTO(policyDTO);
                        exportPolicy.data(policyDTO);
                        exportPolicy.subtype(RestApiConstants.RESOURCE_SUBSCRIPTION_POLICY);
                        policyFound = true;
                    } else if (!type.equals(StringUtils.EMPTY)) {
                        RestApiUtil.handleResourceNotFoundError(
                                RestApiConstants.RESOURCE_SUBSCRIPTION_POLICY + " not found by the name " + policyName,
                                log);
                    }
                } catch (APIManagementException e) {
                    String errorMessage = "Error while retrieving Subscription policy: " + policyName;
                    RestApiUtil.handleInternalServerError(errorMessage, e, log);
                }
            }
            if ((type.equals(PolicyConstants.POLICY_LEVEL_API) || type.equals(StringUtils.EMPTY)) && !policyFound) {
                try {
                    exportPolicy.subtype(RestApiConstants.RESOURCE_ADVANCED_POLICY);
                    APIPolicy apiPolicy = apiProvider.getAPIPolicy(userName, policyName);
                    if (apiPolicy != null) {
                        policyId = apiPolicy.getUUID();
                        if (!RestApiAdminUtils.isPolicyAccessibleToUser(userName, apiPolicy)) {
                            RestApiUtil.handleAuthorizationFailure(RestApiConstants.RESOURCE_ADVANCED_POLICY, policyId,
                                    log);
                        }
                        AdvancedThrottlePolicyDTO policyDTO = AdvancedThrottlePolicyMappingUtil.fromAdvancedPolicyToDTO(
                                apiPolicy);
                        exportPolicy.data(policyDTO);
                        policyFound = true;
                    } else if (!type.equals(StringUtils.EMPTY)) {
                        RestApiUtil.handleResourceNotFoundError(
                                RestApiConstants.RESOURCE_ADVANCED_POLICY + " not found by the name " + policyName,
                                log);
                    }
                } catch (APIManagementException e) {
                    String errorMessage = "Error while retrieving Advanced policy: " + policyName;
                    RestApiUtil.handleInternalServerError(errorMessage, e, log);
                }
            }
            if ((type.equals(PolicyConstants.POLICY_LEVEL_GLOBAL) || type.equals(StringUtils.EMPTY)) && !policyFound) {
                try {
                    //only super tenant is allowed to access global policies/custom rules
                    checkTenantDomainForCustomRules();
                    //This will give PolicyNotFoundException if there's no policy exists with UUID
                    GlobalPolicy globalPolicy = apiProvider.getGlobalPolicy(policyName);
                    if (globalPolicy != null) {
                        CustomRuleDTO policyDTO = GlobalThrottlePolicyMappingUtil.fromGlobalThrottlePolicyToDTO(
                                globalPolicy);
                        exportPolicy.data(policyDTO);
                        exportPolicy.subtype(RestApiConstants.RESOURCE_CUSTOM_RULE);
                        policyFound = true;
                    } else if (!type.equals(StringUtils.EMPTY)) {
                        RestApiUtil.handleResourceNotFoundError(
                                RestApiConstants.RESOURCE_CUSTOM_RULE + " not found by the name " + policyName, log);
                    }
                } catch (APIManagementException e) {
                    String errorMessage = "Error while retrieving Custom policy: " + policyName;
                    RestApiUtil.handleInternalServerError(errorMessage, e, log);
                }
            }
            if (policyFound) {
                return Response.ok().entity(exportPolicy).build();
            } else {
                RestApiUtil.handleResourceNotFoundError("No throttle policy found by the name " + policyName, log);
            }
        } catch (APIManagementException | ParseException e) {
            String errorMessage = "Error while retrieving throttling policy. Name : " + policyName;
            RestApiUtil.handleInternalServerError(errorMessage, e, log);
        }
        return null;
    }

    /**
     * Returns the ExportThrottlePolicyDTO by reading the file from input stream
     *
     * @param uploadedInputStream Input stream from the REST request
     * @param fileDetail Details of the file received via InputStream
     * @return ExportThrottlePolicyDTO of the file to be imported
     */
    public static ExportThrottlePolicyDTO getImportPolicy(InputStream uploadedInputStream, Attachment fileDetail)
            throws APIImportExportException, IOException {
        File importFolder = CommonUtil.createTempDirectory(null);
        String uploadFileName = fileDetail.getContentDisposition().getFilename();
        String fileType = (uploadFileName.contains(ImportExportConstants.YAML_EXTENSION)) ? PolicyConstants.EXPORT_POLICY_TYPE_YAML:PolicyConstants.EXPORT_POLICY_TYPE_JSON;
        String absolutePath = importFolder.getAbsolutePath() + File.separator+uploadFileName;
        File targetFile = new File(absolutePath);
        FileUtils.copyInputStreamToFile(uploadedInputStream, targetFile);
        return preprocessImportedArtifact(absolutePath,fileType);
    }

    /**
     * Preprocesses the either yaml or json file into the ExportThrottlePolicyDTO
     *
     * @param absolutePath temporary location of the throttle policy file
     * @param fileType Type of the file to be imported (.yaml/.json)
     * @return ExportThrottlePolicyDTO from the file
     */
    private static ExportThrottlePolicyDTO preprocessImportedArtifact(String absolutePath, String fileType)
            throws IOException {
        ExportThrottlePolicyDTO importPolicy;
        
        if (Objects.equals(fileType, PolicyConstants.EXPORT_POLICY_TYPE_YAML)){
            ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
            importPolicy = yamlMapper.readValue(new File(absolutePath), ExportThrottlePolicyDTO.class);
        }else{
            ObjectMapper mapper = new ObjectMapper();
            JSONParser parser = new JSONParser();
            Object obj;
            try {
                obj = parser.parse(new FileReader(absolutePath));
            } catch (ParseException | IOException e) {
                throw new RuntimeException(e);
            }
            JSONObject jsonObject = (JSONObject)obj;
            importPolicy = mapper.convertValue(jsonObject,ExportThrottlePolicyDTO.class);
        }
        return importPolicy;
    }

    /**
     * Imports a Throttling policy with the overwriting capability
     *
     * @param fileInputStream Input stream from the REST request
     * @param fileDetail exportThrottlePolicyDTO Exported Throttling policy details
     * @param overwrite User can either update an existing throttling policy with the same name or let the conflict happen
     * @return 200 OK response if successfully imported/updated the policy with a message
     *
     */
    @Override public Response importThrottlingPolicy(InputStream fileInputStream, Attachment fileDetail,
            Boolean overwrite, MessageContext messageContext) throws APIManagementException {
        ExportThrottlePolicyDTO exportThrottlePolicyDTO = null;
        try {
            exportThrottlePolicyDTO =  getImportPolicy(fileInputStream,fileDetail);
        }
        catch (APIImportExportException | IOException e){
            String errorMessage = "Error retrieving Throttling policy";
            RestApiUtil.handleInternalServerError(errorMessage, e, log);
        }
        ObjectMapper mapper = new ObjectMapper();
        APIProvider apiProvider = RestApiCommonUtil.getLoggedInUserProvider();
        String username = RestApiCommonUtil.getLoggedInUsername();
        assert exportThrottlePolicyDTO != null;
        String policyType = exportThrottlePolicyDTO.getSubtype();
        if (policyType.equals(RestApiConstants.RESOURCE_SUBSCRIPTION_POLICY)) {
            SubscriptionThrottlePolicyDTO subscriptionPolicy = mapper.convertValue(exportThrottlePolicyDTO.getData(),
                    SubscriptionThrottlePolicyDTO.class);
            try {
                Response resp = throttlingPoliciesSubscriptionPost(RestApiConstants.APPLICATION_JSON,
                        subscriptionPolicy, messageContext);
                String message =
                        "Successfully imported Subscription Throttling Policy : " + subscriptionPolicy.getPolicyName();
                return Response.fromResponse(resp).entity(message).build();
            } catch (ConflictException e) {
                if (overwrite) {
                    Policy policyIfExists = apiProvider.getSubscriptionPolicy(username,
                            subscriptionPolicy.getPolicyName());
                    String uuid = policyIfExists.getUUID();
                    Response resp = throttlingPoliciesSubscriptionPolicyIdPut(uuid, RestApiConstants.APPLICATION_JSON,
                            subscriptionPolicy, messageContext);
                    String message = "Successfully updated Subscription Throttling Policy : "
                            + subscriptionPolicy.getPolicyName();
                    return Response.fromResponse(resp).entity(message).build();
                } else {
                    throw e;
                }
            }
        }
        if (policyType.equals(RestApiConstants.RESOURCE_APP_POLICY)) {
            ApplicationThrottlePolicyDTO applicationPolicy = mapper.convertValue(exportThrottlePolicyDTO.getData(),
                    ApplicationThrottlePolicyDTO.class);
            try {
                Response resp = throttlingPoliciesApplicationPost(RestApiConstants.APPLICATION_JSON, applicationPolicy,
                        messageContext);
                String message =
                        "Successfully imported Application Throttling Policy : " + applicationPolicy.getPolicyName();
                return Response.fromResponse(resp).entity(message).build();
            } catch (ConflictException e) {
                if (overwrite) {
                    Policy policyIfExists = apiProvider.getApplicationPolicy(username,
                            applicationPolicy.getPolicyName());
                    String uuid = policyIfExists.getUUID();
                    Response resp = throttlingPoliciesApplicationPolicyIdPut(uuid, RestApiConstants.APPLICATION_JSON,
                            applicationPolicy, messageContext);
                    String message =
                            "Successfully updated Application Throttling Policy : " + applicationPolicy.getPolicyName();
                    return Response.fromResponse(resp).entity(message).build();
                } else {
                    throw e;
                }
            }
        }
        if (policyType.equals(RestApiConstants.RESOURCE_ADVANCED_POLICY)) {
            AdvancedThrottlePolicyDTO advancedPolicy = mapper.convertValue(exportThrottlePolicyDTO.getData(),
                    AdvancedThrottlePolicyDTO.class);
            try {
                Response resp = throttlingPoliciesAdvancedPost(RestApiConstants.APPLICATION_JSON, advancedPolicy,
                        messageContext);
                String message = "Successfully imported Advanced Throttling Policy : " + advancedPolicy.getPolicyName();
                return Response.fromResponse(resp).entity(message).build();
            } catch (ConflictException e) {
                if (overwrite) {
                    Policy policyIfExists = apiProvider.getAPIPolicy(username, advancedPolicy.getPolicyName());
                    String uuid = policyIfExists.getUUID();
                    Response resp = throttlingPoliciesAdvancedPolicyIdPut(uuid, RestApiConstants.APPLICATION_JSON,
                            advancedPolicy, messageContext);
                    String message =
                            "Successfully updated Advanced Throttling Policy : " + advancedPolicy.getPolicyName();
                    return Response.fromResponse(resp).entity(message).build();
                } else {
                    throw e;
                }
            }
        }
        if (policyType.equals(RestApiConstants.RESOURCE_CUSTOM_RULE)) {
            CustomRuleDTO customPolicy = mapper.convertValue(exportThrottlePolicyDTO.getData(), CustomRuleDTO.class);
            try {
                Response resp = throttlingPoliciesCustomPost(RestApiConstants.APPLICATION_JSON, customPolicy,
                        messageContext);
                String message = "Successfully imported Custom Throttling Policy : " + customPolicy.getPolicyName();
                return Response.fromResponse(resp).entity(message).build();
            } catch (ConflictException e) {
                if (overwrite) {
                    Policy policyIfExists = apiProvider.getGlobalPolicy(customPolicy.getPolicyName());
                    String uuid = policyIfExists.getUUID();
                    Response resp = throttlingPoliciesCustomRuleIdPut(uuid, RestApiConstants.APPLICATION_JSON,
                            customPolicy, messageContext);
                    String message = "Successfully updated Custom Throttling Policy : " + customPolicy.getPolicyName();
                    return Response.fromResponse(resp).entity(message).build();
                } else {
                    throw e;
                }
            }
        }
        return Response.ok().build();
    }

    /**
     * Returns throttle policies details as a list
     *
     * @param policyLevel type of the throttling policy list to be returned
     * @return Retrieves throttling policy list by type
     */
    private List<ThrottlePolicyDetailsDTO> getThrottlingPolicies(String policyLevel) throws APIManagementException {
        final String ALL_TYPES ="all";
        APIAdmin apiAdmin = new APIAdminImpl();
        String userName = RestApiCommonUtil.getLoggedInUsername();
        int tenantId = APIUtil.getTenantId(userName);
        Policy[] temp;
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        List<ThrottlePolicyDetailsDTO> policies = new ArrayList<>();
        if (policyLevel.equals(ALL_TYPES) || policyLevel.equals(PolicyConstants.POLICY_LEVEL_APP)) {
            temp = apiAdmin.getPolicies(tenantId, PolicyConstants.POLICY_LEVEL_APP);
            for (Policy policy : temp) {
                ThrottlePolicyDetailsDTO policyDetails = mapper.convertValue(policy, ThrottlePolicyDetailsDTO.class);
                policyDetails.setType(PolicyConstants.POLICY_LEVEL_APP);
                policyDetails.setIsDeployed(policy.isDeployed());
                policies.add(policyDetails);
            }
        }
        if (policyLevel.equals(ALL_TYPES) || policyLevel.equals(PolicyConstants.POLICY_LEVEL_SUB)) {
            temp = apiAdmin.getPolicies(tenantId, PolicyConstants.POLICY_LEVEL_SUB);
            for (Policy policy : temp) {
                ThrottlePolicyDetailsDTO policyDetails = mapper.convertValue(policy, ThrottlePolicyDetailsDTO.class);
                policyDetails.setType(PolicyConstants.POLICY_LEVEL_SUB);
                policyDetails.setIsDeployed(policy.isDeployed());
                policies.add(policyDetails);
            }
        }
        if (policyLevel.equals(ALL_TYPES) || policyLevel.equals(PolicyConstants.POLICY_LEVEL_API)) {
            temp = apiAdmin.getPolicies(tenantId, PolicyConstants.POLICY_LEVEL_API);
            for (Policy policy : temp) {
                ThrottlePolicyDetailsDTO policyDetails = mapper.convertValue(policy, ThrottlePolicyDetailsDTO.class);
                policyDetails.setType(PolicyConstants.POLICY_LEVEL_API);
                policyDetails.setIsDeployed(policy.isDeployed());
                policies.add(policyDetails);
            }
        }
        if (policyLevel.equals(ALL_TYPES) || policyLevel.equals(PolicyConstants.POLICY_LEVEL_GLOBAL)) {
            temp = apiAdmin.getPolicies(tenantId, PolicyConstants.POLICY_LEVEL_GLOBAL);
            for (Policy policy : temp) {
                ThrottlePolicyDetailsDTO policyDetails = mapper.convertValue(policy, ThrottlePolicyDetailsDTO.class);
                policyDetails.setType(PolicyConstants.POLICY_LEVEL_GLOBAL);
                policyDetails.setIsDeployed(policy.isDeployed());
                policies.add(policyDetails);
            }
        }
        return policies;
    }

    /**
     * Retrieves all Block Conditions
     *
     * @param accept Accept header value
     * @return All matched block conditions to the given request
     */
    @Override public Response throttlingDenyPoliciesGet(String accept, MessageContext messageContext) {
        try {
            APIProvider apiProvider = RestApiCommonUtil.getLoggedInUserProvider();
            List<BlockConditionsDTO> blockConditions = apiProvider.getBlockConditions();
            BlockingConditionListDTO listDTO = BlockingConditionMappingUtil.fromBlockConditionListToListDTO(
                    blockConditions);
            return Response.ok().entity(listDTO).build();
        } catch (APIManagementException | ParseException e) {
            String errorMessage = "Error while retrieving Block Conditions";
            RestApiUtil.handleInternalServerError(errorMessage, e, log);
        }
        return null;
    }

    /**
     * Add a Block Condition
     *
     * @param body        DTO of new block condition to be created
     * @param contentType Content-Type header
     * @return Created block condition along with the location of it with Location header
     */
    @Override public Response throttlingDenyPoliciesPost(String contentType, BlockingConditionDTO body,
            MessageContext messageContext) {
        try {
            APIProvider apiProvider = RestApiCommonUtil.getLoggedInUserProvider();
            //Add the block condition. It will throw BlockConditionAlreadyExistsException if the condition already
            //  exists in the system
            String uuid = null;
            if (ConditionTypeEnum.API.equals(body.getConditionType()) || ConditionTypeEnum.APPLICATION.equals(
                    body.getConditionType()) || ConditionTypeEnum.USER.equals(body.getConditionType())) {
                uuid = apiProvider.addBlockCondition(body.getConditionType().toString(),
                        (String) body.getConditionValue(), body.isConditionStatus());
            } else if (ConditionTypeEnum.IP.equals(body.getConditionType()) || ConditionTypeEnum.IPRANGE.equals(
                    body.getConditionType())) {
                if (body.getConditionValue() instanceof Map) {
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.putAll((Map) body.getConditionValue());

                    if (ConditionTypeEnum.IP.equals(body.getConditionType())) {
                        RestApiAdminUtils.validateIPAddress(jsonObject.get("fixedIp").toString());
                    }
                    if (ConditionTypeEnum.IPRANGE.equals(body.getConditionType())) {
                        RestApiAdminUtils.validateIPAddress(jsonObject.get("startingIp").toString());
                        RestApiAdminUtils.validateIPAddress(jsonObject.get("endingIp").toString());
                    }
                    uuid = apiProvider.addBlockCondition(body.getConditionType().toString(), jsonObject.toJSONString(),
                            body.isConditionStatus());
                }
            }

            //retrieve the new blocking condition and send back as the response
            BlockConditionsDTO newBlockingCondition = apiProvider.getBlockConditionByUUID(uuid);
            BlockingConditionDTO dto = BlockingConditionMappingUtil.fromBlockingConditionToDTO(newBlockingCondition);
            return Response.created(new URI(RestApiConstants.RESOURCE_PATH_THROTTLING_BLOCK_CONDITIONS + "/" + uuid))
                    .entity(dto).build();
        } catch (APIManagementException e) {
            if (RestApiUtil.isDueToResourceAlreadyExists(e)) {
                RestApiUtil.handleResourceAlreadyExistsError(
                        "A black list item with type: " + body.getConditionType() + ", value: "
                                + body.getConditionValue() + " already exists", e, log);
            } else {
                String errorMessage =
                        "Error while adding Blocking Condition. Condition type: " + body.getConditionType() + ", "
                                + "value: " + body.getConditionValue() + ". " + e.getMessage();
                RestApiUtil.handleInternalServerError(errorMessage, e, log);
            }
        } catch (URISyntaxException | ParseException e) {
            String errorMessage = "Error while retrieving Blocking Condition resource location: Condition type: "
                    + body.getConditionType() + ", " + "value: " + body.getConditionValue() + ". " + e.getMessage();
            RestApiUtil.handleInternalServerError(errorMessage, e, log);
        }
        return null;
    }

    /**
     * Get a specific Block condition by its id
     *
     * @param conditionId Id of the block condition
     * @return Matched block condition for the given Id
     */
    @Override public Response throttlingDenyPolicyConditionIdGet(String conditionId, MessageContext messageContext) {
        try {
            APIProvider apiProvider = RestApiCommonUtil.getLoggedInUserProvider();
            String username = RestApiCommonUtil.getLoggedInUsername();

            //This will give BlockConditionNotFoundException if there's no block condition exists with UUID
            BlockConditionsDTO blockCondition = apiProvider.getBlockConditionByUUID(conditionId);
            if (!RestApiAdminUtils.isBlockConditionAccessibleToUser(username, blockCondition)) {
                RestApiUtil.handleAuthorizationFailure(RestApiConstants.RESOURCE_BLOCK_CONDITION, conditionId, log);
            }
            BlockingConditionDTO dto = BlockingConditionMappingUtil.fromBlockingConditionToDTO(blockCondition);
            return Response.ok().entity(dto).build();
        } catch (APIManagementException e) {
            if (RestApiUtil.isDueToResourceNotFound(e)) {
                RestApiUtil.handleResourceNotFoundError(RestApiConstants.RESOURCE_BLOCK_CONDITION, conditionId, e, log);
            } else {
                String errorMessage = "Error while retrieving Block Condition. Id : " + conditionId;
                RestApiUtil.handleInternalServerError(errorMessage, e, log);
            }
        } catch (ParseException e) {
            String errorMessage = "Error while retrieving Blocking Conditions";
            RestApiUtil.handleInternalServerError(errorMessage, e, log);
        }
        return null;
    }

    /**
     * Delete a block condition specified by the condition Id
     *
     * @param conditionId Id of the block condition
     * @return 200 OK response if successfully deleted the block condition
     */
    @Override public Response throttlingDenyPolicyConditionIdDelete(String conditionId, MessageContext messageContext) {
        try {
            APIProvider apiProvider = RestApiCommonUtil.getLoggedInUserProvider();
            String username = RestApiCommonUtil.getLoggedInUsername();

            //This will give BlockConditionNotFoundException if there's no block condition exists with UUID
            BlockConditionsDTO existingCondition = apiProvider.getBlockConditionByUUID(conditionId);
            if (!RestApiAdminUtils.isBlockConditionAccessibleToUser(username, existingCondition)) {
                RestApiUtil.handleAuthorizationFailure(RestApiConstants.RESOURCE_BLOCK_CONDITION, conditionId, log);
            }
            apiProvider.deleteBlockConditionByUUID(conditionId);
            return Response.ok().build();
        } catch (APIManagementException e) {
            if (RestApiUtil.isDueToResourceNotFound(e)) {
                RestApiUtil.handleResourceNotFoundError(RestApiConstants.RESOURCE_BLOCK_CONDITION, conditionId, e, log);
            } else {
                String errorMessage = "Error while deleting Block Condition. Id : " + conditionId;
                RestApiUtil.handleInternalServerError(errorMessage, e, log);
            }
        }
        return null;
    }

    /**
     * Updates an existing condition status of a blocking condition
     *
     * @param conditionId Id of the block condition
     * @param body        content to update
     * @param contentType Content-Type header
     * @return 200 response if successful
     */
    @Override public Response throttlingDenyPolicyConditionIdPatch(String conditionId, String contentType,
            BlockingConditionStatusDTO body, MessageContext messageContext) {
        try {
            APIProvider apiProvider = RestApiCommonUtil.getLoggedInUserProvider();
            String username = RestApiCommonUtil.getLoggedInUsername();

            //This will give BlockConditionNotFoundException if there's no block condition exists with UUID
            BlockConditionsDTO existingCondition = apiProvider.getBlockConditionByUUID(conditionId);
            if (!RestApiAdminUtils.isBlockConditionAccessibleToUser(username, existingCondition)) {
                RestApiUtil.handleAuthorizationFailure(RestApiConstants.RESOURCE_BLOCK_CONDITION, conditionId, log);
            }

            //update the status
            apiProvider.updateBlockConditionByUUID(conditionId, String.valueOf(body.isConditionStatus()));

            //retrieve the new blocking condition and send back as the response
            BlockConditionsDTO newBlockingCondition = apiProvider.getBlockConditionByUUID(conditionId);
            BlockingConditionDTO dto = BlockingConditionMappingUtil.fromBlockingConditionToDTO(newBlockingCondition);
            return Response.ok().entity(dto).build();
        } catch (APIManagementException | ParseException e) {
            if (RestApiUtil.isDueToResourceNotFound(e)) {
                RestApiUtil.handleResourceNotFoundError(RestApiConstants.RESOURCE_BLOCK_CONDITION, conditionId, e, log);
            } else {
                String errorMessage = "Error while updating Block Condition Status. Id : " + conditionId;
                RestApiUtil.handleInternalServerError(errorMessage, e, log);
            }
        }
        return null;
    }

    /**
     * Checks if the logged in user belongs to super tenant and throws 403 error if not
     *
     * @throws ForbiddenException
     */
    private void checkTenantDomainForCustomRules() throws ForbiddenException {
        String tenantDomain = RestApiCommonUtil.getLoggedInUserTenantDomain();
        if (!tenantDomain.equals(MultitenantConstants.SUPER_TENANT_DOMAIN_NAME)) {
            RestApiUtil.handleAuthorizationFailure("You are not allowed to access this resource",
                    new APIManagementException("Tenant " + tenantDomain + " is not allowed to access custom rules. "
                            + "Only super tenant is allowed"), log);
        }
    }
}
