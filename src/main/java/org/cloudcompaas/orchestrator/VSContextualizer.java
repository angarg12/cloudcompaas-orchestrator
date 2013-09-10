package org.cloudcompaas.orchestrator;

import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Random;

import org.cloudcompaas.common.communication.RESTComm;

/**
 * @author angarg12
 *
 */
public class VSContextualizer {
	private int timeoutConnection = 10000;
	private int connectionRetries = 5;
	private int contextualizerListenPort = 9999;
	private long monitorInterval = 7000;
	private String idSla;
	private String localSdtId;
	private String[] epr;

	public VSContextualizer(String[] epr_, String localSdtId_, String idSla_){
		epr = epr_;
		localSdtId = localSdtId_;
		idSla = idSla_;
	}
	
	public void setup() throws Exception {
		RESTComm comm = new RESTComm("Catalog");
		comm.setUrl("/service/search?name=Catalog");
		String id_service = comm.get().getFirst("//id_service");
		comm.setUrl("/service_instance/search?service="+id_service);
		String[] catalogEpr = comm.get().get("//epr");
		Random rand = new Random();
		String randomEpr = catalogEpr[rand.nextInt(catalogEpr.length)];
		
		for(int i = 0; i < epr.length; i++){
			Socket sock = new Socket();
			for(int j = 0; j < connectionRetries; j++){
				try{
					System.out.println(randomEpr+" "+epr[i]+":10001"+" "+idSla+" "+monitorInterval);
					SocketAddress saddr = new InetSocketAddress(epr[i].split(":")[0], contextualizerListenPort);
					sock.connect(saddr);
					PrintWriter out = new PrintWriter(
							sock.getOutputStream(), true);
					out.println(randomEpr);
					out.println(epr[i]+":10001");
					out.println(idSla);
					out.println(localSdtId);
					out.println(monitorInterval);
					out.flush();
					out.close();
					break;
				}catch(Exception e){
					//e.printStackTrace();
					Thread.sleep(timeoutConnection);
				}
			}
			sock.close();
		}

		/**
		 * Ad-hoc implementation for the use case linpack service
		 *

			String[][] rows = new String[epr.length+1][5];		        
	        rows[0][0] = "epr";
	        rows[0][1] = "service";
	        rows[0][2] = "version";
	        rows[0][3] = "id_sla";
	        rows[0][4] = "local_sdt_id";	   
			for(int i = 0; i < epr.length; i++){	  
				rows[i+1][0] = epr[i]+":10001";
				rows[i+1][1] = "5";
		        rows[i+1][2] = "5";
		        rows[i+1][3] = idSla;
		        rows[i+1][4] = localSdtId;	        
	        }

        	tcrs.addRequest("persistent.SERVICE_INSTANCE", rows);
		/**
		 * 
		 */
	}
	
	public void teardown() throws Exception {		
		for(int i = 0; i < epr.length; i++){
			Socket sock = new Socket();
			for(int j = 0; j < connectionRetries; j++){
				try{
					SocketAddress saddr = new InetSocketAddress(epr[i].split(":")[0], contextualizerListenPort);
					sock.connect(saddr);
					sock.getInputStream().read();
					break;
				}catch(Exception e){
					//e.printStackTrace();
					Thread.sleep(timeoutConnection);
				}
			}
			sock.close();
		}
	}
}
