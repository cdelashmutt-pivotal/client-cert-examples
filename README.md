# Client Certificate with Actuators
Examples of some options on how to use client cert validation with a Spring app but allow actuators to work without a client cert.

## [Simple Example](simple/README.md)
`simple` contains a standalone Spring Boot 3.2 application that requires a trusted client cert for all endpoints except the health check actuator endpoint.