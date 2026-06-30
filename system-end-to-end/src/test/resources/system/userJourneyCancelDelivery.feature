Feature: Delivery cancellation
  As a sender
  I want to cancel a delivery I created
  so that it is no longer shipped.

  Scenario: Successful delivery cancellation via the API gateway
    Given the system is running
    And I have registered as "marco" with password "Secret#123"
    And I have logged in as "marco"
    And I have created a delivery of weight "2" kg from "via Emilia, 9" to "via Veneto, 5" scheduled in "30" minutes
    When I cancel that delivery
    Then I should get a confirmation that the delivery has been cancelled
