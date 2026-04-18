# NetWatch — Real-Time IP Tracking & Analytics System

Java backend (Javalin + HikariCP + JDBC) + MySQL schema + dark-mode admin dashboard.

## Stack
- **Backend**: Java 21, Javalin 6, HikariCP 5, MySQL Connector/J 8
- **Database**: MySQL 8+
- **Frontend**: HTML5, CSS3, Vanilla JS, Chart.js

## Prerequisites
- Java JDK 21+
- Apache Maven 3.8+
- MySQL Server 8.0+

## Setup

### 1. Create the database
```bash
mysql -u root -p < schema.sql
```

### 2. Set environment variables
```powershell
$env:DB_USER     = "root"
$env:DB_PASSWORD = "your_password"
$env:PORT        = "7070"
```

### 3. Build
```bash
mvn clean package -q
```

### 4. Run
```bash
java -jar target/netwatch.jar
```

Open **http://localhost:7070**

## API Endpoints

| Method | Path             | Description                        |
|--------|------------------|------------------------------------|
| POST   | /api/track       | Record an access event             |
| GET    | /api/analytics   | Full analytics payload             |
| GET    | /api/logs        | Recent logs (`?limit=50`)          |
| GET    | /api/origins     | Top IPs by request count           |
| GET    | /api/health      | Liveness probe                     |

## Database Schema

**network_access_logs** — one row per HTTP request (ip, method, endpoint, user_agent, status_code, accessed_at)

**ip_statistics** — aggregated stats per IP (request_count, first_seen, last_seen, most_visited_endpoint, is_blocked)

## Configuration

| Variable      | Default                                  |
|---------------|------------------------------------------|
| DB_URL        | jdbc:mysql://localhost:3306/netwatch     |
| DB_USER       | root                                     |
| DB_PASSWORD   | password                                 |
| PORT          | 7070                                     |
