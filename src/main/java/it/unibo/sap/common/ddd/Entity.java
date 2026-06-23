package it.unibo.sap.common.ddd;


public interface Entity<ID extends Identifier<?>> {

    ID getId();
}
