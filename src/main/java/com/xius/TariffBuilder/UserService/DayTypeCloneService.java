package com.xius.TariffBuilder.UserService;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.xius.TariffBuilder.exception.TariffInsertException;

@Service
public class DayTypeCloneService {

    private static final Logger logger = LoggerFactory.getLogger(DayTypeCloneService.class);

    @Autowired
    @Qualifier("oracleJdbcTemplate")
    private JdbcTemplate jdbcTemplate;

   
    public Long cloneDayType(Long oldDayTypeId,
                             Long networkId,
                             String suffix,
                             Map<Long, Long> dayTypeCache) {

        logger.info("Starting DayType clone for DAYTYPE_ID={} networkId={}", oldDayTypeId, networkId);

        // ── Cache check ──────────────────────────────────────────────────────
        if (dayTypeCache.containsKey(oldDayTypeId)) {
            Long cached = dayTypeCache.get(oldDayTypeId);
            logger.info("DayType already cloned. Returning cached DAYTYPE_ID={} for oldDayTypeId={}",
                    cached, oldDayTypeId);
            return cached;
        }

        // ── Generate new DAYTYPE_ID using MAX+1 ──────────────────────────────
        Long newDayTypeId = generateMaxPlusOne("RAT_MT_DAYTYPE", "DAYTYPE_ID");
        logger.info("Generated DAYTYPE_ID={} for oldDayTypeId={}", newDayTypeId, oldDayTypeId);

        // ── Clone RAT_MT_DAYTYPE row ──────────────────────────────────────────
        cloneDayTypeRow(oldDayTypeId, newDayTypeId, networkId, suffix);

        // ── Clone RAT_TIMEZONE rows (and their dependent slot tables) ─────────
        List<Map<String, Object>> timezoneRows = jdbcTemplate.queryForList(
                """
                select *
                from RAT_TIMEZONE
                where DAYTYPE_ID = ?
                """,
                oldDayTypeId
        );

        logger.info("Found {} TimeZone record(s) for DAYTYPE_ID={}", timezoneRows.size(), oldDayTypeId);

        if (timezoneRows.isEmpty()) {
            logger.warn("No TimeZone records found for DAYTYPE_ID={}. DayType cloned without TimeZone/SlotRate data.",
                    oldDayTypeId);
        }

        for (Map<String, Object> tzRow : timezoneRows) {

            Long oldSlotId = ((Number) tzRow.get("RATESLOT_ID")).longValue();
            logger.info("Processing TimeZone row with RATESLOT_ID={} for oldDayTypeId={}", oldSlotId, oldDayTypeId);

            // Clone RAT_MT_RATESLOTS + RAT_AT_SLOTRATE; returns the new RATESLOT_ID
            Long newSlotId = cloneSlotRate(oldSlotId, networkId);

            // Clone the RAT_TIMEZONE row using the new DAYTYPE_ID and new RATESLOT_ID
            cloneTimezoneRow(newDayTypeId, newSlotId, networkId);
        }

        // ── Cache and return ─────────────────────────────────────────────────
        dayTypeCache.put(oldDayTypeId, newDayTypeId);
        logger.info("Cached DayType mapping {} -> {}", oldDayTypeId, newDayTypeId);
        logger.info("DayType clone completed for oldDayTypeId={}. Returning DAYTYPE_ID={}",
                oldDayTypeId, newDayTypeId);

        return newDayTypeId;
    }

    private void cloneDayTypeRow(Long oldDayTypeId, Long newDayTypeId, Long networkId, String suffix) {

        logger.info("Cloning RAT_MT_DAYTYPE oldDayTypeId={} -> newDayTypeId={} networkId={}",
                oldDayTypeId, newDayTypeId, networkId);

        try {
            jdbcTemplate.update(
                    """
                    insert into RAT_MT_DAYTYPE
                    (
                        DAYTYPE_ID,
                        DAYTYPE_NAME,
                        DESCRIPTION,
                        PRIORITY_ID,
                        NETWORK_ID
                    )
                    select
                        ?,
                        REGEXP_REPLACE(DAYTYPE_NAME, '_CL[0-9]+$', '') || ?,
                        NULL,
                        PRIORITY_ID,
                        ?
                    from RAT_MT_DAYTYPE
                    where DAYTYPE_ID = ?
                    """,
                    newDayTypeId,
                    suffix,
                    networkId,
                    oldDayTypeId
            );
        } catch (Exception ex) {
            logger.error("Error cloning RAT_MT_DAYTYPE oldDayTypeId={}", oldDayTypeId, ex);
            throw new TariffInsertException("cloneDayType", "RAT_MT_DAYTYPE", ex);
        }

        logger.info("Successfully inserted RAT_MT_DAYTYPE newDayTypeId={}", newDayTypeId);
    }

