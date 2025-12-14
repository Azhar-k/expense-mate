# ExpenseMate - Application Features

ExpenseMate is a comprehensive personal finance management application designed to automate expense tracking and provide detailed insights into your spending habits. Below is a detailed breakdown of its key features.

## 1. Automated Transaction Tracking (SMS Parsing)
The core feature of ExpenseMate is its ability to automatically detect and record financial transactions by safely monitoring incoming SMS messages. It eliminates the need for manual data entry for every transaction.

*   **Bank Support**: Automatically parses SMS from major banks including:
    *   **ICICI Bank** (Debit, Credit, NEFT, UPI)
    *   **Kotak Mahindra Bank** (Debit, Credit, UPI)
    *   **State Bank of India (SBI)** (Credit Card spends)
    *   **Federal Bank** (Debit, Credit, UPI)
*   **Wallet & Card Support**:
    *   **Pluxee (Sodexo)**: Tracks meal card spends and credits.
    *   **Credit Cards**: Generic pattern matching for other credit card spends.
*   **Universal Payment Methods**:
    *   **UPI**: Detects UPI transactions (debit and credit) from various providers.
    *   **NEFT**: Tracks NEFT credits and debits.
*   **Smart Parsing**: Extracts key details including:
    *   Transaction Amount
    *   Transaction Type (Debit vs. Credit)
    *   Merchant/Receiver or Sender Name
    *   Date and Time
*   **Duplicate Detection**: Intelligent hashing prevents the same transaction from being recorded twice.

## 2. Comprehensive Account Management
Manage all your financial sources in one place.

*   **Multiple Accounts**: Create and track multiple accounts such as Bank Accounts, Cash Wallets, Upl Wallets, and Credit Cards.
*   **Balance Tracking**: Keep an eye on the current balance of each account.
*   **Self-Transfers**: Record transfers between your own accounts (e.g., withdrawing cash from an ATM, moving money from Savings to Wallet) without affecting your total net worth.

## 3. Expense & Income Management
Beyond automated tracking, the app offers full control over your financial records.

*   **Manual Entry**: Manually add cash transactions or any expenses that didn't generate an SMS.
*   **Categorization**: Organize transactions into custom categories (e.g., Food, Travel, Rent, Salary) for better analysis.
*   **Editing**: Modify details of any transaction (amount, category, date, notes) at any time.

## 4. Recurring Payments
Never miss a bill or subscription renewal.

*   **Subscription Tracking**: Set up recurring payments for monthly bills like Netflix, Rent, Electricity, or SIPs.
*   **Management**: View and edit upcoming and past recurring expenses.

## 5. Financial Insights & Summaries
Visualize your financial health.

*   **Category-wise Breakdown**: View spending distribution across different categories to identify where your money goes.
*   **Transaction History**: A detailed list of all past transactions with filtering options.

## 6. Cloud Backup & Restore
Keep your data safe and portable.

*   **Google Drive Integration**: Seamlessly backup your financial data to your personal Google Drive.
*   **Restore**: Easily restore your data when switching phones or reinstalling the app.
*   **Data Privacy**: Backups are stored in a private folder (`ExpenseMate_Backups`) in your own Google Drive account.

## 7. Privacy & Security
*   **Local Processing**: SMS parsing happens entirely on your device. Your sensitive financial SMS data is processed locally and not sent to any third-party server for analysis.
*   **Permissions**: Requests only necessary permissions (`READ_SMS`, `RECEIVE_SMS`) to function.
