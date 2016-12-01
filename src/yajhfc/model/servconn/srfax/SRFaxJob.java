package yajhfc.model.servconn.srfax;

import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import com.google.gson.JsonObject;

import gnu.inet.ftp.ServerResponseException;
import yajhfc.file.FileFormat;
import yajhfc.model.FmtItem;
import yajhfc.model.JobFormat;
import yajhfc.model.RecvFormat;
import yajhfc.model.TableType;
import yajhfc.model.VirtualColumnType;
import yajhfc.model.jobq.HylaDirAccessor;
import yajhfc.model.servconn.FaxDocument;
import yajhfc.model.servconn.JobState;
import yajhfc.model.servconn.defimpl.AbstractFaxJob;
import yajhfc.server.ServerOptions;
import yajhfc.util.SRFaxAPI;

public class SRFaxJob<T extends FmtItem> extends AbstractFaxJob<T>  {
	private static final long serialVersionUID = 1;
	static final Logger log = Logger.getLogger(SRFaxJob.class.getName());

	protected final String jobID;
	private JsonObject parameters;
	private TableType tableType;

	protected SRFaxJob(SRFaxJobList<T> parent, JsonObject parameters, TableType tableType) throws IOException {
		super(parent);
//		if(tableType == TableType.SENDING){
//			this.jobID = parameters.get("FaxFileName").getAsString();
//		}else{
//			this.jobID = parameters.get("FileName").getAsString();
//		}
		
		this.jobID = parameters.get("FileName").getAsString();
		this.parameters = parameters;
		this.tableType = tableType;

		this.documents = new ArrayList<FaxDocument>();
		this.documents.clear();
		this.documents.add(new SRFaxDoc<T>(this, this.parameters, FileFormat.PDF, tableType));
	}

	public void delete() throws IOException, ServerResponseException {
		if(tableType==TableType.SENDING){
			suspend();
			return;
		}
		
		JsonObject json = null;
		String detailsID = jobID.substring(jobID.indexOf("|")+1);
		json = SRFaxAPI.sendPost("Delete_Fax&sFaxFileName_0="+jobID+"&sFaxDetailsID_0="+detailsID+"&sDirection="+((tableType==TableType.RECEIVED)?"IN":"OUT"), this.parent.getParent().getOptions().user, this.parent.getParent().getOptions().pass.getPassword());
		
		if(json.get("Status").getAsString().compareTo("Failed")==0){
			throw new ServerResponseException(json.get("Result").getAsString());
		}
		//System.out.println(json);
	}

	public boolean pollForChanges() throws IOException {
		readInbox();
		return true;
	}

