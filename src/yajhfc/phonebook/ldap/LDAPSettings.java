package yajhfc.phonebook.ldap;
/*
 * YAJHFC - Yet another Java Hylafax client
 * Copyright (C) 2007 Jonas Wolz
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

import java.util.logging.Logger;

import yajhfc.Password;
import yajhfc.Utils;
import yajhfc.phonebook.GeneralConnectionSettings;
import yajhfc.phonebook.PBEntryField;


public class LDAPSettings extends GeneralConnectionSettings {
    public String serverName = ""; //"suse101-vmw";
    public int port = 389;
    public String baseDN = ""; //"o=Scalix";
    public boolean useAuth = false;
    public String bindDN = "";
    public final Password credential = new Password();
    public boolean askForCredential = false;
    public boolean initiallyLoadAll = true;
    public int countLimit = 1000;
    
    public String objectFilter = "(facsimileTelephoneNumber=*)";
    public boolean searchSubTree = true;
    
    public String displayCaption = "";
    
    public String[] getAttributes() {
        int count = 0;
        final PBEntryField[] values =  PBEntryField.values();
        for (PBEntryField field : values) 
            if (!isNoField(getMappingFor(field)))
                count++;
        
        String[] rv = new String[count];
        int j = 0;
        for (PBEntryField field : values) {
            String mapping = getMappingFor(field);
            if (!isNoField(mapping)) 
                rv[j++] = mapping;
        }
        
        return rv;
    }
    
    public LDAPSettings() {
        super("");
        setMappingFor(PBEntryField.Name, "sn");
        setMappingFor(PBEntryField.GivenName,"givenName");
        setMappingFor(PBEntryField.Location, "l");
        setMappingFor(PBEntryField.Position, "title");
        setMappingFor(PBEntryField.Company, "o");
        setMappingFor(PBEntryField.VoiceNumber, "telephoneNumber");
        setMappingFor(PBEntryField.FaxNumber, "facsimileTelephoneNumber");
        setMappingFor(PBEntryField.Comment, "description");
        
        setMappingFor(PBEntryField.Department, "ou");
        setMappingFor(PBEntryField.EMailAddress, "mail");
        setMappingFor(PBEntryField.Street, "street");
        setMappingFor(PBEntryField.State, "st");
        setMappingFor(PBEntryField.ZIPCode, "postalCode");
        setMappingFor(PBEntryField.Country, "c");
        setMappingFor(PBEntryField.WebSite, "URL");
    }
    
    public LDAPSettings(String serialized) {
        this();
        loadFromString(serialized);
    }
    public LDAPSettings(LDAPSettings src) {
        this();
        copyFrom(src);
    }


    // Parser states:
    private static final int STATE_NORMAL = 0;
    private static final int STATE_INQUOTE = 1;
    private static final int STATE_CURCHARESCAPED = 2;
    private static final int STATE_STRIPSPACES = 3;
    /**
     * Strips spaces after , and ; used as separator to work around
     * server bugs.
     * @param dn
     * @return
     */
    public static String sanitizeDN(String dn) {
        StringBuilder rv = new StringBuilder(dn.length());
        int state = STATE_NORMAL;

        // Simple "parser" to detect commas inside quotes
        // and escaped commas (i.e. \, and \;)
        for (int i=0; i < dn.length(); i++) {
            char c = dn.charAt(i);

            switch (state) {
            case STATE_NORMAL:
            default:
                rv.append(c);

                switch (c) {
                case '\"':
                    state = STATE_INQUOTE;
                    break;
                case '\\':
                    state = STATE_CURCHARESCAPED;
                    break;
                case ',':
                case ';':
                    state = STATE_STRIPSPACES;
                    break;
                default:
                    break;
                }
                break;
            case STATE_INQUOTE:
                rv.append(c);

                if (c=='\"') {
                    state = STATE_NORMAL;
                }
                break;
            case STATE_CURCHARESCAPED:
                rv.append(c);

                state = STATE_NORMAL;
                break;
            case STATE_STRIPSPACES:
                if (!Character.isWhitespace(c)) {
                    rv.append(c);
                    state = STATE_NORMAL;
                }
                break;
            }
        }

        if (Utils.debugMode) {
            Logger.getLogger(LDAPSettings.class.getName()).fine("Changed DN from \"" + dn +  "\" to \"" + rv + "\"");
        }
        return rv.toString();
    }

    // Test code:
//    public static void main(String[] args) {
//        System.out.println(sanitizeDN("ou=test,dc=test,dc=de"));
//        System.out.println(sanitizeDN("ou=test, dc=test,   dc=de"));
//        System.out.println(sanitizeDN("ou=\"test\", dc=test, dc=de"));
//        System.out.println(sanitizeDN("ou=\"test, foo\", dc=test, dc=de"));
//        System.out.println(sanitizeDN("ou=test\\,  bar, dc=test; dc=de"));
//    }
}
