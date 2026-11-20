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
        long fundId FK "Logical FK to Fund: what the money is for"
        long accountId FK "Logical FK to Account: where the money is"
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
        long fromFundId FK "Logical FK to Fund: a top-up moves money from it"
        long toFundId FK "Logical FK to Fund: a top-up moves money into it"
    }

    FUND {
        long id PK "Auto-generated"
        String name
        String description
        boolean isDefault "Exactly one: everyday money, which budgets track"
        int sortOrder "User's order, 0 until reordered"
        double target "Amount to save up to, optional"
        long targetDate "When to reach the target, optional"
    }

    ACCOUNT {
        long id PK "Auto-generated"
        String name
        String accountNumber
        String bank
        long expiryDate "Timestamp"
        String description
        boolean isDefault "Exactly one"
        int sortOrder "User's order, 0 until reordered"
        String currency "ISO 4217; every transaction in it is in this currency"
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
    %% Transaction links to a Fund via fundId and an Account via accountId; the ids come from separate tables
    FUND ||--o{ TRANSACTION : "fundId"
    ACCOUNT ||--o{ TRANSACTION : "accountId"

    %% A bill's top-up moves money between two funds
    FUND |o--o{ RECURRING_PAYMENT : "fromFundId"
    FUND |o--o{ RECURRING_PAYMENT : "toFundId"
    
    %% Transaction links to RecurringPayment via linkedRecurringPaymentId
    RECURRING_PAYMENT |o--o{ TRANSACTION : "linkedRecurringPaymentId"

    %% Transaction links to Loan via loanId; linked credits add up to what has come back
    LOAN |o--o{ TRANSACTION : "loanId"

    %% Transaction links to Category via name string (not ID)
    CATEGORY ||--o{ TRANSACTION : "category (name match)"
