Feature: User registration, login and delivery creation
  As a sender
  I want to register, login and create a delivery
  so that my package can be shipped from one place to another.

  Scenario: Successful registration, login and immediate delivery creation
    Given the system is running
    And there is no account with username "marco"
    When I register as a sender with username "marco" and password "Secret#123"
    Then I should get a confirmation that my account has been created
    When I login with username "marco" and password "Secret#123"
    Then I should get a session confirming I am logged in
    When I create a delivery of weight "2" kg from "via Emilia, 9" to "via Veneto, 5" to ship immediately
    Then I should get a confirmation that the delivery has been created and its delivery id
    When I request the detail of that delivery
    Then I should get its detail with weight "2" kg, pickup "via Emilia, 9" and destination "via Veneto, 5"
