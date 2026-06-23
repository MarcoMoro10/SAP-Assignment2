Feature: Create and track a delivery (component, REST black-box)
  As a logged-in Sender,
  I want to create a delivery and track it
  so that my package is shipped and I can follow it in real time.

  # Component test: the delivery-service (with Fleet wired in-process) is exercised directly over
  # its REST API. No api-gateway and no account-service are involved.

  Scenario: Successful immediate delivery creation assigns a drone
    When I create an immediate delivery of weight "2" kg from "via Emilia, 9" to "via Veneto, 5" as "user-1"
    Then the delivery is created with status "IN_PROGRESS"
    And a drone is assigned to the delivery

  Scenario: Successful scheduled delivery creation reserves a drone
    When I create a delivery of weight "2" kg from "via Emilia, 9" to "via Veneto, 5" scheduled in "2" days as "user-1"
    Then the delivery is created with status "SCHEDULED"
    And a drone is assigned to the delivery

  Scenario: Delivery rejected because the package is too heavy for any drone
    When I create an immediate delivery of weight "12" kg from "via Emilia, 9" to "via Veneto, 5" as "user-1"
    Then the response status is 422 with error "No drone can carry this package"

  Scenario: Delivery creation fails with an invalid address
    When I create an immediate delivery of weight "2" kg from "xxxxx" to "via Veneto, 5" as "user-1"
    Then the response status is 400 with error "Invalid address"

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
