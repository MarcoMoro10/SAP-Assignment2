Feature: Create and track a delivery (component, REST black-box)
  As a logged-in Sender,
  I want to create a delivery and track it
  so that my package is shipped and I can follow it in real time.

  Scenario: Successful immediate delivery creation assigns a drone
    When I create an immediate delivery of weight "2" kg from "via Emilia, 9" to "via Veneto, 5" as "user-1"
    Then the delivery is created with status "IN_PROGRESS"
    And a drone is assigned to the delivery

  Scenario: Successful scheduled delivery creation reserves a drone
    When I create a delivery of weight "2" kg from "via Emilia, 9" to "via Veneto, 5" scheduled in "2" days as "user-1"
    Then the delivery is created with status "SCHEDULED"
    And a drone is assigned to the delivery

  Scenario: Cancel a scheduled delivery
    When I create a delivery of weight "2" kg from "via Emilia, 9" to "via Veneto, 5" scheduled in "2" days as "user-1"
    Then the delivery is created with status "SCHEDULED"
    When I cancel that delivery as "user-1"
    Then the cancellation succeeds
    When I request the detail of that delivery as "user-1"
    Then the delivery detail shows status "CANCELLED"

  Scenario: Cancelling a delivery in flight is rejected
    When I create an immediate delivery of weight "2" kg from "via Emilia, 9" to "via Veneto, 5" as "user-1"
    Then the delivery is created with status "IN_PROGRESS"
    When I cancel that delivery as "user-1"
    Then the cancellation is rejected because the delivery is in flight

  Scenario: Delivery rejected because the package is too heavy for any drone
    When I create an immediate delivery of weight "12" kg from "via Emilia, 9" to "via Veneto, 5" as "user-1"
    Then the response status is 422 with error "No drone can carry this package"

  Scenario: Delivery creation fails with an invalid address
    When I create an immediate delivery of weight "2" kg from "xxxxx" to "via Veneto, 5" as "user-1"
    Then the response status is 400 with error "Invalid address"

  Scenario: Delivery creation without a deadline is rejected
    When I create an immediate delivery of weight "2" kg from "via Emilia, 9" to "via Veneto, 5" as "user-1" without a deadline
    Then the response status is 400 with error "deadlineMinutes is required and must be greater than 0"

  Scenario: A scheduled delivery with a past shipping time is rejected
    When I create a delivery of weight "2" kg from "via Emilia, 9" to "via Veneto, 5" scheduled in "-1" days as "user-1"
    Then the response status is 400 with error "Invalid shipping time"

  Scenario: A scheduled delivery beyond the maximum horizon is rejected
    When I create a delivery of weight "2" kg from "via Emilia, 9" to "via Veneto, 5" scheduled in "30" days as "user-1"
    Then the response status is 422 with error "Shipping time exceeds the maximum scheduling horizon"

  Scenario: Read back the detail of a created delivery
    When I create an immediate delivery of weight "2" kg from "via Emilia, 9" to "via Veneto, 5" as "user-1"
    Then the delivery is created with status "IN_PROGRESS"
    When I request the detail of that delivery as "user-1"
    Then the delivery detail shows status "IN_PROGRESS"

  Scenario: The detail of a delivery owned by someone else is not found
    When I create an immediate delivery of weight "2" kg from "via Emilia, 9" to "via Veneto, 5" as "user-1"
    Then the delivery is created with status "IN_PROGRESS"
    When I request the detail of that delivery as "intruder"
    Then the response status is 404 with error "Delivery not found"

  Scenario: Start tracking a delivery in progress
    When I create an immediate delivery of weight "2" kg from "via Emilia, 9" to "via Veneto, 5" as "user-1"
    Then the delivery is created with status "IN_PROGRESS"
    When I start tracking that delivery as "user-1"
    Then tracking starts successfully

  Scenario: Tracking a delivery that does not exist is not found
    When I start tracking delivery "does-not-exist" as "user-1"
    Then the response status is 404 with error "Delivery not found"

  Scenario: Admin views the whole fleet
    When the admin requests the fleet view
    Then the fleet view lists 3 drones
    And every drone in the fleet view reports a position and a status
    And all drones in the fleet view are "AVAILABLE"

  Scenario: A drone appears in delivery after a delivery starts
    When I create an immediate delivery of weight "2" kg from "via Emilia, 9" to "via Veneto, 5" as "user-1"
    Then the delivery is created with status "IN_PROGRESS"
    When the admin requests the fleet view
    Then the fleet view lists 3 drones
    And at least one drone is "IN_DELIVERY" and carrying a package

  Scenario: Admin views the scheduled deliveries
    When I create a delivery of weight "2" kg from "via Emilia, 9" to "via Veneto, 5" scheduled in "2" days as "user-1"
    Then the delivery is created with status "SCHEDULED"
    When the admin requests the scheduling view
    Then the scheduling view lists that delivery with a scheduled slot
