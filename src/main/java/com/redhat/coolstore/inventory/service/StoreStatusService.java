package com.redhat.coolstore.inventory.service;

import java.io.StringReader;
import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonObject;
import javax.ws.rs.ServiceUnavailableException;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.wildfly.swarm.spi.runtime.annotations.ConfigurationValue;

@ApplicationScoped
public class StoreStatusService {

    private WebTarget storeService;

    @Inject
    @ConfigurationValue("store.service.url")
    private String storeUrl;

    public String storeStatus(String store) {
        Response response = storeService.resolveTemplate("store", store).request(MediaType.APPLICATION_JSON).get();
        if (response.getStatus() == 200) {
            JsonObject jsonResponse = Json.createReader(new StringReader(response.readEntity(String.class))).readObject();
            return jsonResponse.getString("status");
        } else if (response.getStatus() == 404) {
            return null;
        } else {
            throw new ServiceUnavailableException();
        }
    }


    @PostConstruct
    public void init() {
        storeService = ((ResteasyClientBuilder)ClientBuilder.newBuilder())
                .connectionPoolSize(10).build().target(storeUrl).path("store/status").path("{store}");
    }

}
