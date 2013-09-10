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

import java.util.Properties;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;
import org.apache.wink.common.annotations.Scope;
import org.apache.wink.common.http.HttpStatus;
import org.apache.xmlbeans.XmlOptions;
import org.cloudcompaas.common.communication.RESTComm;
import org.cloudcompaas.common.components.Component;
import org.cloudcompaas.common.components.Register;
import org.cloudcompaas.common.util.XMLWrapper;
import org.ogf.schemas.graap.wsAgreement.AgreementPropertiesDocument;
import org.ogf.schemas.graap.wsAgreement.ServiceDescriptionTermType;

/**
 * @author angarg12
 *
 */
@Scope(Scope.ScopeType.SINGLETON)
@Path("/agreement")
public class Orchestrator extends Component implements IOrchestrator {
	public Orchestrator() throws Exception{
		super();
		
		Properties properties = new Properties();
		properties.load(getClass().getResourceAsStream("/conf/Orchestrator.properties"));
		String service = properties.getProperty("service");
		String version = properties.getProperty("version");
		String epr = properties.getProperty("epr");

		Register register = new Register(Thread.currentThread(), service, version, epr);
		register.start();
	}
	
	@PUT
	public Response startSLA(@HeaderParam("Authorization") String auth, String idSla){
		if(auth == null || securityHandler.authenticate(auth) == false){
			return Response
			.status(HttpStatus.UNAUTHORIZED.getCode())
			.build();
		}

		try {
        	RESTComm comm = new RESTComm("Catalog");
        	comm.setUrl("sla/"+idSla);
    		XMLWrapper wrap = comm.get();

			XmlOptions options = new XmlOptions();
		    options.setLoadStripWhitespace();
		    options.setLoadTrimTextBuffer();
			AgreementPropertiesDocument xmlsla = AgreementPropertiesDocument.Factory.parse(wrap.getFirst("//xmlsla"));

			ServiceDescriptionTermType[] terms = xmlsla.getAgreementProperties().getTerms().getAll().getServiceDescriptionTermArray();
			boolean virtualmachine = false;
			boolean virtualcontainer = false;
			boolean virtualservice = false;

			String serviceSdtId = null;
			for(int i = 0; i < terms.length; i++){
				if(terms[i].getDomNode().getFirstChild().getNodeName().equals("ccpaas:VirtualMachine")){
					virtualmachine = true;
				}
				if(terms[i].getDomNode().getFirstChild().getNodeName().equals("ccpaas:VirtualContainer")){
					virtualcontainer = true;
				}
				if(terms[i].getDomNode().getFirstChild().getNodeName().equals("ccpaas:Service")){
					serviceSdtId = terms[i].getName();
					virtualservice = true;
				}
			}

			String[] eprs = null;
			if(virtualmachine == true){		
            	comm = new RESTComm("InfrastructureConnector");
            	comm.setContentType("text/plain");
        		wrap = comm.post(xmlsla.getAgreementProperties().getAgreementId());
        		eprs = wrap.get("//epr");
			}

			if(virtualcontainer == true){
				comm = new RESTComm("PlatformConnector");
				comm.setUrl(xmlsla.getAgreementProperties().getAgreementId());
	        	comm.setContentType("text/plain");
	        	String eprPlain = "";
	        	for(int i = 0; i < eprs.length; i++){
	        		eprPlain += eprs[i]+"\n";
	        	}
	        	eprPlain = eprPlain.trim();
	    		wrap = comm.post(eprPlain);
	    		
				//VCContextualizer vccont = new VCContextualizer(xmlsla, this);
				//vccont.execute();
			}
			
			if(virtualservice == true){
				/** WARNING contextualization assumes that eprs have a value.
				 * in a proper implementation the SaaSConnector (who is independent)
				 * should obtain this value either getting it from the PaaS Connector (IaaS Connector by bypass)
				 * or produce it by itself. 
				 */
				
				VSContextualizer vscont = new VSContextualizer(eprs, serviceSdtId, xmlsla.getAgreementProperties().getAgreementId());
				vscont.setup();
			}
		} catch (Exception e) {
			e.printStackTrace();
			return Response
			.status(HttpStatus.INTERNAL_SERVER_ERROR.getCode())
			.entity(e.getMessage())
			.build();
		} 
		return Response
		.status(HttpStatus.OK.getCode())
		.build();
	}
	
