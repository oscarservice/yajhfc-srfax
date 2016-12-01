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
import info.clearthought.layout.TableLayout;
import info.clearthought.layout.TableLayoutConstraints;

import java.awt.Component;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.JTextComponent;

import com.google.gson.JsonObject;

import yajhfc.Utils;
import yajhfc.phonebook.AbstractConnectionSettings;
import yajhfc.plugin.PluginManager;
import yajhfc.util.CancelAction;
import yajhfc.util.ClipboardPopup;
import yajhfc.util.ExceptionDialog;
import yajhfc.util.PasswordDialog;
import yajhfc.util.SRFaxAPI;

public final class ConnectionDialog extends JDialog implements ActionListener {
    private static final Logger log = Logger.getLogger(ConnectionDialog.class.getName());
    
    //private JComboBox comboName, comboGivenName, comboTitle, comboCompany, comboLocation, comboVoiceNumber, comboFaxNumber, comboComment;
    private ArrayList<JComboBox> fieldCombos = new ArrayList<JComboBox>();
    private JTextField textDriverClass, textURL, textUserName/*, textQuery*/;
    private JComboBox comboTable;
    JCheckBox checkAskForPassword;
    JPasswordField textPassword;
    private JButton buttonOK, buttonCancel, buttonTest;
    private boolean noFieldOK;
    
    private final static double border = 10;
    
    public boolean clickedOK;
    
    private Connection conn;
    
    /**
     * A map mapping field names to user visible captions
     */
    private Map<String,FieldMapEntry> fieldNameMap;
    
    private Map<String, Component> settingsComponents = new HashMap<String, Component>();
    private static final String DBURL_FIELD = "dbURL";
    private static final String DRIVER_FIELD = "driver";
    private static final String USER_FIELD = "user";
    private static final String PASSWORD_FIELD = "pwd";
    private static final String TABLE_FIELD = "table";
    private static final String ASKFORPWD_FIELD = "askForPWD";
//    private static final int FIXEDFIELD_COUNT = 6;
    
    private static final String FIELD_ClientProperty = "YajHFC-FieldComboField";
    
    private JComboBox addFieldCombo(JPanel container, String field, String caption, int col, int row) {
        JComboBox rv = new JComboBox();
        rv.setEditable(true);
        rv.putClientProperty(FIELD_ClientProperty, field);
        fieldCombos.add(rv);
        settingsComponents.put(field, rv);
        
        TableLayoutConstraints layout = new TableLayoutConstraints(col, row);
        layout.hAlign = TableLayoutConstraints.FULL;
        layout.vAlign = TableLayoutConstraints.CENTER;
        Utils.addWithLabelHorz(container, rv, caption, layout);
        return rv;
    }
    private JComponent addAdditionalEntryField(JPanel container, String field, FieldMapEntry entry, int col, int row) {
        JComponent result;
        if (entry.dataType.equals(Boolean.class)) {
            result = new JCheckBox(entry.caption);
            container.add(result, new TableLayoutConstraints(col-2,row,col,row,TableLayoutConstraints.LEFT,TableLayoutConstraints.CENTER));
        } else if (entry.dataType.equals(String.class)) {
            result = new JTextField();
            result.addMouseListener(ClipboardPopup.DEFAULT_POPUP);
            Utils.addWithLabelHorz(container, result, entry.caption, new TableLayoutConstraints(col,row,col,row,TableLayoutConstraints.FULL,TableLayoutConstraints.CENTER));
        } else {
            throw new IllegalArgumentException("Unsupported data type for additional field.");
        }
        settingsComponents.put(field, result);
        return result;
    }
    
