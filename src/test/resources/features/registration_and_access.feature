Feature: Registration and access
  As a new user,
  I want to register and log into the service
  so that I can request and track drone deliveries.

  Scenario: Successful registration
    Given I am on the registration page
    When I register with username "user-1" and password "Secret#123"
    Then I should see a confirmation that my account has been created
    And I should be able to log in

  Scenario: Registration fails with an already used username
    Given a user already exists with username "user-1"
    And I am on the registration page
    When I register with username "user-1" and password "Secret#123"
    Then I should see the error "Username already taken"
    And the account should not be created

  Scenario: Successful login
    Given I am a registered user "user-1" with password "Secret#123"
    And I am on the login page
    When I log in with username "user-1" and password "Secret#123"
    Then I should be authenticated
    And I should be redirected to my home page

  Scenario: Login fails with wrong password
    Given I am a registered user "user-1" with password "Secret#123"
    And I am on the login page
    When I log in with username "user-1" and password "WrongPass#1"
    Then I should see the error "Invalid credentials"
    And I should not be authenticated

  Scenario: Successful admin login
    Given I am a registered admin "admin-1" with password "Admin#123"
    And I am on the login page
    When I log in with username "admin-1" and password "Admin#123"
    Then I should be authenticated as an admin
    And I should be redirected to the fleet monitoring home page
