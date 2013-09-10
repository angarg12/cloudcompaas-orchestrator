/*******************************************************************************
 * Copyright (c) 2013, Andrés García García All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 * 
 * (1) Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * 
 * (2) Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
 * 
 * (3) Neither the name of the Universitat Politècnica de València nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 ******************************************************************************/
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
