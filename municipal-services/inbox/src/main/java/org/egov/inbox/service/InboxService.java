package org.egov.inbox.service;

import static org.egov.inbox.util.BpaConstants.BPA;
import static org.egov.inbox.util.BpaConstants.BPAREG;
import static org.egov.inbox.util.BpaConstants.BPA_APPLICATION_NUMBER_PARAM;
import static org.egov.inbox.util.BpaConstants.LOCALITY_PARAM;
import static org.egov.inbox.util.BpaConstants.MOBILE_NUMBER_PARAM;
import static org.egov.inbox.util.BpaConstants.OFFSET_PARAM;
import static org.egov.inbox.util.BpaConstants.STATUS_PARAM;
import static org.egov.inbox.util.FSMConstants.APPLICATIONSTATUS;
import static org.egov.inbox.util.FSMConstants.CITIZEN_FEEDBACK_PENDING_STATE;
import static org.egov.inbox.util.FSMConstants.COMPLETED_STATE;
import static org.egov.inbox.util.FSMConstants.COUNT;
import static org.egov.inbox.util.FSMConstants.DISPOSED_STATE;
import static org.egov.inbox.util.FSMConstants.DSO_INPROGRESS_STATE;
import static org.egov.inbox.util.FSMConstants.FSM_MODULE;
import static org.egov.inbox.util.FSMConstants.FSM_VEHICLE_TRIP_MODULE;
import static org.egov.inbox.util.FSMConstants.STATUSID;
import static org.egov.inbox.util.FSMConstants.VEHICLE_LOG;
import static org.egov.inbox.util.FSMConstants.WAITING_FOR_DISPOSAL_STATE;
import static org.egov.inbox.util.NocConstants.NOC;
import static org.egov.inbox.util.NocConstants.NOC_APPLICATION_NUMBER_PARAM;
import static org.egov.inbox.util.PTConstants.ACKNOWLEDGEMENT_IDS_PARAM;
import static org.egov.inbox.util.PTConstants.PT;
import static org.egov.inbox.util.TLConstants.APPLICATION_NUMBER_PARAM;
import static org.egov.inbox.util.TLConstants.BUSINESS_SERVICE_PARAM;
import static org.egov.inbox.util.TLConstants.REQUESTINFO_PARAM;
import static org.egov.inbox.util.TLConstants.SEARCH_CRITERIA_PARAM;
import static org.egov.inbox.util.TLConstants.TENANT_ID_PARAM;
import static org.egov.inbox.util.TLConstants.TL;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.commons.collections4.MapUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.Role;
import org.egov.inbox.config.InboxConfiguration;
import org.egov.inbox.model.vehicle.VehicleSearchCriteria;
import org.egov.inbox.model.vehicle.VehicleTripDetail;
import org.egov.inbox.model.vehicle.VehicleTripDetailResponse;
import org.egov.inbox.model.vehicle.VehicleTripSearchCriteria;
import org.egov.inbox.repository.ServiceRequestRepository;
import org.egov.inbox.util.BpaConstants;
import org.egov.inbox.util.ErrorConstants;
import org.egov.inbox.util.FSMConstants;
import org.egov.inbox.util.TLConstants;
import org.egov.inbox.web.model.Inbox;
import org.egov.inbox.web.model.InboxResponse;
import org.egov.inbox.web.model.InboxSearchCriteria;
import org.egov.inbox.web.model.RequestInfoWrapper;
import org.egov.inbox.web.model.VehicleCustomResponse;
import org.egov.inbox.web.model.workflow.BusinessService;
import org.egov.inbox.web.model.workflow.ProcessInstance;
import org.egov.inbox.web.model.workflow.ProcessInstanceResponse;
import org.egov.inbox.web.model.workflow.ProcessInstanceSearchCriteria;
import org.egov.inbox.web.model.workflow.State;
import org.egov.tracer.model.CustomException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.jayway.jsonpath.JsonPath;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class InboxService {

    private InboxConfiguration config;

    private ServiceRequestRepository serviceRequestRepository;

    private ObjectMapper mapper;

    private WorkflowService workflowService;

    @Autowired
    private PtInboxFilterService ptInboxFilterService;

    @Autowired
    private TLInboxFilterService tlInboxFilterService;

    @Autowired
    private BPAInboxFilterService bpaInboxFilterService;

    @Autowired
    private FSMInboxFilterService fsmInboxFilter;
    
    @Autowired
    private NOCInboxFilterService nocInboxFilterService;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    public InboxService(InboxConfiguration config, ServiceRequestRepository serviceRequestRepository,
            ObjectMapper mapper, WorkflowService workflowService) {
        this.config = config;
        this.serviceRequestRepository = serviceRequestRepository;
        this.mapper = mapper;
        this.mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

        this.workflowService = workflowService;
    }

    public InboxResponse fetchInboxData(InboxSearchCriteria criteria, RequestInfo requestInfo) {
    	
        ProcessInstanceSearchCriteria processCriteria = criteria.getProcessSearchCriteria();
        HashMap moduleSearchCriteria = criteria.getModuleSearchCriteria();
        processCriteria.setTenantId(criteria.getTenantId());
        Integer totalCount = workflowService.getProcessCount(criteria.getTenantId(), requestInfo, processCriteria);
        List<String> inputStatuses = new ArrayList<>();
        if (!CollectionUtils.isEmpty(processCriteria.getStatus()))
            inputStatuses = new ArrayList<>(processCriteria.getStatus());
        StringBuilder assigneeUuid = new StringBuilder();
        String dsoId = null;
        if (requestInfo.getUserInfo().getRoles().get(0).getCode().equals(FSMConstants.FSM_DSO)) {
            Map<String, Object> searcherRequestForDSO = new HashMap<>();
            Map<String, Object> searchCriteriaForDSO = new HashMap<>();
            searchCriteriaForDSO.put(TENANT_ID_PARAM, criteria.getTenantId());
            searchCriteriaForDSO.put(FSMConstants.OWNER_ID, requestInfo.getUserInfo().getUuid());
            searcherRequestForDSO.put(REQUESTINFO_PARAM, requestInfo);
            searcherRequestForDSO.put(SEARCH_CRITERIA_PARAM, searchCriteriaForDSO);
            StringBuilder uri = new StringBuilder();
            uri.append(config.getSearcherHost()).append(config.getFsmInboxDSoIDEndpoint());

            Object resultForDsoId = restTemplate.postForObject(uri.toString(), searcherRequestForDSO, Map.class);

            dsoId = JsonPath.read(resultForDsoId, "$.vendor[0].id");

        }
        if (!ObjectUtils.isEmpty(processCriteria.getAssignee())) {
            assigneeUuid = assigneeUuid.append(processCriteria.getAssignee());
            processCriteria.setStatus(null);
        }
        // Since we want the whole status count map regardless of the status filter and assignee filter being passed
        processCriteria.setAssignee(null);
        processCriteria.setStatus(null);
        
        List<HashMap<String, Object>> bpaCitizenStatusCountMap = new ArrayList<HashMap<String,Object>>();
        List<String> roles = requestInfo.getUserInfo().getRoles().stream().map(Role::getCode).collect(Collectors.toList());
        Map<String, List<String>> tenantAndApplnNumbersMap = new HashMap<>();
        if(processCriteria != null && !ObjectUtils.isEmpty(processCriteria.getModuleName())
                && processCriteria.getModuleName().equals(BPA) && roles.contains(BpaConstants.CITIZEN)) {
            List<Map<String, String>> tenantWiseApplns = bpaInboxFilterService.fetchTenantWiseApplicationNumbersForCitizenInboxFromSearcher(criteria, moduleSearchCriteria, requestInfo);
            if (moduleSearchCriteria == null || moduleSearchCriteria.isEmpty()) {
                moduleSearchCriteria = new HashMap<>();
                moduleSearchCriteria.put(MOBILE_NUMBER_PARAM, requestInfo.getUserInfo().getMobileNumber());
                criteria.setModuleSearchCriteria(moduleSearchCriteria);
            } 
            for(Map<String, String> tenantAppln : tenantWiseApplns) {
                String tenant = tenantAppln.get("tenantid");
                String applnNo = tenantAppln.get("applicationno");
                if(tenantAndApplnNumbersMap.containsKey(tenant)) {
                    List<String> applnNos = tenantAndApplnNumbersMap.get(tenant);
                    applnNos.add(applnNo);
                    tenantAndApplnNumbersMap.put(tenant, applnNos);
                } else {
                    List<String> l = new ArrayList<>();
                    l.add(applnNo);
                    tenantAndApplnNumbersMap.put(tenant, l);
                }
            }
            String inputTenantID = processCriteria.getTenantId();
            List<String> inputBusinessIds = processCriteria.getBusinessIds();
            for(Map.Entry<String, List<String>> t : tenantAndApplnNumbersMap.entrySet()) {
                processCriteria.setTenantId(t.getKey());
                processCriteria.setBusinessIds(t.getValue());
                bpaCitizenStatusCountMap.addAll(workflowService.getProcessStatusCount(requestInfo, processCriteria));
            }
            processCriteria.setTenantId(inputTenantID);
            processCriteria.setBusinessIds(inputBusinessIds);
        }
         String moduleName = processCriteria.getModuleName();
        if(ObjectUtils.isEmpty(processCriteria.getModuleName()) && !ObjectUtils.isEmpty(processCriteria.getBusinessService()) &&
        		(processCriteria.getBusinessService().contains("FSM") || processCriteria.getBusinessService().contains("FSM_VEHICLE_TRIP"))){
        	processCriteria.setModuleName(processCriteria.getBusinessService().get(0));
        }
        List<HashMap<String, Object>> statusCountMap = workflowService.getProcessStatusCount(requestInfo, processCriteria);
        processCriteria.setModuleName(moduleName);
        if(!bpaCitizenStatusCountMap.isEmpty()) {
            statusCountMap = bpaCitizenStatusCountMap;
            processCriteria.setBusinessIds(Collections.emptyList());
        }
        processCriteria.setStatus(inputStatuses);
        processCriteria.setAssignee(assigneeUuid.toString());
        List<String> businessServiceName = processCriteria.getBusinessService();
        List<Inbox> inboxes = new ArrayList<Inbox>();
        InboxResponse response = new InboxResponse();
        JSONArray businessObjects = null;
        // Map<String,String> srvMap = (Map<String, String>) config.getServiceSearchMapping().get(businessServiceName.get(0));
        Map<String, String> srvMap = fetchAppropriateServiceMap(businessServiceName);
        if (CollectionUtils.isEmpty(businessServiceName)) {
            throw new CustomException(ErrorConstants.MODULE_SEARCH_INVLAID, "Bussiness Service is mandatory for module search");
        }
        
        if (!CollectionUtils.isEmpty(moduleSearchCriteria)) {
            moduleSearchCriteria.put("tenantId", criteria.getTenantId());
            moduleSearchCriteria.put("offset", criteria.getOffset());
            moduleSearchCriteria.put("limit", criteria.getLimit());
            List<BusinessService> bussinessSrvs = new ArrayList<BusinessService>();
            for (String businessSrv : businessServiceName) {
                BusinessService businessService = workflowService.getBusinessService(criteria.getTenantId(), requestInfo,
                        businessSrv);
                bussinessSrvs.add(businessService);
            }
            HashMap<String, String> StatusIdNameMap = workflowService.getActionableStatusesForRole(requestInfo, bussinessSrvs,
                    processCriteria);
            String applicationStatusParam = srvMap.get("applsStatusParam");
            String businessIdParam = srvMap.get("businessIdProperty");
            if (StringUtils.isEmpty(applicationStatusParam)) {
                applicationStatusParam = "applicationStatus";
            }
            List<String> crtieriaStatuses = new ArrayList<String>();
            // if(!CollectionUtils.isEmpty((Collection<String>) moduleSearchCriteria.get(applicationStatusParam))) {
            // //crtieriaStatuses = (List<String>) moduleSearchCriteria.get(applicationStatusParam);
            // }else {
            if (StatusIdNameMap.values().size() > 0) {
                if (!CollectionUtils.isEmpty(processCriteria.getStatus())) {
                    List<String> statuses = new ArrayList<String>();
                    processCriteria.getStatus().forEach(status -> {
                        statuses.add(StatusIdNameMap.get(status));
                    });
                    moduleSearchCriteria.put(applicationStatusParam, StringUtils.arrayToDelimitedString(statuses.toArray(), ","));
                } else {
                    moduleSearchCriteria.put(applicationStatusParam,
                            StringUtils.arrayToDelimitedString(StatusIdNameMap.values().toArray(), ","));
                }

            }

            // }
            // Redirect request to searcher in case of PT to fetch acknowledgement IDS
            Boolean isSearchResultEmpty = false;
            List<String> businessKeys = new ArrayList<>();
            if (!ObjectUtils.isEmpty(processCriteria.getModuleName()) && processCriteria.getModuleName().equals(PT)) {
                totalCount = ptInboxFilterService.fetchAcknowledgementIdsCountFromSearcher(criteria, StatusIdNameMap,
                        requestInfo);
                List<String> acknowledgementNumbers = ptInboxFilterService.fetchAcknowledgementIdsFromSearcher(criteria,
                        StatusIdNameMap, requestInfo);
                if (!CollectionUtils.isEmpty(acknowledgementNumbers)) {
                    moduleSearchCriteria.put(ACKNOWLEDGEMENT_IDS_PARAM, acknowledgementNumbers);
                    businessKeys.addAll(acknowledgementNumbers);
                    moduleSearchCriteria.remove(LOCALITY_PARAM);
                    moduleSearchCriteria.remove(OFFSET_PARAM);
                } else {
                    isSearchResultEmpty = true;
                }
            }
            if (!ObjectUtils.isEmpty(processCriteria.getModuleName()) && ( processCriteria.getModuleName().equals(TL)
                    || processCriteria.getModuleName().equals(BPAREG))) {
                totalCount = tlInboxFilterService.fetchApplicationCountFromSearcher(criteria, StatusIdNameMap, requestInfo);
                List<String> applicationNumbers = tlInboxFilterService.fetchApplicationNumbersFromSearcher(criteria,
                        StatusIdNameMap, requestInfo);
                if (!CollectionUtils.isEmpty(applicationNumbers)) {
                    moduleSearchCriteria.put(APPLICATION_NUMBER_PARAM, applicationNumbers);
                    businessKeys.addAll(applicationNumbers);
                    moduleSearchCriteria.remove(TLConstants.STATUS_PARAM);
                    moduleSearchCriteria.remove(LOCALITY_PARAM);
                    moduleSearchCriteria.remove(OFFSET_PARAM);
                } else {
                    isSearchResultEmpty = true;
                }
            }

            if (!ObjectUtils.isEmpty(processCriteria.getBusinessService())
                    && processCriteria.getBusinessService().get(0).equals(FSMConstants.FSM_MODULE)) {

                totalCount = fsmInboxFilter.fetchApplicationCountFromSearcher(criteria, StatusIdNameMap, requestInfo, dsoId);
            }
            if (processCriteria != null && !ObjectUtils.isEmpty(processCriteria.getModuleName())
                    && processCriteria.getModuleName().equals(BPA)) {
                totalCount = bpaInboxFilterService.fetchApplicationCountFromSearcher(criteria, StatusIdNameMap, requestInfo);
                List<String> applicationNumbers = bpaInboxFilterService.fetchApplicationNumbersFromSearcher(criteria,
                        StatusIdNameMap, requestInfo);
                if (!CollectionUtils.isEmpty(applicationNumbers)) {
                    moduleSearchCriteria.put(BPA_APPLICATION_NUMBER_PARAM, applicationNumbers);
                    businessKeys.addAll(applicationNumbers);
                    moduleSearchCriteria.remove(STATUS_PARAM);
                    moduleSearchCriteria.remove(MOBILE_NUMBER_PARAM);
                    moduleSearchCriteria.remove(LOCALITY_PARAM);
                    moduleSearchCriteria.remove(OFFSET_PARAM);
                } else {
                    isSearchResultEmpty = true;
                }
            }
            
            if (processCriteria != null && !ObjectUtils.isEmpty(processCriteria.getModuleName())
                    && processCriteria.getModuleName().equals(NOC)) {
                totalCount = nocInboxFilterService.fetchApplicationCountFromSearcher(criteria, StatusIdNameMap, requestInfo);
                List<String> applicationNumbers = nocInboxFilterService.fetchApplicationNumbersFromSearcher(criteria, StatusIdNameMap, requestInfo);
                if (!CollectionUtils.isEmpty(applicationNumbers)) {
                    moduleSearchCriteria.put(NOC_APPLICATION_NUMBER_PARAM, applicationNumbers);
                    businessKeys.addAll(applicationNumbers);
                    moduleSearchCriteria.remove(STATUS_PARAM);
                    moduleSearchCriteria.remove(MOBILE_NUMBER_PARAM);
                    moduleSearchCriteria.remove(LOCALITY_PARAM);
                    moduleSearchCriteria.remove(OFFSET_PARAM);
                } else {
                    isSearchResultEmpty = true;
                }
            }
            
            /*
             * if(!ObjectUtils.isEmpty(processCriteria.getModuleName()) && processCriteria.getModuleName().equals(PT)){ Boolean
             * isMobileNumberPresent = false; if(moduleSearchCriteria.containsKey(MOBILE_NUMBER_PARAM)){ isMobileNumberPresent =
             * true; } Boolean isUserPresentForGivenMobileNumber = false; if(isMobileNumberPresent) { String tenantId =
             * criteria.getTenantId(); String mobileNumber = (String) moduleSearchCriteria.get(MOBILE_NUMBER_PARAM); String
             * userUUID = fetchUserUUID(mobileNumber, requestInfo, tenantId); isUserPresentForGivenMobileNumber =
             * ObjectUtils.isEmpty(userUUID) ? true : false; } if(isMobileNumberPresent && isUserPresentForGivenMobileNumber){
             * isSearchResultEmpty = true; } if(!isSearchResultEmpty){ Object result = null; Map<String, Object> searcherRequest =
             * new HashMap<>(); Map<String, Object> searchCriteria = new HashMap<>();
             * searchCriteria.put(TENANT_ID_PARAM,criteria.getTenantId()); // Accomodating module search criteria in searcher
             * request if(moduleSearchCriteria.containsKey(MOBILE_NUMBER_PARAM)){ searchCriteria.put(MOBILE_NUMBER_PARAM,
             * moduleSearchCriteria.get(MOBILE_NUMBER_PARAM)); } if(moduleSearchCriteria.containsKey(LOCALITY_PARAM)){
             * searchCriteria.put(LOCALITY_PARAM, moduleSearchCriteria.get(LOCALITY_PARAM)); }
             * if(moduleSearchCriteria.containsKey(PROPERTY_ID_PARAM)){ searchCriteria.put(PROPERTY_ID_PARAM,
             * moduleSearchCriteria.get(PROPERTY_ID_PARAM)); } if(moduleSearchCriteria.containsKey(APPLICATION_NUMBER_PARAM)) {
             * searchCriteria.put(APPLICATION_NUMBER_PARAM, moduleSearchCriteria.get(APPLICATION_NUMBER_PARAM)); } // Accomodating
             * process search criteria in searcher request if(!ObjectUtils.isEmpty(processCriteria.getAssignee())){
             * searchCriteria.put(ASSIGNEE_PARAM, processCriteria.getAssignee()); }
             * if(!ObjectUtils.isEmpty(processCriteria.getStatus())){ searchCriteria.put(STATUS_PARAM,
             * processCriteria.getStatus()); }else{ if(StatusIdNameMap.values().size() > 0) {
             * if(CollectionUtils.isEmpty(processCriteria.getStatus())) { searchCriteria.put(STATUS_PARAM,
             * StatusIdNameMap.keySet()); } } } // Paginating searcher results searchCriteria.put(OFFSET_PARAM,
             * criteria.getOffset()); searchCriteria.put(NO_OF_RECORDS_PARAM, criteria.getLimit());
             * searcherRequest.put(REQUESTINFO_PARAM, requestInfo); searcherRequest.put(SEARCH_CRITERIA_PARAM, searchCriteria);
             * result = restTemplate.postForObject(PT_INBOX_SEARCHER_URL, searcherRequest, Map.class); List<String>
             * acknowledgementNumbers = JsonPath.read(result, "$.Properties.*.acknowldgementnumber");
             * if(!CollectionUtils.isEmpty(acknowledgementNumbers)) { moduleSearchCriteria.put(ACKNOWLEDGEMENT_IDS_PARAM,
             * acknowledgementNumbers); moduleSearchCriteria.remove(OFFSET_PARAM); moduleSearchCriteria.remove(LIMIT_PARAM);
             * }else{ isSearchResultEmpty = true; } } }
             */
            businessObjects = new JSONArray();
            if (!isSearchResultEmpty) {
                businessObjects = fetchModuleObjects(moduleSearchCriteria, businessServiceName, criteria.getTenantId(),
                        requestInfo, srvMap);
            }
            Map<String, Object> businessMap = StreamSupport.stream(businessObjects.spliterator(), false)
                    .collect(Collectors.toMap(s1 -> ((JSONObject) s1).get(businessIdParam).toString(),
                            s1 -> s1, (e1, e2) -> e1, LinkedHashMap::new));
            ArrayList businessIds = new ArrayList();
            businessIds.addAll(businessMap.keySet());
            processCriteria.setBusinessIds(businessIds);
            // processCriteria.setOffset(criteria.getOffset());
            // processCriteria.setLimit(criteria.getLimit());
            processCriteria.setIsProcessCountCall(false);
            ProcessInstanceResponse processInstanceResponse;
            /*
             * In BPA, the stakeholder can able to submit applications for multiple cities
             * and in the single inbox all cities submitted applications need to show
             */
            if(processCriteria != null && !ObjectUtils.isEmpty(processCriteria.getModuleName())
                    && processCriteria.getModuleName().equals(BPA) && roles.contains(BpaConstants.CITIZEN)) {
                Map<String, List<String>> tenantAndApplnNoForProcessInstance = new HashMap<>();
                for(Object businessId : businessIds) {
                    for (Map.Entry<String, List<String>> tenantAppln : tenantAndApplnNumbersMap.entrySet()) {
                        String tenantId = tenantAppln.getKey();
                        if (tenantAppln.getValue().contains(businessId)
                                && tenantAndApplnNoForProcessInstance.containsKey(tenantId)) {
                              List<String> applnNos = tenantAndApplnNoForProcessInstance.get(tenantId);
                              applnNos.add(String.valueOf(businessId));
                              tenantAndApplnNoForProcessInstance.put(tenantId, applnNos);
                          } else {
                              List<String> businesIds = new ArrayList<>();
                              businesIds.add(String.valueOf(businessId));
                              tenantAndApplnNoForProcessInstance.put(tenantId, businesIds);
                          }
                      }
                }
                ProcessInstanceResponse processInstanceRes = new ProcessInstanceResponse();
                for(Map.Entry<String, List<String>> appln : tenantAndApplnNoForProcessInstance.entrySet()) {
                    processCriteria.setTenantId(appln.getKey());
                    processCriteria.setBusinessIds(appln.getValue());
                    ProcessInstanceResponse processInstance = workflowService.getProcessInstance(processCriteria, requestInfo);
                    processInstanceRes.setResponseInfo(processInstance.getResponseInfo());
                    if(processInstanceRes.getProcessInstances() == null)
                        processInstanceRes.setProcessInstances(processInstance.getProcessInstances());
                    else
                        processInstanceRes.getProcessInstances().addAll(processInstance.getProcessInstances());
                }
                processInstanceResponse = processInstanceRes;
            } else {
                processInstanceResponse = workflowService.getProcessInstance(processCriteria, requestInfo);
            }
            
            List<ProcessInstance> processInstances = processInstanceResponse.getProcessInstances();
            Map<String, ProcessInstance> processInstanceMap = processInstances.stream()
                    .collect(Collectors.toMap(ProcessInstance::getBusinessId, Function.identity()));
            
            if (businessObjects.length() > 0 && processInstances.size() > 0) {
                if (CollectionUtils.isEmpty(businessKeys)) {
                    businessMap.keySet().forEach(busiessKey -> {
                        Inbox inbox = new Inbox();
                        inbox.setProcessInstance(processInstanceMap.get(busiessKey));
                        inbox.setBusinessObject(toMap((JSONObject) businessMap.get(busiessKey)));
                        inboxes.add(inbox);
                    });
                } else {
                    businessKeys.forEach(busiessKey -> {
                        Inbox inbox = new Inbox();
                        inbox.setProcessInstance(processInstanceMap.get(busiessKey));
                        inbox.setBusinessObject(toMap((JSONObject) businessMap.get(busiessKey)));
                        inboxes.add(inbox);
                    });
                }
            }
        } else {
            processCriteria.setOffset(criteria.getOffset());
            processCriteria.setLimit(criteria.getLimit());

            ProcessInstanceResponse processInstanceResponse = workflowService.getProcessInstance(processCriteria, requestInfo);
            List<ProcessInstance> processInstances = processInstanceResponse.getProcessInstances();
            HashMap<String, List<String>> businessSrvIdsMap = new HashMap<String, List<String>>();
            Map<String, ProcessInstance> processInstanceMap = processInstances.stream()
                    .collect(Collectors.toMap(ProcessInstance::getBusinessId, Function.identity()));
            moduleSearchCriteria = new HashMap<String, String>();
            if (CollectionUtils.isEmpty(srvMap)) {
                throw new CustomException(ErrorConstants.INVALID_MODULE,
                        "config not found for the businessService : " + businessServiceName);
            }
            String businessIdParam = srvMap.get("businessIdProperty");
            moduleSearchCriteria.put(srvMap.get("applNosParam"),
                    StringUtils.arrayToDelimitedString(processInstanceMap.keySet().toArray(), ","));
            moduleSearchCriteria.put("tenantId", criteria.getTenantId());
            // moduleSearchCriteria.put("offset", criteria.getOffset());
            moduleSearchCriteria.put("limit", -1);
            businessObjects = fetchModuleObjects(moduleSearchCriteria, businessServiceName, criteria.getTenantId(), requestInfo,
                    srvMap);
            Map<String, Object> businessMap = StreamSupport.stream(businessObjects.spliterator(), false)
                    .collect(Collectors.toMap(s1 -> ((JSONObject) s1).get(businessIdParam).toString(),
                            s1 -> s1));

            if (businessObjects.length() > 0 && processInstances.size() > 0) {
                processInstanceMap.keySet().forEach(pinstance -> {
                    Inbox inbox = new Inbox();
                    inbox.setProcessInstance(processInstanceMap.get(pinstance));
                    inbox.setBusinessObject(toMap((JSONObject) businessMap.get(pinstance)));
                    inboxes.add(inbox);
                });
            }

        }
        
        log.info("businessServiceName.contains(FSM_MODULE) ::: " + businessServiceName.contains(FSM_MODULE));
        
		if (businessServiceName.contains(FSM_MODULE)) {

			
			List<String> applicationStatus = new ArrayList<>();
			
			applicationStatus.add(WAITING_FOR_DISPOSAL_STATE);
			applicationStatus.add(DISPOSED_STATE);
			
			List<Map<String, Object>> vehicleResponse = fetchVehicleTripResponse(criteria, requestInfo,applicationStatus);

			BusinessService businessService = workflowService.getBusinessService(criteria.getTenantId(), requestInfo,
					FSM_VEHICLE_TRIP_MODULE);
			
			log.info("businessService :::: " + businessService);
			populateStatusCountMap(statusCountMap, vehicleResponse, businessService);

			for(HashMap<String, Object> vTripMap : statusCountMap) {
				if((WAITING_FOR_DISPOSAL_STATE.equals(vTripMap.get(APPLICATIONSTATUS)) ||
						DISPOSED_STATE.equals(vTripMap.get(APPLICATIONSTATUS))) && 
						inputStatuses.contains(vTripMap.get(STATUSID)) ) {
					totalCount+=((int)vTripMap.get(COUNT));
				}
			}
			
			List<String> requiredApplications = new ArrayList<>();

			inboxes.forEach(inbox -> {
				ProcessInstance inboxProcessInstance = inbox.getProcessInstance();
				if (null != inboxProcessInstance && null!= inboxProcessInstance.getState()) {
					
					String appStatus = inboxProcessInstance.getState().getApplicationStatus();

					if (DSO_INPROGRESS_STATE.equals(appStatus) || CITIZEN_FEEDBACK_PENDING_STATE.equals(appStatus)
							|| COMPLETED_STATE.equals(appStatus)) {
						requiredApplications.add(inboxProcessInstance.getBusinessId());
					}
				}
			});
			
			log.info("requiredApplications :::: " + requiredApplications);
			
			List<VehicleTripDetail> vehicleTripDetail = fetchVehicleStatusForApplication(requiredApplications,requestInfo,criteria.getTenantId());

			log.info("vehicleTripDetail :::: " + vehicleTripDetail);			
			inboxes.forEach(inbox -> {
				
				if (null != inbox && null != inbox.getProcessInstance()
						&& null != inbox.getProcessInstance().getBusinessId()) {


					List<VehicleTripDetail> vehicleTripDetails = vehicleTripDetail.stream()
							.filter(trip -> inbox.getProcessInstance().getBusinessId().equals(trip.getReferenceNo()))
							.collect(Collectors.toList());

					Map<String, Object> vehicleBusinessObject = inbox.getBusinessObject();

					vehicleBusinessObject.put(VEHICLE_LOG, vehicleTripDetails);
				}
			});
			
			log.info("CollectionUtils.isEmpty(inboxes) :::: " + CollectionUtils.isEmpty(inboxes));

			if (CollectionUtils.isEmpty(inboxes)) {
				inputStatuses = inputStatuses.stream().filter(x -> x != null).collect(Collectors.toList());

				List<String> fsmApplicationList = fetchVehicleStateMap(inputStatuses, requestInfo, criteria.getTenantId());
				moduleSearchCriteria.put("applicationNos", fsmApplicationList);
				moduleSearchCriteria.put("applicationStatus", requiredApplications);
				moduleSearchCriteria.put("offset", criteria.getOffset());
	            moduleSearchCriteria.put("limit", criteria.getLimit());

				processCriteria.setBusinessIds(fsmApplicationList);
				processCriteria.setStatus(null);

				ProcessInstanceResponse processInstanceResponse = workflowService.getProcessInstance(processCriteria,
						requestInfo);

				log.info("processInstanceResponse :::: " + processInstanceResponse);
				
				List<ProcessInstance> vehicleProcessInstances = processInstanceResponse.getProcessInstances();

				Map<String, ProcessInstance> vehicleProcessInstanceMap = vehicleProcessInstances.stream()
						.collect(Collectors.toMap(ProcessInstance::getBusinessId, Function.identity()));

				JSONArray vehicleBusinessObjects = fetchModuleObjects(moduleSearchCriteria, businessServiceName,
						criteria.getTenantId(), requestInfo, srvMap);

				String businessIdParam = srvMap.get("businessIdProperty");
				
				log.info("businessIdParam :::: " + businessIdParam);

				Map<String, Object> vehicleBusinessMap = StreamSupport
						.stream(vehicleBusinessObjects.spliterator(), false)
						.collect(Collectors.toMap(s1 -> ((JSONObject) s1).get(businessIdParam).toString(), s1 -> s1,
								(e1, e2) -> e1, LinkedHashMap::new));

				log.info("businessIdParam :::: " + businessIdParam);
				
				if (vehicleBusinessObjects.length() > 0 && vehicleProcessInstances.size() > 0) {
					
					log.info("vehicleBusinessObjects.length() :::: " + vehicleBusinessObjects.length());
					log.info("vehicleProcessInstances.size() :::: " + vehicleProcessInstances.size());
					
					fsmApplicationList.forEach(busiessKey -> {
						
						Inbox inbox = new Inbox();
						inbox.setProcessInstance(vehicleProcessInstanceMap.get(busiessKey));
						inbox.setBusinessObject(toMap((JSONObject) vehicleBusinessMap.get(busiessKey)));

						inboxes.add(inbox);
					});
				}
			}
		}
		
		log.info("statusCountMap size :::: " + statusCountMap.size());
		
        response.setTotalCount(totalCount);
        response.setStatusMap(statusCountMap);
        response.setItems(inboxes);
        return response;
    }

    public List<String> fetchVehicleStateMap(List<String> inputStatuses, RequestInfo requestInfo, String tenantId) {
		VehicleTripSearchCriteria vehicleTripSearchCriteria = new VehicleTripSearchCriteria();
		vehicleTripSearchCriteria.setApplicationStatus(inputStatuses);
		vehicleTripSearchCriteria.setTenantId(tenantId);
		StringBuilder url = new StringBuilder(config.getVehicleHost());
		url.append( config.getFetchApplicationIds());
		
		Object result = serviceRequestRepository.fetchResult(url, vehicleTripSearchCriteria);
		VehicleCustomResponse response =null;
		try {
			response = mapper.convertValue(result, VehicleCustomResponse.class);
			if(null != response && null != response.getApplicationIdList()) {
				System.out.println("size ::::  "+response.getApplicationIdList().size());;
				return response.getApplicationIdList();
			}
		} catch (IllegalArgumentException e) {
			throw new CustomException(ErrorConstants.PARSING_ERROR, "Failed to parse response of ProcessInstance");
		}
		return new ArrayList<>();
	}
    
    /**
	 * @param requiredApplications
	 * @return
	 * Description : Fetch the vehicle_trip_detail by list of reference no.
	 */
	private List<VehicleTripDetail> fetchVehicleStatusForApplication(List<String> requiredApplications,RequestInfo requestInfo, String tenantId) {
		VehicleTripSearchCriteria vehicleTripSearchCriteria = new VehicleTripSearchCriteria();
		vehicleTripSearchCriteria.setApplicationNos(requiredApplications);
		vehicleTripSearchCriteria.setTenantId(tenantId);
		return fetchVehicleTripDetailsByReferenceNo(vehicleTripSearchCriteria,requestInfo);
	}
	
	public List<VehicleTripDetail> fetchVehicleTripDetailsByReferenceNo(VehicleTripSearchCriteria vehicleTripSearchCriteria, RequestInfo requestInfo) {
		StringBuilder url = new StringBuilder(config.getVehicleHost());
		url.append( config.getVehicleSearchTripPath());
		Object result = serviceRequestRepository.fetchResult(url, vehicleTripSearchCriteria);
		VehicleTripDetailResponse response =null;
		try {
			response = mapper.convertValue(result, VehicleTripDetailResponse.class);
			if(null != response && null != response.getVehicleTripDetail()) {
				System.out.println("size ::::  "+response.getVehicleTripDetail().size());;
				return response.getVehicleTripDetail();
			}
		} catch (IllegalArgumentException e) {
			throw new CustomException(ErrorConstants.PARSING_ERROR, "Failed to parse response of ProcessInstance");
		}
		return new ArrayList<>();
	}


	private void populateStatusCountMap(List<HashMap<String, Object>> statusCountMap,
			List<Map<String, Object>> vehicleResponse, BusinessService businessService) {
		
		if (!CollectionUtils.isEmpty(vehicleResponse) && businessService != null) {
			List<State> appStates = businessService.getStates();

			for (State appState : appStates) {
				
				vehicleResponse.forEach(trip -> {
					
					HashMap<String, Object> vehicleTripStatusMp = new HashMap<>();
					if(trip.get(APPLICATIONSTATUS).equals(appState.getApplicationStatus())) {
						
						vehicleTripStatusMp.put(COUNT, trip.get(COUNT));
						vehicleTripStatusMp.put(APPLICATIONSTATUS, appState.getApplicationStatus());
						vehicleTripStatusMp.put(STATUSID, appState.getUuid());
						vehicleTripStatusMp.put(BUSINESS_SERVICE_PARAM, FSM_VEHICLE_TRIP_MODULE);
					}
					
					if (MapUtils.isNotEmpty(vehicleTripStatusMp))
						statusCountMap.add(vehicleTripStatusMp);
				});
			}
		}
	}
    
    private List<Map<String, Object>> fetchVehicleTripResponse(InboxSearchCriteria criteria, RequestInfo requestInfo,List<String> applicationStatus) {

		VehicleSearchCriteria vehicleTripSearchCriteria = new VehicleSearchCriteria();
		
		vehicleTripSearchCriteria.setApplicationStatus(applicationStatus);

		vehicleTripSearchCriteria.setTenantId(criteria.getTenantId());
		
		List<Map<String, Object>> vehicleResponse = null ;
		VehicleCustomResponse vehicleCustomResponse =  fetchApplicationCount(vehicleTripSearchCriteria, requestInfo);
		if(null != vehicleCustomResponse && null != vehicleCustomResponse.getApplicationStatusCount() ) {
			vehicleResponse =vehicleCustomResponse.getApplicationStatusCount();
		}else {
			vehicleResponse = new ArrayList<Map<String,Object>>();
		}
    	
    	
    	return vehicleResponse;
    }
    
    public VehicleCustomResponse fetchApplicationCount(VehicleSearchCriteria criteria, RequestInfo requestInfo) {
		StringBuilder url = new StringBuilder(config.getVehicleHost());
		url.append( config.getVehicleApplicationStatusCountPath());
		Object result = serviceRequestRepository.fetchResult(url, criteria);
		VehicleCustomResponse resposne =null;
		try {
			resposne = mapper.convertValue(result, VehicleCustomResponse.class);
		} catch (IllegalArgumentException e) {
			throw new CustomException(ErrorConstants.PARSING_ERROR, "Failed to parse response of ProcessInstance");
		}
		return resposne;
	}
    
    /*
     * private String fetchUserUUID(String mobileNumber, RequestInfo requestInfo, String tenantId) { StringBuilder uri = new
     * StringBuilder(); uri.append(userHost).append(userSearchEndpoint); Map<String, Object> userSearchRequest = new HashMap<>();
     * userSearchRequest.put("RequestInfo", requestInfo); userSearchRequest.put("tenantId", tenantId);
     * userSearchRequest.put("userType", "CITIZEN"); userSearchRequest.put("userName", mobileNumber); String uuid = ""; try {
     * Object user = serviceRequestRepository.fetchResult(uri, userSearchRequest); if(null != user) { uuid = JsonPath.read(user,
     * "$.user[0].uuid"); }else { log.error("Service returned null while fetching user for username - " + mobileNumber); }
     * }catch(Exception e) { log.error("Exception while fetching user for username - " + mobileNumber);
     * log.error("Exception trace: ", e); } return uuid; }
     */

    private Map<String, String> fetchAppropriateServiceMap(List<String> businessServiceName) {
        StringBuilder appropriateKey = new StringBuilder();
        for (String businessServiceKeys : config.getServiceSearchMapping().keySet()) {
            if (businessServiceKeys.contains(businessServiceName.get(0))) {
                appropriateKey.append(businessServiceKeys);
                break;
            }
        }
        if (ObjectUtils.isEmpty(appropriateKey)) {
            throw new CustomException("EG_INBOX_SEARCH_ERROR",
                    "Inbox service is not configured for the provided business services");
        }
        for (String inputBusinessService : businessServiceName) {
            if (!appropriateKey.toString().contains(inputBusinessService)) {
                throw new CustomException("EG_INBOX_SEARCH_ERROR", "Cross module search is NOT allowed.");
            }
        }
        return config.getServiceSearchMapping().get(appropriateKey.toString());
    }

    private JSONArray fetchModuleObjects(HashMap moduleSearchCriteria, List<String> businessServiceName, String tenantId,
            RequestInfo requestInfo, Map<String, String> srvMap) {
        JSONArray resutls = null;
        
        if (CollectionUtils.isEmpty(srvMap) || StringUtils.isEmpty(srvMap.get("searchPath"))) {
            throw new CustomException(ErrorConstants.INVALID_MODULE_SEARCH_PATH,
                    "search path not configured for the businessService : " + businessServiceName);
        }
        StringBuilder url = new StringBuilder(srvMap.get("searchPath"));
        url.append("?tenantId=").append(tenantId);
       
        Set<String> searchParams = moduleSearchCriteria.keySet();
        
		searchParams.forEach((param) -> {

			if (!param.equalsIgnoreCase("tenantId")) {

				if (moduleSearchCriteria.get(param) instanceof Collection) {
					url.append("&").append(param).append("=");
					url.append(StringUtils
							.arrayToDelimitedString(((Collection<?>) moduleSearchCriteria.get(param)).toArray(), ","));
				} else {
					url.append("&").append(param).append("=").append(moduleSearchCriteria.get(param).toString());
				}
			}
		});
        
        RequestInfoWrapper requestInfoWrapper = RequestInfoWrapper.builder().requestInfo(requestInfo).build();
        Object result = serviceRequestRepository.fetchResult(url, requestInfoWrapper);
        
        LinkedHashMap responseMap;
        try {
            responseMap = mapper.convertValue(result, LinkedHashMap.class);
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorConstants.PARSING_ERROR, "Failed to parse response of ProcessInstance Count");
        }
        
        
        JSONObject jsonObject = new JSONObject(responseMap);
        
        try {
            resutls = (JSONArray) jsonObject.getJSONArray(srvMap.get("dataRoot"));
        } catch (Exception e) {
            throw new CustomException(ErrorConstants.INVALID_MODULE_DATA,
                    " search api could not find data in dataroot " + srvMap.get("dataRoot"));
        }
        
        
        return resutls;
    }

    public static Map<String, Object> toMap(JSONObject object) throws JSONException {
        Map<String, Object> map = new HashMap<String, Object>();

        if (object == null) {
            return map;
        }
        Iterator<String> keysItr = object.keys();
        while (keysItr.hasNext()) {
            String key = keysItr.next();
            Object value = object.get(key);

            if (value instanceof JSONArray) {
                value = toList((JSONArray) value);
            }

            else if (value instanceof JSONObject) {
                value = toMap((JSONObject) value);
            }
            map.put(key, value);
        }
        return map;
    }

    public static List<Object> toList(JSONArray array) throws JSONException {
        List<Object> list = new ArrayList<Object>();
        for (int i = 0; i < array.length(); i++) {
            Object value = array.get(i);
            if (value instanceof JSONArray) {
                value = toList((JSONArray) value);
            }

            else if (value instanceof JSONObject) {
                value = toMap((JSONObject) value);
            }
            list.add(value);
        }
        return list;
    }

}
