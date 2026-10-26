package com.igot.cb.profile.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.transactional.cassandrautils.CassandraOperation;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.Constants;
import com.igot.cb.util.ProjectUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Appends a serviceHistory entry for a user when a DESIGNATION_CHANGE workflow request is
 * approved (consumed off Kafka from sunbird-cb-workflow). Append-only: never reads or mutates
 * any existing serviceHistory entry. Dedups on wfId so a redelivered event is a no-op.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ServiceHistoryService {

    private final CassandraOperation cassandraOperation;

    private final ProjectUtil projectUtil;

    private final ObjectMapper mapper;

    private final ProfileServiceImpl profileServiceImpl;

    /**
     * Appends a new serviceHistory entry for a user following an approved DESIGNATION_CHANGE
     * workflow request. Append-only: existing entries in the list are never read for mutation or
     * removed, only a new entry is added. Idempotent: if an entry for this wfId already exists
     * (e.g. a redelivered Kafka message), this is a no-op that returns true.
     *
     * @param userId      the user to update
     * @param wfId        the approved workflow request id, used for the dedup check and stored
     *                    on the new entry
     * @param designation the newly approved designation
     * @param orgName     the user's org name at approval time
     * @return true if the entry was appended (or already present); false if persistence failed
     */
    public boolean appendDesignationApprovalToServiceHistory(String userId, String wfId, String designation,
            String orgName) {
        try {
            List<Map<String, Object>> existingList = getExistingContextData(userId);

            boolean alreadyProcessed = existingList.stream()
                    .anyMatch(entry -> StringUtils.equals(wfId, (String) entry.get(Constants.WF_IF)));
            if (alreadyProcessed) {
                log.info("Service history already contains an entry for wfId: {}, userId: {}; skipping duplicate",
                        wfId, userId);
                return true;
            }
            Map<String, Object> newEntry = new HashMap<>();
            newEntry.put(Constants.ORG_NAME, orgName);
            newEntry.put(Constants.SEARCH_ORG_NAME, StringUtils.EMPTY);
            newEntry.put(Constants.DESIGNATION, designation);
            newEntry.put(Constants.SEARCH_DESIGNATION, StringUtils.EMPTY);
            newEntry.put(Constants.ORG_STATE, StringUtils.EMPTY);
            newEntry.put(Constants.ORG_DISTRICT, StringUtils.EMPTY);
            newEntry.put(Constants.START_DATE, StringUtils.EMPTY);
            newEntry.put(Constants.END_DATE, StringUtils.EMPTY);
            newEntry.put(Constants.CURRENTLY_WORKING, Constants.FALSE);
            newEntry.put(Constants.DESCRIPTION, StringUtils.EMPTY);
            newEntry.put(Constants.ORG_ID, StringUtils.EMPTY);
            newEntry.put(Constants.ROOT_ORG_ID, StringUtils.defaultString(getUserRootOrgId(userId)));
            newEntry.put(Constants.UUID, UUID.randomUUID().toString());
            newEntry.put(Constants.APPROVED_VIA_WORKFLOW, true);
            newEntry.put(Constants.WF_IF, wfId);
            existingList.add(newEntry);
            if (!saveContextData(userId, existingList)) {
                log.error("Failed to persist designation-approval service history for userId: {}, wfId: {}",
                        userId, wfId);
                return false;
            }
            return true;
        } catch (Exception e) {
            log.error("Error appending designation-approval service history for userId: {}, wfId: {}", userId, wfId,
                    e);
            return false;
        }
    }

    /**
     * Resolves the user's current rootOrgId via {@link ProfileServiceImpl#readUserDataFromDB},
     * the same lookup {@code getBasicProfile} uses for the user table.
     *
     * @param userId the user to look up
     * @return the user's rootOrgId, or null if the user record has no value for it
     */
    private String getUserRootOrgId(String userId) {
        Map<String, Object> userData = profileServiceImpl.readUserDataFromDB(userId, List.of(Constants.ROOT_ORG_ID));
        return (String) userData.get(Constants.ROOT_ORG_ID);
    }

    /**
     * Reads the user's current serviceHistory list from user_extended_profile.
     *
     * @param userId the user to look up
     * @return the existing serviceHistory entries, or an empty (mutable) list if none exist or
     *         the stored JSON fails to parse
     */
    private List<Map<String, Object>> getExistingContextData(String userId) {
        Map<String, Object> query = Map.of(Constants.USERID_KEY, userId, Constants.CONTEXT_TYPE,
                Constants.SERVICE_HISTORY);
        List<Map<String, Object>> rows = cassandraOperation.getRecordsByPropertiesByKey(Constants.KEYSPACE_SUNBIRD,
                Constants.TABLE_USER_EXTENDED_PROFILE, query, null, null);
        if (CollectionUtils.isNotEmpty(rows)) {
            String json = (String) rows.get(0).get(Constants.CONTEXT_DATA);
            try {
                return projectUtil.parseListOfMap(json);
            } catch (IOException e) {
                log.error("Error parsing existing serviceHistory data for userId: {}", userId);
            }
        }
        return new ArrayList<>();
    }

    /**
     * Persists the full serviceHistory list back to user_extended_profile, overwriting the
     * stored contextData JSON for this user/contextType.
     *
     * @param userId   the user to persist for
     * @param dataList the complete serviceHistory list to write (existing entries plus the new one)
     * @return true if the Cassandra write succeeded
     */
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
            log.error("Failed to serialize serviceHistory data for userId: {}", userId);
        }
        return false;
    }
}
