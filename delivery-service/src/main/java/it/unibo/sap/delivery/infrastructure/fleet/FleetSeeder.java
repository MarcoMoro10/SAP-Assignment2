package it.unibo.sap.delivery.infrastructure.fleet;

import it.unibo.sap.delivery.domain.fleet.Coordinates;
import it.unibo.sap.delivery.domain.fleet.Drone;
import it.unibo.sap.delivery.domain.fleet.DroneId;
import it.unibo.sap.delivery.domain.fleet.PayloadCapacity;
import it.unibo.sap.delivery.domain.fleet.Position;
public final class FleetSeeder {

    private FleetSeeder() {
    }

    public static void seed(final InMemoryDroneRepository repository) {
        repository.save(Drone.create(DroneId.of("DRN-1"),
                Position.at(new Coordinates(44.49, 11.34)), new PayloadCapacity(5.0)));
        repository.save(Drone.create(DroneId.of("DRN-2"),
                Position.at(new Coordinates(44.50, 11.35)), new PayloadCapacity(5.0)));
        repository.save(Drone.create(DroneId.of("DRN-3"),
                Position.at(new Coordinates(44.51, 11.33)), new PayloadCapacity(10.0)));
    }
}