	@PUT
	@Path("{id}/{servicename}/ServiceTermState/Metadata/Replicas/RangeValue/Exact")
	@Consumes("text/plain")
	public Response deployReplicas(@HeaderParam("Authorization") String auth, @PathParam("id") String idSla, @PathParam("servicename") String serviceName, String numReplicas) {
		if(auth == null || securityHandler.authenticate(auth) == false){
			return Response
			.status(HttpStatus.UNAUTHORIZED.getCode())
			.build();
		}
		
		try {
        	RESTComm comm = new RESTComm("Catalog");
        	comm.setUrl("sla/"+idSla);
    		XMLWrapper wrap = comm.get();

			XmlOptions options = new XmlOptions();
		    options.setLoadStripWhitespace();
		    options.setLoadTrimTextBuffer();
			AgreementPropertiesDocument xmlsla = AgreementPropertiesDocument.Factory.parse(wrap.getFirst("//xmlsla"));

			ServiceDescriptionTermType[] terms = xmlsla.getAgreementProperties().getTerms().getAll().getServiceDescriptionTermArray();
			boolean virtualmachine = false;
			boolean virtualcontainer = false;
			boolean virtualservice = false;
			
			String serviceSdtId = null;
			for(int i = 0; i < terms.length; i++){
				if(terms[i].getDomNode().getFirstChild().getNodeName().equals("ccpaas:VirtualMachine")){
					virtualmachine = true;
				}
				if(terms[i].getDomNode().getFirstChild().getNodeName().equals("ccpaas:VirtualContainer")){
					virtualcontainer = true;
				}
				if(terms[i].getDomNode().getFirstChild().getNodeName().equals("ccpaas:Service")){
					serviceSdtId = terms[i].getName();
					virtualservice = true;
				}
			}
			
			String[] eprs = null;
			if(virtualmachine == true){
            	comm = new RESTComm("InfrastructureConnector");
            	comm.setUrl(idSla+"/"+serviceName+"/ServiceTermState/Metadata/Replicas/RangeValue/Exact");
            	comm.setContentType("text/plain");
        		wrap = comm.post(numReplicas);
        		eprs = wrap.get("//epr");
			}
	
			if(virtualcontainer == true){
				comm = new RESTComm("PlatformConnector");
				comm.setUrl(idSla+"/"+serviceName+"/ServiceTermState/Metadata/Replicas/");
	        	comm.setContentType("text/plain");
	        	String eprPlain = "";
	        	for(int i = 0; i < eprs.length; i++){
	        		eprPlain += eprs[i]+"\n";
	        	}
	        	eprPlain = eprPlain.trim();
	    		wrap = comm.post(eprPlain);
	    		
				//VCContextualizer vccont = new VCContextualizer(xmlsla, this);
				//vccont.execute();
			}
			
			if(virtualservice == true){
				/** WARNING contextualization assumes that eprs have a value.
				 * in a proper implementation the SaaSConnector (who is independent)
				 * should obtain this value either getting it from the PaaS Connector (IaaS Connector by bypass)
				 * or produce it by itself. 
				 */
				
				VSContextualizer vscont = new VSContextualizer(eprs, serviceSdtId, xmlsla.getAgreementProperties().getAgreementId());
				vscont.setup();
			}
		} catch (Exception e) {
			e.printStackTrace();
			return Response
			.status(HttpStatus.INTERNAL_SERVER_ERROR.getCode())
			.entity(e.getMessage())
			.build();
		} 
		return Response
		.status(HttpStatus.OK.getCode())
		.build();
	}
	
	@DELETE
	@Path("{id}")
	public Response finalizeSLA(@HeaderParam("Authorization") String auth, @PathParam("id") String idSla) {
		if(auth == null || securityHandler.authenticate(auth) == false){
			return Response
			.status(HttpStatus.UNAUTHORIZED.getCode())
			.build();
		}
		
		try {
        	RESTComm comm = new RESTComm("Catalog");
        	comm.setUrl("sla/"+idSla);
    		XMLWrapper wrap = comm.get();

			XmlOptions options = new XmlOptions();
		    options.setLoadStripWhitespace();
		    options.setLoadTrimTextBuffer();
			AgreementPropertiesDocument xmlsla = AgreementPropertiesDocument.Factory.parse(wrap.getFirst("//xmlsla"));

			ServiceDescriptionTermType[] terms = xmlsla.getAgreementProperties().getTerms().getAll().getServiceDescriptionTermArray();
			boolean virtualmachine = false;
			boolean virtualcontainer = false;
			boolean virtualservice = false;
			
			String serviceSdtId = null;
			for(int i = 0; i < terms.length; i++){
				if(terms[i].getDomNode().getFirstChild().getNodeName().equals("ccpaas:VirtualMachine")){
					virtualmachine = true;
				}
				if(terms[i].getDomNode().getFirstChild().getNodeName().equals("ccpaas:VirtualContainer")){
					virtualcontainer = true;
				}
				if(terms[i].getDomNode().getFirstChild().getNodeName().equals("ccpaas:Service")){
					serviceSdtId = terms[i].getName();
					virtualservice = true;
				}
			}
			
			if(virtualservice == true){
				/** WARNING contextualization assumes that eprs have a value.
				 * in a proper implementation the SaaSConnector (who is independent)
				 * should obtain this value either getting it from the PaaS Connector (IaaS Connector by bypass)
				 * or produce it by itself. 
				 */
			}
			
			if(virtualcontainer == true){

			}
			
			if(virtualmachine == true){
				comm = new RESTComm("InfrastructureConnector");
	        	comm.setUrl(idSla);
	    		comm.delete();
			}
		} catch (Exception e) {
			e.printStackTrace();
			return Response
			.status(HttpStatus.INTERNAL_SERVER_ERROR.getCode())
			.entity(e.getMessage())
			.build();
		} 
		return Response
		.status(HttpStatus.OK.getCode())
		.build();
	}
	
