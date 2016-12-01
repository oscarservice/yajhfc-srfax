package yajhfc.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;

//import org.apache.http.HttpEntity;
//import org.apache.http.HttpResponse;
//import org.apache.http.client.HttpClient;
//import org.apache.http.client.ResponseHandler;
//import org.apache.http.client.methods.CloseableHttpResponse;
//import org.apache.http.client.methods.HttpPost;
//import org.apache.http.client.methods.HttpUriRequest;
//import org.apache.http.client.methods.RequestBuilder;
//import org.apache.http.entity.ContentType;
//import org.apache.http.entity.StringEntity;
//import org.apache.http.impl.client.CloseableHttpClient;
//import org.apache.http.impl.client.HttpClientBuilder;
//import org.apache.http.impl.client.HttpClients;
//import org.apache.http.protocol.HTTP;
//import org.apache.http.util.EntityUtils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import gnu.inet.ftp.ServerResponseException;

public class SRFaxAPI {
	public static JsonObject sendPost(String action, String userid, String pwd) throws ServerResponseException{
		JsonObject jsonObject = null;
		try{
			String url = "https://www.srfax.com/SRF_SecWebSvc.php";
			String param = "access_id="+userid+"&access_pwd="+pwd+"&action=" + action;
			
//			CloseableHttpClient httpclient = HttpClients.createDefault();
//			HttpPost httppost = new HttpPost(url);
//			HttpEntity entity = new StringEntity(param, ContentType.APPLICATION_FORM_URLENCODED);
//			httppost.setEntity(entity);
//            // Create a custom response handler			
//            ResponseHandler<String> responseHandler = new ResponseHandler<String>() {
//                public String handleResponse(final HttpResponse response) throws IOException {
//                    int status = response.getStatusLine().getStatusCode();
//                    if (status >= 200 && status < 300) {
//                        HttpEntity entity = response.getEntity();
//                        return entity != null ? EntityUtils.toString(entity) : null;
//                    } else {
//                    	return null;
//                    }
//                }
//            };
//            String responseBody = httpclient.execute(httppost, responseHandler);
			
            
            
			HttpsURLConnection con = (HttpsURLConnection) new URL(url).openConnection();
			con.setRequestMethod("POST");
			con.setDoOutput(true);
			//SSLSocketFactory sssf = (SSLSocketFactory) SSLSocketFactory.getDefault();
			//con.setSSLSocketFactory(sssf);
			//con.connect();
			//con.getCipherSuite(); //TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256 in Java 8
			con.getOutputStream().write((param).getBytes("UTF-8"));
//			DataOutputStream wr = new DataOutputStream(con.getOutputStream());
//			wr.writeBytes("access_id="+userid+"&access_pwd="+pwd+"&action=" + action);
//			wr.flush();
//			wr.close();

//			int responseCode = con.getResponseCode();
			
			BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
			String inputLine;
			StringBuffer responseBody = new StringBuffer();

			while ((inputLine = in.readLine()) != null) {
				responseBody.append(inputLine);
			}
			in.close();

			jsonObject = new JsonParser().parse(responseBody.toString()).getAsJsonObject();
			
		} catch(Exception e) {
			throw new ServerResponseException(e.getMessage());
		}
		
		return jsonObject;
		//System.out.println(jsonObject.get("Status").getAsString());
		//System.out.println(jsonObject.get("Result").getAsJsonArray().get(0).getAsJsonObject().get("EpochTime"));
	}
}
