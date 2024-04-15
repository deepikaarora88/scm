import React, { useState, useEffect, useMemo } from "react";
import { useTranslation } from "react-i18next";
import { useDispatch } from "react-redux";
import { DatePicker, Dropdown, ImageUploadHandler, TextArea, TextInput, UploadFile, CardLabel } from "@egovernments/digit-ui-react-components";
import { useRouteMatch, useHistory } from "react-router-dom";
import { useQueryClient } from "react-query";

import { FormComposer } from "../../../components/FormComposer";
import { createComplaint } from "../../../redux/actions/index";

export const CreateComplaint = ({ parentUrl }) => {
  
  console.log("purl", parentUrl)
  const cities = Digit.Hooks.pgr.useTenants();
  const { t } = useTranslation();
  const [issueType, setIssueType] = useState({});
  const [linkedIssues, setLinkedIssues]=useState();
  const [impact, setImpact]=useState();
 
  const [priorityLevel, setPriorityLevel]=useState({});
  const [urgency, setUrgency]=useState();
  const [dueDate, setDueDate]=useState({});
  const [requestType, setRequestType]=useState();
  const [envType, setEnvType]=useState();
  const [file, setFile]=useState();
  let reporterName = JSON.parse(sessionStorage.getItem("Digit.User"))?.value?.info?.name;
  
  let n1="hii"
  //const [reporterName, setReporterName]=useState(name|| "");
  //console.log("rn", reporterName)
  const [canSubmit, setSubmitValve] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const [params, setParams] = useState({});
  const tenantId = window.Digit.SessionStorage.get("Employee.tenantId");
  const userName=window.Digit.SessionStorage.get("user_type");
  
  
  const menu = [
    {
      "name": "BUG",
      "code": "BUG",
    },
    {
      "name": "Task",
      "code": "Task",
    },
    {
      "name": "Epic",
      "code": "Epic",
    }
  ]
  const  priorityMenu= 
  [
    {
      "name": "LOW",
      "code": "LOW",
      "active": true
    },
    {
      "name": "MEDIUM",
      "code": "MEDIUM",
      "active": true
    },
    {
      "name": "HIGH",
      "code": "HIGH",
      "active": true
    }

  ]
   const RequestMenu=[
    {
      "name":"IT Hardware",
      "code":"IT Hardware"
    },
    {
      "name":"IT Software",
      "code":"IT Software"
    },
    {
      "name":"payroll related",
      "code":"payroll related"
    }
   ]
   const envMenu=[
    {
      "name":"Test",
      "code":"Test"
    },
    {
      "name":"prod",
      "code":"prod"
    }
   ]
   const linkedMenu=[
    {
      "name":"p1",
      "code":"p1"
    },
    {
      "name":"p2",
      "code":"p2"
    }
   ]
   // Urgency: Critical, High, Medium, Low, Lowest.
   const urgencyMenu=[
    {
      "name":"Critical",
      "code":"Critical"
    },
    {
      "name":"High",
      "code":"High"
    },
    {
      "name":"Medium",
      "code":"Medium"
    },
    {
      "name":"Low",
      "code":"Low"
    },
    {
      
        "name":"Lowest",
        "code":"Lowest"
     
    }


   ]
  //  Impact: Extensive, Significant, Moderate, Minor.
   const ImpactMenu=[
    {
      "name":"Moderate",
      "code":"Moderate"
    },
    {
      "name":"Minor",
      "code":"Minor",
    },
    {
      "name":"Extensive",
      "code":"Extensive",
    },
    {
      "name":"Significant",
      "code":"Significant",
    }
   ]
  const dispatch = useDispatch();
  const match = useRouteMatch();
  const history = useHistory();
  
  const client = useQueryClient();
  console.log("req", requestType)
  useEffect(()=>{
    if( issueType.code && requestType.code && priorityLevel.code && urgency.code && impact.code){
      setSubmitValve(true);
    }else{
      setSubmitValve(false)
    }
  },[priorityLevel])
  
  async function selectedPriorityLevel(value){
    sessionStorage.setItem("priorityLevel", JSON.stringify(value))
    setPriorityLevel(value);
    
  }
  async function selectedIssueType(value){
    
    setIssueType(value);
    
  }
  async function selectedImpact(value){
    
    setImpact(value);
    
  }
  console.log("issuety", issueType)
  async function selectedRequestType(value){
    
    setRequestType(value);
    
  }
  async function selectedDueDate(value){
    setDueDate(value);
  }

  async function selectedEnvType(value){
    setEnvType(value);
  }

  // const selectedReporterName=(value)=>{
  //   console.log("val", value)
  //   setReporterName(value);
  // }

  async function selectedLinkedissues(value){
    setLinkedIssues(value);
  }

  async function selectFile(ids){
    setFile(ids);
  }

  async function selectedUrgency(value){
    setUrgency(value);
  }

  // async function selectedImpact(value){
  //   setStatus(value);
  // }

  const wrapperSubmit = (data) => {
    if (!canSubmit) return;
    setSubmitted(true);
    !submitted && onSubmit(data);
  };

  //On SUbmit
  const onSubmit = async (data) => {
    console.log("data2", data)
    if (!canSubmit) return;
    
    const formData = { ...data, priorityLevel, issueType, requestType, envType, impact, urgency, dueDate};
    console.log("formdat", formData)
    await dispatch(createComplaint(formData));
    await client.refetchQueries(["fetchInboxData"]);
    history.push(parentUrl + "/response");
  };

  
  const config = [
    
    {
      head: t("INCIDENT_DETAILS"),
      body: [
        {
          label :t("INCIDENT_ISSUE_TYPE"),
          type: "dropdown",
          isMandatory:true,
          populators: (
           <Dropdown option={menu} optionKey="name" id="issueType" selected={issueType} select={selectedIssueType} />
           
          ), 
           
         },
        
        {
          label:t("INCIDENT_PRIORITY_LEVEL"),
          isMandatory:true,
          type: "dropdown",
             populators: (
              <Dropdown option={priorityMenu} optionKey="name" id="priorityLevel" selected={priorityLevel} select={selectedPriorityLevel} 
             />
             
             )
         },
         {
          label:t("INCIDENT_REQUEST_TYPE"),
          isMandatory:true,
          type: "dropdown",
          populators: (
            <Dropdown option={RequestMenu} optionKey="name" id="requestType" selected={requestType} select={selectedRequestType} />
            
          ),
           
         },
         {
          label:t("INCIDENT_ENVIRONMENT_TYPE"),
          isMandatory:true,
          type: "dropdown",
          populators: (
            <Dropdown option={envMenu} optionKey="name" id="envType" selected={envType} select={selectedEnvType} />
             
          ),
           
         },
         {
          label:t("INCIDENT_DUE_DATE"),
          type: "date",
          populators: (
            <DatePicker  optionKey="date"  date={dueDate} onChange={selectedDueDate} />
  
          ),
           
         },
         {
          label:t("INCIDENT_REPORTER_NAME"),
          type: "text",
          populators: (
            {
            name: "reporterName",
            defaultValue: reporterName,
            disable:true
          }),
        },
        {
          label: t("INCIDENT_IMPACT"),
          type: "dropdown",
          isMandatory:true,
          populators: (
            <Dropdown option={ImpactMenu} optionKey="name"  selected={impact} select={selectedImpact} />
             
          ), 
         },
         {
          label:t("INCIDENT_URGENCY"),
          isMandatory:true,
          type: "dropdown",
          populators: (
           
          <Dropdown  option={urgencyMenu} optionKey="name" selected={urgency} select={selectedUrgency} />
          
          ),
           
         },
         {
          label: t("INCIDENT_LINKED_ISSUES"),
          type: "text",
          isMandatory:true,
          populators: {
            name: "linkedIssues",
            
          },
        },
        {
          label: t("INCIDENT_AFFECTED_SERVICES"),
          type: "text",
          isMandatory:true,
          populators: {
            name: "affectedServices",
            
          },
        },
        //  {
        //   label:t("INCIDENT_IMPACT"),
        //   type: "text",
        //   populators: (
            
              
        //       <TextInput value={impact} onChange={selectedImpact}  /> 
            
        //   ),  
        // },
        {
          label: t("INCIDENT_DESCRIPTION"),
          type: "text",
          isMandatory:true,
          populators: {
            name: "description",
            
          },
        },
        {
          label: t("INCIDENT_SUMMARY"),
          type: "text",
          isMandatory:true,
          populators: {
            name: "summary",
            
          },
        },
        {
          label:t("INCIDENT_UPLOAD_FILE"),
          populators:
          <UploadFile 
              id={"doc"} 
              accept=".jpeg" 
              onUpload={selectFile} 
              onDelete={()=>{setFile(null)}} 
              message={file? `1 ${t(`ACTION_FILEUPLOADED`)}` : t(`ACTION_NO_FILEUPLOADED`)}
          />,   
         },
         
      ],
    },
  ];
  return (
    <FormComposer
      heading={t("ES_CREATE_INCIDENT")}
      config={config}
      onSubmit={wrapperSubmit}
      isDisabled={!canSubmit && !submitted}
      label={t("FILE_INCIDENT")}
    />
  );
};

// IT Hardware
// IT Software
// Payroll related issue
// Deepika Arora (IN)

// Urgency: Critical, High, Medium, Low, Lowest. 



// IT Hardware
// IT Software
// Payroll related issue

//  Impact: Extensive, Significant, Moderate, Minor.
// Request Type: IT Hardware
// IT Software
// Payroll related issue
