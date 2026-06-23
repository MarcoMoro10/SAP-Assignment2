Feature: Delivery scheduling (automatic; Admin observes)

  Scenario: An Admin reviews the daily schedule of a drone (read-only)
    Given I am logged in as admin "admin-1" with password "Admin#123"
    And drone "DRN-1" has "2" scheduled deliveries today
    When I open the scheduling view for drone "DRN-1"
    Then I should see "2" scheduled deliveries

  Scenario: A scheduled delivery automatically reserves a drone for the requested slot
    Given a scheduled delivery "DLV-300" is planned for "10:00" today
    When the system schedules it onto drone "DRN-1"
    Then drone "DRN-1" should be reserved for that slot
    And drone "DRN-1" should be in status "RESERVED"
    And drone "DRN-1" should not be assignable to another delivery in that slot

  Scenario: Scheduling fails when no drone is free for the requested slot
    Given all drones are already reserved for "10:00" today
    When a scheduled delivery is requested for that slot
    Then the request should be rejected with the error "No drone available for the requested time"
