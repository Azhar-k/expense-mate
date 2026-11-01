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
        long accountId FK "Logical FK to a virtual Account"
        long realAccountId FK "Logical FK to a real Account"
        boolean isExcludedFromSummary
    }

    CATEGORY {
        long id PK "Auto-generated"
        String name "Unique identifier used in Transaction"
        String type "EXPENSE or INCOME"
        boolean isDefault "One per type, goes on captured payments"
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
        String type "VIRTUAL or REAL"
        String currency
    }

    %% Relationships
    %% Transaction links to a virtual Account via accountId and a real Account via realAccountId
    ACCOUNT ||--o{ TRANSACTION : "accountId"
    ACCOUNT ||--o{ TRANSACTION : "realAccountId"
    
    %% Transaction links to RecurringPayment via linkedRecurringPaymentId
    RECURRING_PAYMENT |o--o{ TRANSACTION : "linkedRecurringPaymentId"

    %% Transaction links to Category via name string (not ID)
    CATEGORY ||--o{ TRANSACTION : "category (name match)"
