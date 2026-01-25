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
        long accountId FK "Logical FK to Account"
        boolean isExcludedFromSummary
    }

    CATEGORY {
        long id PK "Auto-generated"
        String name "Unique identifier used in Transaction"
        String type "EXPENSE or INCOME"
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
        boolean isDefault
    }

    %% Relationships
    %% Transaction links to Account via accountId
    ACCOUNT ||--o{ TRANSACTION : "accountId"
    
    %% Transaction links to RecurringPayment via linkedRecurringPaymentId
    RECURRING_PAYMENT |o--o{ TRANSACTION : "linkedRecurringPaymentId"

    %% Transaction links to Category via name string (not ID)
    CATEGORY ||--o{ TRANSACTION : "category (name match)"
