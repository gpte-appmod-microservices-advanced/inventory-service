package com.redhat.coolstore.inventory;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Security;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.HashSet;
import java.util.Properties;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;
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
import org.keycloak.jose.jws.Algorithm;
import org.keycloak.jose.jws.JWSBuilder;
import org.keycloak.representations.AccessToken;
import org.keycloak.util.TokenUtil;
import org.wildfly.swarm.Swarm;
import org.wildfly.swarm.arquillian.CreateSwarm;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;

@RunWith(Arquillian.class)
public class RestApiTest {

    private static String port = System.getProperty("arquillian.swarm.http.port", "18080");
    
    private Client client;

    @CreateSwarm
    public static Swarm newContainer() throws Exception {
        Properties properties = new Properties();
        properties.put("swarm.http.port", port);
        return new Swarm(properties).withProfile("local");
    }

    @Deployment
    public static Archive<?> createDeployment() {
        return ShrinkWrap.create(WebArchive.class, "inventory-service.war")
                .addPackages(true, RestApplication.class.getPackage())
                .addAsResource("project-local.yml", "project-local.yml")
                .addAsResource("META-INF/test-persistence.xml",  "META-INF/persistence.xml")
                .addAsResource("META-INF/test-load.sql",  "META-INF/test-load.sql")
                .addAsWebInfResource("test-beans.xml", "beans.xml")
                .addAsWebInfResource("keycloak.json","keycloak.json");
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
        
        Response response = target.request(MediaType.APPLICATION_JSON)
        	    .header("Authorization", "Bearer " + getValidAccessToken("coolstore")).get();
        
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

        Response response = target.request(MediaType.APPLICATION_JSON)
        	    .header("Authorization", "Bearer " + getValidAccessToken("coolstore")).get();

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

        Response response = target.request(MediaType.APPLICATION_JSON)
        	    .header("Authorization", "Bearer " + getValidAccessToken("coolstore")).get();

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
        assertThat(state.getString("name", ""), equalTo("server-state"));
        assertThat(state.getString("state", ""), equalTo("UP"));
    }
    
    private PrivateKey readPrivateKey() throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        KeyFactory factory = KeyFactory.getInstance("RSA", "BC");
        InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream("private.pem");
        PemReader privateKeyReader = new PemReader(new InputStreamReader(is));
        try {
            PemObject privObject = privateKeyReader.readPemObject();
            PKCS8EncodedKeySpec privKeySpec = new PKCS8EncodedKeySpec(privObject.getContent());
            PrivateKey privateKey = factory.generatePrivate(privKeySpec);
            return privateKey;
        } finally {
            privateKeyReader.close();
        }
    }    
    
    private String createAccessToken(String role, int issuedAt) throws Exception {
        AccessToken token = new AccessToken();
        token.type(TokenUtil.TOKEN_TYPE_BEARER);
        token.subject("testuser");
        token.issuedAt(issuedAt);
        token.issuer("https://rhsso:8443/auth/realms/coolstore-test");
        token.expiration(issuedAt + 300);
        token.setAllowedOrigins(new HashSet<>());

        AccessToken.Access access = new AccessToken.Access();
        token.setRealmAccess(access);
        access.addRole(role);

        Algorithm jwsAlgorithm = Algorithm.RS256;
        PrivateKey privateKey = readPrivateKey();
        String encodedToken = new JWSBuilder().type("JWT").jsonContent(token).sign(jwsAlgorithm, privateKey);
        return encodedToken;
    }
    
    private String getValidAccessToken(String role) throws Exception {
        return createAccessToken(role, (int) (System.currentTimeMillis() / 1000));
    }

    private String getExpiredAccessToken(String role) throws Exception {
        return createAccessToken(role, (int) ((System.currentTimeMillis() / 1000)-600));
    }    
}