	@DELETE
	@Path("{id}/{servicename}/ServiceTermState/Metadata/Replicas/RangeValue/Exact/{numReplicas}")
	public Response undeployReplicas(@HeaderParam("Authorization") String auth, @PathParam("id") String idSla, @PathParam("servicename") String serviceName, @PathParam("numReplicas") int numReplicas) {
		if(auth == null || securityHandler.authenticate(auth) == false){
			return Response
			.status(HttpStatus.UNAUTHORIZED.getCode())
			.build();
		}
		
		try {
        	RESTComm comm = new RESTComm("Catalog");
        	comm.setUrl("sla/"+idSla);
    		XMLWrapper wrap = comm.get();

			XmlOptions options = new XmlOptions();
		    options.setLoadStripWhitespace();
		    options.setLoadTrimTextBuffer();
			AgreementPropertiesDocument xmlsla = AgreementPropertiesDocument.Factory.parse(wrap.getFirst("//xmlsla"));

			ServiceDescriptionTermType[] terms = xmlsla.getAgreementProperties().getTerms().getAll().getServiceDescriptionTermArray();
			boolean virtualmachine = false;
			boolean virtualcontainer = false;
			boolean virtualservice = false;
			
			String serviceSdtId = null;
			for(int i = 0; i < terms.length; i++){
				if(terms[i].getDomNode().getFirstChild().getNodeName().equals("ccpaas:VirtualMachine")){
					virtualmachine = true;
				}
				if(terms[i].getDomNode().getFirstChild().getNodeName().equals("ccpaas:VirtualContainer")){
					virtualcontainer = true;
				}
				if(terms[i].getDomNode().getFirstChild().getNodeName().equals("ccpaas:Service")){
					serviceSdtId = terms[i].getName();
					virtualservice = true;
				}
			}
        	comm.setUrl("/vm_instance/search?id_sla="+idSla);
        	wrap = comm.get();
        	
        	String[] eprs = wrap.get("//epr");

			int numberToUndeploy = numReplicas;
			if(eprs.length < numReplicas){
				numberToUndeploy = eprs.length;
			}
        	String[] eprsToUndeploy = new String[numberToUndeploy];
        	String eprPlain = "";
        	for(int i = 0; i < numberToUndeploy; i++){
        		eprPlain += eprs[i]+"\n";
        		eprsToUndeploy[i] = eprs[i];
        	}
        	eprPlain = eprPlain.trim();
			
			if(virtualservice == true){
				/** WARNING contextualization assumes that eprs have a value.
				 * in a proper implementation the SaaSConnector (who is independent)
				 * should obtain this value either getting it from the PaaS Connector (IaaS Connector by bypass)
				 * or produce it by itself. 
				 */
				VSContextualizer vscont = new VSContextualizer(eprsToUndeploy, serviceSdtId, xmlsla.getAgreementProperties().getAgreementId());
				vscont.teardown();
			}
			
			if(virtualcontainer == true){
			}
			
			if(virtualmachine == true){
				comm = new RESTComm("InfrastructureConnector");
	        	comm.setUrl(idSla+"/"+serviceName+"/"+eprPlain);
	    		comm.delete();
			}
		} catch (Exception e) {
			e.printStackTrace();
			return Response
			.status(HttpStatus.INTERNAL_SERVER_ERROR.getCode())
			.entity(e.getMessage())
			.build();
		} 
		
		return Response
		.status(HttpStatus.OK.getCode())
		.build();
	}
}
