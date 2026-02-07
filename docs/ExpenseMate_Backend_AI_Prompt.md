You are an expert backend engineer and system architect.

Generate a production-grade backend server for an application called walleto, a personal finance and expense tracking system.
Now we are inside an empty spring boot project. Work on this project.

## 1. Architecture Responsibilities

- Client will be a mobile application
- All persistence and business logic must live in this backend server.
- Use spring boot and hibernate as the framework
- Use flyway migration
- Use postgress as the database server
- Have clear interfaces and abstractions for persistence layer so that we can replace database (eg: postgres to mysql) later easily
- All the data should be stored against a tenant id.
- For now, tenantId can be the user id retrieved from the jwt token.
- All the interface of the persistence layer should accept the tenantId as a parameter as it is mandatory to query the database
- The application should automatically create database for the given tenant id if not exist.
- Persistence layer should determine the database to be connected dynamically based on the tenantId of the query.
- Also store the tenantId as a field in all tables.
- Use standard exception handling logics. 
- Differentiate between the exception or error messages that can be shown to the user vs the internal errors or exceptions.
- Have proper info level and error logging. 
- The tenant id and the user id should be there for all the logs. Set at the thread level.
---

## 2. Authentication & Authorization

An external User Service handles:
- User registration
- Login
- Roles and permissions
- JWT token generation

The walleto backend:
- Must NOT implement login or registration
- Must validate JWT tokens on every request
- Must extract userId from JWT
- Must enforce authorization using token claims
- The instructions for implementing authentication and authorization can be found at AUTHENTICATION_AUTHORIZATION_GUIDE.md
---

## 4. Components

### Accounts
- POST /accounts
- GET /accounts
- PUT /accounts/{id}
- DELETE /accounts/{id}
- PUT /accounts/{id}/default (mark an account as default)
- GET /accounts/default (get the default account)

#### Account entity
- id
- tenantId
- name
- accountNumber
- bank
- currency
- expiryDate
- description
- isDefault

#### Functionalities
- Only one default account per user
- Default account cannot be deleted
- currency is mandatory for the account.
- Only allow the standard set of currencies

### Categories
- POST /categories
- GET /categories
- PUT /categories/{id}
- DELETE /categories/{id}


#### Category entity
- id
- tenantId
- name (unique per user)
- type (EXPENSE | INCOME)

### Transactions
- POST /transactions
- PUT /transactions/{id}
- DELETE /transactions/{id}
- POST /transactions/search (Search through the transactions)
- PATCH /transactions/exclude_from_summary
- PATCH /transactions/link_recurring_payment/{recurringPaymentId}


#### Transaction entity
- id
- tenantId
- amount
- transactionType (DEBIT | CREDIT)
- dateTime
- description
- categoryId
- accountId
- counterpartyName
- smsBody
- smsSender
- smsHash
- recurringPaymentId
- isExcludeFromSummary

#### Functionalities
- SMS hash used for duplicate detection
- Category type must match transaction type
- Logic to generate the sms hash is as follows
private String generateSmsHash(String smsBody, String smsSender) {
        if (smsBody == null || smsSender == null) {
            Log.d("Transaction", "SMS hash generation failed: null body or sender");
            return null;
        }
        // Normalize the strings by trimming and converting to lowercase
        String normalizedBody = smsBody.trim().toLowerCase();
        String normalizedSender = smsSender.trim().toLowerCase();
        String combined = normalizedBody + "|" + normalizedSender;
        String hash = String.valueOf(combined.hashCode());
        Log.d("Transaction", "Generated SMS hash: " + hash);
        Log.d("Transaction", "Original SMS body: [" + smsBody + "]");
        Log.d("Transaction", "Original SMS sender: [" + smsSender + "]");
        Log.d("Transaction", "Normalized combined: [" + combined + "]");
        return hash;
    }

- Link a transaction to a recurring payment and mark the recurring payment as completed
- Exclude the transaction from summary screen

#### Filter Options
- **Date Range**:
  - From Date  (defaults to 30 days ago)
  - To Date  (defaults to today)
- **Transaction Type**: Dropdown (DEBIT, CREDIT, or empty for all)
- **Free text field**: Search in description or counterparty
- **Description**: Text input for partial matching
- **Counterparty Name**: Text input for partial matching
- **Category Id**: Category id
- **Account Id**: Account id
- **Recurring Payment**: Dropdown with all recurring payments
- **Amount**: Range filter
- **Exclude from Summary**: Toggle switch

### Recurring Payments
- POST /recurring-payments
- GET /recurring-payments
- PUT /recurring-payments/{id}
- DELETE /recurring-payments/{id}
- PUT /recurring-payments/{id}/complete
- PUT /recurring-payments/toggle-all

#### RecurringPayment Entity
- id
- tenantId
- name
- amount
- dueDay
- expiryDate
- isCompleted
- lastCompletedDate

### SMS Processing
- POST /sms/process
- POST /sms/process/bulk

#### Functionalities
- Parse the sms and identify the transaction details and create a transaction if eligible.
- Get the regex from the database and use it for pattern matching snd identify the transactions.

### Regex Management
- GET /regex
- POST /regex
- PUT /regex/{id}
- DELETE /regex/{id}

---

### Audit log

#### Functionality
- Record each of the activity in the application as an audit log

#### AuditLog
Long id;
String tenantId;
String entityType; // e.g., "Transaction", "Account"
Long entityId; // ID of the entity being audited
AuditAction action; // CREATE, UPDATE, DELETE
String username; // User who performed the action
LocalDateTime timestamp;
String changes; // JSON representation of changed fields (for UPDATE operations)
String description; // Human-readable description
String requestPath; // API endpoint that triggered the action
String requestMethod; // HTTP method (GET, POST, PUT, DELETE)

---

### Balance
- GET /balance/{accountId}
- GET /balance (Retrieve the net balance across the accounts)

#### Functionalities
- Scan through the transaction table to calculate the balance
- Net Balance is calculated as:Total Income (All Accounts) - Total Expense (All Accounts)
- Balance of a single account is calculated as: total income of that account - total expense of that account
- Transactions excluded from summary have not been considered for balance calculation.

### Summary
- GET /summary/{accountId}
- GET /summary/{accountId}/categories/expense (Category wise breakdown of the expenses)
- GET /summary/{accountId}/categories/income (Category wise breakdown of the income)

#### Summary consist of
- **Total Expense**: total expenses for selected period (₹X.XX format) for the given account
- **Total Income**: total income for selected period (₹X.XX format) for the given account
- **Total Balance**: Calculated balance (Income - Expense) 
#### Category wise breakdown
- Category name
- Total amount (expense or income depending on the api input) for that category


### Transfers
- POST /transfers


#### Functionality
- Creates two transactions:
  - DEBIT transaction on "From Account"
  - CREDIT transaction on "To Account"
- Both transactions use same amount, date, category, and description
- Validates that from and to accounts are different

#### Form Fields
- **From Account id**: source account
- **To Account id**: destination account
- **Amount**: Number input
- **LocalDateTime**: defaults to current date/time
- **Category Id**: Category id 
- **Description**: Text input



## 5. Non-Functional Requirements

- JWT validation on every request
- User-scoped data access
- UTC timestamps
- Decimal-safe money handling
- Transactional consistency
- Centralized error handling
- Proper HTTP status codes

---

## 6. What Not To Implement

- User registration
- User login
- Password management

Handled by external User Service.