    private void initialize() {
        //final int varFieldCount = (template.getAvailableFields().size() - FIXEDFIELD_COUNT + 1) / 2;
        //Count the number of "data fields" and additional fields
        int dataFieldCount = 0, addFieldCount = 0;
//        for (FieldMapEntry entry : fieldNameMap.values()) {
//            if (entry.isDataField) {
//                dataFieldCount++;
//            } else {
//                addFieldCount++;
//            }
//        }
        
        final int dataRowCount = ((dataFieldCount + 1) / 2)*2;
        final int addRowCount = ((addFieldCount + 1) / 2)*2;
        final int len = dataRowCount + addRowCount + 18; 
        double dLay[][] = {
                {border, TableLayout.PREFERRED, border, 0.5, border, TableLayout.PREFERRED, border, TableLayout.FILL, border},      
                new double[len]
        };
        double rowh = 1/(double)(len-5)*2;
        for (int i = 0; i < len; i++) {
            if ((i&1) == 0) {
                dLay[1][i] = border;
            } else {
                dLay[1][i] = rowh;
            }
        }
        dLay[1][11+addRowCount] = dLay[1][len - 3] = dLay[1][len-1] = TableLayout.PREFERRED;
        
        TableLayout lay = new TableLayout(dLay);
        JPanel jContentPane = new JPanel(lay);
        
//        textDriverClass = new JTextField();
//        textDriverClass.addMouseListener(ClipboardPopup.DEFAULT_POPUP);
//        
//        textURL = new JTextField();
//        textURL.addMouseListener(ClipboardPopup.DEFAULT_POPUP);
        
        textUserName = new JTextField();
        textUserName.addMouseListener(ClipboardPopup.DEFAULT_POPUP);
        
        textPassword = new JPasswordField();

        //textQuery = new JTextField();
        //textQuery.addMouseListener(clpPop);
//        comboTable = new JComboBox();
//        comboTable.setEditable(true);
//        comboTable.setActionCommand("tablesel");
//        comboTable.addActionListener(this);
        
        checkAskForPassword = new JCheckBox(_("Always ask"));
        checkAskForPassword.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                textPassword.setEnabled(!checkAskForPassword.isSelected());
            }
        });
        
        CancelAction actCancel = new CancelAction(this);
        buttonCancel = actCancel.createCancelButton();
        
        buttonOK = new JButton(_("OK"));
        buttonOK.setActionCommand("ok");
        buttonOK.addActionListener(this);
        buttonTest = new JButton(_("Test connection"));
        buttonTest.setActionCommand("test");
        buttonTest.addActionListener(this);
        
//        Utils.addWithLabelHorz(jContentPane, textDriverClass, _("Driver class:"), "3, 1, 7, 1, f, c");
//        settingsComponents.put(DRIVER_FIELD, textDriverClass);
//        Utils.addWithLabelHorz(jContentPane, textURL, _("Database URL:"), "3, 3, 7, 3, f, c");
//        settingsComponents.put(DBURL_FIELD, textURL);
        Utils.addWithLabelHorz(jContentPane, textUserName, _("SRFax Account Number:"), "3, 1, 5, 1, f, c");
        settingsComponents.put(USER_FIELD, textUserName);
        jContentPane.add(buttonTest, "7, 1");
        Utils.addWithLabelHorz(jContentPane, textPassword, _("SRFax Password:"), "3, 3, 5, 3, f, c");
        settingsComponents.put(PASSWORD_FIELD, textPassword);
        jContentPane.add(checkAskForPassword, "7, 3");
        settingsComponents.put(ASKFORPWD_FIELD, checkAskForPassword);
        //Utils.addWithLabelHorz(jContentPane, textQuery, _("Query:"), "3, 9, 7, 9, f, c");
//        Utils.addWithLabelHorz(jContentPane, comboTable, _("Table:"), "3, 9, 7, 9, f, c");
//        settingsComponents.put(TABLE_FIELD, comboTable);
//        Utils.addWithLabelHorz(jContentPane, textUserName, _("Phone book name to display:"), "3, 1, 5, 1, f, c");
//        settingsComponents.put(USER_FIELD, textUserName);
        
        jContentPane.add(new JSeparator(JSeparator.HORIZONTAL), new TableLayoutConstraints(0, 9, 8, 9));
        //jContentPane.add(new JLabel(fieldPrompt), new TableLayoutConstraints(1, 13+addRowCount, 7, 13+addRowCount, TableLayoutConstraints.FULL, TableLayoutConstraints.CENTER));
