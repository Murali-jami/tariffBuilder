package com.xius.TariffBuilder.UserService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.xius.TariffBuilder.exception.TariffInsertException;

import jakarta.transaction.Transactional;

@Service
public class ServiceplanZone {

    private static final Logger logger = LoggerFactory.getLogger(ServiceplanZone.class);

    @Autowired
    @Qualifier("oracleJdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    // ── NEW: delegate DayType / TimeZone / SlotRate cloning ──────────────────
    @Autowired
    private DayTypeCloneService dayTypeCloneService;

    public Long generateNewZoneId() {
        return jdbcTemplate.queryForObject(
                """
                        select nvl(max(ZONE_GROUP_ID), 0) + 1
                        from CS_RAT_ZONE_GROUPS
                        """,
                Long.class);
    }

    @Transactional
    public void cloneZoneIfExists(Long oldZoneId, Long newZoneId, Long networkId, String tpName) {

        if (oldZoneId == null || newZoneId == null) {
            logger.info("Zone clone skipped oldZoneId={} newZoneId={}", oldZoneId, newZoneId);
            return;
        }

        String suffix = "_" + tpName;

        Integer oldRatCount = countZone("CS_RAT_ZONE_GROUPS", oldZoneId);
        Integer oldDreCount = countZone("CS_DRE_RATING_GROUP_DETAILS", oldZoneId);

        if (oldRatCount == 0 && oldDreCount == 0) {
            logger.info("Old zone id {} not found in zone tables. Skipping zone clone.", oldZoneId);
            return;
        }

        if (oldRatCount > 0) {

            Integer newRatCount = countZone("CS_RAT_ZONE_GROUPS", newZoneId);

            if (newRatCount == 0) {
                try {
                    jdbcTemplate.update(
                            """
                                    insert into CS_RAT_ZONE_GROUPS
                                    (
                                        ZONE_GROUP_ID,
                                        ZONE_GROUP_DESC,
                                        NETWORK_ID,
                                        TYPE_OF_SERVICE,
                                        RATING_YN
                                    )
                                    select
                                        ?,
                                        REGEXP_REPLACE(ZONE_GROUP_DESC,'_CL[0-9]+$','') || ?,
                                        NETWORK_ID,
                                        TYPE_OF_SERVICE,
                                        RATING_YN
                                    from CS_RAT_ZONE_GROUPS
                                    where ZONE_GROUP_ID = ?
                                    """,
                            newZoneId,
                            suffix,
                            oldZoneId);
                } catch (Exception ex) {
                    throw new TariffInsertException("cloneZoneGroup", "CS_RAT_ZONE_GROUPS", ex);
                }

                logger.info("CS_RAT_ZONE_GROUPS cloned oldZoneId={} newZoneId={}", oldZoneId, newZoneId);
            } else {
                logger.info("CS_RAT_ZONE_GROUPS already has newZoneId={}. Skipping insert.", newZoneId);
            }
        }

        if (oldDreCount > 0) {

            Integer newDreCount = countZone("CS_DRE_RATING_GROUP_DETAILS", newZoneId);

            if (newDreCount == 0) {
                try {
                    jdbcTemplate.update(
                            """
                                    insert into CS_DRE_RATING_GROUP_DETAILS
                                    (
                                        NETWORK_ID,
                                        ROAMING_NETWORK_ID,
                                        ZONE_GROUP_ID,
                                        ZONE_GROUP_NAME,
                                        APN_ID,
                                        RATING_GROUP_ID,
                                        CALENDAR_ID,
                                        RATING_YN
                                    )
                                    select
                                        NETWORK_ID,
                                        ROAMING_NETWORK_ID,
                                        ?,
                                        REGEXP_REPLACE(ZONE_GROUP_NAME,'_CL[0-9]+$','') || ?,
                                        APN_ID,
                                        RATING_GROUP_ID,
                                        CALENDAR_ID,
                                        RATING_YN
                                    from CS_DRE_RATING_GROUP_DETAILS
                                    where ZONE_GROUP_ID = ?
                                    """,
                            newZoneId,
                            suffix,
                            oldZoneId);
                } catch (Exception ex) {
                    throw new TariffInsertException("cloneRatingGroupDetails", "CS_DRE_RATING_GROUP_DETAILS", ex);
                }

                logger.info("CS_DRE_RATING_GROUP_DETAILS cloned oldZoneId={} newZoneId={}", oldZoneId, newZoneId);
            } else {
                logger.info("CS_DRE_RATING_GROUP_DETAILS already has newZoneId={}. Skipping insert.", newZoneId);
            }
        }

        cloneSlabAndCalendar(oldZoneId, newZoneId, networkId, suffix);

        logger.info("Zone clone completed oldZoneId={} newZoneId={}", oldZoneId, newZoneId);
    }

    private Integer countZone(String tableName, Long zoneId) {

        if (zoneId == null) {
            return 0;
        }

        return jdbcTemplate.queryForObject(
                "select count(*) from " + tableName + " where ZONE_GROUP_ID = ?",
                Integer.class,
                zoneId);
    }

    private void cloneSlabAndCalendar(Long oldZoneId, Long newZoneId, Long networkId, String suffix) {

        List<Map<String, Object>> mappings = jdbcTemplate.queryForList(
                """
                        select SLAB_ID, AIRTIME_CALENDAR
                        from CS_RAT_ZONEGROUP_SLAB_MAPPING
                        where ZONE_GROUP_ID = ?
                        """,
                oldZoneId);

        Map<Long, Long> calendarCache = new HashMap<>();

        for (Map<String, Object> row : mappings) {

            Long oldSlabId = ((Number) row.get("SLAB_ID")).longValue();

            Long oldCalendarId = row.get("AIRTIME_CALENDAR") == null
                    ? null
                    : ((Number) row.get("AIRTIME_CALENDAR")).longValue();

            Long newCalendarId = cloneCalendar(oldCalendarId, suffix, networkId, calendarCache);

            Integer mappingCount = jdbcTemplate.queryForObject(
                    """
                            select count(*)
                            from CS_RAT_ZONEGROUP_SLAB_MAPPING
                            where ZONE_GROUP_ID = ?
                            and SLAB_ID = ?
                            and NETWORK_ID = ?
                            """,
                    Integer.class,
                    newZoneId,
                    oldSlabId,
                    networkId);

            if (mappingCount == null || mappingCount == 0) {
                jdbcTemplate.update(
                        """
                                insert into CS_RAT_ZONEGROUP_SLAB_MAPPING
                                (
                                    ZONE_GROUP_ID,
                                    SLAB_ID,
                                    AIRTIME_CALENDAR,
                                    NETWORK_ID
                                )
                                values (?,?,?,?)
                                """,
                        newZoneId,
                        oldSlabId,
                        newCalendarId,
                        networkId);
            }
        }
    }

    private Long cloneCalendar(Long oldCalendarId,
            String suffix,
            Long networkId,
            Map<Long, Long> calendarCache) {

        if (oldCalendarId == null) {
            return null;
        }

        if (calendarCache.containsKey(oldCalendarId)) {
            return calendarCache.get(oldCalendarId);
        }

        // ── Read the original calendar row ───────────────────────────────────
        Map<String, Object> oldCalRow = jdbcTemplate.queryForMap(
                """
                        select *
                        from RAT_MT_CALENDAR
                        where CALENDAR_ID = ?
                        """,
                oldCalendarId);

        // ── Extract the 7 original DayType IDs ──────────────────────────────
        Long oldSunday = toLong(oldCalRow.get("SUNDAY_DAYTYPE"));
        Long oldMonday = toLong(oldCalRow.get("MONDAY_DAYTYPE"));
        Long oldTuesday = toLong(oldCalRow.get("TUESDAY_DAYTYPE"));
        Long oldWednesday = toLong(oldCalRow.get("WEDNESDAY_DAYTYPE"));
        Long oldThursday = toLong(oldCalRow.get("THURSDAY_DAYTYPE"));
        Long oldFriday = toLong(oldCalRow.get("FRIDAY_DAYTYPE"));
        Long oldSaturday = toLong(oldCalRow.get("SATURDAY_DAYTYPE"));

        logger.info(
                "Original calendar CALENDAR_ID={} DayTypes: Sun={} Mon={} Tue={} Wed={} Thu={} Fri={} Sat={}",
                oldCalendarId,
                oldSunday, oldMonday, oldTuesday, oldWednesday,
                oldThursday, oldFriday, oldSaturday);

        // ── Clone each distinct DayType; cache prevents duplicate cloning ────
        Map<Long, Long> dayTypeCache = new HashMap<>();

        Long newSunday = dayTypeCloneService.cloneDayType(oldSunday, networkId, suffix, dayTypeCache);
        Long newMonday = dayTypeCloneService.cloneDayType(oldMonday, networkId, suffix, dayTypeCache);
        Long newTuesday = dayTypeCloneService.cloneDayType(oldTuesday, networkId, suffix, dayTypeCache);
        Long newWednesday = dayTypeCloneService.cloneDayType(oldWednesday, networkId, suffix, dayTypeCache);
        Long newThursday = dayTypeCloneService.cloneDayType(oldThursday, networkId, suffix, dayTypeCache);
        Long newFriday = dayTypeCloneService.cloneDayType(oldFriday, networkId, suffix, dayTypeCache);
        Long newSaturday = dayTypeCloneService.cloneDayType(oldSaturday, networkId, suffix, dayTypeCache);

        logger.info(
                "DayType mappings for CALENDAR_ID={}: Sun={}->{} Mon={}->{} Tue={}->{} Wed={}->{} Thu={}->{} Fri={}->{} Sat={}->{}",
                oldCalendarId,
                oldSunday, newSunday,
                oldMonday, newMonday,
                oldTuesday, newTuesday,
                oldWednesday, newWednesday,
                oldThursday, newThursday,
                oldFriday, newFriday,
                oldSaturday, newSaturday);

        // ── Generate new CALENDAR_ID ─────────────────────────────────────────
        Long newCalendarId = jdbcTemplate.queryForObject(
                """
                        select nvl(max(CALENDAR_ID),0)+1
                        from RAT_MT_CALENDAR
                        """,
                Long.class);

        logger.info(
                "Inserting RAT_MT_CALENDAR newCalendarId={} with newDayTypes: Sun={} Mon={} Tue={} Wed={} Thu={} Fri={} Sat={}",
                newCalendarId,
                newSunday, newMonday, newTuesday, newWednesday,
                newThursday, newFriday, newSaturday);

        // ── Insert the cloned calendar row using NEW DayType IDs ─────────────
        try {
            jdbcTemplate.update(
                    """
                            insert into RAT_MT_CALENDAR
                            (
                                CALENDAR_ID,
                                SUNDAY_DAYTYPE,
                                MONDAY_DAYTYPE,
                                TUESDAY_DAYTYPE,
                                WEDNESDAY_DAYTYPE,
                                THURSDAY_DAYTYPE,
                                FRIDAY_DAYTYPE,
                                SATURDAY_DAYTYPE,
                                CALENDAR_NAME,
                                DESCRIPTION,
                                NETWORK_ID,
                                DURATION_VOLUME_FLAG
                            )
                            select
                                ?,
                                ?,
                                ?,
                                ?,
                                ?,
                                ?,
                                ?,
                                ?,
                                REGEXP_REPLACE(CALENDAR_NAME,'_CL[0-9]+$','') || ?,
                                REGEXP_REPLACE(DESCRIPTION,'_CL[0-9]+$','') || ?,
                                ?,
                                DURATION_VOLUME_FLAG
                            from RAT_MT_CALENDAR
                            where CALENDAR_ID = ?
                            """,
                    newCalendarId,
                    newSunday,
                    newMonday,
                    newTuesday,
                    newWednesday,
                    newThursday,
                    newFriday,
                    newSaturday,
                    suffix,
                    suffix,
                    networkId,
                    oldCalendarId);
        } catch (Exception ex) {
            logger.error("Error cloning RAT_MT_CALENDAR oldCalendarId={}", oldCalendarId, ex);
            throw new TariffInsertException("cloneCalendar", "RAT_MT_CALENDAR", ex);
        }

        calendarCache.put(oldCalendarId, newCalendarId);

        logger.info("RAT_MT_CALENDAR cloned oldCalendarId={} newCalendarId={}", oldCalendarId, newCalendarId);

        return newCalendarId;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        return ((Number) value).longValue();
    }
}