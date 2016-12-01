/*
 * YAJHFC - Yet another Java Hylafax client
 * Copyright (C) 2005-2011 Jonas Wolz <info@yajhfc.de>
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
package yajhfc.options;

import static yajhfc.Utils._;
import static yajhfc.options.OptionsWin.border;
import info.clearthought.layout.TableLayout;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import yajhfc.FaxOptions;
import yajhfc.VersionInfo;
import yajhfc.util.ExcDialogAbstractAction;
import yajhfc.util.ExceptionDialog;
import yajhfc.util.SafeJFileChooser;

/**
 * @author jonas
 *
 */
public class AdminSettingsPage extends AbstractOptionsPanel<FaxOptions> {
    protected final OptionsWin parentWin;
    
    JCheckBox checkUseWin32ShutdownManager, checkAdjustMenusForMacOSX, checkUseJDK16PSBugfix;
    JCheckBox checkRegardingAsUsrKey, checkAllowChangeFilter, checkSendFORMCommand;
    JFileChooser fileChooser;
    
    Action actImport, actExport;
    
    public AdminSettingsPage(OptionsWin parentWin) {
        super(false);
        this.parentWin = parentWin;
    }
    
    @Override
    protected void createOptionsUI() {
        double[][] dLay = {
                {border, TableLayout.FILL, border, TableLayout.PREFERRED, border},
                {border, TableLayout.PREFERRED, border*2, TableLayout.FILL, border}
        };
        setLayout(new TableLayout(dLay));
        
        JLabel descriptionLabel = new JLabel("<html>" + _("This page allows you to import/export settings and to set miscellaneous advanced settings (change them only if you know what you are doing; you may need to restart YajHFC for them to take effect).") + "</html>");
        
        add(descriptionLabel, "1,1,3,1");
        add(createSettingsPanel(), "1,3");
        add(createImportExportPanel(), "3,3");
    }
    
    private JPanel createSettingsPanel() {
        JPanel settingsPanel = new JPanel(null, false);
        settingsPanel.setLayout(new BoxLayout(settingsPanel, BoxLayout.Y_AXIS));
        settingsPanel.setBorder(BorderFactory.createTitledBorder(_("Advanced settings") + ":"));
        
        Dimension spacer = new Dimension(border, border);
        checkAllowChangeFilter = new JCheckBox(_("Allow the user to change the fax job filter (View menu)"));
        checkRegardingAsUsrKey = new JCheckBox(_("Use the fax's subject as HylaFAX USRKEY property"));
        checkUseJDK16PSBugfix = new JCheckBox(_("Repair PostScript generated by JDK 1.6 PrintService classes"));
        checkUseWin32ShutdownManager = new JCheckBox(_("Use ShutdownHook bug fix on Win32"));
        checkAdjustMenusForMacOSX = new JCheckBox(_("Remove application menu entries from normal menu on Mac OS X"));
        checkSendFORMCommand = new JCheckBox(_("Send FORM command"));
        
        settingsPanel.add(checkAllowChangeFilter);
        settingsPanel.add(Box.createRigidArea(spacer));
        settingsPanel.add(checkRegardingAsUsrKey);
        settingsPanel.add(Box.createRigidArea(spacer));
        settingsPanel.add(checkUseJDK16PSBugfix);
        settingsPanel.add(Box.createRigidArea(spacer));
        settingsPanel.add(checkUseWin32ShutdownManager);
        settingsPanel.add(Box.createRigidArea(spacer));
        settingsPanel.add(checkAdjustMenusForMacOSX);
        settingsPanel.add(Box.createRigidArea(spacer));
        settingsPanel.add(checkSendFORMCommand);
        
        return settingsPanel;
    }
    
    JFileChooser getFileChooser() {
        if (fileChooser == null) {
            fileChooser = new SafeJFileChooser();
            fileChooser.setFileHidingEnabled(false);
        }
        return fileChooser;
    }
    
