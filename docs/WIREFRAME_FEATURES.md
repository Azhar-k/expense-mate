# Walleto - Feature Documentation for Wireframe Generation

## Application Overview

Walleto is a comprehensive personal finance management Android application that automates expense tracking through SMS parsing and provides detailed financial insights. The app features a bottom navigation bar for primary screens and a drawer navigation for secondary features.
---

## Key Features Summary

1. **Automated Transaction Tracking**: SMS parsing for major Indian banks and payment methods
2. **Multi-Account Management**: Track multiple bank accounts, wallets, and credit cards
3. **Manual Transaction Entry**: Add transactions manually when SMS not available
4. **Category Management**: Custom categories for expenses and income
5. **Self Transfers**: Record transfers between own accounts
6. **Recurring Payments**: Track subscriptions and bills
7. **Advanced Filtering**: Complex transaction filtering with multiple criteria
8. **Financial Insights**: Monthly summaries with category breakdowns
9. **Account Details**: Individual account transaction history
10. **SMS Scanning**: Manual scan of historical SMS messages
11. **Google Drive Backup**: Cloud backup and restore functionality
12. **Customizable regex**: Users can configure more regex patterns for SMS parsing and transaction processing in addition to the system ones.
---

## Architecture

1. Expense mate ui: A mobile application client
2. Expense mate backend: A spring boot backend service to handle core business logic, APIs and database
3. User management service: A spring boot backend service to allow user registration, login and roles and permissions.


## General notes
- Get the currency correspoding to an account and show the symbol accordingly wherever needed.
- When showing amount, use color-coded: Red for debit, Green for credit
- Use a common theme/components for text input fields, Headings, etc
- Use a standard look and feel for the sections that allow add, edit, delete options of different entities.
- Have form validation before add/edit an item (Mandatory field check, type check etc)
## Data Models

### Transaction
- Amount, Description, Date/Time
- Transaction Type (DEBIT/CREDIT)
- Category, Account (Reference to account and category table)
- Counterparty Name, SMS Body, SMS Sender
- SMS Hash (for duplicate detection)
- Linked Recurring Payment ID
- Exclude from Summary flag

### Account
- Name, Account Number, Bank
- Expiry Date, Description
- Default Account flag
- Currency

### Category
- Name (unique identifier)
- Type (EXPENSE/INCOME)

### Recurring Payment
- Name, Amount, Due Day (1-31)
- Expiry Date
- Completion Status
- Last Completed Date

### Regex
Define one for regex

---

## Navigation Structure
### Bottom Navigation Bar (Primary Navigation)
- **Summary** (Home screen - default)
- **Accounts**
- **Transactions**
### Drawer Navigation (Secondary Navigation)
- User section 
  - Login button if not logged in -> Navigate to login screen
  - Logout button if already logged in
- Recurring Payments
- Categories
- Self Transfer
- Scan SMS
- Settings
- About (placeholder)
---

I will give the screen details one by one

## Screen: Login

 - Allow user regisration and login.
 - It is managed by a separate user service
 - Registration with email, username and password 
 - Login with email and password
 - Call user management service for user registration.
 - Call user management service for login and generate an auth token.
 - Use this auth token when calling the expense mate backend APIs

 When the application is opened, if not logged in, navigate to this screen.
## Screen: Transactions

### Purpose
Comprehensive list of all transactions with advanced filtering capabilities.

### Components

- **Account Dropdown**: Filter transactions by account ("All" or specific account). 
  - By default, show the transactions of default account

#### Filter Options
- **Date Range**:
  - From Date picker (defaults to 30 days ago)
  - To Date picker (defaults to today)
- **Transaction Type**: Dropdown (DEBIT, CREDIT, or empty for all)
- **Free text field**: Search in description or counterparty
- **Description**: Text input for partial matching
- **Counterparty Name**: Text input for partial matching
- **Category**: Dropdown with all categories
- **Recurring Payment**: Dropdown with all recurring payments
- **Amount**: Range filter
- **Exclude from Summary**: Toggle switch
- **Action Buttons**:
  - Apply Filters button
  - Clear Filters button (resets to default 30-day range)
  - Close button (X icon)


