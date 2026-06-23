Feature: Fleet telemetry (internal drone simulation)
  As the Shipping on the Air system,
  I want the internally simulated drones to update their position and status
  so that delivery tracking and fleet monitoring reflect the real state of the fleet.

  Background:
    Given drone "DRN-1" is a known drone in the fleet
    And drone "DRN-1" is assigned to delivery "DLV-100" in status "IN_PROGRESS"

  Scenario: A valid position update is recorded by the Fleet module
    When drone "DRN-1" updates its position to "44.50, 11.35" with status "IN_DELIVERY"
    Then the update should be applied
    And the current position of drone "DRN-1" should be "44.50, 11.35"
    And a "Position Updated" event should be published for drone "DRN-1"

  Scenario: A valid position update is eventually reflected in delivery tracking
    When drone "DRN-1" updates its position to "44.50, 11.35" with status "IN_DELIVERY"
    Then the update should be applied
    And the tracking view of delivery "DLV-100" should eventually show position "44.50, 11.35"

  Scenario: An update for an unknown drone violates a Fleet precondition
    When an update is issued for an unknown drone "DRN-999" with position "44.50, 11.35" and status "IN_DELIVERY"
    Then the update should be rejected with the error "Unknown drone"
    And no delivery state should be changed

  Scenario: A position with out-of-range coordinates violates the Position invariant
    When drone "DRN-1" attempts to update its position to "999.0, 999.0" with status "IN_DELIVERY"
    Then the update should be rejected with the error "Invalid position"
    And the last known position of drone "DRN-1" should not change

  Scenario: Arrival marks the drone as arrived and eventually completes the delivery
    Given the destination of delivery "DLV-100" is at position "44.55, 11.40"
    When drone "DRN-1" updates its position to "44.55, 11.40" with status "ARRIVED"
    Then the update should be applied
    And drone "DRN-1" should be in status "ARRIVED"
    And a "Drone Arrived" event should be published for drone "DRN-1"
    And delivery "DLV-100" should eventually be in status "DELIVERED"
    And the estimated time remaining for delivery "DLV-100" should eventually be "0"

  Scenario: A drone going out of service mid-flight abolishes its delivery
    When drone "DRN-1" updates its status to "OUT_OF_SERVICE"
    Then the update should be applied
    And drone "DRN-1" should be in status "OUT_OF_SERVICE"
    And a "Drone Out Of Service" event should be published for drone "DRN-1"
    And delivery "DLV-100" should eventually be in status "ABOLISHED"
    And the drone reservation for delivery "DLV-100" should eventually be released

  Scenario: An idle drone updates fleet state without affecting any delivery
    Given drone "DRN-2" is a known drone with no assigned delivery
    When drone "DRN-2" updates its position to "44.49, 11.34" with status "AVAILABLE"
    Then the update should be applied
    And the current position of drone "DRN-2" should be "44.49, 11.34"
    And no delivery state should be changed
