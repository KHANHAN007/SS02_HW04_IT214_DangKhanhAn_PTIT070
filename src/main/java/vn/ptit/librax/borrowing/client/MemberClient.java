package vn.ptit.librax.borrowing.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import vn.ptit.librax.borrowing.dto.MemberResponse;

@Component
public class MemberClient {

    private final RestTemplate restTemplate;
    private final String memberServiceBaseUrl;

    public MemberClient(RestTemplate restTemplate,
                        @Value("${member-service.base-url}") String memberServiceBaseUrl) {
        this.restTemplate = restTemplate;
        this.memberServiceBaseUrl = memberServiceBaseUrl;
    }

    public MemberResponse getMemberById(Long memberId) {
        return restTemplate.getForObject(
                memberServiceBaseUrl + "/api/members/{memberId}",
                MemberResponse.class,
                memberId
        );
    }
}
