package com.pulseops.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.pulseops.exception.BusinessRuleException;
import com.pulseops.security.outbound.MonitoredUrlPolicy;
import com.pulseops.security.outbound.PinnedAddressResolverGroup;
import java.time.Duration;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;

/** Same SSRF policy and pinned DNS transport as monitoring. Redirects never forward credentials. */
@Component
public class IntegrationHttpClient {
    private final MonitoredUrlPolicy policy;
    private final WebClient.Builder builder;
    public IntegrationHttpClient(MonitoredUrlPolicy policy, WebClient.Builder builder) { this.policy=policy;this.builder=builder; }
    public Result exchange(String baseUrl,String path,HttpMethod method,Object payload,String token,String idempotencyKey) {
        try {
            Result result = Mono.fromCallable(() -> policy.validate(baseUrl,path)).subscribeOn(Schedulers.boundedElastic())
                .flatMap(target -> {
                    PinnedAddressResolverGroup resolver=new PinnedAddressResolverGroup(target);
                    HttpClient transport=HttpClient.newConnection().resolver(resolver).followRedirect(false);
                    WebClient client=builder.clone().codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                        .clientConnector(new ReactorClientHttpConnector(transport)).build();
                    WebClient.RequestBodySpec request=client.method(method).uri(target.targetUri());
                    if(token!=null && !token.isBlank()) request.headers(headers -> headers.setBearerAuth(token));
                    if(idempotencyKey!=null) request.header("Idempotency-Key",idempotencyKey);
                    return (payload==null ? request : request.bodyValue(payload)).exchangeToMono(response -> {
                        if(response.statusCode().value()>=300) return response.releaseBody().thenReturn(new Result(response.statusCode().value(),null));
                        return response.bodyToMono(JsonNode.class).map(body -> new Result(response.statusCode().value(),body))
                            .switchIfEmpty(Mono.just(new Result(response.statusCode().value(),null)));
                    }).doFinally(signal -> resolver.close());
                }).timeout(Duration.ofSeconds(10)).block();
            if(result==null) throw new BusinessRuleException("A integração não retornou uma resposta");
            return result;
        } catch(BusinessRuleException exception){throw exception;}
        catch(RuntimeException exception){throw new BusinessRuleException("Não foi possível comunicar com a integração. Verifique o endereço, a credencial e o tempo de resposta.");}
    }
    public record Result(int httpStatus,JsonNode body) { }
}
