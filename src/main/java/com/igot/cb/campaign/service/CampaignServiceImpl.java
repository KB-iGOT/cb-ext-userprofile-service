package com.igot.cb.campaign.service;

import com.igot.cb.transactional.cassandrautils.CassandraOperation;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.Constants;
import com.igot.cb.util.ProjectUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@Slf4j
public class CampaignServiceImpl implements CampaignService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[0-9]{10}$");

    private final CassandraOperation cassandraOperation;

    public CampaignServiceImpl(CassandraOperation cassandraOperation) {
        this.cassandraOperation = cassandraOperation;
    }

    @Override
    public ApiResponse createLead(Map<String, Object> requestBody) {
        ApiResponse apiResponse = ProjectUtil.createDefaultResponse(Constants.API_CAMPAIGN_REGISTER);

        try {
            if (!validation(apiResponse, requestBody)) {
                return apiResponse;
            }

            Map<String, Object> data = getRequestData(requestBody);
            Map<String, Object> record = buildLeadRecord(data);
            ApiResponse insertResponse = (ApiResponse) cassandraOperation.insertRecord(
                    Constants.KEYSPACE_SUNBIRD,
                    Constants.TABLE_CAMPAIGN_LEAD,
                    record
            );

            if (insertResponse != null && Constants.SUCCESS.equalsIgnoreCase((String) insertResponse.get(Constants.RESPONSE))) {
                apiResponse.getResult().put(Constants.RESPONSE, Constants.SUCCESS);
            } else {
                ProjectUtil.errorResponse(apiResponse, "Failed to save campaign lead record", HttpStatus.INTERNAL_SERVER_ERROR);
            }
        } catch (Exception ex) {
            log.error("Exception occurred while creating campaign lead: {}", ex.getMessage(), ex);
            ProjectUtil.errorResponse(apiResponse, "Internal server error occurred while processing campaign lead", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return apiResponse;
    }

    @Override
    public boolean validation(ApiResponse apiResponse, Map<String, Object> requestBody) {
        if (MapUtils.isEmpty(requestBody)) {
            ProjectUtil.errorResponse(apiResponse, "Request body cannot be empty", HttpStatus.BAD_REQUEST);
            return false;
        }

        Map<String, Object> data = getRequestData(requestBody);

        // Validate mandatory non-empty fields
        for (String field : List.of(Constants.CAMPAIGN_ID, Constants.PHONE, Constants.NAME, Constants.CONSENT_VERSION, Constants.SOURCE)) {
            Object val = data.get(field);
            if (val == null || StringUtils.isBlank(val.toString())) {
                ProjectUtil.errorResponse(apiResponse, field + " cannot be null or empty", HttpStatus.BAD_REQUEST);
                return false;
            }
        }

        if (!Constants.CAMPAIGN_SEVA_BHAV.equals(data.get(Constants.CAMPAIGN_ID))) {
            ProjectUtil.errorResponse(apiResponse, "Invalid campaignid. Only 'Seva Bhav' is accepted", HttpStatus.BAD_REQUEST);
            return false;
        }

        if (!PHONE_PATTERN.matcher(data.get(Constants.PHONE).toString().trim()).matches()) {
            ProjectUtil.errorResponse(apiResponse, "Invalid mobile number. It must be a 10-digit number", HttpStatus.BAD_REQUEST);
            return false;
        }

        // consentVersion validation (must be positive integer)
        Object versionObj = data.get(Constants.CONSENT_VERSION);
        if (!(versionObj instanceof Number n) || n.intValue() < 1) {
            ProjectUtil.errorResponse(apiResponse, "consentVersion must be a valid positive number", HttpStatus.BAD_REQUEST);
            return false;
        }

        // Email validation (optional: only validate if present and non-blank)
        Object emailObj = data.get(Constants.EMAIL);
        if (emailObj != null && StringUtils.isNotBlank(emailObj.toString())) {
            if (!EMAIL_PATTERN.matcher(emailObj.toString().trim()).matches()) {
                ProjectUtil.errorResponse(apiResponse, "Invalid email pattern", HttpStatus.BAD_REQUEST);
                return false;
            }
        }

        // Consent validation
        Object consent = data.get(Constants.CONSENT);
        if (consent == null) {
            ProjectUtil.errorResponse(apiResponse, "consent cannot be null", HttpStatus.BAD_REQUEST);
            return false;
        }
        if (!(consent instanceof Boolean)) {
            ProjectUtil.errorResponse(apiResponse, "consent must be a boolean value", HttpStatus.BAD_REQUEST);
            return false;
        }

        return true;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getRequestData(Map<String, Object> requestBody) {
        Map<String, Object> data = requestBody;
        if (data.get(Constants.REQUEST) instanceof Map<?, ?> req) {
            data = (Map<String, Object>) req;
        }
        if (data.get(Constants.DATA) instanceof Map<?, ?> innerData) {
            data = (Map<String, Object>) innerData;
        }
        return data;
    }

    private Map<String, Object> buildLeadRecord(Map<String, Object> data) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put(Constants.CAMPAIGN_ID, data.get(Constants.CAMPAIGN_ID));
        record.put(Constants.PHONE, data.get(Constants.PHONE).toString().trim());
        record.put(Constants.CONSENT_VERSION, ((Number) data.get(Constants.CONSENT_VERSION)).intValue());
        record.put(Constants.NAME, data.get(Constants.NAME).toString().trim());
        record.put(Constants.CONSENT, data.get(Constants.CONSENT));
        record.put(Constants.SOURCE, data.get(Constants.SOURCE).toString().trim());
        Object email = data.get(Constants.EMAIL);
        if (email != null && StringUtils.isNotBlank(email.toString())) {
            record.put(Constants.EMAIL, email.toString().trim());
        }
        return record;
    }
}
