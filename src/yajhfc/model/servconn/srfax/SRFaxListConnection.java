package yajhfc.model.servconn.srfax;

import static yajhfc.Utils._;

import java.io.IOException;
import java.util.TimerTask;
import javax.swing.JOptionPane;

import com.google.gson.JsonObject;

import gnu.hylafax.HylaFAXClient;
import yajhfc.HylaClientManager;
import yajhfc.model.JobFormat;
import yajhfc.model.RecvFormat;
import yajhfc.model.TableType;
import yajhfc.model.servconn.ConnectionState;
import yajhfc.model.servconn.FaxListConnectionListener.RefreshKind;
import yajhfc.model.servconn.hylafax.HylaFaxListConnection;
import yajhfc.model.servconn.hylafax.ManagedFaxJobList;
import yajhfc.server.ServerOptions;
import yajhfc.ui.YajOptionPane;
import yajhfc.util.SRFaxAPI;

public class SRFaxListConnection extends HylaFaxListConnection {
	
	public SRFaxListConnection(ServerOptions fo, YajOptionPane dialogUI) {
		super(fo, dialogUI);
	}

	@Override
	public boolean connect(boolean adminMode) {
		setStatusText(_("Connecting..."));
		//SRFaxSentIds.getInstance().ids.clear();
		
		try {
			//Test SRFax connection
			JsonObject tempJson = SRFaxAPI.sendPost("Get_Fax_Usage", fo.user, fo.pass.getPassword());
			
			if (tempJson.get("Status").getAsString().compareTo("Success")==0){
				
			} else {
				//throw new IOException(tempJson.get("Result").getAsString());
				dialogUI.showMessageDialog(tempJson.get("Result").getAsString(), _("Test connection"), JOptionPane.INFORMATION_MESSAGE);
			}
			
		} catch (Exception e) {
			dialogUI.showExceptionDialog(e.getClass().getSimpleName(), e);
			setConnectionState(ConnectionState.DISCONNECTED);
			return false;
		}
		
		setConnectionState(ConnectionState.CONNECTED);
		setStatusText(_("Connected")+" "+fo.SRFaxNumber);
		
		refreshTimer.schedule(jobRefresher = createJobListRefresher(), 
                 0, 60000);

		return true;
	}	
	
	@Override
    protected TimerTask createJobListRefresher() {
        return new SRFaxJobListRefresher();
    }
    
	@Override
	protected ManagedFaxJobList<RecvFormat> createRecvdList() {
		return new SRFaxJobList<RecvFormat>(fo.getParent().recvfmt, this, TableType.RECEIVED);
	}

	@Override
	public ManagedFaxJobList<JobFormat> createSendingList() {
		return new SRFaxJobList<JobFormat>(fo.getParent().sendingfmt, this, TableType.SENDING);
	}

	@Override
	public ManagedFaxJobList<JobFormat> createSentList() {
		return new SRFaxJobList<JobFormat>(fo.getParent().sentfmt, this, TableType.SENT);
	}

	@Override
    public HylaClientManager getClientManager() {
        return null;
    }
    
	@Override
	public HylaFAXClient beginServerTransaction() throws IOException {
		return null;
	}
	
	@Override
	public void endServerTransaction() {

	}
    
	@Override
    public void beginMultiOperation() throws IOException {
        beginServerTransaction();
    }
    
	@Override
    public void endMultiOperation() {
        endServerTransaction();
    }
    
	
	@Override
	public void refreshFaxLists() {
		if (receivedJobs != null) {
			try {
				receivedJobs.pollForNewJobs(null);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		if (sentJobs != null) {
			try {
				sentJobs.pollForNewJobs(null);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		if (sendingJobs != null) {
			try {
				sendingJobs.pollForNewJobs(null);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		fireRefreshComplete(RefreshKind.FAXLISTS, true); 
    }
	
	@Override
	public void setOptions(ServerOptions so) {
		super.setOptions(so);
	}
	
    class SRFaxJobListRefresher extends TimerTask {
        public synchronized void run() {
        	refreshFaxLists();
        }
        
        @Override
        public boolean cancel() {
            return super.cancel();
        }
    }
    
    @Override
    protected void saveToCache() {
    	
    }
    
    @Override
    protected void loadFromCache() {
    	
    }
}
