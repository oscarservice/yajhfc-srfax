package yajhfc.phonebook.srfax;
/*
 * YAJHFC - Yet another Java Hylafax client
 * Copyright (C) 2006 Jonas Wolz
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *  
 *  Linking YajHFC statically or dynamically with other modules is making 
 *  a combined work based on YajHFC. Thus, the terms and conditions of 
 *  the GNU General Public License cover the whole combination.
 *  In addition, as a special exception, the copyright holders of YajHFC 
 *  give you permission to combine YajHFC with modules that are loaded using
 *  the YajHFC plugin interface as long as such plugins do not attempt to
 *  change the application's name (for example they may not change the main window title bar 
 *  and may not replace or change the About dialog).
 *  You may copy and distribute such a system following the terms of the
 *  GNU GPL for YajHFC and the licenses of the other code concerned,
 *  provided that you include the source code of that other code when 
 *  and as the GNU GPL requires distribution of source code.
 *  
 *  Note that people who make modified versions of YajHFC are not obligated to grant 
 *  this special exception for their modified versions; it is their choice whether to do so.
 *  The GNU General Public License gives permission to release a modified 
 *  version without this exception; this exception also makes it possible 
 *  to release a modified version which carries forward this exception.
 */

import static yajhfc.Utils._;

import java.awt.Dialog;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.swing.JOptionPane;

import com.google.gson.JsonObject;

import yajhfc.Utils;
import yajhfc.phonebook.AbstractConnectionSettings;
import yajhfc.phonebook.DistributionList;
import yajhfc.phonebook.GeneralConnectionSettings;
import yajhfc.phonebook.GeneralConnectionSettings.PBEntrySettingsField;
import yajhfc.phonebook.srfax.SRFaxPhoneBookEntry;
import yajhfc.phonebook.srfax.ConnectionDialog;
import yajhfc.phonebook.srfax.ConnectionSettings;
import yajhfc.phonebook.PBEntryField;
import yajhfc.phonebook.PhoneBook;
import yajhfc.phonebook.PhoneBookEntry;
import yajhfc.phonebook.PhoneBookException;
import yajhfc.phonebook.WrapperDistributionList;
import yajhfc.plugin.PluginManager;
import yajhfc.util.ExceptionDialog;
import yajhfc.util.PasswordDialog;
import yajhfc.util.SRFaxAPI;

public class SRFaxPhoneBook extends PhoneBook {
    private static final Logger log = Logger.getLogger(SRFaxPhoneBook.class.getName());
    
    ConnectionSettings settings;
    boolean open = false;
    protected boolean wasChanged = false;
    
    List<SRFaxPhoneBookEntry> items = new ArrayList<SRFaxPhoneBookEntry>();
    List<SRFaxPhoneBookEntry> deleted_items = new ArrayList<SRFaxPhoneBookEntry>();
    List<PhoneBookEntry> itemsView = new ArrayList<PhoneBookEntry>();
    
    int[] maxLength = new int[PBEntryField.FIELD_COUNT];
    
    protected static final Map<String,ConnectionDialog.FieldMapEntry> fieldNameMap = new HashMap<String,ConnectionDialog.FieldMapEntry>(); 
    static {
//        PBEntrySettingsField[] fields = GeneralConnectionSettings.entryFields;
//        for (int i = 0; i < fields.length; i++) {
//            PBEntrySettingsField field = fields[i];
//            fieldNameMap.put(field.getName(), new ConnectionDialog.FieldMapEntry(field.getField().getDescription()+":", i));
//        }
//        fieldNameMap.put("givenName", new ConnectionDialog.FieldMapEntry(Utils._("Given name:"),0));
//        fieldNameMap.put("name", new ConnectionDialog.FieldMapEntry(Utils._("Name:"),1));
//        fieldNameMap.put("title", new ConnectionDialog.FieldMapEntry(Utils. _("Title:"),2));
//        fieldNameMap.put("company", new ConnectionDialog.FieldMapEntry(Utils._("Company:"),3));
//        fieldNameMap.put("location", new ConnectionDialog.FieldMapEntry(Utils._("Location:"),4));
//        fieldNameMap.put("faxNumber", new ConnectionDialog.FieldMapEntry(Utils._("Fax number:"),5));
//        fieldNameMap.put("voiceNumber", new ConnectionDialog.FieldMapEntry(Utils._("Voice number:"),6));
//        fieldNameMap.put("comment", new ConnectionDialog.FieldMapEntry(Utils._("Comments:"),7));
        
//        fieldNameMap.put("readOnly", new ConnectionDialog.FieldMapEntry(Utils._("Open as read only"),0,false,Boolean.class));
        fieldNameMap.put("displayCaption", new ConnectionDialog.FieldMapEntry(Utils._("Phone book name to display:"),2,false,String.class));
//        fieldNameMap.put("allowDistLists", new ConnectionDialog.FieldMapEntry(Utils._("Allow distribution list entries"),1,false,Boolean.class));
    }
    
    public static final String PB_Prefix = "SRFax";      // The prefix of this Phonebook type's descriptor
    public static final String PB_DisplayName = Utils._("SRFax phone book"); // A user-readable name for this Phonebook type
    public static final String PB_Description = Utils._("The phone book in your SRFax account."); // A user-readable description of this Phonebook type
    
    public SRFaxPhoneBook(Dialog parent) {
        super(parent);
        
    }

    @Override
    public PhoneBookEntry addNewEntry() {
    	String[] values = {"YajHFC","","00000000000"};
    	values[1] = JOptionPane.showInputDialog(parentDialog, Utils._("Contact Name:"));
    	values[2] = JOptionPane.showInputDialog(parentDialog, Utils._("Fax number (11 digits):"));
    	SRFaxPhoneBookEntry entry = new SRFaxPhoneBookEntry(this, values);
    	
    	items.add(entry);
        fireEntriesAdded(items.size()-1, entry);
        wasChanged = true;
        return entry;
    }
    
