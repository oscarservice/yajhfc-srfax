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

import javax.swing.JComponent;

import yajhfc.FaxOptions;

class OptionsPageWrapper<T> implements OptionsPage<FaxOptions> {
    protected OptionsPage<T> realPage;
    protected T options;
    protected Callback<T> callback;
    
    public OptionsPage<T> getRealPage() {
        return realPage;
    }
    
    public T getOptions() {
        return options;
    }
    
    public JComponent getPanel() {
        return realPage.getPanel();
    }
    
    public void loadSettings(FaxOptions foEdit) {
        // NOP
    }
    public void saveSettings(FaxOptions foEdit) {
        callback.saveSettingsCalled(this, foEdit);
    }
    public boolean validateSettings(OptionsWin optionsWin) {
        return callback.validateSettingsCalled(this, optionsWin);
    }
    
    public boolean pageIsHidden(OptionsWin optionsWin) {
        if (!realPage.validateSettings(optionsWin))
            return false;
        realPage.saveSettings(options);
        callback.elementSaved(this);
        return true;
    }
    
    public void pageIsShown(OptionsWin optionsWin) {
        realPage.loadSettings(options);
    }

    public OptionsPageWrapper(OptionsPage<T> realPage, T options, Callback<T> callback) {
        super();
        this.realPage = realPage;
        this.options = options;
        this.callback = callback;
    }

    public void initializeTreeNode(PanelTreeNode node, FaxOptions foEdit) {
        // NOP
    }
    
    public interface Callback<T> {
        public void elementSaved(OptionsPageWrapper<T> source);
        public void saveSettingsCalled(OptionsPageWrapper<T> source, FaxOptions foEdit);
        public boolean validateSettingsCalled(OptionsPageWrapper<T> source, OptionsWin optionsWin);
    }
}