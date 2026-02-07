# ExpenseMate - Feature Documentation for Wireframe Generation

## Application Overview

ExpenseMate is a comprehensive personal finance management Android application that automates expense tracking through SMS parsing and provides detailed financial insights. The app features a bottom navigation bar for primary screens and a drawer navigation for secondary features.
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
12. **Privacy-First**: All SMS processing happens locally on device
---

## Navigation Structure
### Bottom Navigation Bar (Primary Navigation)
- **Summary** (Home screen - default)
- **Accounts**
- **Transactions**
### Drawer Navigation (Secondary Navigation)
- User section
- Categories
- Self Transfer
- Recurring Payments
- Scan SMS
- Settings
- About (placeholder)
---

I will give the screen details one by one

## Screen 1: Summary (Home Screen)
### Purpose
Main dashboard showing financial overview with monthly breakdowns and category-wise spending analysis.
### UI Components
#### Header Section
- **Period Display**: Shows current month and year (e.g., "January 2024")
- **Navigation Controls**: 
  - Previous Month button (left arrow icon)
  - Next Month button (right arrow icon)
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
- **Category List**: RecyclerView displaying:
  - Category name
  - Total amount for that category
  - Visual indicator (percentage bar or similar)
- **Category Click Action**: Opens dialog showing all transactions for that category in the selected period
### Interactions
- Month navigation updates all financial data
- Account selection filters all calculations
- Toggle between expense and income breakdowns
- Click category to view detailed transactions
- Category breakdown can be hidden/shown via switch

### Data Displayed
- Monthly totals (expense, income, balance)
- Category-wise spending/income breakdown
- Filtered by selected account and month/year

---

## Screen 2: Transactions

### Purpose
Comprehensive list of all transactions with advanced filtering capabilities.

### UI Components

#### Header Section
- **Account Dropdown**: Filter transactions by account ("All" or specific account)
- **Filter FAB**: Floating Action Button with filter icon to open filter bottom sheet

#### Transaction List
- **RecyclerView**: Displays transactions in reverse chronological order (newest first)
- **Transaction Item Display**:
  - Date and time
  - Description
  - Amount (color-coded: Red for debit, Green for credit)
  - Category name
  - Account name
  - Transaction type indicator
  - Linked recurring payment if any
- **Empty State**: Message displayed when no transactions match filters

#### Floating Action Buttons
- **Add Transaction FAB**: Opens dialog to manually add transaction
- **Filter FAB**: Opens bottom sheet with filter options

### Filter Bottom Sheet

#### Filter Options
- **Date Range**:
  - From Date picker (defaults to 30 days ago)
  - To Date picker (defaults to today)
- **Transaction Type**: Dropdown (DEBIT, CREDIT, or empty for all)
- **Description**: Text input for partial matching
- **Receiver Name**: Text input for partial matching
- **Category**: Dropdown with all categories
- **Recurring Payment**: Dropdown with all recurring payments
- **Amount**: Text input for exact amount match
- **Exclude from Summary**: Toggle switch
- **Action Buttons**:
  - Apply Filters button
  - Clear Filters button (resets to default 30-day range)
  - Close button (X icon)

### Add/Edit Transaction Dialog

#### Input Fields
- **Amount**: Number input
- **Description**: Text input
- **Date**: Date picker with time selection
- **Transaction Type**: Dropdown (DEBIT/CREDIT)
- **Category**: Dropdown with categories
- **Account**: Dropdown with accounts
- **Receiver Name**: Text input (optional)
- **Recurring payment**: Dropdown with recurring payments
- **Notes**: Additional text field (if available)

#### Actions
- Save button
- Cancel button
- Delete button (when editing)

### Interactions
- Default view shows last 30 days of transactions
- Account dropdown filters transactions immediately
- Filter bottom sheet allows complex multi-criteria filtering
- Click transaction item to edit
- Swipe or long-press for delete action (if implemented)
- Add new transaction via FAB
- Link a transaction to a recurring payment
- Exclude the transaction from summary screen

---

## Screen 3: Accounts

### Purpose
Manage all financial accounts (bank accounts, wallets, credit cards).

### UI Components

#### Account List
- **RecyclerView**: Displays all accounts
- **Account Item Display**:
  - Account name
  - Bank name
  - Account number (if available)
  - Default account indicator (star icon)
  - Balance information (if calculated)
  - Edit button
  - Delete button
  - Set as Default button

#### Floating Action Button
- **Add Account FAB**: Opens dialog to add new account

### Add/Edit Account Dialog

#### Input Fields
- **Account Name**: Text input (required)
- **Account Number**: Text input (optional)
- **Bank**: Text input (optional)
- **Expiry Date**: Date picker (optional, for credit cards)
- **Description**: Text input (optional)

#### Actions
- Save button
- Cancel button
- Delete button (when editing, disabled for default account)

### Account Details Screen (Navigated from Account List)

#### UI Components
- **Account Information Header**: Shows account name and details
- **Total Balance Display**: 
  - Large balance amount
  - Color-coded (green/red based on positive/negative)
- **Date Range Selectors**:
  - Start Date button (defaults to 30 days ago)
  - End Date button (defaults to today)
- **Transaction List**: RecyclerView showing transactions for this account within date range
- **Empty State**: Message when no transactions in range

### Interactions
- Click account item to view details
- Set default account (only one can be default)
- Cannot delete default account
- Date range selection updates transaction list
- Transactions scroll to top when date range changes

---

## Screen 4: Categories

### Purpose
Manage expense and income categories for transaction organization.

### UI Components

