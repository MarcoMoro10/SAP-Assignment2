Feature: Nearest drone assignment

  As a Sender,
  I want the nearest available drone to be assigned to my immediate delivery
  so that my package is picked up as quickly as possible.

  Background:
    Given I am logged in as "user-1" with password "Secret#123"
    And I am on the delivery creation page

  Scenario: The nearest eligible drone is assigned
    Given drone "DRN-1" is available "2" km from the pickup point
    And drone "DRN-2" is available "5" km from the pickup point
    And both drones can carry the package
    When I create a delivery with weight "2" kg, starting place "via Emilia, 9", destination place "via Veneto, 5" to ship immediately
    Then drone "DRN-1" should be assigned to the delivery
    And the delivery should be in status "IN_PROGRESS"

  Scenario: The nearest drone is skipped when it cannot carry the package
    Given drone "DRN-1" is available "2" km from the pickup point with max capacity "1" kg
    And drone "DRN-2" is available "5" km from the pickup point with max capacity "5" kg
    When I create a delivery with weight "3" kg, starting place "via Emilia, 9", destination place "via Veneto, 5" to ship immediately
    Then drone "DRN-2" should be assigned to the delivery
    And the delivery should be in status "IN_PROGRESS"
