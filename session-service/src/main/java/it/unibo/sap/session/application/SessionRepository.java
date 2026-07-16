package it.unibo.sap.session.application;

import it.unibo.sap.common.ddd.Repository;
import it.unibo.sap.common.hexagonal.OutputPort;
import it.unibo.sap.session.domain.Session;
import it.unibo.sap.session.domain.SessionId;

public interface SessionRepository extends Repository<SessionId, Session>, OutputPort {
}
