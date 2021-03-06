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
package yajhfc.send;

import gnu.hylafax.HylaFAXClient;
import gnu.hylafax.Job;

import static yajhfc.Utils._;

import java.awt.Frame;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JOptionPane;
import javax.xml.bind.DatatypeConverter;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import com.google.gson.JsonObject;

import yajhfc.FaxNotification;
import yajhfc.FaxOptions;
import yajhfc.FaxResolution;
import yajhfc.HylaClientManager;
import yajhfc.HylaModem;
import yajhfc.MainWin.SendReadyState;
import yajhfc.PaperSize;
import yajhfc.SenderIdentity;
import yajhfc.Utils;
import yajhfc.faxcover.Faxcover;
import yajhfc.faxcover.Faxcover.InvalidCoverFormatException;
import yajhfc.file.FileFormat;
import yajhfc.file.FormattedFile;
import yajhfc.file.MultiFileConverter;
import yajhfc.file.PDFMultiFileConverter;
import yajhfc.launch.Launcher2;
import yajhfc.model.servconn.FaxListConnectionType;
import yajhfc.model.servconn.srfax.SRFaxSentIds;
import yajhfc.phonebook.PBEntryField;
import yajhfc.phonebook.convrules.DefaultPBEntryFieldContainer;
import yajhfc.phonebook.convrules.NameRule;
import yajhfc.phonebook.convrules.PBEntryFieldContainer;
import yajhfc.server.Server;
import yajhfc.server.ServerOptions;
import yajhfc.ui.YajOptionPane;
import yajhfc.ui.swing.SwingYajOptionPane;
import yajhfc.util.ProgressWorker;
import yajhfc.util.SRFaxAPI;
import yajhfc.util.ProgressWorker.ProgressUI;

/**
 * @author jonas
 *
 */
public class SendController implements FaxSender {
    
    // These properties are set in the constructor:
    protected Server server;
    protected YajOptionPane dialogUI;
    protected boolean pollMode;
    
    // These properties have default values and may be set using getters and setters
    protected boolean useCover = false;
    protected List<HylaTFLItem> files = new ArrayList<HylaTFLItem>();
    protected List<PBEntryFieldContainer> numbers = new ArrayList<PBEntryFieldContainer>();
    protected String subject = "", comments = "";
    protected PaperSize paperSize; 
    protected File customCover = null;
    
    protected int maxTries;
    protected FaxNotification notificationType;
    protected FaxResolution resolution;
    protected int killTime;
    // null = "NOW"
    protected Date sendTime = null;
    protected boolean archiveJob;
    
    protected final Map<String,String> customProperties;
    
    protected ProgressUI progressMonitor = null;
    
    protected SenderIdentity fromIdentity;
    
    /**
     * The selected modem. Either a HylaModem or a String containing the modem's name.
     */
    protected Object selectedModem;
    
    /**
     * The list of the submitted job IDs
     */
    protected final List<Long> submittedJobs = new ArrayList<Long>();
    
    // Internal properties:
    protected final List<SendControllerListener> listeners = new ArrayList<SendControllerListener>();
    
    private static final int FILE_DISPLAY_LEN = 30;
    
    public ProgressUI getProgressMonitor() {
        return progressMonitor;
    }

    public void setProgressMonitor(ProgressUI progressMonitor) {
        this.progressMonitor = progressMonitor;
    }

    void setPaperSizes() {
        //PaperSize desiredSize = (PaperSize)comboPaperSize.getSelectedItem();
        for (HylaTFLItem item : files) {
            item.setDesiredPaperSize(paperSize);
        }
    }

