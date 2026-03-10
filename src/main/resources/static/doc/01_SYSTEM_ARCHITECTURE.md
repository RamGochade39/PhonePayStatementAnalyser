# PhonePay Transaction App - System Architecture

## Overview
PhonePay Transaction App is a Spring Boot web application designed to parse PhonePay PDF statement files and provide comprehensive transaction analytics and insights through a modern web interface.

## Technology Stack
- **Backend**: Java 21 + Spring Boot 3.x
- **Database**: MySQL 8.0+
- **Frontend**: HTML5, CSS3, JavaScript (Vanilla)
- **Build Tool**: Maven
- **PDF Processing**: Apache PDFBox
- **Server Port**: 8080
- **Database Port**: 3306

## System Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                         CLIENT TIER (Frontend)                      │
│                                                                     │
│  ┌────────────────┐  ┌──────────────┐  ┌──────────────┐           │
│  │  upload.html   │  │  index.html  │  │ dashboard.html│          │
│  │  (Entry Point) │  │   (Main UI)  │  │  (Alt View)  │          │
│  └────────┬───────┘  └──────┬───────┘  └──────┬───────┘           │
│           │                 │                 │                   │
│           └─────────────────┼─────────────────┘                   │
│                             │                                     │
│                      RESTful API Calls                            │
│                    (HTTP/CORS Enabled)                           │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                      http://localhost:8080/api
                             │
┌────────────────────────────┴────────────────────────────────────────┐
│                    APPLICATION TIER (Backend)                       │
│                                                                     │
│  ┌──────────────────────────────────────────────────────────────┐ │
│  │              Spring Boot Web Application                     │ │
│  │                                                              │ │
│  │  ┌────────────────────────────────────────────────────────┐ │ │
│  │  │              REST Controllers Layer                   │ │ │
│  │  │  ┌──────────────────┐      ┌──────────────────────┐  │ │ │
│  │  │  │ TransactionCtrl  │      │ DashboardController │  │ │ │
│  │  │  │ (CRUD & Queries) │      │ (View Navigation)   │  │ │ │
│  │  │  └────────┬─────────┘      └─────────────────────┘  │ │ │
│  │  └───────────┼──────────────────────────────────────────┘ │ │
│  │              │                                            │ │
│  │  ┌───────────┴──────────────────────────────────────────┐ │ │
│  │  │           Service Layer (Business Logic)            │ │ │
│  │  │  ┌─────────────────────┐  ┌──────────────────────┐  │ │ │
│  │  │  │ TransactionService  │  │ TransactionDataMgr  │  │ │ │
│  │  │  │ • PDF Processing    │  │ • Session Storage   │  │ │ │
│  │  │  │ • Data Aggregation  │  │ • In-Memory Cache   │  │ │ │
│  │  │  │ • Analytics Logic   │  │                      │  │ │ │
│  │  │  └────────┬────────────┘  └──────────────────────┘  │ │ │
│  │  └───────────┼────────────────────────────────────────┘ │ │
│  │              │                                            │ │
│  │  ┌───────────┴──────────────────────────────────────────┐ │ │
│  │  │              Utility Layer                           │ │ │
│  │  │  ┌─────────────────┐      ┌─────────────────────┐   │ │ │
│  │  │  │    PdfUtil      │      │  StatementParser    │   │ │ │
│  │  │  │ • PDF Extraction│      │ • Text Parsing      │   │ │ │
│  │  │  │ • Text Cleanup  │      │ • Data Extraction   │   │ │ │
│  │  │  └─────────────────┘      │ • Validation        │   │ │ │
│  │  │                            └─────────────────────┘   │ │ │
│  │  └──────────────────────────────────────────────────────┘ │ │
│  │              │                                            │ │
│  │  ┌───────────┴──────────────────────────────────────────┐ │ │
│  │  │         Repository Layer (Data Access)             │ │ │
│  │  │  ┌──────────────────────────────────────────────┐   │ │ │
│  │  │  │    TransactionRepository (JPA)              │   │ │ │
│  │  │  │ • Entity Management                          │   │ │ │
│  │  │  │ • Query Methods                              │   │ │ │
│  │  │  │ • Custom Queries (JPQL & Native SQL)        │   │ │ │
│  │  │  └──────────────────────────────────────────────┘   │ │ │
│  │  └──────────────────────────────────────────────────────┘ │ │
│  │              │                                            │ │
│  │  ┌───────────┴──────────────────────────────────────────┐ │ │
│  │  │           Entity Model (JPA Entities)              │ │ │
│  │  │  ┌──────────────┐ ┌──────────────┐ ┌───────────┐   │ │ │
│  │  │  │ Transaction  │ │ TransType    │ │ TransCat  │   │ │ │
│  │  │  │ (Main Data)  │ │ (DEBIT/CRD)  │ │ (Category)│   │ │ │
│  │  │  └──────────────┘ └──────────────┘ └───────────┘   │ │ │
│  │  └──────────────────────────────────────────────────────┘ │ │
│  │                                                           │ │
│  └──────────────────────────────────────────────────────────┘ │
│                             │                                │
│              JPA / Hibernate ORM Mapping                    │
│                             │                                │
└─────────────────────────────┼────────────────────────────────┘
                              │
