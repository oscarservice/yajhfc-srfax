package yajhfc.model.servconn.srfax;

import java.io.IOException;
import java.io.OutputStream;
import javax.xml.bind.DatatypeConverter;

import com.google.gson.JsonObject;

import java.io.ByteArrayInputStream;

import gnu.inet.ftp.ServerResponseException;
import yajhfc.Utils;
import yajhfc.file.FileFormat;
import yajhfc.launch.Launcher2;
import yajhfc.model.FmtItem;
import yajhfc.model.TableType;
import yajhfc.model.servconn.defimpl.AbstractFaxDocument;
import yajhfc.server.ServerOptions;
import yajhfc.util.SRFaxAPI;


class SRFaxDoc<T extends FmtItem> extends AbstractFaxDocument<T> {
    private static final long serialVersionUID = 1;
    
    private JsonObject params;
    private TableType tableType;
    
    public SRFaxDoc(SRFaxJob<T> parent, JsonObject parameters,
            FileFormat type, TableType tableType) {
        super(parent, parameters.get("FileName").getAsString(), type);
        this.params = parameters;
        this.tableType = tableType;
    }

    @Override
    protected void downloadFromServer(OutputStream target) throws IOException, ServerResponseException {
    	ServerOptions so = this.parent.getParent().getParent().getOptions();
    	
    	String detailsID = this.params.get("FileName").getAsString().substring(path.indexOf("|")+1);
    	JsonObject json = SRFaxAPI.sendPost("Retrieve_Fax&sFaxFileName="+this.params.get("FileName").getAsString()+"&sFaxDetailsID="+detailsID+"&sDirection="+((tableType==TableType.RECEIVED)?"IN":"OUT")
    			+(this.params.get("User_ID").getAsString().equals(so.user) ? "" : "&sSubUserID="+this.params.get("User_ID").getAsString()), so.user, so.pass.getPassword());
    	String encoded = json.get("Result").getAsString();
    	if(json.get("Status").getAsString().compareTo("Failed")==0){
    		throw new ServerResponseException(encoded); //Launcher2.application.getDialogUI().
    	}

    	byte[] decoded = DatatypeConverter.parseBase64Binary(encoded.replaceAll("\\n", ""));

    	ByteArrayInputStream in = new ByteArrayInputStream(decoded);
    	Utils.copyStream(in, target);
    }
}
