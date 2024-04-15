import { CREATE_COMPLAINT } from "./types";

const createComplaint = ({
  cityCode,
  complaintType,
  description,
  issueType,
  priorityLevel,
  impact,
  dueDate,
  urgency,
  reporterName,
  
  summary,
  affectedServices,
  linkedIssues,
  envType,
  requestType,
  landmark,
  city,
  district,
  region,
  state,
  pincode,
  localityCode,
  localityName,
  uploadedImages,
  mobileNumber,
  name,
}) => async (dispatch, getState) => {
  const response = await Digit.Complaint.create({
    
    
    issueType,
    reporterName,
    envType,
    requestType,
    description,
    priorityLevel,
    impact,
    urgency,
    summary,
    affectedServices,
    linkedIssues,
    dueDate,
    
    
  });
  console.log("res", response)
  dispatch({
    type: CREATE_COMPLAINT,
    payload: response,
  });
};

export default createComplaint;
