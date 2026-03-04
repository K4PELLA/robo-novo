package com.arbhft.controller;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/proxy")
public class ProxyController {
    private static final List<String> BLOCKED=List.of("host","content-length","transfer-encoding","connection","accept-encoding");
    private final WebClient client=WebClient.builder()
        .defaultHeader("User-Agent","Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
        .defaultHeader("Accept","application/json")
        .defaultHeader("Accept-Encoding","identity")
        .codecs(c->c.defaultCodecs().maxInMemorySize(4*1024*1024)).build();
    @RequestMapping(value="/**",method={RequestMethod.GET,RequestMethod.POST,RequestMethod.PUT,RequestMethod.DELETE})
    public Mono<ResponseEntity<String>> proxy(@RequestBody(required=false) String body,@RequestHeader HttpHeaders headers,@RequestParam(name="_target") String target,HttpMethod method){
        System.out.println("[PROXY] "+method+" -> "+target);
        WebClient.RequestBodySpec req=client.method(method).uri(URI.create(target))
            .headers(h->{
                headers.forEach((name,values)->{if(BLOCKED.stream().noneMatch(b->b.equalsIgnoreCase(name)))values.forEach(v->h.add(name,v));});
            });
        if(body!=null&&!body.isBlank())req.contentType(MediaType.APPLICATION_JSON).bodyValue(body);
        return req.retrieve().toEntity(String.class)
            .onErrorResume(WebClientResponseException.class,e->{
                System.out.println("[PROXY] "+e.getStatusCode()+" body: "+e.getResponseBodyAsString());
                return Mono.just(ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString()));
            })
            .onErrorResume(e->{
                System.err.println("[PROXY] Erro: "+e.getMessage());
                return Mono.just(ResponseEntity.status(502).body("{\"error\":\""+e.getMessage()+"\"}"));
            });
    }
}