    protected Faxcover initFaxCover() throws IOException, FileNotFoundException, InvalidCoverFormatException {
        FaxOptions fo = Utils.getFaxOptions();   
        Faxcover cov;

        File coverTemplate = null;
        if (customCover != null) {
            coverTemplate = customCover;
        } else if (fromIdentity.useCustomDefaultCover) {
            coverTemplate = new File(fromIdentity.defaultCover);
        }
        cov = Faxcover.createInstanceForTemplate(coverTemplate);
        
        
        cov.pageCount = 0;

        if (customCover != null) {
            if (!(customCover.canRead())) {
                dialogUI.showMessageDialog(MessageFormat.format(Utils._("Can not read file \"{0}\"!"), customCover.toString()), Utils._("Error"), JOptionPane.WARNING_MESSAGE);
                return null;
            }
        } else if (fromIdentity.useCustomDefaultCover) {
            if (!(new File(fromIdentity.defaultCover).canRead())) {
                dialogUI.showMessageDialog(MessageFormat.format(Utils._("Can not read default cover page file \"{0}\"!"), fromIdentity.defaultCover), Utils._("Error"), JOptionPane.WARNING_MESSAGE);
                return null;
            }
        }

        for (HylaTFLItem item : files) {
            InputStream strIn = new FileInputStream(item.getPreviewFilename().file);
            // Try to get page count 
            cov.estimatePostscriptPages(strIn);
            strIn.close();
        }
        
        cov.fromData = fromIdentity;
        cov.nameRule = fo.coverNameRule;
        cov.locationRule = fo.coverLocationRule.generateRule(fo.coverZIPCodeRule);
        cov.companyRule = fo.coverCompanyRule;
        
        cov.comments = comments;
        cov.regarding = subject;

        cov.pageSize = paperSize;
        if (sendTime != null)
            cov.coverDate = sendTime;
        
        return cov;
    }
    protected File makeCoverFile(Faxcover cov, PBEntryFieldContainer to) throws IOException, FileNotFoundException {
        File coverFile;

        if (to != null) {
            cov.toData = to;
        } else {
            cov.toData = new DefaultPBEntryFieldContainer("");
        }
        if (numbers.size() > 1) {
            PBEntryFieldContainer[] cc = new PBEntryFieldContainer[numbers.size()-1];
            int idx = 0;
            for (PBEntryFieldContainer pbe : numbers) {
                if (!pbe.equals(to) && idx < cc.length) {
                    cc[idx++] = pbe;
                }
            }
            cov.ccData = cc;
        } else {
            cov.ccData = null;
        }

        // Create cover:
        coverFile = File.createTempFile("cover", ".ps");
        yajhfc.shutdown.ShutdownManager.deleteOnExit(coverFile);
        FileOutputStream fout = new FileOutputStream(coverFile);
        cov.makeCoverSheet(fout);
        fout.close();                       

        return coverFile;
    }

    class PreviewWorker extends ProgressWorker {
        protected PBEntryFieldContainer selectedNumber;
        
        public PreviewWorker(PBEntryFieldContainer selectedNumber) {
            this.selectedNumber = selectedNumber;
            this.progressMonitor = SendController.this.progressMonitor;
        }
        
        protected int calculateMaxProgress() {
            return 15000;
        }

        @Override
        public void doWork() {
            try {
                int step;
                setPaperSizes();
                List<FormattedFile> viewFiles = new ArrayList<FormattedFile>(files.size() + 1);
                
                if (useCover && server.getOptions().faxListConnectionType!=FaxListConnectionType.SRFAX) {
                    step = 10000 / (files.size() + 1);
                    updateNote(Utils._("Creating cover page"));

                    File coverFile = makeCoverFile(initFaxCover(), selectedNumber);
                    //FormattedFile.viewFile(coverFile.getPath(), FileFormat.PostScript);
                    viewFiles.add(new FormattedFile(coverFile));
                    setProgress(step);
                } else {
                    if (files.size() > 0)
                        step = 10000 / files.size();
                    else
                        step = 0;
                }

                for (HylaTFLItem item : files) {
                    updateNote(MessageFormat.format(Utils._("Formatting {0}"), Utils.shortenFileNameForDisplay(item.getText(), FILE_DISPLAY_LEN)));
                    //item.preview(SendController.this.parent, hyfc);
                    FormattedFile file = item.getPreviewFilename();
                    if (file != null)
                        // Only add valid files with supported preview
                        viewFiles.add(file);
                    stepProgressBar(step);
                }

                if (viewFiles.size() > 0) {
                    updateNote(Utils._("Launching viewer"));
                    MultiFileConverter.viewMultipleFiles(viewFiles, paperSize, true);
                }
            } catch (Exception e1) {
                showExceptionDialog(Utils._("Error previewing the documents:"), e1);
            } 
        } 
        
    }
    class SendWorker extends ProgressWorker {
        final Logger log = Logger.getLogger(SendWorker.class.getName());    
        final SendFileManager fileManager;
        boolean success = false;
        
