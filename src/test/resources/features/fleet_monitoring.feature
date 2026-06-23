Feature: Fleet monitoring

  As an Admin,
  I want to monitor the position and status of every drone
  so that I have an overview of the fleet operations.

  Background:
    Given I am logged in as admin "admin-1" with password "Admin#123"

  Scenario: View the position of all drones on the map
    Given the fleet has "3" drones
    When I open the fleet monitoring page
    Then I should see "3" drones on the map
    And each drone should show its current position

  Scenario: View the operational status of a drone
    Given a drone "DRN-1" is in status "IN_DELIVERY" and is carrying a package
    When I open the fleet monitoring page
    Then I should see drone "DRN-1" with status "IN_DELIVERY"
    And drone "DRN-1" should be shown as carrying a package

  Scenario: Drone position updates in real time
    Given I am on the fleet monitoring page
    And a drone "DRN-1" is at position "44.49, 11.34"
    When drone "DRN-1" updates its position to "44.50, 11.35"
    Then the map should eventually show drone "DRN-1" at position "44.50, 11.35"
