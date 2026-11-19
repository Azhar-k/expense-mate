erDiagram
    TRANSACTION {
        long id PK "Auto-generated"
        double amount
        String description
        long date "Timestamp"
        String transactionType "DEBIT or CREDIT"
        String receiverName
        String smsBody
        String smsSender
        String category "Refers to Category.name"
        long linkedRecurringPaymentId FK "Logical FK to RecurringPayment"
        String smsHash
        long accountId FK "Logical FK to a fund (Account of type VIRTUAL)"
        long realAccountId FK "Logical FK to a real Account"
        boolean isExcludedFromSummary
        long loanId FK "Logical FK to Loan: a debit lent it, a credit returns it"
    }

    LOAN {
        long id PK "Auto-generated"
        String person
        double amount "What was lent"
        long date "Timestamp"
        String note
        double returnedManually "Returns recorded by hand, such as cash"
        boolean closedManually "Marked as returned by hand"
        long closedDate "Last done by hand: marked returned, or a return recorded"
    }

    CATEGORY {
        long id PK "Auto-generated"
        String name "Unique identifier used in Transaction"
        String type "EXPENSE or INCOME"
        boolean isDefault "One per type, goes on captured payments"
        int sortOrder "User's order within the type, 0 until reordered"
    }

    RECURRING_PAYMENT {
        long id PK "Auto-generated"
        String name
        double amount
        int dueDay
        long expiryDate "Timestamp"
        boolean isCompleted
        long lastCompletedDate "Timestamp"
    }

    ACCOUNT {
        long id PK "Auto-generated"
        String name
        String accountNumber
        String bank
        long expiryDate "Timestamp"
        String description
        boolean isDefault "One per type"
        String type "VIRTUAL (a fund) or REAL"
        int sortOrder "User's order within the type, 0 until reordered"
        String currency "Real accounts only"
        double target "Funds only: amount to save up to, optional"
        long targetDate "Funds only: when to reach the target, optional"
    }

    BUDGET {
        long id PK "Auto-generated"
        String name
        double amount "Limit per calendar month"
        String currency "Only spending in it counts"
        int sortOrder "0 until reordered"
    }

    BUDGET_CATEGORY {
        long budgetId PK "Logical FK to Budget"
        String category PK "Category name, as transactions store it"
    }

    %% Relationships
    %% A budget counts debits from the default fund in its categories, excluding transfers and excluded rows
    BUDGET ||--o{ BUDGET_CATEGORY : "budgetId"
    %% Transaction links to a fund via accountId and a real Account via realAccountId
    ACCOUNT ||--o{ TRANSACTION : "accountId"
    ACCOUNT ||--o{ TRANSACTION : "realAccountId"
    
    %% Transaction links to RecurringPayment via linkedRecurringPaymentId
    RECURRING_PAYMENT |o--o{ TRANSACTION : "linkedRecurringPaymentId"

    %% Transaction links to Loan via loanId; linked credits add up to what has come back
    LOAN |o--o{ TRANSACTION : "loanId"

    %% Transaction links to Category via name string (not ID)
    CATEGORY ||--o{ TRANSACTION : "category (name match)"
