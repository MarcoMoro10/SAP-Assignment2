Feature: Delivery tracking
  As a sender
  I want to track my delivery in real time
  so that I know its current position and the estimated time remaining.

  Scenario: Successful delivery tracking and stop
    Given the system is running
    And I have registered as "marco" with password "Secret#123"
    And I have logged in as "marco"
    And I have created a delivery of weight "2" kg from "via Emilia, 9" to "via Veneto, 5" to ship immediately
    When I start tracking that delivery
    Then I should receive a confirmation that tracking has started
    When I request the current status of that delivery
    Then I should get its current status and estimated time remaining
    When I stop tracking that delivery
    Then I should receive a confirmation that tracking has stopped

  Scenario: Start tracking a scheduled delivery
    Given the system is running
    And I have registered as "marco" with password "Secret#123"
    And I have logged in as "marco"
    And I have created a delivery of weight "2" kg from "via Emilia, 9" to "via Veneto, 5" scheduled in "30" minutes
    When I start tracking that delivery
    Then I should receive a confirmation that tracking has started for the scheduled delivery
