package com.igot.cb.profile.service;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.profile.model.WorkflowServiceHistoryEvent;
import com.igot.cb.transactional.cassandrautils.CassandraOperation;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.Constants;
import com.igot.cb.util.ProjectUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Applies the org-transfer/designation-change service-history approval events published by
 * sunbird-cb-workflow to the user's serviceHistory context in user_extended_profile.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ServiceHistoryApprovalServiceImpl implements ServiceHistoryApprovalService {

    private static final String ACTION_ORG_TRANSFER = "org-transfer";
    private static final String ACTION_DESIGNATION_CHANGE = "designation-change";

    private final CassandraOperation cassandraOperation;

    private final ObjectMapper mapper;

    private final ProjectUtil projectUtil;

    @Override
    public boolean processServiceHistoryApprovalEvent(List<WorkflowServiceHistoryEvent> events) {
        if (CollectionUtils.isEmpty(events) || events.size() != 2) {
            log.warn("Invalid service history approval event payload, expected 2 entries but got: {}",
                    events == null ? 0 : events.size());
            return false;
        }

        WorkflowServiceHistoryEvent previousEvent = findEntryByType(events, Constants.ENTRY_TYPE_PREVIOUS);
        WorkflowServiceHistoryEvent currentEvent = findEntryByType(events, Constants.ENTRY_TYPE_CURRENT);
        if (previousEvent == null || currentEvent == null) {
            log.warn("Missing PREVIOUS/CURRENT entry in service history approval event for wfId: {}",
                    events.get(0).getWfId());
            return false;
        }

        String userId = currentEvent.getUserId();
        if (!isUserActive(userId)) {
            log.warn("Skipping service history approval event for inactive/unknown userId: {}, wfId: {}", userId,
                    currentEvent.getWfId());
            return false;
        }

        if (Constants.APPROVAL_TYPE_DESIGNATION.equalsIgnoreCase(currentEvent.getApprovalType())) {
            return applyDesignationChange(userId, previousEvent, currentEvent);
        }
        return applyOrgTransfer(userId, previousEvent, currentEvent);
    }

    /**
     * Applies an org-transfer approval, mirroring {@link #applyDesignationChange}: orgId is
     * required to differ between PREVIOUS and CURRENT; designation is carried through unchanged
     * and is not checked. The highest-index entry is validated against the PREVIOUS event's orgId
     * only.
     */
    private boolean applyOrgTransfer(String userId, WorkflowServiceHistoryEvent previousEvent,
            WorkflowServiceHistoryEvent currentEvent) {
        if (StringUtils.equals(previousEvent.getOrgId(), currentEvent.getOrgId())) {
            log.info(
                    "Previous and current orgId are both '{}' for userId: {}, wfId: {}; already up to date, skipping.",
                    currentEvent.getOrgId(), userId, currentEvent.getWfId());
            return false;
        }
        return closeAndAppendMatchingEntry(userId, currentEvent, ACTION_ORG_TRANSFER,
                entry -> StringUtils.equals(previousEvent.getOrgId(), (String) entry.get(Constants.ORG_ID)));
    }

    /**
     * Applies a designation-change approval, mirroring {@link #applyOrgTransfer}: the highest-index
     * entry is validated against the PREVIOUS event's orgId and designation together.
     */
    private boolean applyDesignationChange(String userId, WorkflowServiceHistoryEvent previousEvent,
            WorkflowServiceHistoryEvent currentEvent) {
        if (StringUtils.equals(previousEvent.getDesignation(), currentEvent.getDesignation())) {
            log.info(
                    "Previous and current designation are both '{}' for userId: {}, wfId: {}; already up to date, skipping.",
                    currentEvent.getDesignation(), userId, currentEvent.getWfId());
            return false;
        }
        return closeAndAppendMatchingEntry(userId, currentEvent, ACTION_DESIGNATION_CHANGE,
                entry -> matchesPreviousEvent(entry, previousEvent));
    }

    /**
     * Shared close-and-append flow for both approval types: loads the existing serviceHistory list,
     * finds the highest-index entry, validates it with {@code matchesPrevious}, closes it out, and
     * appends a new entry at highestIndex + 1, persisting and logging under the given action label.
     */
    private boolean closeAndAppendMatchingEntry(String userId, WorkflowServiceHistoryEvent currentEvent,
            String actionLabel, Predicate<Map<String, Object>> matchesPrevious) {
        List<Map<String, Object>> existingList = getExistingContextData(userId);
        Map<String, Object> highestIndexEntry = findHighestIndexEntry(existingList);
        if (highestIndexEntry == null) {
            log.warn("No existing service history entries found for userId: {}, wfId: {}", userId,
                    currentEvent.getWfId());
            return false;
        }

        if (!matchesPrevious.test(highestIndexEntry)) {
            log.warn(
                    "Highest-index service history entry does not match PREVIOUS event for userId: {}, wfId: {}; skipping.",
                    userId, currentEvent.getWfId());
            return false;
        }

        int newIndex = toIndex(highestIndexEntry.get(Constants.INDEX)) + 1;
        highestIndexEntry.put(Constants.END_DATE, OffsetDateTime.now().toString());
        highestIndexEntry.put(Constants.CURRENTLY_WORKING, Boolean.FALSE.toString());
        existingList.add(buildIndexedServiceHistoryEntry(currentEvent, newIndex));

        if (!saveContextData(userId, existingList)) {
            log.error("Failed to persist {} service history update for userId: {}, wfId: {}", actionLabel, userId,
                    currentEvent.getWfId());
            return false;
        }

        log.info("Applied {} service history event for userId: {}, wfId: {}", actionLabel, userId,
                currentEvent.getWfId());
        return true;
    }

    private boolean matchesPreviousEvent(Map<String, Object> entry, WorkflowServiceHistoryEvent previousEvent) {
        return StringUtils.equals(previousEvent.getOrgId(), (String) entry.get(Constants.ORG_ID))
                && StringUtils.equals(previousEvent.getDesignation(), (String) entry.get(Constants.DESIGNATION));
    }

    private Map<String, Object> findHighestIndexEntry(List<Map<String, Object>> existingList) {
        return existingList.stream()
                .max(Comparator.comparingInt(entry -> toIndex(entry.get(Constants.INDEX))))
                .orElse(null);
    }

    private int toIndex(Object indexValue) {
        if (indexValue instanceof Number number) {
            return number.intValue();
        }
        if (indexValue instanceof String str && StringUtils.isNotBlank(str)) {
            try {
                return Integer.parseInt(str);
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
    }

    private Map<String, Object> buildIndexedServiceHistoryEntry(WorkflowServiceHistoryEvent currentEvent,
            int index) {
        Map<String, Object> entry = buildServiceHistoryEntry(currentEvent);
        entry.put(Constants.INDEX, index);
        return entry;
    }

    private WorkflowServiceHistoryEvent findEntryByType(List<WorkflowServiceHistoryEvent> events, String entryType) {
        return events.stream()
                .filter(event -> entryType.equalsIgnoreCase(event.getEntryType()))
                .findFirst()
                .orElse(null);
    }

    private Map<String, Object> buildServiceHistoryEntry(WorkflowServiceHistoryEvent currentEvent) {
        Map<String, Object> entry = new HashMap<>();
        entry.put(Constants.UUID, UUID.randomUUID().toString());
        entry.put(Constants.DESIGNATION, currentEvent.getDesignation());
        entry.put(Constants.ORG_NAME, currentEvent.getOrgName());
        entry.put(Constants.ORG_ID, currentEvent.getOrgId());
        entry.put(Constants.CURRENTLY_WORKING, Boolean.TRUE.toString());
        entry.put(Constants.APPROVED_VIA_WORKFLOW, Boolean.TRUE.toString());
        return entry;
    }

    private boolean isUserActive(String userId) {
        if (StringUtils.isBlank(userId)) {
            return false;
        }
        List<Map<String, Object>> userRecords = cassandraOperation.getRecordsByPropertiesByKey(
                Constants.KEYSPACE_SUNBIRD, Constants.USER, Map.of(Constants.ID, userId),
                List.of(Constants.ID, Constants.ISDELETED), userId);
        if (CollectionUtils.isEmpty(userRecords)) {
            return false;
        }
        return !Boolean.TRUE.equals(userRecords.get(0).get(Constants.ISDELETED));
    }

    private List<Map<String, Object>> getExistingContextData(String userId) {
        Map<String, Object> query = Map.of(Constants.USERID_KEY, userId, Constants.CONTEXT_TYPE,
                Constants.SERVICE_HISTORY);
        List<Map<String, Object>> rows = cassandraOperation.getRecordsByPropertiesByKey(Constants.KEYSPACE_SUNBIRD,
                Constants.TABLE_USER_EXTENDED_PROFILE, query, null, null);
        if (rows != null && !rows.isEmpty()) {
            String json = (String) rows.get(0).get(Constants.CONTEXT_DATA);
            try {
                return projectUtil.parseListOfMap(json);
            } catch (IOException e) {
                log.error("Error parsing existing serviceHistory data for userId: {}", userId, e);
            }
        }
        return new ArrayList<>();
    }

    private boolean saveContextData(String userId, List<Map<String, Object>> dataList) {
        try {
            String finalJson = mapper.writeValueAsString(dataList);
            Map<String, Object> query = new HashMap<>();
            query.put(Constants.USERID_KEY, userId);
            query.put(Constants.CONTEXT_TYPE, Constants.SERVICE_HISTORY);
            query.put(Constants.CONTEXT_DATA, finalJson);
            ApiResponse insertResponse = (ApiResponse) cassandraOperation.insertRecord(Constants.KEYSPACE_SUNBIRD,
                    Constants.TABLE_USER_EXTENDED_PROFILE, query);
            return Constants.SUCCESS.equalsIgnoreCase((String) insertResponse.get(Constants.RESPONSE));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize serviceHistory data for userId: {}", userId, e);
        }
        return false;
    }
}