#### Transaction List
- Displays transactions in reverse chronological order (newest first)
- **Transaction Item Display**:
  - Date and time
  - Description
  - Amount (color-coded: Red for debit, Green for credit)
  - Category name
  - Account name
  - Transaction type indicator
  - Linked recurring payment if any
- **Empty State**: Message displayed when no transactions match filters

### Add/Edit Transaction

#### Input Fields
- **Amount**: Number input
- **Description**: Text input
- **Date**: Date picker with time selection
- **Transaction Type**: Dropdown (DEBIT/CREDIT)
- **Category**: Dropdown with categories
- **Account**: Dropdown with accounts
- **Counterparty Name**: Text input (optional)
- **Recurring payment**: Dropdown with recurring payments
- **Notes**: Additional text field (if available)


### Interactions
- Default view shows last 30 days of transactions
- Account dropdown filters transactions immediately
- Filter allows for most of the fields of the account
- Add/Edit option
- Link a transaction to a recurring payment and mark the recurring payment as completed
- Exclude the transaction from summary screen
- The currency associated with the corresponding account of the transaction should be shown along with the amount

---

## Screen : Accounts

- Manage all financial accounts (bank accounts, wallets, credit cards).
- Account should be associated with a currency.
- Where ever an amount is shown, the corresponding account's currency should be shown.

### Net balance
Calculated as below
- Net Balance is calculated as:Total Income (All Accounts) - Total Expense (All Accounts)
- Transactions excluded from summary have not been considered for balance calculation.
- An info button with info icon. When clicking on it, explain how balance is calculated

### Account List
- **RecyclerView**: Displays all accounts
- **Account Item Display**:
  - Account name
  - Default account indicator
  - Account Balance 
    - Color-coded (green/red based on positive/negative) 

### Add Account

#### Input Fields
- **Account Name**: Text input (required)
- **Account Number**: Text input (optional)
- **Bank**: Text input (optional)
- **Expiry Date**: Date picker (optional, for credit cards)
- **Description**: Text input (optional)
- **Currency** should be selected from a dropdown

### Account Details Screen (Navigated from Account List)


- **Account Information Header**: Shows account name and details
- **Total Balance Display**:
  - balance amount
  - Color-coded (green/red based on positive/negative)
- **Date Range Selectors**:
  - defaults to 30 days ago

- **Transaction List**: showing transactions for this account within date range
- **Empty State**: Message when no transactions in range

### Interactions
- Add/edit or delete account options
- Select an account as default
- Account should be associated with a currency.
- Where ever an amount is shown, the corresponding account's currency should be shown.
- Click account item to view details
- Set default account (only one can be default)
- Cannot delete default account
- Date range selection updates transaction list
- Transactions scroll to top when date range changes
---

## Screen : Categories

### Purpose
Manage expense and income categories for transaction organization.
#### Category List
- **RecyclerView**: Displays all categories. Different section for Expense and Income categories.
- **Category Item Display**:
  - Category name
  - Category type badge (EXPENSE/INCOME)

### Add/Edit Category

#### Input Fields
- **Category Name**: Text input (required)
- **Category Type**: Dropdown (EXPENSE or INCOME)

### Interactions
- Create custom categories for expenses and income
- Edit category name and type
- Delete categories (with confirmation)
- Categories are used throughout app for transaction classification

---

## Screen : Summary (Home Screen)

### Purpose
Main dashboard showing financial overview with monthly breakdowns and category-wise spending analysis.

#### Header Section
- Shows current month and year (e.g., "January 2024") for which the summaryto be shown. Default current month
- Able to choose the previous months and year (Easy way to navigate among months)
- **Account Dropdown**: AutoCompleteTextView to filter by account or "All"

#### Financial Summary
- **Total Expense**: total expenses for selected period (₹X.XX format)
- **Total Income**: total income for selected period (₹X.XX format)
- **Total Balance**: Calculated balance (Income - Expense)
  - Color coding: Green for positive, Red for negative

#### Category Breakdown Section
- **Toggle Switch**: "Show Category Breakup" - Controls visibility of category breakdown
- **Breakdown Type Toggle**: 
  - Expense Breakdown button
  - Income Breakdown button