	protected void readInbox() throws IOException {

		if (tableType == TableType.RECEIVED) {
			List<RecvFormat> desiredCols = (List<RecvFormat>) parent.getColumns().getCompleteView();

			Object[] resultData = new Object[desiredCols.size()];
			for (int i = 0; i < desiredCols.size(); i++) {
				switch (desiredCols.get(i)) {
				case a: 
					resultData[i] = "";
					break;
				case b:
					resultData[i] = Integer.valueOf(-1);
					break;
				case d:
					resultData[i] = "PDF";
					break;
				case e:
					resultData[i] = "";
					break;
				case f:
					resultData[i] = parameters.get("FileName").getAsString();
					break;
				case h:
					resultData[i] = null;
					break;
				case i:
					resultData[i] = "";
					break;
				case j:
					resultData[i] = parameters.get("CallerID").getAsString();
					break;
				case l:
					resultData[i] = Integer.valueOf(-1);
					break;
				case m:
					//case q:
					resultData[i] = "-r--r--";//RecvQFaxJob.getProtection(hyda.getProtection(fileName));
					break;
				case n:
					resultData[i] = parameters.get("Size").getAsInt();
					break;
				case o:
					resultData[i] = parameters.get("User_FaxNumber").isJsonNull()? "": parameters.get("User_FaxNumber").getAsString();
					break;
				case p:
					resultData[i] = parameters.get("Pages").getAsInt();
					break;
				case r:
					resultData[i] = Integer.valueOf(-1);
					break;
				case s:
					resultData[i] = parameters.get("CallerID").getAsString()+" / "+parameters.get("RemoteID").getAsString();
					break;
				case t:
					resultData[i] = DateFormat.getDateTimeInstance().format(parameters.get("EpochTime").getAsLong()*1000);
					break;
				case w:
					resultData[i] = Integer.valueOf(1);
					break;
				case z:
					resultData[i] = Boolean.FALSE;
					break;
				case Y:
				case Z:
					resultData[i] = parameters.get("EpochTime").getAsLong()*1000;
					break;
//				case virt_read:
//					resultData[i] = parameters.get("ViewedStatus").getAsString().compareTo("Y")==0; //useless
//					break;
				}
			}
			data = resultData;
		} else if (tableType == TableType.SENT || tableType == TableType.SENDING) {
			List<JobFormat> desiredCols = (List<JobFormat>) parent.getColumns().getCompleteView();

			Object[] resultData = new Object[desiredCols.size()];
			for (int i = 0; i < desiredCols.size(); i++) {
				switch (desiredCols.get(i)) {
				case j: 
					//if(tableType == TableType.SENT){
						resultData[i] = parameters.get("FileName").getAsString();
					//}else{
					//	resultData[i] = parameters.get("FaxFileName").getAsString();
					//}
					break;
				case s:
					if(tableType == TableType.SENT){
						resultData[i] = (parameters.get("SentStatus").getAsString().compareTo("Sent") == 0) ? "Sent" : parameters.get("ErrorCode").getAsString();
					}else{
						resultData[i] = parameters.get("Status").getAsString();
					}
					break;
				case z:
					if(tableType == TableType.SENT){
						resultData[i] = parameters.get("Duration").getAsString();
					}else{
						resultData[i] = "";
					}
					break;
				case e:
					resultData[i] = parameters.get("ToFaxNumber").getAsString();
					break;
				case R:
					try{
						resultData[i] = parameters.get("ToFaxNumber").getAsString()+" / "+parameters.get("RemoteID").getAsString();
					}catch(Exception e){
						resultData[i] = ""; //catch when "RemoteID": null
					}
					break;
				case Y:
				case Z:
					//if(tableType == TableType.SENT){
						try{
							resultData[i] = parameters.get("EpochTime").getAsLong()*1000;
						}catch(Exception e){
							resultData[i] = Long.valueOf(-1); //catch when "EpochTime" = false
						}
					//}else{
					//	resultData[i] = System.currentTimeMillis(); //because SRFax does not have "EpochTime" in the new Get_Faxes_Queued API
					//}
					break;
				case M:
					resultData[i] = parent.getParent().getOptions().SRFaxEmail;
					break;
				case o:
				case S:
					if(tableType == TableType.SENT){
						resultData[i] = parameters.get("User_FaxNumber").isJsonNull()? "": parameters.get("User_FaxNumber").getAsString();
					}else{
						resultData[i] = parameters.get("User_ID").getAsString();
					}
					break;
				case p:
				case y:
					resultData[i] = parameters.get("Pages").isJsonNull() ? Integer.valueOf(-1) : parameters.get("Pages").getAsInt();
					break;
				case srfax_subject:
					resultData[i] = parameters.get("Subject").isJsonNull()? "" : parameters.get("Subject").getAsString();
					break;
				case a:
				case n:
				case a_desc:
				case n_desc:
					if(tableType == TableType.SENT){
						resultData[i] = (parameters.get("SentStatus").getAsString().compareTo("Sent") == 0) ? JobState.DONE: (parameters.get("SentStatus").getAsString().compareTo("In Progress") == 0) ? JobState.PENDING : JobState.FAILED;
					}else{
						resultData[i] = JobState.RUNNING;
					}
					break;
				default:
					resultData[i] = Integer.valueOf(-1);
				}
			}
			data = resultData;
		}

		state = JobState.DONE;
	}

	@Override
	protected JobState calculateJobState() {
		return JobState.DONE;
	}

	protected void readSpoolFile(HylaDirAccessor hyda) throws IOException {
		readInbox();
	}

	public void suspend() throws IOException, ServerResponseException {
		JsonObject json = null;
		String detailsID = jobID.substring(jobID.indexOf("|")+1);
		json = SRFaxAPI.sendPost("Stop_Fax&sFaxDetailsID="+detailsID, this.parent.getParent().getOptions().user, this.parent.getParent().getOptions().pass.getPassword());
		
		if(json.get("Status").getAsString().compareTo("Failed")==0){
			throw new ServerResponseException(json.get("Result").getAsString());
		}
	}

	public void resume() throws IOException, ServerResponseException {
		throw new ServerResponseException("SRFax does not support resume operation.");
	}
	
	@Override
	public boolean isRead() {
        return parameters.get("ViewedStatus").getAsString().compareTo("Y")==0;
    }
	
	@Override
	public void setRead(boolean isRead) {
		parameters.remove("ViewedStatus");
		parameters.addProperty("ViewedStatus", "Y");
		
		//set read status
		ServerOptions so = this.parent.getParent().getOptions();
    	String detailsID = jobID.substring(jobID.indexOf("|")+1);
    	try {
			JsonObject json = SRFaxAPI.sendPost("Update_Viewed_Status&sFaxFileName="+jobID+"&sFaxDetailsID="+detailsID+"&sDirection="+((tableType==TableType.RECEIVED)?"IN":"OUT")+"&sMarkasViewed=Y", so.user, so.pass.getPassword());
		} catch (ServerResponseException e) {
		}
		
		setRead(isRead, true);
    }
	
	@Override
	protected List<FaxDocument> calcDocuments() {
		return null;
	}

	@Override
	public Object getIDValue() {
		return jobID;
	}
}
