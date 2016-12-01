package yajhfc.model.servconn.srfax;

import java.util.ArrayList;

public class SRFaxSentIds {
	   private static SRFaxSentIds instance = null;
	   
	   public ArrayList<String> ids = new ArrayList<String>();
	   protected SRFaxSentIds() {
	      // Exists only to defeat instantiation.
		   
	   }
	   public static SRFaxSentIds getInstance() {
	      if(instance == null) {
	         instance = new SRFaxSentIds();
	      }
	      return instance;
	   }
}
