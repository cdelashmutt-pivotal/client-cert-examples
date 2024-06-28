# Server Setup
## Gen CA and import to keystore
openssl req -new -x509 -keyout crypto/ca-key -out crypto/ca-cert -subj "/C=US/ST=Georgia/L=Canton/O=Broadcom Inc/OU=Tanzu/CN=Test CA" -passout pass:casecret
keytool -keystore src/main/resources/keystore.jks -alias CARoot -import -file crypto/ca-cert -storepass storesecret -noprompt

## Gen server key, cert request, and sign with CA
keytool -genkey -alias simple -keyalg RSA -keystore src/main/resources/keystore.jks -keysize 2048 -dname "CN=server,OU=Tanzu,O=Broadcom Inc,L=Canton,ST=Georgia,C=US" -storepass storesecret
keytool -keystore src/main/resources/keystore.jks -alias simple -certreq -file crypto/server-csr -ext san=dns:localhost,ip:127.0.0.1 -storepass storesecret
openssl x509 -req -CA crypto/ca-cert -CAkey crypto/ca-key -in crypto/server-csr -out crypto/server-cert -days 365 -CAcreateserial -copy_extensions copy -passin pass:casecret
keytool -keystore src/main/resources/keystore.jks -alias simple -import -file crypto/server-cert -storepass storesecret

## Import CA into truststore
keytool -keystore src/main/resources/truststore.jks -alias RootCA -import -file crypto/ca-cert -noprompt -storepass trustsecret

# Client
## Generate a key and cert signed by trusted CA for curl
openssl req -newkey rsa:2048 -keyout crypto/client-key -out crypto/client-csr -subj "/C=US/ST=Georgia/L=Canton/O=Broadcom Inc/OU=Tanzu/CN=client" -passout pass:clientsecret
openssl x509 -req -CA crypto/ca-cert -CAkey crypto/ca-key -in crypto/client-csr -out crypto/client-cert -days 365 -CAcreateserial -passin pass:casecret

## Generate a self signed cert that isn't signed by trusted CA
openssl req -x509 -newkey rsa:4096 -keyout crypto/bad-client-key -out crypto/bad-client-cert -sha256 -days 365 -nodes -subj "/C=US/ST=Georgia/L=Canton/O=Broadcom Inc/OU=Tanzu/CN=bad-client"

## Testing with curl
Start up the server, and use this command to try a curl against the server to validate everything works
`curl https://localhost:8080/message --cacert crypto/ca-cert --cert crypto/client-cert:clientsecret --key crypto/client-key -v`
And to see the negative case, don't pass in a client cert
`curl https://localhost:8080/message --cacert crypto/ca-cert -v`
And to see the negative case where we pass in a non-trusted cert
`curl https://localhost:8080/message --cacert crypto/ca-cert --cert crypto/bad-client-cert --key crypto/bad-client-key -v`
And to see the actuator health endpoint allowed when you don't pass in a cert
`curl https://localhost:8080/actuator/health --cacert crypto/ca-cert -v`