#### Category List
- **RecyclerView**: Displays all categories
- **Category Item Display**:
  - Category name
  - Category type badge (EXPENSE/INCOME)
  - Edit button
  - Delete button

#### Floating Action Button
- **Add Category FAB**: Opens dialog to add new category

### Add/Edit Category Dialog

#### Input Fields
- **Category Name**: Text input (required)
- **Category Type**: Dropdown (EXPENSE or INCOME)

#### Actions
- Add/Save button
- Cancel button
- Delete button (when editing)

### Special Feature
- Typing "backup" as category name triggers backup data loading (hidden feature)

### Interactions
- Create custom categories for expenses and income
- Edit category name and type
- Delete categories (with confirmation)
- Categories are used throughout app for transaction classification

---

## Screen 5: Self Transfer

### Purpose
Record transfers between user's own accounts without affecting net worth.

### UI Components

#### Form Fields
- **From Account**: Dropdown to select source account
- **To Account**: Dropdown to select destination account
- **Amount**: Number input
- **Date**: Date picker with time selection (defaults to current date/time)
- **Category**: Dropdown with expense categories
- **Description**: Text input (optional)

#### Action Button
- **Transfer Button**: Large button to execute transfer

### Functionality
- Creates two transactions:
  - DEBIT transaction on "From Account"
  - CREDIT transaction on "To Account"
- Both transactions use same amount, date, category, and description
- Validates that from and to accounts are different
- Clears form after successful transfer

### Interactions
- Account dropdowns show all available accounts
- Category defaults to first expense category
- Date defaults to current date/time
- Form validation before transfer execution

---

## Screen 6: Recurring Payments

### Purpose
Track and manage recurring bills, subscriptions, and payments.

### UI Components

#### Header Section
- **Total Amount**: Sum of all recurring payment amounts
- **Remaining Amount**: Sum of uncompleted payments
- **Select All Button**: Toggle to mark all payments as completed/uncompleted

#### Recurring Payment List
- **RecyclerView**: Displays all recurring payments
- **Payment Item Display**:
  - Payment name
  - Amount
  - Due day (day of month)
  - Expiry date
  - Completion status checkbox
  - Edit button
  - Delete button

#### Floating Action Button
- **Add Recurring Payment FAB**: Opens dialog to add new payment

### Add/Edit Recurring Payment Dialog

#### Input Fields
- **Payment Name**: Text input (required)
- **Amount**: Number input (required)
- **Due Day**: Number input (1-31, required)
- **Expiry Date**: Date picker (required)

#### Actions
- Add/Save button
- Cancel button
- Delete button (when editing)

### Interactions
- Mark payments as completed/uncompleted via checkbox
- Select all button toggles all payments
- Edit payment details
- Delete payments with confirmation
- Payments can be linked to transactions

---

## Screen 7: Scan SMS

### Purpose
Manually scan SMS messages from a date range to extract and import transactions.

### UI Components

#### Date Selection
- **From Date**: Date picker button (defaults to today)
- **To Date**: Date picker button (defaults to today)

#### Action Button
- **Scan SMS Button**: Large button to initiate scan

#### Status Display
- **Status Text**: Shows scan progress and results
  - "Scanning SMS..." during scan
  - "Scan complete! Processed X SMS, Created Y transactions" after completion
  - Error messages if permission denied or scan fails

### Functionality
- Scans SMS inbox for date range
- Parses SMS using same logic as automatic monitoring
- Creates transactions for matched SMS
- Skips duplicates (using SMS hash)
- Logs results (matched, unmatched, duplicates, errors)

### Interactions
- Select date range before scanning
- Requires SMS read permission
- Shows progress during scan
- Displays summary after completion
- Button disabled during scan

---

## Screen 8: Settings

### Purpose
Manage app settings, Google Drive backup/restore, and account management.

### UI Components

#### Google Sign-In Section
- **Sign In Button**: Google Sign-In button (when not signed in)
- **Sign Out Button**: Button to sign out (when signed in)
- **Account Status Text**: Shows signed-in email or "Not signed in"

#### Backup & Restore Section
- **Backup Button**: Creates backup to Google Drive (disabled when not signed in)
- **Restore Button**: Restores from Google Drive backup (disabled when not signed in)
- **Delete Old Backups Button**: Removes old backup files from Drive (disabled when not signed in)
- **Status Text**: Shows operation status and results

####
- **Currency selector**

### Functionality

#### Backup
- Exports all database data to Google Drive
- Stores in private folder: `ExpenseMate_Backups`
- Overwrites existing backup
- Confirmation dialog before backup

#### Restore
- Loads backup from Google Drive
- Replaces all local data
- Confirmation dialog (warns about data deletion)
- Shows success/error status

#### Delete Old Backups
- Removes old backup files from Google Drive
- Keeps only recent backups
- Confirmation dialog

### Interactions
- Sign in required for backup/restore features
- Buttons disabled when not signed in
- Confirmation dialogs for destructive actions
- Status updates during operations
- Toast notifications for success/errors

---


## Data Models

### Transaction
- Amount, Description, Date/Time
- Transaction Type (DEBIT/CREDIT)
- Category, Account
- Receiver Name, SMS Body, SMS Sender
- SMS Hash (for duplicate detection)
- Linked Recurring Payment ID
- Exclude from Summary flag

### Account
- Name, Account Number, Bank
- Expiry Date, Description
- Default Account flag

### Category
- Name (unique identifier)
- Type (EXPENSE/INCOME)

### Recurring Payment
- Name, Amount, Due Day (1-31)
- Expiry Date
- Completion Status
- Last Completed Date

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