┌─────────────────────────────┴────────────────────────────────┐
│              DATA PERSISTENCE TIER (Database)                │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐  │
│  │        MySQL Database (statement_db)                │  │
│  │                                                      │  │
│  │  ┌──────────────────────────────────────────────┐  │  │
│  │  │         transactions_ram (Table)             │  │  │
│  │  │  • id (PK)                                   │  │  │
│  │  │  • transaction_date                          │  │  │
│  │  │  • transaction_time                          │  │  │
│  │  │  • description                               │  │  │
│  │  │  • transaction_id (Unique)                   │  │  │
│  │  │  • utr                                       │  │  │
│  │  │  • type (DEBIT/CREDIT)                       │  │  │
│  │  │  • amount                                    │  │  │
│  │  └──────────────────────────────────────────────┘  │  │
│  │                                                      │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

## Key Components

### 1. Controllers
- **TransactionController**: Handles all transaction-related API endpoints
- **DashboardController**: Handles view navigation

### 2. Services
- **TransactionService**: Core business logic for transaction processing and analytics
- **TransactionDataManager**: Session-based in-memory data management

### 3. Utilities
- **PdfUtil**: PDF text extraction using Apache PDFBox
- **StatementParser**: Parses extracted text into Transaction objects

### 4. Data Access
- **TransactionRepository**: JPA repository with custom query methods

### 5. Entities
- **Transaction**: Main entity representing a bank transaction
- **TransactionType**: Enum (DEBIT, CREDIT)
- **TransactionCategory**: Category classification for transactions

## Database Schema

```sql
CREATE TABLE transactions_ram (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    transaction_date DATE,
    transaction_time TIME,
    description VARCHAR(255),
    transaction_id VARCHAR(255) UNIQUE NOT NULL,
    utr VARCHAR(255),
    type ENUM('DEBIT', 'CREDIT'),
    amount DOUBLE
);
```

## Request-Response Flow

```
User Upload PDF
    ↓
POST /api/transactions/upload
    ↓
TransactionController.uploadPdf()
    ↓
PdfUtil.extractText()  → Raw Text
    ↓
StatementParser.parse()  → Transaction Objects
    ↓
TransactionRepository.save()  → Database
    ↓
TransactionDataManager.store()  → Session Cache
    ↓
Return Success Response
    ↓
Frontend Displays Data
```

## Security Features
- **CORS Enabled**: All endpoints accessible from frontend
- **Session Management**: Unique session ID for each user upload
- **Password Protected PDFs**: Support for password-protected statement files
- **Duplicate Prevention**: Transaction ID uniqueness constraint prevents duplicate entries

## Deployment Configuration
- **Port**: 8080
- **Database URL**: jdbc:mysql://localhost:3306/statement_db
- **Database User**: root
- **JPA DDL**: Auto-update (hibernate.ddl-auto=update)
- **Batch Size**: 2000 records per batch for performance

## Data Flow Summary
1. User uploads PDF through web interface
2. Backend extracts text from PDF
3. Parser converts text to Transaction objects
4. Duplicate check via transaction_id
5. Data persisted to MySQL
6. Data cached in session memory
7. Frontend fetches via REST API endpoints
8. Client-side JavaScript aggregates and displays analytics

