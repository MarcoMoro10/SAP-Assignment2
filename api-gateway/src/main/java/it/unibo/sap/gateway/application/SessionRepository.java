package it.unibo.sap.gateway.application;

import it.unibo.sap.common.ddd.Repository;
import it.unibo.sap.common.hexagonal.OutputPort;
import it.unibo.sap.gateway.domain.Session;
import it.unibo.sap.gateway.domain.SessionId;

public interface SessionRepository extends Repository<SessionId, Session>, OutputPort {
}
