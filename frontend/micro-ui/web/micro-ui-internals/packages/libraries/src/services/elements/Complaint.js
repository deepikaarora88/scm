export const Complaint = {
  create: async ({
    cityCode,
    description,
    landmark,
    city,
    district,
    region,
    state,
    pincode,
    localityCode,
    localityName,
    uploadedImages,
    impact,
    urgency,
    reporterName,
    priorityLevel,
    dueDate,
    summary,
    affectedServices,
    linkedIssues,
    envType,
    requestType,
    issueType,
  }) => {
    const tenantId = Digit.ULBService.getCurrentTenantId();
    let mobileNumber = JSON.parse(sessionStorage.getItem("Digit.User"))?.value?.info?.mobileNumber;
    console.log("citycode, ", cityCode)
    console.log("mbl",mobileNumber)
    const defaultData = {
      incident: {
        tenantId: cityCode,
        environment:envType.code,
        requestType:requestType.code,
        description: description,
        impact:impact.code,
        urgency:urgency.code,
        incidentType:issueType.code,
        priority:priorityLevel.code,
        summary:summary,
        linkedIssues:linkedIssues,
        affectedServices:affectedServices,
        dueDate:dueDate,
        additionalDetail: {},
        source: Digit.Utils.browser.isWebview() ? "mobile" : "web",
        address: {
          landmark: landmark,
          city: city,
          district: district,
          region: region,
          state: state,
          pincode: pincode,
          locality: {
            code: localityCode,
            name: localityName,
          },
          geoLocation: {},
        },
      },
      workflow: {
        action: "APPLY",
        verificationDocuments: uploadedImages,
      },
    };

    if (Digit.SessionStorage.get("user_type") === "employee") {
      defaultData.incident.reporter = {
      //   "reporter": {
      //     "name": "MANVN",
      //     "type": "CITIZEN",
      //     "mobileNumber": "9897490123",
      //     "roles": [
      //         {
      //             "id": null,
      //             "name": "Citizen",
      //             "code": "CITIZEN",
      //             "tenantId": "pg.citya"
      //         }
      //     ],
      //     "tenantId": "pg"
      // }
  //},

        name:reporterName,
        type: "EMPLOYEE",
        mobileNumber: mobileNumber,
        roles: [
          {
            id: null,
            name: "Citizen",
            code: "CITIZEN",
            tenantId: tenantId,
          },
        ],
        tenantId: tenantId,
      };
    }
    console.log("def", defaultData)
    const response = await Digit.PGRService.create(defaultData, cityCode);
    console.log("res", response)
    return response;
  },

  assign: async (complaintDetails, action, employeeData, comments, uploadedDocument, tenantId) => {
    complaintDetails.workflow.action = action;
    complaintDetails.workflow.assignes = employeeData ? [employeeData.uuid] : null;
    complaintDetails.workflow.comments = comments;
    uploadedDocument
      ? (complaintDetails.workflow.verificationDocuments = [
            {
              documentType: "PHOTO",
              fileStoreId: uploadedDocument,
              documentUid: "",
              additionalDetails: {},
            },
          ])
      : null;

    if (!uploadedDocument) complaintDetails.workflow.verificationDocuments = [];
    
    //TODO: get tenant id
    const response = await Digit.PGRService.update(complaintDetails, tenantId);
    return response;
  },
};
