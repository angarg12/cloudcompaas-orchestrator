package org.cloudcompaas.orchestrator;

import javax.ws.rs.core.Response;

/**
 * @author angarg12
 *
 */
public interface IOrchestrator {
	public Response startSLA(String auth, String idSla);
	public Response deployReplicas(String auth, String idSla, String serviceName, String numReplicas);
	public Response finalizeSLA(String auth, String idSla);
	public Response undeployReplicas(String auth, String idSla, String serviceName, int numReplicas);
}
