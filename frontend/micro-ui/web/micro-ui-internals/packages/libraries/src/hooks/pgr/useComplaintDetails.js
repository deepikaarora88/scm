import { useQuery, useQueryClient } from "react-query";

// TODO: move to service
const getThumbnails = async (ids, tenantId) => {
  const res = await Digit.UploadServices.Filefetch(ids, tenantId);
  if (res.data.fileStoreIds && res.data.fileStoreIds.length !== 0) {
    return { thumbs: res.data.fileStoreIds.map((o) => o.url.split(",")[3]), images: res.data.fileStoreIds.map((o) => Digit.Utils.getFileUrl(o.url)) };
  } else {
    return null;
  }
};

const getDetailsRow = ({ id, incident, complaintType }) => ({
  
 
  CS_INCIDENT_NO: id,
  CS_INCIDENT_STATUS: `CS_COMMON_${incident.applicationStatus}`,
  CS_INCIDENT_ISSUE_TYPE: complaintType === "" ? "": `${complaintType}`,
  CS_INCIDENT_ENVIRONMENT_TYPE: `${incident.environmentType}`,
  CS_INCIDENT_REQUEST_TYPE: `${incident.requestType}`,
  CS_INCIDENT_URGENCY: `${incident.urgency}`,
  CS_INCIDENT_IMPACT: `${incident.impact}`,
  //CS_ADDCOMPLAINT_COMPLAINT_SUB_TYPE: `SERVICEDEFS.${service.serviceCode.toUpperCase()}`,
  CS_INCIDENT_DESCRIPTION: incident.description,
  CS_INCIDENT_SUMMARY: incident.summary,
  CS_INCIDENT_LINKED_ISSUES: incident.linkedIssues,
  CS_INCIDENT_DUEDATE: incident.dueDate,
  CS_INCIDENT_FILED_DATE: Digit.DateUtils.ConvertTimestampToDate(incident.auditDetails.createdTime),
  // ES_CREATECOMPLAINT_ADDRESS: [
  //   service.address.landmark,
  //   Digit.Utils.locale.getLocalityCode(service.address.locality, service.tenantId),
  //   service.address.city,
  //   service.address.pincode,
  // ],
});



const isEmptyOrNull = (obj) => obj === undefined || obj === null || Object.keys(obj).length === 0;

const transformDetails = ({ id, incident, workflow, thumbnails, complaintType }) => {
  console.log("complty", complaintType)

  const { Customizations, SessionStorage } = window.Digit;
  const role = (SessionStorage.get("user_type") || "CITIZEN").toUpperCase();
  //const id=id1
  const customDetails = Customizations?.PGR?.getComplaintDetailsTableRows
    ? Customizations.PGR.getComplaintDetailsTableRows({ id, incident, role })
    : {};
  return {
    details: !isEmptyOrNull(customDetails) ? customDetails : getDetailsRow({ id, incident, complaintType }),
    thumbnails: thumbnails?.thumbs,
    images: thumbnails?.images,
    workflow: workflow,
    incident,
    audit: {
      citizen: incident.citizen,
      details: incident.auditDetails,
      source: incident.source,
      rating: incident.rating,
      serviceCode: incident.serviceCode,
    },
    incident: incident,
  };
};

const fetchComplaintDetails = async (tenantId, id) => {
  console.log("iff890", id)
  //var serviceDefs = await Digit.MDMSService.getServiceDefs(tenantId, "PGR");
  //console.log("sf",serviceDefs)
  const incident= {
              "active": true,
              "citizen": {
                  "id": 2148,
                  "userName": "8080808000",
                  "name": "shilpa",
                  "type": "CITIZEN",
                  "mobileNumber": "8080808000",
                  "emailId": "",
                  "roles": [
                      {
                          "id": null,
                          "name": "Citizen",
                          "code": "CITIZEN",
                          "tenantId": "pg"
                      }
                  ],
                  "tenantId": "pg",
                  "uuid": "dda30b0a-ae2d-4f87-8b52-d1fc73ed7643",
                  "active": true
              },
              "id": "b213dcb1-14ab-44bf-8b88-4b3791a75a01",
              "tenantId": "pg.citya",
              
              "incidentType": "bug",
              "environmentType":"test",
              "incidentId": "PG-IM-2024-03-29-002320",
              "description": "Hii",
              "requestType":"IT Hardware",
              "urgency":"Critical",
              "linkedIssues":"linked",
              "dueDate":"09-01-2024",
              "summary":"summary",
              "impact":"minor",
              "accountId": "dda30b0a-ae2d-4f87-8b52-d1fc73ed7643",
              "rating": null,
              "additionalDetail": {},
              "applicationStatus": "PENDINGFORASSIGNMENT",
              "source": "web",
              "address": {
                  "tenantId": "pg.citya",
                  "doorNo": null,
                  "plotNo": null,
                  "id": "966cb395-c5ea-41a8-a295-ee15912d8cee",
                  "landmark": "",
                  "city": "City A",
                  "district": "City A",
                  "region": "City A",
                  "state": null,
                  "country": null,
                  "pincode": "",
                  "additionDetails": null,
                  "buildingName": null,
                  "street": null,
                  "locality": {
                      "code": "JLC477",
                      "name": null,
                      "label": null,
                      "latitude": null,
                      "longitude": null,
                      "children": null,
                      "materializedPath": null
                  },
                  "geoLocation": {
                      "latitude": 0.0,
                      "longitude": 0.0,
                      "additionalDetails": null
                  }
              },
              "auditDetails": {
                  "createdBy": "55fa55f0-5348-4eef-922c-2d36c50c56e1",
                  "lastModifiedBy": "55fa55f0-5348-4eef-922c-2d36c50c56e1",
                  "createdTime": 1711552512424,
                  "lastModifiedTime": 1711552512424
              },
              "priority": "LOW"
          }
          
  
  //const service=service1?.ServiceWrappers?.service
 // const {workflow } = (await Digit.PGRService.search(tenantId, { serviceRequestId: id })).ServiceWrappers[0] || {};
  const workflow=incident
  console.log("service77", incident)
  Digit.SessionStorage.set("complaintDetails", { incident, workflow });
  if (incident && workflow ) {
    const complaintType = incident.incidentType.toUpperCase();
    console.log("menuPath", complaintType)
    const ids = workflow.verificationDocuments
      ? workflow.verificationDocuments.filter((doc) => doc.documentType === "PHOTO").map((photo) => photo.fileStoreId || photo.id)
      : null;
    const thumbnails = ids ? await getThumbnails(ids, incident.tenantId) : null;
    console.log("ids", ids)
    const details = transformDetails({ id, incident, workflow, thumbnails, complaintType });
    return details;
  } else {
    return {};
  }
};

const useComplaintDetails = ({ tenantId, id }) => {
  console.log("id9", id)
  const queryClient = useQueryClient();
  const { isLoading, error, data } = useQuery(["complaintDetails", tenantId, id], () => fetchComplaintDetails(tenantId, id));
  return { isLoading, error, complaintDetails: data, revalidate: () => queryClient.invalidateQueries(["complaintDetails", tenantId, id]) };
};

export default useComplaintDetails;
