Feature: Account registration and access (component, REST black-box)
  As a new user,
  I want to register and log into the account-service
  so that I can later request and track drone deliveries.

  Scenario: Successful registration
    When I register with username "user-1" and password "Secret#123"
    Then the account is created with role "SENDER"

  Scenario: Registration fails with an already used username
    Given an account already exists with username "user-1"
    When I register with username "user-1" and password "Secret#123"
    Then the response status is 409 with error "Username already taken"

  Scenario: Successful login
    Given an account already exists with username "user-1"
    When I log in with username "user-1" and password "Secret#123"
    Then I am authenticated with role "SENDER"

  Scenario: Login fails with a wrong password
    Given an account already exists with username "user-1"
    When I log in with username "user-1" and password "WrongPass#1"
    Then the response status is 401 with error "Invalid credentials"

  Scenario: Login fails for an unknown account
    When I log in with username "ghost" and password "whatever"
    Then the response status is 404 with error "Account not found"

  Scenario: Successful admin login
    When I log in with username "admin-1" and password "Admin#123"
    Then I am authenticated with role "ADMIN"