    private Long cloneSlotRate(Long oldSlotId, Long networkId) {

        // ── Step 1: generate new RATESLOT_ID from the master slot table ──────
        Long newSlotId = generateMaxPlusOne("RAT_MT_RATESLOTS", "RATESLOT_ID");
        logger.info("Generated RATESLOT_ID={} for oldSlotId={}", newSlotId, oldSlotId);

        // ── Step 2: clone RAT_MT_RATESLOTS — use supplied networkId ──────────
        try {
            jdbcTemplate.update(
                    """
                    insert into RAT_MT_RATESLOTS
                    (
                        RATESLOT_ID,
                        NETWORK_ID,
                        SCHEME_TYPE,
                        FLAT_INCR
                    )
                    select
                        ?,
                        ?,
                        SCHEME_TYPE,
                        FLAT_INCR
                    from RAT_MT_RATESLOTS
                    where RATESLOT_ID = ?
                    """,
                    newSlotId,
                    networkId,
                    oldSlotId
            );
        } catch (Exception ex) {
            logger.error("Error cloning RAT_MT_RATESLOTS oldSlotId={}", oldSlotId, ex);
            throw new TariffInsertException("cloneRateSlot", "RAT_MT_RATESLOTS", ex);
        }

        logger.info("Successfully inserted RAT_MT_RATESLOTS newSlotId={} networkId={}", newSlotId, networkId);

        // ── Step 3: clone RAT_AT_SLOTRATE rows (SLOT_ID = RATESLOT_ID) ───────
        List<Map<String, Object>> stepRows = jdbcTemplate.queryForList(
                """
                select STEP_NO, MOC_BTU, MOC_RATE, MTC_BTU, MTC_RATE
                from RAT_AT_SLOTRATE
                where SLOT_ID = ?
                """,
                oldSlotId
        );

        logger.info("Found {} RAT_AT_SLOTRATE STEP_NO record(s) for SLOT_ID={}", stepRows.size(), oldSlotId);

        if (stepRows.isEmpty()) {
            logger.warn("No RAT_AT_SLOTRATE records found for SLOT_ID={}. Slot cloned with no rate steps.", oldSlotId);
        }

        for (Map<String, Object> step : stepRows) {
            try {
                jdbcTemplate.update(
                        """
                        insert into RAT_AT_SLOTRATE
                        (
                            SLOT_ID,
                            STEP_NO,
                            MOC_BTU,
                            MOC_RATE,
                            MTC_BTU,
                            MTC_RATE
                        )
                        values (?, ?, ?, ?, ?, ?)
                        """,
                        newSlotId,
                        step.get("STEP_NO"),
                        step.get("MOC_BTU"),
                        step.get("MOC_RATE"),
                        step.get("MTC_BTU"),
                        step.get("MTC_RATE")
                );
            } catch (Exception ex) {
                logger.error("Error cloning RAT_AT_SLOTRATE oldSlotId={} STEP_NO={}", oldSlotId, step.get("STEP_NO"), ex);
                throw new TariffInsertException("cloneSlotRate", "RAT_AT_SLOTRATE", ex);
            }
        }

        logger.info("Successfully cloned {} RAT_AT_SLOTRATE record(s) for newSlotId={}", stepRows.size(), newSlotId);
        return newSlotId;
    }

   
    private void cloneTimezoneRow(Long newDayTypeId, Long newSlotId, Long networkId) {

        logger.info("Cloning RAT_TIMEZONE -> newDayTypeId={} newSlotId={} networkId={}",
                newDayTypeId, newSlotId, networkId);

        try {
            jdbcTemplate.update(
                    """
                    insert into RAT_TIMEZONE
                    (
                        FROM_TIME,
                        TO_TIME,
                        RATESLOT_ID,
                        DAYTYPE_ID,
                        NAME,
                        DESCRIPTION,
                        NETWORK_ID
                    )
                    values (
                        TRUNC(SYSDATE),
                        TRUNC(SYSDATE) + INTERVAL '23:59:59' HOUR TO SECOND,
                        ?, ?, NULL, NULL, ?
                    )
                    """,
                    newSlotId,
                    newDayTypeId,
                    networkId
            );
        } catch (Exception ex) {
            logger.error("Error cloning RAT_TIMEZONE newDayTypeId={} newSlotId={}", newDayTypeId, newSlotId, ex);
            throw new TariffInsertException("cloneTimeZone", "RAT_TIMEZONE", ex);
        }

        logger.info("Successfully inserted RAT_TIMEZONE newDayTypeId={} newSlotId={}", newDayTypeId, newSlotId);
    }


    private Long generateMaxPlusOne(String tableName, String primaryKeyColumn) {

        logger.info("Generating ID via MAX({})+1 from {}", primaryKeyColumn, tableName);

        Long id = jdbcTemplate.queryForObject(
                "select NVL(MAX(" + primaryKeyColumn + "), 0) + 1 from " + tableName,
                Long.class
        );

        logger.info("Generated ID={} from {}", id, tableName);
        return id;
    }
}