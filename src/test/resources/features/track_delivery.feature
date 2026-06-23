Feature: Track a delivery
  As a logged-in Sender,
  I want to track my delivery in real time
  so that I know the current position of my package and when it will arrive.

  Background:
    Given I am logged in as "user-1" with password "Secret#123"

  Scenario: Track a delivery in progress
    Given I have a delivery "DLV-100" in status "IN_PROGRESS"
    When I open the tracking page for delivery "DLV-100"
    Then I should see the current status of the delivery
    And I should see the current position of the drone
    And I should see the estimated time remaining

  Scenario: Estimated time remaining updates while the drone moves
    Given I have a delivery "DLV-100" in status "IN_PROGRESS"
    And I am on the tracking page for delivery "DLV-100"
    When the drone moves to a new position closer to the destination
    Then the tracking view of delivery "DLV-100" should eventually show a decreased estimated time remaining

  Scenario: Tracking a delivery that does not belong to the user is not allowed
    Given a delivery "DLV-200" belongs to another user
    When I open the tracking page for delivery "DLV-200"
    Then I should see the error "Delivery not found"

  Scenario: Track a completed delivery
    Given I have a delivery "DLV-100" in status "DELIVERED"
    When I open the tracking page for delivery "DLV-100"
    Then I should see the status "DELIVERED"
    And the estimated time remaining should be "0"
