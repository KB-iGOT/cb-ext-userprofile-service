package com.igot.cb.common;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
@Setter
public class CbExtServerProperties {

    @Value("${sb.service.url}")
    private String sbUrl;

    @Value("${lms.user.read.path}")
    private String lmsUserReadPath;

    @Value("${lms.user.update.path}")
    private String lmsUserUpdatePath;

    @Value("${sb.api.key}")
    private String sbApiKey;
}
