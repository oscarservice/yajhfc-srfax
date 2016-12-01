package yajhfc.model.servconn.srfax;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import gnu.hylafax.HylaFAXClient;
import gnu.inet.ftp.ServerResponseException;
import yajhfc.util.SRFaxAPI;
import yajhfc.model.FmtItem;
import yajhfc.model.FmtItemList;
import yajhfc.model.TableType;
import yajhfc.model.servconn.FaxJob;
import yajhfc.model.servconn.FaxListConnection;
import yajhfc.model.servconn.defimpl.AbstractFaxJobList;
import yajhfc.model.servconn.hylafax.ManagedFaxJobList;
import yajhfc.server.ServerOptions;

public class SRFaxJobList <T extends FmtItem> extends AbstractFaxJobList<T> implements ManagedFaxJobList<T> {

	TableType tableType;
	
	
	protected SRFaxJobList(FmtItemList<T> columns, FaxListConnection parent, TableType tableType) {
		super(columns, parent);
		this.tableType = tableType;
	}

	public TableType getJobType() {
		return this.tableType;
	}

	public void pollForNewJobs(HylaFAXClient hyfc) throws IOException, ServerResponseException {
		setJobs(updateQueueFiles());
	}

	public void reloadSettings(ServerOptions fo) {
	}

	public void disconnectCleanup() {
        setJobs(Collections.<FaxJob<T>>emptyList());
	}
	
	@SuppressWarnings("unchecked")
	public List<FaxJob<T>> updateQueueFiles() throws IOException, ServerResponseException {
		List<FaxJob<T>> resultList = new ArrayList<FaxJob<T>>();
		
		ServerOptions so = this.getParent().getOptions();
		
		String action;
		if (tableType == TableType.RECEIVED) {
			action = "Get_Fax_Inbox&sIncludeSubUsers=Y";
		} else if(tableType == TableType.SENT){
			action = "Get_Fax_Outbox&sIncludeSubUsers=Y";
		} else{
			//Queued jobs
//			if(SRFaxSentIds.getInstance().ids.size()<1){
//				return resultList;
//			}
//			
//			ArrayList<String> ids = new ArrayList<String>();
//			for(int i=0; i<SRFaxSentIds.getInstance().ids.size();i++){
//				String[] parts = StringUtils.split(SRFaxSentIds.getInstance().ids.get(i), "@");
//				if(parts[0].compareTo(so.user)==0){
//					ids.add(parts[1]);
//				}
//			}
//			
//			if(ids.size()<1){
//				return resultList;
//			}
			
			//action = "Get_MultiFaxStatus&sFaxDetailsID="+StringUtils.join(ids, "|");
			action = "Get_Faxes_Queued&sIncludeSubUsers=Y";
		}
		
		//fax period
		if(tableType != TableType.SENDING && so.SRFaxDayRange > 0){
			LocalDateTime now = LocalDateTime.now().plusDays(1);
			LocalDateTime from = now.minusDays(so.SRFaxDayRange+1);
			action += "&sPeriod=RANGE&sStartDate="+from.format(DateTimeFormatter.ofPattern("yyyyMMdd"))+"&sEndDate="+now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
		}
		
		//read/unread filter
		if(tableType == TableType.RECEIVED){
			if(!so.SRFaxShowRead){
				action += "&sViewedStatus=UNREAD";
			}else if(!so.SRFaxShowUnread){
				action += "&sViewedStatus=READ";
			}
		}
		
		//get data
		JsonObject json;
		JsonArray results = new JsonArray();
		try {
			json = SRFaxAPI.sendPost(action, so.user, so.pass.getPassword());
			if(json.get("Status").getAsString().compareTo("Failed")==0){
				//msg
				return resultList;
			}
			results = json.get("Result").getAsJsonArray();
		} catch (Exception e) {
			throw new ServerResponseException(e.getMessage());
		}
		
		//parse data
		for (int i=0; i < results.size(); i++) {
			JsonObject param = results.get(i).getAsJsonObject();
			
			if(!so.SRFaxIncludeSubUsers){
				if(!so.user.equals(param.get("User_ID").getAsString())){
					continue;
				}
			}
			// Filter tabletype.SENT and tabletype.SENDING
//			if(tableType != TableType.RECEIVED) {
//				// Exception. If we can't connect to SRFax, the code handles the exception here. (Can't find SentStatus)
//				if(param.get("SentStatus").getAsString().compareTo("Sent")==0 && tableType == TableType.SENDING) continue;
//				if(param.get("SentStatus").getAsString().compareTo("Failed")==0 && tableType == TableType.SENDING) continue;
//				if(param.get("SentStatus").getAsString().compareTo("Unknown")==0 && tableType == TableType.SENDING) continue;
//				if(param.get("SentStatus").getAsString().compareTo("In Progress")==0 && tableType == TableType.SENT) continue;
//				if(param.get("SentStatus").getAsString().compareTo("Sending Email")==0 && tableType == TableType.SENT) continue;
//			}
//			if(tableType == TableType.SENDING){
//				if(param.get("SentStatus").getAsString().compareTo("Sent")==0 || param.get("SentStatus").getAsString().compareTo("Failed")==0){
//					//remove from transmitting table
//					String filename = param.get("FileName").getAsString();
//					String id = filename.substring(filename.indexOf("|")+1);
//					SRFaxSentIds.getInstance().ids.remove(so.user+"@"+id);
//					continue;
//				}
//			}
			
			SRFaxJob<T> job = createJob(param);
			job.readInbox();
			resultList.add(job);
		}
		return resultList;
	}
	
	
	
	protected SRFaxJob<T> createJob(JsonObject parameters) throws IOException {
		return new SRFaxJob<T>(this, parameters, this.tableType);
	}
}