    private JPanel createImportExportPanel() {
        actImport = new ExcDialogAbstractAction(_("Import settings") + "...") {
            @Override
            protected void actualActionPerformed(ActionEvent e) {
                JFileChooser chooser = getFileChooser();
                chooser.setDialogTitle(_("Import settings"));
                if (chooser.showOpenDialog(AdminSettingsPage.this) == JFileChooser.APPROVE_OPTION) {
                    try {
                        Properties p = new Properties();
                        FileInputStream inStream = new FileInputStream(chooser.getSelectedFile());
                        p.load(inStream);
                        inStream.close();
                        
                        FaxOptions newOpts = new FaxOptions();
                        newOpts.loadFromProperties(p);
                        parentWin.reloadSettings(newOpts);
                        
                        JOptionPane.showMessageDialog(AdminSettingsPage.this, _("Settings imported successfully."));
                    } catch (Exception ex) {
                        ExceptionDialog.showExceptionDialog(AdminSettingsPage.this, _("Error importing settings:"), ex);
                    }
                }  
            }
        };
        
        actExport = new ExcDialogAbstractAction(_("Export settings") + "...") {
            @Override
            protected void actualActionPerformed(ActionEvent e) {
                JFileChooser chooser = getFileChooser();
                chooser.setDialogTitle(_("Export settings"));
                if (chooser.showSaveDialog(AdminSettingsPage.this) == JFileChooser.APPROVE_OPTION) {
                    try {
                        Properties p = new Properties();
                        FaxOptions newOpts = new FaxOptions();
                        
                        // Copy existing settings over: 
                        parentWin.foToEdit.storeToProperties(p);
                        newOpts.loadFromProperties(p);
                        
                        if (parentWin.saveSettings(newOpts)) {
                            newOpts.storeToProperties(p);
                            
                            FileOutputStream out = new FileOutputStream(chooser.getSelectedFile());
                            p.store(out, VersionInfo.AppShortName + " " + VersionInfo.AppVersion + " configuration file; exported from the options dialog");
                            out.close();
                        }
                    } catch (Exception ex) {
                        ExceptionDialog.showExceptionDialog(AdminSettingsPage.this, _("Error exporting settings:"), ex);
                    }
                }  
            }
        };
        
        JPanel importExportPanel = new JPanel(null, false);
        importExportPanel.setLayout(new TableLayout(new double[][] {
                {border, TableLayout.FILL, border},
                {border, TableLayout.PREFERRED, border, TableLayout.PREFERRED, TableLayout.FILL}
        }));
        importExportPanel.setBorder(BorderFactory.createTitledBorder(_("Import/Export")));
        
        importExportPanel.add(new JButton(actImport), "1,1");
        importExportPanel.add(new JButton(actExport), "1,3");
        
        return importExportPanel;
    }
    
    /* (non-Javadoc)
     * @see yajhfc.options.OptionsPage#loadSettings(yajhfc.FaxOptions)
     */
    public void loadSettings(FaxOptions foEdit) {
        checkAdjustMenusForMacOSX.setSelected(foEdit.adjustMenusForMacOSX);
        checkAllowChangeFilter.setSelected(foEdit.allowChangeFilter);
        checkRegardingAsUsrKey.setSelected(foEdit.regardingAsUsrKey);
        checkUseJDK16PSBugfix.setSelected(foEdit.useJDK16PSBugfix);
        checkUseWin32ShutdownManager.setSelected(foEdit.useWin32ShutdownManager);
        checkSendFORMCommand.setSelected(foEdit.sendFORMCommand);
    }

    /* (non-Javadoc)
     * @see yajhfc.options.OptionsPage#saveSettings(yajhfc.FaxOptions)
     */
    public void saveSettings(FaxOptions foEdit) {
        foEdit.adjustMenusForMacOSX = checkAdjustMenusForMacOSX.isSelected();
        foEdit.allowChangeFilter = checkAllowChangeFilter.isSelected();
        foEdit.regardingAsUsrKey =  checkRegardingAsUsrKey.isSelected();
        foEdit.useJDK16PSBugfix = checkUseJDK16PSBugfix.isSelected();
        foEdit.useWin32ShutdownManager = checkUseWin32ShutdownManager.isSelected();
        foEdit.sendFORMCommand = checkSendFORMCommand.isSelected();
    }
}