        private void setIfNotEmpty(Job j, String prop, String val) {
            try {
                if (val.length() >  0)
                    j.setProperty(prop, Utils.sanitizeInput(val));
            } catch (Exception e) {
                log.log(Level.WARNING, "Couldn't set additional job info " + prop + ": ", e);
            }
        }

        private String getModem() {
            Object sel = selectedModem;
            if (Utils.debugMode) {
                log.fine("Selected modem (" + sel.getClass().getCanonicalName() + "): " + sel);
            }
            if (sel instanceof HylaModem) {
                return ((HylaModem)sel).getInternalName();
            } else {
                String str = sel.toString();
                int pos = str.indexOf(' '); // Use part up to the first space
                if (pos == -1)
                    return str;
                else
                    return str.substring(0, pos);
            }
        }

        @Override
        protected int calculateMaxProgress() {
            int maxProgress;
            maxProgress = 20 * numbers.size() + 10 + fileManager.calcMaxProgress();
            if (useCover) {
                maxProgress += 20;
            }
            return maxProgress;
        }

        @Override
        public void doWork() {
        	FaxOptions fo = Utils.getFaxOptions();
            ServerOptions so = server.getOptions();
            
            //SRFax implemention of Send Fax
            if (so.faxListConnectionType == FaxListConnectionType.SRFAX) {
            	
            	//Prepare Recipients
            	ArrayList<String> faxNos = new ArrayList<String>();
            	ArrayList<String> faxNames = new ArrayList<String>();
            	for (PBEntryFieldContainer numItem : numbers) {
            		//fix fax numbers
            		String fno = numItem.getField(PBEntryField.FaxNumber).replaceAll("[^\\d.]", "");
            		if(fno.length()==10){
            			fno = "1"+fno;
            		}
            		faxNos.add(fno);
            		faxNames.add(numItem.getField(PBEntryField.GivenName));
            	}
            	
            	
            	
            	//Prepare Files
            	JsonObject json;
            	String action = "Queue_Fax";
            	String fileData = "";
            	int index = 0;
            	
            	//Subject
            	fileData += "&sCPSubject="+subject;
            	
            	//Cover File
            	if (useCover) {
                    try {
//                    	File coverFile = makeCoverFile(initFaxCover(), numbers.get(0));
//                    	String encoded = Base64.getMimeEncoder().encodeToString(Files.readAllBytes(coverFile.toPath()));
//                    	fileData += "&sFileName_"+index+"="+URLEncoder.encode("cover.ps", "UTF-8")+"&sFileContent_"+index+"="+URLEncoder.encode(encoded, "UTF-8"); index++;
                    	
                    	fileData += "&sCoverPage=Company&sCPComments="+comments+"&sCPFromName="+so.SRFaxNumber+"&sCPToName="+StringUtils.join(faxNames, ", ");
					} catch (Exception e) {
						showExceptionDialog(Utils._("An error occured with the cover page: "), e);
					}
                    //stepProgressBar(20);
                }
            	
            	//Files
            	for (HylaTFLItem item : files) {
            		updateNote(MessageFormat.format(Utils._("Creating job for {0}"), item.toString()));
            		try {
            			File p = new File(item.toString());
            			if(!p.exists()){
            				FormattedFile ff = item.getPreviewFilename();
                			File gs = new File(Utils.getFaxOptions().ghostScriptLocation);
                			if(ff.getFormat()==FileFormat.PostScript){
                				if(!gs.exists()){
                					showMessageDialog("Ghostscript is required to convert PostScript file to PDF files. SRFax does not accept PostScript file in the API.", "Ghostscript executable not found", JOptionPane.ERROR_MESSAGE);
                				}
                				
                				p = File.createTempFile("temp",".pdf");
//                    			FileOutputStream os = new FileOutputStream(p);
//                    			FileConverter fconv = FileConverters.getConverterFor(ff.getFormat());
//                    			fconv.convertToHylaFormat(ff.getFile(), os, paperSize, FileFormat.PDF);
                    			PDFMultiFileConverter pc = new PDFMultiFileConverter();
                    			File[] fa = new File[1];
                    			fa[0] = ff.getFile();
                    			pc.convertMultiplePSorPDFFiles(fa, p, paperSize);
                    			
//                    		    os.close();
                			}else{
                				p = ff.getFile();
                			}
            			}
            			
            			if(p.exists()){
            				byte[] data = FileUtils.readFileToByteArray(p);
                			//byte[] data = Files.readAllBytes(p);
                			String encoded = DatatypeConverter.printBase64Binary(data);
                			fileData += "&sFileName_"+index+"="+URLEncoder.encode(p.getName().toString(), "UTF-8")+"&sFileContent_"+index+"="+URLEncoder.encode(encoded, "UTF-8");
                			
            			}else{
            				//file is a forward fax
                			String filename = item.toString().substring(item.toString().indexOf(":")+1);
                			String detailsID = filename.substring(filename.indexOf("|")+1);
                			
                			if(files.size()>1){
                				//Multiple files to forward
                				String encoded = SRFaxAPI.sendPost("Retrieve_Fax&sFaxFileName="+filename+"&sFaxDetailsID="+detailsID+"&sDirection=IN", so.user, so.pass.getPassword()).get("Result").getAsString();
                				try {
    								fileData += "&sFileName_"+index+"="+URLEncoder.encode(detailsID+".pdf", "UTF-8")+"&sFileContent_"+index+"="+URLEncoder.encode(encoded, "UTF-8");
    							} catch (UnsupportedEncodingException e1) {
    								dialogUI.showExceptionDialog("UnsupportedEncodingException", e1);
    			    				success = false;
    			                	return;
    							}
                				
                			}else{
                				//Single srfax file to forward
                    			action = "Forward_Fax";
                    			fileData = "&sDirection=IN&sFaxFileName="+filename+"&sFaxDetailsID"+detailsID;
                			}
            			}
            			
            		} catch (Exception e) {
						dialogUI.showExceptionDialog("Error", "Something wrong with your files", e);
	    				success = false;
	                	return;
					}

            		index++;
            	}
            	
            	//Time to send
            	if (sendTime == null || (sendTime.getTime() - 10000) < System.currentTimeMillis()) {
            		
                } else {
                	SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
                	SimpleDateFormat tf = new SimpleDateFormat("HH:mm");
                	fileData += "&sQueueFaxDate="+(df.format(sendTime))+"&sQueueFaxTime="+tf.format(sendTime);
                }
            	
            	//Send SRFax
				try {
					json = SRFaxAPI.sendPost(action+"&sCallerID="+so.SRFaxNumber+"&sSenderEmail="+so.SRFaxEmail+"&sFaxType="+(faxNos.size()>1?"BROADCAST":"SINGLE")+"&sToFaxNumber="+StringUtils.join(faxNos, "|")+fileData, so.user, so.pass.getPassword());
					if(json.get("Status").getAsString().compareTo("Failed")==0){
	    				dialogUI.showMessageDialog(json.get("Result").getAsString(), _("Error"), JOptionPane.ERROR_MESSAGE);
	    				success = false;
	                	return;
	    			}else{
	    				//Add to transmitting list
	    				String fid = json.get("Result").getAsString();
	    				SRFaxSentIds.getInstance().ids.add(so.user+"@"+fid);
	    			}
					//System.out.println(json);
				} catch (Exception e) {
					showExceptionDialog(Utils._("An error occured while submitting the fax: "), e);
				}
    			
				fo.useCover = useCover;
				dialogUI.showMessageDialog("Fax successfully queued.", _("Success"), JOptionPane.INFORMATION_MESSAGE);
            	success = true;
            	return;
            }
        	
            HylaClientManager clientManager = server.getClientManager();
            try {
                HylaFAXClient hyfc = clientManager.beginServerTransaction(SendController.this.dialogUI);
                if (hyfc == null) {
                    return;
                }

                submittedJobs.clear();
                Faxcover cover = null;
                

                if (!pollMode) {
                    setPaperSizes();

                    if (useCover) {
                        cover = initFaxCover();
                        stepProgressBar(20);
                    }

                    fileManager.uploadFiles(hyfc, this);
                }            

                String modem = Utils.sanitizeInput(getModem());
                if (Utils.debugMode) {
                    log.fine("Use modem: " + modem);
                    //Utils.debugOut.println(modem);
                }
                final String numberFilterChars = so.filterFromFaxNr + "\r\n";
                for (PBEntryFieldContainer numItem : numbers) {
                    updateNote(MessageFormat.format(Utils._("Creating job to {0}"), numItem.getField(PBEntryField.FaxNumber)));

                    try {
                        if (cover != null) {
                            fileManager.setCoverFile(makeCoverFile(cover, numItem), hyfc);
                        }
                        stepProgressBar(5);

                        synchronized (hyfc) {
                            log.finest("In hyfc monitor");
                            Job j = hyfc.createJob();

                            stepProgressBar(5);

                            j.setFromUser(Utils.sanitizeInput(clientManager.getUser()));
                            String notifyAddr = Utils.sanitizeInput(so.notifyAddress);
                            if (notifyAddr != null && notifyAddr.length() > 0) {
                                j.setNotifyAddress(notifyAddr);
                            }
                            j.setMaximumDials(so.maxDial);

                            if (!pollMode) {
                                // Set general job information...
                                setIfNotEmpty(j, "TOUSER", fo.coverNameRule.applyRule(numItem));
                                setIfNotEmpty(j, "TOCOMPANY", fo.coverCompanyRule.applyRule(numItem));
                                setIfNotEmpty(j, "TOLOCATION", fo.coverLocationRule.generateRule(fo.coverZIPCodeRule).applyRule(numItem));
                                setIfNotEmpty(j, "TOVOICE", numItem.getField(PBEntryField.VoiceNumber));
                                setIfNotEmpty(j, "REGARDING", subject);
                                setIfNotEmpty(j, "COMMENTS", comments);
                                setIfNotEmpty(j, "FROMCOMPANY", fromIdentity.FromCompany);
                                setIfNotEmpty(j, "FROMLOCATION", fromIdentity.FromLocation);
                                setIfNotEmpty(j, "FROMVOICE", fromIdentity.FromVoiceNumber);

                                if (fo.regardingAsUsrKey) {
                                    setIfNotEmpty(j, "USRKEY", subject);
                                }
                            }

                            String faxNumber = so.numberPrefix + Utils.stringFilterOut(numItem.getField(PBEntryField.FaxNumber), numberFilterChars, 255);
                            j.setDialstring(faxNumber);
                            //j.setProperty("EXTERNAL", faxNumber); // needed to fix an error while sending multiple jobs
                            j.setMaximumTries(maxTries);
                            j.setNotifyType(notificationType.getType());
                            j.setPageDimension(paperSize.getSize());
                            j.setVerticalResolution(resolution.getResolution());
                            if (resolution.useXVRes()) {
                                j.setProperty("USEXVRES", "YES");
                            }
                            //j.setProperty("USEXVRES", resolution.useXVRes() ? "YES" : "NO");
                            if (sendTime == null || (sendTime.getTime() - 10000) < System.currentTimeMillis()) {
                                j.setSendTime("NOW"); // If send time has not been specified or is not at least 10 secs. in the future, send now
                            } else {
                                j.setSendTime(sendTime);
                            }
                            j.setKilltime(Utils.minutesToHylaTime(killTime));  

                            j.setProperty("MODEM", modem);

                            if (archiveJob) {
                                j.setProperty("doneop", "archive");
                            }
                            
                            for (Map.Entry<String, String> prop : customProperties.entrySet()) {
                                j.setProperty(prop.getKey(), prop.getValue());
                            }
                            
                            if (pollMode) 
                                j.setProperty("POLL", "\"\" \"\"");
                            else {               
                                fileManager.attachDocuments(hyfc, j, this);

                                fo.useCover = useCover;
                                fo.useCustomCover = (customCover != null);
                                fo.CustomCover =  (customCover != null) ? customCover.getAbsolutePath() : null;
                            }

                            stepProgressBar(5);

                            hyfc.submit(j);
                            
                            Long jobID = Long.valueOf(j.getId());
                            submittedJobs.add(jobID);
                            printJobIDIfRequested(jobID);
                        }
                        log.finest("Out of hyfc monitor");
                        stepProgressBar(5);
                    } catch (Exception e1) {
                        showExceptionDialog(MessageFormat.format(Utils._("An error occured while submitting the fax job for phone number \"{0}\" (will try to submit the fax to the other numbers anyway): "), numItem.getField(PBEntryField.FaxNumber)) , e1);
                    }
                }

                updateNote(Utils._("Cleaning up"));
                success = true;
            } catch (Exception e1) {
                //JOptionPane.showMessageDialog(ButtonSend, _("An error occured while submitting the fax: ") + "\n" + e1.getLocalizedMessage(), _("Error"), JOptionPane.ERROR_MESSAGE);
                showExceptionDialog(Utils._("An error occured while submitting the fax: "), e1);
            } 
            clientManager.endServerTransaction();
        }