- **Show the category wise breakdown**
  - Category name
  - Total amount for that category
  - use a suitable diagram like pie diagram
- **Category Click Action**: Show all transactions for that category in the selected period

### Interactions
- Month navigation updates all financial data
- Account selection filters all calculations
- Toggle between expense and income breakdowns
- Click category to view detailed transactions
- Category breakdown can be hidden/shown via switch
- Do not count the transactions that are excluded from summary

### Data Displayed
- Monthly totals (expense, income, balance)
- Category-wise spending/income breakdown
- Filtered by selected account and month/year

---

## Screen : Self Transfer
  
### Purpose
Record transfers between user's own accounts without affecting net worth.

#### Form Fields
- **From Account**: Dropdown to select source account
- **To Account**: Dropdown to select destination account
- **Amount**: Number input
- **Date**: Date picker with time selection (defaults to current date/time)
- **Category**: Dropdown with expense categories
- **Description**: Text input (optional)

#### Action Button
- **Transfer Button**: button to execute transfer

### Functionality
- Creates two transactions:
  - DEBIT transaction on "From Account"
  - CREDIT transaction on "To Account"
- Both transactions use same amount, date, category, and description
- Validates that from and to accounts are different
- Clears form after successful transfer

### Interactions
- Account dropdowns show all available accounts
- Category dropdown defaults to the default category
- Date defaults to current date/time
---

## Screen : Recurring Payments

### Purpose
Track and manage recurring bills, subscriptions, and payments.


#### Header Section
- **Total Amount**: Sum of all recurring payment amounts
- **Remaining Amount**: Sum of uncompleted payments
- **Select All Button**: Toggle to mark all payments as completed/uncompleted

#### Recurring Payment List
- Displays all recurring payments
- **Payment Item Display**:
  - Payment name
  - Amount
  - Due day (day of month)
  - Expiry date
  - Completion status checkbox
  - Edit button
  - Delete button

### Add/Edit Recurring Payment Dialog

#### Input Fields
- **Payment Name**: Text input (required)
- **Amount**: Number input (required)
- **Due Day**: Number input (1-31, required)
- **Expiry Date**: Date picker (required)


### Interactions
- Add new recurring payment
- Mark payments as completed/uncompleted via checkbox
- Select all button toggles all payments
- Edit payment details
- Delete payments with confirmation
- Highlight the expired recurring payment

---

## Screen : Scan SMS

### Purpose
Manually scan SMS messages from a date range to extract and import transactions.
#### Date Selection
- **From Date**: Date picker button (defaults to today)
- **To Date**: Date picker button (defaults to today)

#### Action Button
- **Scan SMS Button**: Button to initiate scan

#### Status Display
- **Status Text**: Shows scan progress and results
  - "Scan complete! Processed X SMS, Created Y transactions" after completion
  - Error messages if permission denied or scan fails

### Functionality
- Scans SMS inbox for date range
- Parses SMS using same logic as automatic monitoring
- Creates transactions for matched SMS
- Skips duplicates (using SMS hash)
- Logs results (matched, unmatched, duplicates, errors)
- User can also paste the sms body manually in a text area and process it in the same way as that of processing the scanned sms.
- user can either use the scan functionality or manually paste the sms text

### Interactions
- Select date range before scanning
- Requires SMS read permission
- Shows progress during scan
- Displays summary after completion
- Button disabled during scan
- Paste the sms body manually and process it
- API should support bulk upload of SMS

---

## Screen: Settings

### Manage regex
- Allow the user to view, add, edit and delete the regex. 
- Do not allow the system regex to be edited or deleted

### Interactions
- View, Add, edit, and delete custom regex
---

## Permissions Required

### SMS Permissions
- `READ_SMS`: To read SMS messages
- `RECEIVE_SMS`: To receive incoming SMS notifications

### Foreground Service Permission
- `FOREGROUND_SERVICE_DATA_SYNC`: For Android 14+ (for SMS monitoring service)

---

## Background Services

### SMS Monitor Service
- Runs as foreground service
- Monitors incoming SMS messages
- Automatically parses and creates transactions
- Processes SMS in real-time
- Prevents duplicate transactions using SMS hash

---


