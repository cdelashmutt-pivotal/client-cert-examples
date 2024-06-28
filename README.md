# Client Certificate with Actuators
Examples of some options on how to use client cert validation with a Spring app but allow actuators to work without a client cert.

## [Simple Example](simple/README.md)
`simple` contains a standalone Spring Boot 3.2 application that requires a trusted client cert for all endpoints except the health check actuator endpoint.

## Questions?
Reach out at [chris.delashmutt@broadcom.com](mailto:chris.delashmutt@broadcom.com?subject=Questions+about+https://github.com/cdelashmutt-pivotal/client-cert-examples.git ) if you want to discuss this.