        @Override
        protected void done() {
            fireSendOperationComplete(success);
            Utils.executorService.submit(new Runnable() {
               public void run() {
                   fileManager.cleanup();
                } 
            });
//            if (success) {
//                if (SendController.this.dialogUI.getParent() != null) {
//                    SendController.this.dialogUI.getParent().dispose();
//                }
//            }
        }
        
        public SendWorker() {
            this.progressMonitor = SendController.this.progressMonitor;
            this.fileManager = new SendFileManager(paperSize, files);
        }
    }
    
    public SendController(Server server, Window parent, boolean pollMode) {
        this(server, parent, pollMode, null);
    }
    
    public SendController(Server server, Window parent, boolean pollMode, ProgressUI progressMonitor) {
        this(server, new SwingYajOptionPane(parent), pollMode, progressMonitor);
    }
    
    public SendController(Server server, YajOptionPane dialogUI, boolean pollMode, ProgressUI progressMonitor) {
        this.server = server;
        this.dialogUI = dialogUI;
        this.pollMode = pollMode;
        this.progressMonitor = progressMonitor;
        
        ServerOptions so = server.getOptions();
        paperSize = so.paperSize;
        maxTries = so.maxTry;
        notificationType = so.notifyWhen;
        resolution = so.resolution;
        archiveJob = so.archiveSentFaxes;
        customProperties = new TreeMap<String,String>(so.customJobOptions);
        selectedModem = so.defaultModem;
        killTime = so.killTime;
        
        fromIdentity = server.getDefaultIdentity();
    }
    