    @Override
    public String browseForPhoneBook(boolean exportMode) {
        ConnectionSettings cs = new ConnectionSettings(settings);
        ConnectionDialog cDlg = new ConnectionDialog(parentDialog, Utils._("New SRFax phone book"),
                fieldNameMap, true);
        if (cDlg.promptForNewSettings(cs))
            return PB_Prefix + ":" + cs.saveToString();
        else
            return null;
    }
    
    @Override
    public void close() {        
    	for(int i = 0; i < items.size(); i++){
    		SRFaxPhoneBookEntry row = items.get(i);
    		if(row.dirty){
    			//try delete old
    			try{
	    			JsonObject json = SRFaxAPI.sendPost("Remove_From_AddressBook&sFaxNumber="+row.columnData[2]+"&sGroupName="+row.columnData[0], settings.user, settings.pwd.getPassword());
	    			if (json.get("Status").getAsString().compareTo("Success")!=0){
	    				//JOptionPane.showMessageDialog(parentDialog, json.get("Result").getAsString(), Utils._("Error"), JOptionPane.ERROR_MESSAGE);
	    	        }
    			}catch(Exception e){
    				
    			}
    			
    			try{
    				JsonObject json = SRFaxAPI.sendPost("Add_To_AddressBook&sFaxNumber="+row.columnData[2]+"&sGroupName="+row.columnData[0]+"&sContactName="+row.columnData[1], settings.user, settings.pwd.getPassword());
	    			if (json.get("Status").getAsString().compareTo("Success")!=0){
	    				JOptionPane.showMessageDialog(parentDialog, json.get("Result").getAsString(), Utils._("Error"), JOptionPane.ERROR_MESSAGE);
	    	        }
    			}catch(Exception e){
    				
    			}
    		}
    	}
    	
    }
    
    
    @Override
    protected void openInternal(String descriptorWithoutPrefix) throws PhoneBookException {
        settings = new ConnectionSettings(descriptorWithoutPrefix);
        
        JsonObject tempJson = null;
        try{
        	tempJson = SRFaxAPI.sendPost("Get_Address_Book", settings.user, settings.pwd.getPassword());
        }catch(Exception e){
        	return;
        }
        
		if (tempJson.get("Status").getAsString().compareTo("Success")==0){
        	String book = tempJson.get("Result").getAsString();
        	loadItems(book);
        	open = true;
        	return;
        } else {
        	//throw new IOException(tempJson.get("Result").getAsString());
        	//JOptionPane.showMessageDialog(this, tempJson.get("Result").getAsString(), _("Error"), JOptionPane.ERROR_MESSAGE);
        	return;
        }
    }
    
    protected void loadItems(String book){
        deleted_items.clear();
        items.clear();
        itemsView.clear();
        
        if(book.isEmpty()) return;
        
        String[] rows = book.split("\\\\r\\\\n");
    	for(int i = 0; i < rows.length; i++){
    		SRFaxPhoneBookEntry e = new SRFaxPhoneBookEntry(this, rows[i].replaceAll("^\"|\"$", "").split("\"\\|\"")); //metacharacter...
    		items.add(e);
    		itemsView.add(e);
    	}
    }
    
    void updatePosition(SRFaxPhoneBookEntry entry) {
//        int oldpos = Utils.identityIndexOf(items, entry);
//        items.remove(oldpos);
//        PhoneBookEntry oldView = itemsView.remove(oldpos);
//        
//        int pos = getInsertionPos(oldView, itemsView);
//        
//        items.add(pos, entry);
//        itemsView.add(pos, oldView);
//        
//        fireEntriesChanged(eventObjectForInterval(oldpos, pos));
        
        int pos = Utils.identityIndexOf(items, entry);
        fireEntriesChanged(pos, entry);
    }
    
    void deleteEntry(SRFaxPhoneBookEntry entry) {
        int pos = Utils.identityIndexOf(items, entry);
        if (pos >= 0) {
        	try{
        		JsonObject resp = SRFaxAPI.sendPost("Remove_From_AddressBook&sFaxNumber="+entry.getField(PBEntryField.FaxNumber), settings.user, settings.pwd.getPassword());
        		if (resp.get("Status").getAsString().compareTo("Success")!=0){
                	//JOptionPane.showMessageDialog(parentDialog, resp.get("Result").getAsString(), Utils._("Error"), JOptionPane.ERROR_MESSAGE);
                	//return;
                }
        	}catch(Exception e){
        		
        	}
        	
            
        	
        	items.remove(pos);
            //itemsView.remove(pos);
        
            fireEntriesRemoved(pos, entry);
        }
    }
    
    void writeEntry(PhoneBookEntry entry) {
        int pos = Utils.identityIndexOf(items, entry);
        fireEntriesChanged(pos, entry);
        wasChanged = true;
    }

    @Override
    public List<PhoneBookEntry> getEntries() {
        return itemsView;
    }

    @Override
    public String getDisplayCaption() {
    	if (settings.displayCaption != null && settings.displayCaption.length() > 0) {
            return settings.displayCaption;
        } else {
        	return "[SRFax] "+settings.user;
        }
    }
    
    @Override
    public boolean isFieldAvailable(PBEntryField field) {
    	switch(field){
		case FaxNumber:
			return true;
		case GivenName:
			return true;
		default:
			return false;
		}
    }
    
    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }
    
    @Override
    public boolean supportsDistributionLists() {
        return false;
    }
    
}
