package com.redhat.coolstore.inventory;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(Arquillian.class)
public class RestApiTest {

    private static String port = "18080";
    
    private Client client;

    @Deployment
    public static Archive<?> createDeployment() {
        return ShrinkWrap.create(WebArchive.class)
                .addPackages(true, RestApplication.class.getPackage())
                .addAsResource("project-local.yml", "project-defaults.yml")
                .addAsResource("META-INF/test-persistence.xml",  "META-INF/persistence.xml")
                .addAsResource("META-INF/test-load.sql",  "META-INF/test-load.sql")
                .addAsWebInfResource("test-beans.xml", "beans.xml");
    }

    @Before
    public void before() throws Exception {
        client = ClientBuilder.newClient();
    }

    @After
    public void after() throws Exception {
        client.close();
    }

    @Test
    @RunAsClient
    public void testGetInventory() throws Exception {
        WebTarget target = client.target("http://localhost:" + port).path("/inventory").path("/123456");
        Response response = target.request(MediaType.APPLICATION_JSON).get();
        assertThat(response.getStatus(), equalTo(new Integer(200)));
        JsonObject value = Json.parse(response.readEntity(String.class)).asObject();
        assertThat(value.getString("itemId", null), equalTo("123456"));
        assertThat(value.getString("location", null), equalTo("location"));
        assertThat(value.getInt("quantity", 0), equalTo(new Integer(99)));
        assertThat(value.getString("link", null), equalTo("link"));
    }

    @Test
    @RunAsClient
    public void testGetInventoryWithStoreStatus() throws Exception {
        WebTarget target = client.target("http://localhost:" + port).path("/inventory").path("/123456").queryParam("storeStatus", true);
        Response response = target.request(MediaType.APPLICATION_JSON).get();
        assertThat(response.getStatus(), equalTo(new Integer(200)));
        JsonObject value = Json.parse(response.readEntity(String.class)).asObject();
        assertThat(value.getString("itemId", null), equalTo("123456"));
        assertThat(value.getString("location", null), equalTo("location [MOCK]"));
        assertThat(value.getInt("quantity", 0), equalTo(new Integer(99)));
        assertThat(value.getString("link", null), equalTo("link"));
    }

    @Test
    @RunAsClient
    public void testGetInventorWhenItemIdDoesNotExist() throws Exception {
        WebTarget target = client.target("http://localhost:" + port).path("/inventory").path("/doesnotexist");
        Response response = target.request(MediaType.APPLICATION_JSON).get();
        assertThat(response.getStatus(), equalTo(new Integer(404)));
    }
    
    @Test
    @RunAsClient
    public void testHealthCheck() throws Exception {
        WebTarget target = client.target("http://localhost:" + port).path("/health");
        Response response = target.request(MediaType.APPLICATION_JSON).get();
        assertThat(response.getStatus(), equalTo(new Integer(200)));
        JsonObject value = Json.parse(response.readEntity(String.class)).asObject();
        assertThat(value.getString("outcome", ""), equalTo("UP"));
        JsonArray checks = value.get("checks").asArray();
        assertThat(checks.size(), equalTo(new Integer(1)));
        JsonObject state = checks.get(0).asObject();
    }

}