    /**
     * Validates the settings for correctness and shows problems to the user.
     * @return true if entries are valid
     */
    public boolean validateEntries() {    
        
        if (numbers.size() == 0) {
            dialogUI.showMessageDialog(Utils._("To send a fax you have to enter at least one phone number!"), Utils._("Warning"), JOptionPane.INFORMATION_MESSAGE);
            return false;
        }
        for (int i=0; i < numbers.size(); i++) {
            PBEntryFieldContainer number = numbers.get(i);
            String faxNumber = number.getField(PBEntryField.FaxNumber);
            if (faxNumber == null || faxNumber.length() == 0) {
                dialogUI.showMessageDialog(MessageFormat.format(Utils._("For recipient {0} (\"{1}\") no fax number has been entered."), i+1, NameRule.GIVENNAME_NAME.applyRule(number)), Utils._("Warning"), JOptionPane.INFORMATION_MESSAGE);
                return false;
            }
        }
        
        if (files.size() == 0) {
            if (useCover) {
                if (dialogUI.showConfirmDialog(Utils._("You haven't selected a file to transmit, so your fax will ONLY contain the cover page.\nContinue anyway?"), Utils._("Continue?"), JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.NO_OPTION)
                    return false;
            } else {
                dialogUI.showMessageDialog(Utils._("To send a fax you must select at least one file!"), Utils._("Warning"), JOptionPane.INFORMATION_MESSAGE);
                return false;
            }
        }   
        
        if(server.getOptions().faxListConnectionType == FaxListConnectionType.SRFAX){
        	if(customCover != null){
        		dialogUI.showMessageDialog(Utils._("SRFax does not support custom cover page!"), Utils._("Warning"), JOptionPane.INFORMATION_MESSAGE);
                return false;
        	}
        }
        
        return true;
    }
    
    public void sendFax() {
        if (Launcher2.application.getSendReadyState() != SendReadyState.Ready) {
            throw new IllegalStateException("Application is not ready to send! (SendReadyState=" + Launcher2.application.getSendReadyState() + ")");
        }
        SendWorker wrk = new SendWorker();
        wrk.startWork(dialogUI, Utils._("Sending fax"));
    }
    
    public void previewFax(PBEntryFieldContainer selectedNumber) {
        PreviewWorker wrk = new PreviewWorker(selectedNumber);
        wrk.startWork(dialogUI, Utils._("Previewing fax"));
    }

    
    // Getters/Setters:
    public boolean isUseCover() {
        return useCover;
    }

    public void setUseCover(boolean useCover) {
        this.useCover = useCover;
    }

    public List<PBEntryFieldContainer> getNumbers() {
        return numbers;
    }

    public void setNumbers(List<PBEntryFieldContainer> numbers) {
        this.numbers = numbers;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        if (subject == null) {
            throw new IllegalArgumentException("subject may not be null!");
        }
        this.subject = subject;
    }

    public String getComment() {
        return comments;
    }

    public void setComment(String comments) {
        if (comments == null) {
            throw new IllegalArgumentException("comments may not be null!");
        }
        this.comments = comments;
    }

    public PaperSize getPaperSize() {
        return paperSize;
    }

    public void setPaperSize(PaperSize paperSize) {
        if (paperSize == null) {
            throw new IllegalArgumentException("paperSize may not be null!");
        }
        this.paperSize = paperSize;
    }

    public File getCustomCover() {
        return customCover;
    }

    public void setCustomCover(File customCover) {
        this.customCover = customCover;
    }

    public int getMaxTries() {
        return maxTries;
    }

    public void setMaxTries(int maxTries) {
        this.maxTries = maxTries;
    }

    public FaxNotification getNotificationType() {
        return notificationType;
    }

    public void setNotificationType(FaxNotification notificationType) {
        this.notificationType = notificationType;
    }

    public FaxResolution getResolution() {
        return resolution;
    }

    public void setResolution(FaxResolution resolution) {
        this.resolution = resolution;
    }

    public int getKillTime() {
        return killTime;
    }

    public void setKillTime(int killTime) {
        this.killTime = killTime;
    }

    public Object getSelectedModem() {
        return selectedModem;
    }

    public void setSelectedModem(Object selectedModem) {
        if (selectedModem == null) {
            throw new IllegalArgumentException("selectedModem may not be null!");
        }
        this.selectedModem = selectedModem;
    }

    /**
     * Returns a map containing custom properties
     * @param propertyName
     * @param value
     */
    public Map<String,String> getCustomProperties() {
        return customProperties;
    }
    
    public Server getServer() {
        return server;
    }

    public void setServer(Server server) {
        this.server = server;
    }

    public boolean isPollMode() {
        return pollMode;
    }

    public List<HylaTFLItem> getFiles() {
        return files;
    }
    
    public Date getSendTime() {
        return sendTime;
    }

    /**
     * Sets the time to send. null means "now"
     * @param sendTime
     */
    public void setSendTime(Date sendTime) {
        this.sendTime = sendTime;
    }

    public boolean isArchiveJob() {
        return archiveJob;
    }

    public void setArchiveJob(boolean archiveJob) {
        this.archiveJob = archiveJob;
    }

    public void addSendControllerListener(SendControllerListener l) {
        listeners.add(l);
    }
    
    public void removeSendControllerListener(SendControllerListener l) {
        listeners.remove(l);
    }
    
    protected void fireSendOperationComplete(boolean success) {
        for (SendControllerListener l : listeners) {
            l.sendOperationComplete(success);
        }
    }
    
    public List<Long> getSubmittedJobIDs() {
        return submittedJobs;
    }
    
    public SenderIdentity getIdentity() {
        return fromIdentity;
    }
    
    public void setIdentity(SenderIdentity fromIdentity) {
        this.fromIdentity = fromIdentity;
    }
    
    public static void printJobIDIfRequested(Long jobID) {
        if (Launcher2.jobIDWriter != null) {
            Launcher2.jobIDWriter.printf("%1$tF %1$tT NEW_FAXJOB %2$d", new Date(), jobID);
            Launcher2.jobIDWriter.println();
            Launcher2.jobIDWriter.flush();
        }
    }
    
    static SendWinControl lastSendWin = null;
    /**
     * Creates a new send window or returns a reference to a existing one
     * @param owner
     * @param manager
     * @param initiallyHideFiles
     * @return
     */
    public static SendWinControl getSendWindow(Frame owner, Server server, boolean pollMode, boolean initiallyHideFiles) {
        if (lastSendWin == null || lastSendWin.isPollMode() != pollMode) {
            return createSendWindow(owner, server, false, initiallyHideFiles);
        } else {
            return lastSendWin;
        }
    }
    /**
     * Creates a new send window and returns it.
     * @param owner
     * @param manager
     * @param pollMode
     * @param initiallyHideFiles
     * @return
     */
    public static SendWinControl createSendWindow(Frame owner, Server server, boolean pollMode, boolean initiallyHideFiles) {
        SendWinControl result;
        
        if (lastSendWin != null) {
            lastSendWin.getWindow().dispose();
            lastSendWin = null;
        }
        
        
        if (pollMode || Utils.getFaxOptions().sendWinStyle == SendWinStyle.TRADITIONAL) {
            result = new SendWin(server, owner, pollMode);
        } else {
            result = new SimplifiedSendDialog(server, owner, initiallyHideFiles);
        }
        
        lastSendWin = result;
        result.getWindow().addWindowListener(new WindowAdapter() {
           @Override
            public void windowClosed(WindowEvent e) {
               if (lastSendWin == e.getSource()) {
                   lastSendWin = null;
               }
            } 
        });
        return result;
    }

    public Collection<PBEntryFieldContainer> getRecipients() {
        return numbers;
    }

    public Collection<HylaTFLItem> getDocuments() {
        return files;
    }

    public void setModem(String modem) {
        setSelectedModem(modem);
    }
}
