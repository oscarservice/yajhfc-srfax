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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import yajhfc.Utils;
import yajhfc.phonebook.DefaultPhoneBookEntry;
import yajhfc.phonebook.PBEntryField;
import yajhfc.phonebook.PhoneBook;
import yajhfc.phonebook.SimplePhoneBookEntry;
import yajhfc.phonebook.srfax.ConnectionSettings;

public class SRFaxPhoneBookEntry extends DefaultPhoneBookEntry {
    private static final Logger log = Logger.getLogger(SRFaxPhoneBookEntry.class.getName());
    
    protected SRFaxPhoneBook parent;
    protected String[] columnData;
    protected boolean dirty = false;
    
    static final int ENTRY_NOTINSERTED = 1;
    static final int ENTRY_UNCHANGED = 2;
    static final int ENTRY_CHANGED = 3;
    static final int ENTRY_DELETED = 4;
    
    
    SRFaxPhoneBookEntry(SRFaxPhoneBook parent, String[] columnData) {
    	super();
    	this.parent = parent;
        this.columnData = columnData;
    }

    @Override
    public void delete() {
        parent.deleteEntry(this);
    }

    @Override
    public PhoneBook getParent() {
        return parent;
    }

	@Override
	public String getField(PBEntryField field) {
		switch(field){
		case FaxNumber:
			return columnData[2];
		case GivenName:
			return columnData[1];
		default:
			return "";
		}
	}
	
	@Override
	public void setField(PBEntryField field, String value) {
		int ordinal = -1;
		switch(field){
		case FaxNumber:
			ordinal = 2; //columnData[2] = value;
			break;
		case GivenName:
			ordinal = 1; //columnData[1] = value;
			break;
		default:
			break;
		}
		
		if(ordinal!=-1){
			if(!value.equals(columnData[ordinal])){
				dirty = true;
				columnData[ordinal] = value;
			}
		}
		
	}

	@Override
    public void commit() {
        if (dirty) {
            parent.writeEntry(this);
        }
    }

}