//        comboGivenName = addFieldCombo(jContentPane, _("Given name:"), "3, 15, f, c");
//        comboName = addFieldCombo(jContentPane, _("Name:"), "7, 15, f, c");
//        comboTitle = addFieldCombo(jContentPane, _("Title:"), "3, 17, f, c");
//        comboCompany = addFieldCombo(jContentPane, _("Company:"), "7, 17, f, c");
//        comboLocation = addFieldCombo(jContentPane, _("Location:"), "3, 19, f, c");
//        comboVoiceNumber = addFieldCombo(jContentPane, _("Voice number:"), "7, 19, f, c");
//        comboFaxNumber = addFieldCombo(jContentPane, _("Fax number:"), "3, 21, f, c");
//        comboComment = addFieldCombo(jContentPane, _("Comments:"), "7, 21, f, c");
        
        //Add variable fields
        for (Map.Entry<String, FieldMapEntry> entry : fieldNameMap.entrySet()) {
            String field = entry.getKey();
            if (!settingsComponents.containsKey(field)) { // Only add a field once (especially don't add fixed fields here)
                int ordinalPos = entry.getValue().ordinalPosition;
                if (entry.getValue().isDataField) {
                    addFieldCombo(jContentPane, field, entry.getValue().caption, 3 + 4 * (ordinalPos % 2), 15 + addRowCount + 2*(ordinalPos/2));
                } else {
                    addAdditionalEntryField(jContentPane, field, entry.getValue(), 3 + 4 * (ordinalPos % 2), 5 + 2*(ordinalPos/2));
                }
            }
        }
        
        
        Box buttonBox = new Box(BoxLayout.LINE_AXIS);
        buttonBox.add(Box.createHorizontalGlue());
        buttonBox.add(buttonOK);
        buttonBox.add(Box.createHorizontalStrut((int)border));
        buttonBox.add(buttonCancel);
        buttonBox.add(Box.createHorizontalGlue());
        
        //jContentPane.add(new JSeparator(JSeparator.HORIZONTAL), new TableLayoutConstraints(0,len-3,8,len-3));
        jContentPane.add(buttonBox, new TableLayoutConstraints(0,11,8,11));
        
        setContentPane(jContentPane);
        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setModal(true);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                closeConnection();
            }
            
            @Override
            public void windowClosing(WindowEvent e) {
                closeConnection();
            }
        });
        pack();
    }
    
    private boolean inputMaybeValid() {
        if (textUserName.getDocument().getLength() == 0) {
            JOptionPane.showMessageDialog(this, _("You have to specify a SRFax Account Number."), _("Error"), JOptionPane.ERROR_MESSAGE);
            return false;
        }
        if (textPassword.getDocument().getLength() == 0) {
            JOptionPane.showMessageDialog(this, _("You have to specify a SRFax Password."), _("Error"), JOptionPane.ERROR_MESSAGE);
            return false;
        }
        /*if (comboTable.getSelectedItem().toString().length() == 0) {
            JOptionPane.showMessageDialog(this, _("You have to specify a SQL query."), _("Error"), JOptionPane.ERROR_MESSAGE);
            return false;
        }*/
        return true;
    }
    
    private boolean testConnection() {
        if (!inputMaybeValid())
            return false;
        
        try {
            String password;
            if (checkAskForPassword.isSelected()) {
                String[] pwd = PasswordDialog.showPasswordDialog(this, Utils._("Database password"), Utils._("Please enter the SRFax password:"), textUserName.getText(), false);
                if (pwd == null)
                    return false;
                else
                    password = pwd[1];
            } else {
                password = new String(textPassword.getPassword());
            }
            
            JsonObject tempJson = SRFaxAPI.sendPost("Get_Fax_Usage", textUserName.getText(), password);

            if (tempJson.get("Status").getAsString().compareTo("Success")==0){
            	return true;
            } else {
            	//throw new IOException(tempJson.get("Result").getAsString());
            	JOptionPane.showMessageDialog(this, tempJson.get("Result").getAsString(), _("Error"), JOptionPane.ERROR_MESSAGE);
            }
            
        } catch (Exception e) {
            ExceptionDialog.showExceptionDialog(this, _("Could not connect to the SRFax server:"), e);
            return false;
        }

        return false;
    }
    
    void closeConnection() {
        try {
//            if (stmt != null) {
//                stmt.close();
//                stmt = null;
//            }
            if (conn != null) {
                conn.close();
                conn = null;
            }
        } catch (Exception e) {
            //NOP
        }
    }
    
    private boolean loadFieldNames() {
        if (conn == null /*|| stmt == null*/)
            return false;            
        
        try {
            Vector<String> fieldNames;
            if (comboTable.getSelectedItem() != null) {
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT * FROM " + comboTable.getSelectedItem().toString());
                ResultSetMetaData rsmd = rs.getMetaData();

                fieldNames = new Vector<String>(rsmd.getColumnCount() + 1);
                for (int i = 1; i <= rsmd.getColumnCount(); i++) {
                    fieldNames.add(rsmd.getColumnName(i));
                }
                Collections.sort(fieldNames);
                rs.close();
                stmt.close();
            } else {
                fieldNames = new Vector<String>();
            }
            
            if (noFieldOK) {
                fieldNames.add(0, ConnectionSettings.noField_translated);
            }
            for (JComboBox combo : fieldCombos) {
                Object o = combo.getSelectedItem();
                combo.setModel(new DefaultComboBoxModel(fieldNames));
                if (o != null && !o.equals(""))
                    combo.setSelectedItem(o);
            }
            
        } catch (Exception e) {
            ExceptionDialog.showExceptionDialog(this, _("Could not get the field names:"), e);
            return false;
        }
        return true;
    }
    
    public void actionPerformed(ActionEvent e) {
        String aCmd = e.getActionCommand();
        if (aCmd.equals("ok")) {
            if (!inputMaybeValid())
                return;
            
            clickedOK = true;
            setVisible(false);
        } else if (aCmd.equals("test")) {
            if (testConnection()) {
                JOptionPane.showMessageDialog(this, _("Connection to SRFax succeeded."));
            }
        }
    }
    
    private void setFieldComboSel(JComboBox combo, String field) {
        if (field.equals(ConnectionSettings.noField)) {
            field = ConnectionSettings.noField_translated;
        }
        combo.setSelectedItem(field);
    }
    
    private String getFieldComboSel(JComboBox combo) {
        Object sel = combo.getSelectedItem();
        if (sel.equals(ConnectionSettings.noField_translated)) {
            return ConnectionSettings.noField;
        } else {
            return (String)sel;
        }
    }
    
    private void readFromConnectionSettings(AbstractConnectionSettings src) {
//        textDriverClass.setText(src.driver);
//        textURL.setText(src.dbURL);
//        textUserName.setText(src.user);
//        textPassword.setText(src.pwd);
//        //textQuery.setText(src.query);
//        comboTable.setSelectedItem(src.table);
//        
//        checkAskForPassword.setSelected(src.askForPWD);
//        
//        setFieldComboSel(comboComment, src.comment);
//        setFieldComboSel(comboCompany, src.company);
//        setFieldComboSel(comboFaxNumber, src.faxNumber);
//        setFieldComboSel(comboGivenName, src.givenName);
//        setFieldComboSel(comboLocation, src.location);
//        setFieldComboSel(comboName, src.name);
//        setFieldComboSel(comboTitle, src.title);
//        setFieldComboSel(comboVoiceNumber, src.voiceNumber);
        
        for (Map.Entry<String, Component> entry : settingsComponents.entrySet()) {
            try {
                Component comp = entry.getValue();
                if (comp instanceof JComboBox) {
                    JComboBox box = (JComboBox)comp;
                    if (box.getClientProperty(FIELD_ClientProperty) != null) {
                        setFieldComboSel(box, (String)src.getField(entry.getKey()));
                    } else {
                        box.setSelectedItem(src.getField(entry.getKey()));
                    }
                } else if (comp instanceof JTextComponent) {
                    ((JTextComponent)comp).setText((String)src.getField(entry.getKey()));
                } else if (comp instanceof JCheckBox) {
                    ((JCheckBox)comp).setSelected((Boolean)src.getField(entry.getKey()));
                } else {
                    log.warning("Unknown component type: " + comp);
                }
            } catch (Exception e) {
                log.log(Level.WARNING, "Error reading values", e);
            }
        }
    }
    
    private void writeToConnectionSettings(AbstractConnectionSettings dst) {
//        dst.driver = textDriverClass.getText();
//        dst.dbURL = textURL.getText();
//        dst.user = textUserName.getText();
//        dst.pwd = new String(textPassword.getPassword());
//        dst.table = comboTable.getSelectedItem().toString();
//        
//        dst.askForPWD = checkAskForPassword.isSelected();
//        
//        dst.comment = getFieldComboSel(comboComment);
//        dst.company = getFieldComboSel(comboCompany);
//        dst.faxNumber = getFieldComboSel(comboFaxNumber);
//        dst.givenName = getFieldComboSel(comboGivenName);
//        dst.location = getFieldComboSel(comboLocation);
//        dst.name = getFieldComboSel(comboName);
//        dst.title = getFieldComboSel(comboTitle);
//        dst.voiceNumber = getFieldComboSel(comboVoiceNumber);
        
        for (Map.Entry<String, Component> entry : settingsComponents.entrySet()) {
            try {
                Component comp = entry.getValue();
                if (comp instanceof JComboBox) {
                    JComboBox box = (JComboBox)comp;
                    if (box.getClientProperty(FIELD_ClientProperty) != null) {
                        dst.setField(entry.getKey(), getFieldComboSel(box));
                    } else {
                        dst.setField(entry.getKey(), box.getSelectedItem());
                    }
                } else if (comp instanceof JTextComponent) {
                    dst.setField(entry.getKey(), ((JTextComponent)comp).getText());
                } else if (comp instanceof JCheckBox) {
                    dst.setField(entry.getKey(), ((JCheckBox)comp).isSelected());
                } else {
                    log.warning("Unknown component type: " + comp);
                }
            } catch (Exception e) {
                log.log(Level.WARNING, "Error saving values", e);
            }
        }
    }
    
    public ConnectionDialog(Frame owner, String title, Map<String,FieldMapEntry> fieldCaptionMap, boolean noFieldOK) {
        super(owner, title);
     
        this.noFieldOK = noFieldOK;
        this.fieldNameMap = fieldCaptionMap;
        initialize();
    }
    public ConnectionDialog(Dialog owner, String title, Map<String,FieldMapEntry> fieldCaptionMap, boolean noFieldOK) {
        super(owner, title);
     
        this.noFieldOK = noFieldOK;
        this.fieldNameMap = fieldCaptionMap;
        initialize();
    }
    
    
    
    /**
     * Shows the dialog (initialized with the values of target)
     * and writes the user input into target if the user clicks "OK"
     * @param target
     * @return true if the user clicked "OK"
     */
    public boolean promptForNewSettings(AbstractConnectionSettings target) {
        readFromConnectionSettings(target);
        clickedOK = false;
        setVisible(true);
        if (clickedOK) {
            writeToConnectionSettings(target);
        }
        dispose();
        return clickedOK;
    }
    
    public static class FieldMapEntry {
        public final String caption;
        public final int ordinalPosition;
        public final boolean isDataField;
        public final Class<?> dataType;
        
        public FieldMapEntry(String caption, int ordinalPosition) {
            this(caption,ordinalPosition,true,String.class);
        }
        
        public FieldMapEntry(String caption, int ordinalPosition, boolean isDataField, Class<?> dataType) {
            super();
            this.caption = caption;
            this.isDataField = isDataField;
            this.ordinalPosition = ordinalPosition;
            this.dataType = dataType;
        }
    }
}
