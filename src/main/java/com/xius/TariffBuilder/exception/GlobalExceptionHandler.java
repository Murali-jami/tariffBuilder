package com.xius.TariffBuilder.exception;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // -------------------------------------------------------------------------
    // TariffInsertException — DB failure during clone / approve
    // -------------------------------------------------------------------------
    @ExceptionHandler(TariffInsertException.class)
    public ResponseEntity<Map<String, Object>> handleTariffInsert(TariffInsertException ex) {

        // Full detail goes to logs only — never to the UI response
        logger.error("TariffInsertException step={} table={}", ex.getStep(), ex.getFailedTable(), ex);

        String[] resolved = resolveOracleMessage(ex);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status",  "error");
        body.put("message", resolved[0]);
        body.put("reason",  resolved[1]);
        return ResponseEntity.ok(body);
    }

    // -------------------------------------------------------------------------
    // Request / parameter errors
    // -------------------------------------------------------------------------
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParam(MissingServletRequestParameterException ex) {
        return error("Missing required parameter.",
                     "Parameter '" + ex.getParameterName() + "' is required but was not provided.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return error("Invalid parameter value.",
                     "Parameter '" + ex.getName() + "' received invalid value '" + ex.getValue() + "'.");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException ex) {
        return error("Invalid request body.",
                     "The request body is missing or not valid JSON.");
    }

    // -------------------------------------------------------------------------
    // Null / generic runtime errors
    // -------------------------------------------------------------------------
    @ExceptionHandler(NullPointerException.class)
    public ResponseEntity<Map<String, Object>> handleNpe(NullPointerException ex) {
        logger.error("NullPointerException", ex);
        return error("A required value was not found.",
                     "A required field is missing or null in the request or source data.");
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntime(RuntimeException ex) {
        logger.error("RuntimeException", ex);

        // Never forward raw exception messages to the UI — they can contain SQL text.
        if (isSqlRelated(ex)) {
            return error("Tariff package creation failed.",
                         "A database error occurred. Check server logs for details.");
        }

        return error("An error occurred during processing.",
                     "An unexpected error occurred. Check server logs for details.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAll(Exception ex) {
        logger.error("Unexpected exception", ex);

        if (isSqlRelated(ex)) {
            String table = extractTableFromMessage(ex.getMessage());
            return error(
                "A database error occurred" + (table != null ? " on table: " + table : "") + ".",
                "An unexpected database error occurred. Check server logs for details."
            );
        }

        return error("An unexpected error occurred.",
                     "Check server logs for details.");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /** Builds the response with both message (what) and reason (why). */
    private ResponseEntity<Map<String, Object>> error(String message, String reason) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status",  "error");
        body.put("message", message);
        body.put("reason",  reason);
        return ResponseEntity.ok(body);
    }

    /** Returns true for any SQL/JDBC/DB-related exception in the cause chain. */
    private boolean isSqlRelated(Throwable ex) {
        Throwable t = ex;
        while (t != null) {
            String name = t.getClass().getName();
            if (t instanceof java.sql.SQLException
                    || name.contains("DataAccessException")
                    || name.contains("JdbcSQLException")
                    || name.contains("UncategorizedSQLException")
                    || name.contains("DataIntegrityViolationException")
                    || name.contains("CannotGetJdbcConnectionException")
                    || name.contains("TransactionSystemException")
                    || name.contains("EmptyResultDataAccessException")) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    /**
     * Extracts a table name from a Spring JDBC / Oracle message.
     * Looks for "into TABLE_NAME" or "from TABLE_NAME" patterns.
     */
    private String extractTableFromMessage(String msg) {
        if (msg == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("(?i)(?:into|from|update|table)\\s+([A-Z_][A-Z0-9_$#]{2,})")
            .matcher(msg);
        return m.find() ? m.group(1).toUpperCase() : null;
    }

    /**
     * Returns String[2]:
     *   [0] — message : what happened  (short, table + operation)
     *   [1] — reason  : why it happened (Oracle error explanation)
     *
     * Oracle codes handled:
     *   ORA-00001  unique constraint violated
     *   ORA-01400  cannot insert NULL
     *   ORA-02291  FK parent key not found
     *   ORA-02292  FK child records exist
     *   ORA-00904  invalid column name
     *   ORA-00942  table or view does not exist
     *   ORA-01722  invalid number
     *   ORA-12899  value too large for column
     *   ORA-17041  missing bind parameter
     *   ORA-00060  deadlock
     *   ORA-08177  serialization failure
     *   ORA-04031  out of shared memory
     */
    private String[] resolveOracleMessage(TariffInsertException ex) {

        String what = "Tariff package creation failed.";

        SQLException sql = ex.getSqlCause();
        if (sql == null) {
            // Log the raw cause internally but never send it to the UI
            return new String[]{ what, "An unexpected error occurred. Check server logs for details." };
        }

        int    code   = sql.getErrorCode();
        String oraMsg = sql.getMessage();

        switch (code) {

            case 1:
                String constraint = extractConstraintName(oraMsg);
                return new String[]{
                    what,
                    "A record with the same key already exists"
                        + (constraint != null ? " (constraint: " + constraint + ")" : "")
                        + ". The same TP/ATP may have already been cloned."
                };

            case 1400:
                String nullCol = extractColumnName(oraMsg);
                return new String[]{
                    what,
                    "Mandatory field" + (nullCol != null ? " '" + nullCol + "'" : "")
                        + " is missing or null in the source data."
                };

            case 2291:
                String fkParent = extractConstraintName(oraMsg);
                return new String[]{
                    what,
                    "A referenced parent record does not exist"
                        + (fkParent != null ? " (FK: " + fkParent + ")" : "")
                        + ". The parent record may have been deleted."
                };

            case 2292:
                String fkChild = extractConstraintName(oraMsg);
                return new String[]{
                    what,
                    "Cannot complete — child records still reference this record"
                        + (fkChild != null ? " (FK: " + fkChild + ")" : "") + "."
                };

            case 904:
                return new String[]{
                    what,
                    "An invalid column name was used in the query. Contact support (ORA-00904)."
                };

            case 942:
                return new String[]{
                    what,
                    "Table '" + ex.getFailedTable() + "' does not exist in the database. Contact support (ORA-00942)."
                };

            case 1722:
                return new String[]{
                    what,
                    "A numeric field received a non-numeric value. Check source data types (ORA-01722)."
                };

            case 12899:
                String largeCol = extractColumnName(oraMsg);
                return new String[]{
                    what,
                    "Value too long for field" + (largeCol != null ? " '" + largeCol + "'" : "")
                        + ". Check source data length (ORA-12899)."
                };

            case 17041:
                return new String[]{
                    what,
                    "Internal query parameter count mismatch. Contact support (ORA-17041)."
                };

            case 60:
                return new String[]{
                    what,
                    "A database deadlock was detected. Please retry the operation (ORA-00060)."
                };

            case 8177:
                return new String[]{
                    what,
                    "Concurrent modification conflict — another transaction updated the same data. Please retry (ORA-08177)."
                };

            case 4031:
                return new String[]{
                    what,
                    "Database is low on shared memory. Contact the DBA (ORA-04031)."
                };

            default:
                String oraCode = extractOraCode(oraMsg);
                return new String[]{
                    what,
                    oraCode != null
                        ? "Unexpected database error (" + oraCode + "). Check server logs for details."
                        : "Unexpected database error. Check server logs for details."
                };
        }
    }

    /** Extracts "(SCHEMA.CONSTRAINT_NAME)" from ORA-00001 / ORA-02291 messages. */
    private String extractConstraintName(String msg) {
        if (msg == null) return null;
        java.util.regex.Matcher m =
            java.util.regex.Pattern.compile("\\(([\\w$.]+\\.[\\w$.]+)\\)").matcher(msg);
        return m.find() ? m.group(1) : null;
    }

    /** Extracts column name from ("SCHEMA"."TABLE"."COLUMN") in ORA-01400 / ORA-12899. */
    private String extractColumnName(String msg) {
        if (msg == null) return null;
        java.util.regex.Matcher m =
            java.util.regex.Pattern.compile("\"[^\"]+\"\\s*\\.\\s*\"[^\"]+\"\\s*\\.\\s*\"([^\"]+)\"").matcher(msg);
        return m.find() ? m.group(1) : null;
    }

    /** Extracts just "ORA-XXXXX" from a raw Oracle message. */
    private String extractOraCode(String msg) {
        if (msg == null) return null;
        java.util.regex.Matcher m =
            java.util.regex.Pattern.compile("ORA-\\d+").matcher(msg);
        return m.find() ? m.group() : null;
    }

    /** Strips SQL text from a Spring JDBC message — removes everything after "for SQL [". */
    private String cleanMessage(String msg) {
        if (msg == null) return "Unknown error";
        int sqlIdx = msg.indexOf("for SQL [");
        if (sqlIdx > 0) msg = msg.substring(0, sqlIdx).trim();
        msg = msg.replace("PreparedStatementCallback; ", "");
        msg = msg.replace("uncategorized SQLException ", "").trim();
        return msg.isEmpty() ? "Unknown error" : msg;
    }
}