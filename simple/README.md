# Simple X.509 Validation While Allowing Access to Actuators

## Initial Setup
### Gen CA and import to keystore
First, let's generate an example CA we can use for testing.  The first command generates a self-signed cert to use as a CA.
```bash
openssl req -new -x509 \
  -keyout crypto/ca-key \
  -out crypto/ca-cert \
  -subj "/C=US/ST=Georgia/L=Canton/O=Broadcom Inc/OU=Tanzu/CN=Test CA" \
  -passout pass:casecret
```

Next, we need to trust that CA in our server key store so that we can add certs signed by it later on.
```bash
keytool \
  -keystore src/main/resources/keystore.jks \
  -alias CARoot -import -file crypto/ca-cert \
  -storepass storesecret \
  -noprompt
```

### Gen server key, cert request, and sign with CA
Next, we want to generate a new private key in the server keystore that we can use with a cert.
```bash
keytool \
  -genkey -alias simple -keyalg RSA \
  -keystore src/main/resources/keystore.jks \
  -keysize 2048 \
  -dname "CN=server,OU=Tanzu,O=Broadcom Inc,L=Canton,ST=Georgia,C=US" \
  -storepass storesecret
```

Now, we'll generate a Certificate Request for that private key we just generated in the keystore.
```bash
keytool \
  -keystore src/main/resources/keystore.jks \
  -alias simple -certreq -file crypto/server-csr \
  -ext san=dns:localhost,ip:127.0.0.1 \
  -storepass storesecret
```

Next, we'll sign the Certificate Request with the CA we generated in the beginning of these instructions.
```bash
openssl x509 -req \
  -CA crypto/ca-cert -CAkey crypto/ca-key \
  -in crypto/server-csr -out crypto/server-cert \
  -days 365 -CAcreateserial -copy_extensions copy \ 
  -passin pass:casecret
```
Finally, we'll import the signed certificate for the server back into our keystore
```bash
keytool \
  -keystore src/main/resources/keystore.jks \
  -alias simple -import -file crypto/server-cert \
  -storepass storesecret
```

### Import CA into truststore
Now, we want to set up a trust store to use to list out the CAs we will use to trust signed client certificates.  Since we're using the same CA to sign the server, we'll just import that CA cert into the truststore.
```bash
keytool \
  -keystore src/main/resources/truststore.jks \
  -alias RootCA -import -file crypto/ca-cert \
  -noprompt \
  -storepass trustsecret
```

## Client
Now, we'll need a key for the client and a cert signed by a CA in our trust store.

### Generate a key and cert signed by trusted CA for curl
First, we'll generate a new private key.
```bash
openssl req \
  -newkey rsa:2048 -keyout crypto/client-key \
  -out crypto/client-csr \
  -subj "/C=US/ST=Georgia/L=Canton/O=Broadcom Inc/OU=Tanzu/CN=client" \
  -passout pass:clientsecret
```
Now, we'll sign the Certificate Request that was generated when we created the private key.
```bash
openssl x509 -req \
  -CA crypto/ca-cert -CAkey crypto/ca-key \
  -in crypto/client-csr -out crypto/client-cert \
  -days 365 -CAcreateserial \
  -passin pass:casecret
```

### Generate a self signed cert that isn't signed by trusted CA
So that we can test calling the server with a cert that isn't signed by a trusted CA, let's generate a new key and self-signed certificate.
```bash
openssl req -x509 \
  -newkey rsa:4096 
  -keyout crypto/bad-client-key -out crypto/bad-client-cert \
  -sha256 -days 365 -nodes \
  -subj "/C=US/ST=Georgia/L=Canton/O=Broadcom Inc/OU=Tanzu/CN=bad-client"
```

### Start up the Server
If you followed the above instructions, you should now be able to start up the server for testing with:
```bash
./mvnw spring-boot:run
```

## Testing with `curl`

### Client cert signed by Trusted CA
Use this command to try a curl against the server to validate everything works
```bash
curl https://localhost:8080/message \
  --cacert crypto/ca-cert \
  --cert crypto/client-cert:clientsecret --key crypto/client-key
```
You should see the string response of `Secure client access!`

### No client cert given and accessing secure endpoint
Now, let's try the same request without passing in a client certificate
```bash
curl https://localhost:8080/message --cacert crypto/ca-cert -v
```

Notice this time, we get an HTTP 403 Response code as we didn't provide a valid client cert.

### Client cert signed by non-trusted CA
Let's try the case where we provide a certificate, but it's signed by a CA that isn't trusted.

```bash
curl https://localhost:8080/message --cacert crypto/ca-cert --cert crypto/bad-client-cert --key crypto/bad-client-key -v
```

This time we get blocked by the server connector because it tried to validate the certificate and that validation failed due to the CA for our bad client certificate not being in the trust store configured for the server.

### Actuators are allowed with no certificate
Now, we can invoke the actuator health endpoint without passing in a client certificate.
```bash
curl https://localhost:8080/actuator/health --cacert crypto/ca-cert
```

Note that if we try to pass in the bad client certificate to request the actuator health endpoint, it would still be blocked at the server connector level.

## Key Configurations
There are a few key elements this setup is relying on.  First, we're configuring the server connector for the embedded servlet container started by Spring Boot in the [application.yaml](src/main/resources/application.yaml) file for the project.  We configure an SSL bundle with a keystore and truststore (generated above), and then assign that bundle to the server's SSL config.  When assigning it, we need to make sure we set `client-auth: WANT` as that will ask for client certificates, but not stop a request if none is passed.  We need this relaxed posture to be able to allow Spring Security to make authorization decisions based on the request path.

Next, we need to make sure to include the Spring Security dependency in the [pom.xml](pom.xml) file so that Spring Security is initialized for the app.
```xml
...
<dependencies>
...
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-security</artifactId>
		</dependency>
...
</dependencies>
...
```

Next, we need to provide a configuration class that provides beans to configure custom settings for Spring Security.  These configurations are provided in the [SecurityConfig.java](src/main/java/com/vmware/tanzu/se/simple/SecurityConfig.java) file.

In this file we first supply a bean to specify the security chain configuration for the app.

The first line in the security chain configuration, specifies that any caller can invoke the `/actuator/health` endpoint.
```java
.authorizeHttpRequests((requests) -> requests.requestMatchers("/actuator/health").permitAll())
```

The next line specifies that only authenticated users may invoke any other endpoint.
```java
.authorizeHttpRequests((requests) -> requests.requestMatchers("/**").authenticated())
```

The final configuration line sets up Spring Security to extract security principal info from the X.509 Client Certificate passed into the request.  This default setup extracts the Common Name (CN attribute) from the certificate to use as the "user id" for the authenticated principal making the request.  If no certificate is supplied, then the request is considered un-authenticated.
```java
.x509(Customizer.withDefaults());
```

Further in the configuration, we supply a custom UserDetailsService that is used to supply a User object for the request so that Spring Security marks the request as authenticated.

The [SimpleUserDetailsService.java](src/main/java/com/vmware/tanzu/se/simple/SimpleUserDetailsService.java) simply accepts the CN value that is extracted from the X.509 Certificate passed in (and only valid certificates signed by a trusted CA are allowed to ever reach this point, per the server config) and uses that to create just enough of a User object to satisfy Spring Security that we believe the request is authenticated.  You could absolutely use a real UserDetailsService implementation that looked up information from some other source, but this implementation is set up simply to trust the client certificate data, since it should only be signed by a trusted CA.