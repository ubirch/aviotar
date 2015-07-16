# ubirch Server

The ubirch server is a configurable appliance that can run apps that handle data
sent by sensors or other equipment.

- ElasticSearchSink is a an app that stores sensor data received via MQTT in
  an ElasticSearch cluster.

## LICENSE

    Copyright 2015 ubirch GmbH (http://www.ubirch.com)

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

# Docker build
The Dockfile in this directory is meant to build a Docker container for the ubirch-server.

The container uses the ubirch/java Docker image as base and copies the ubirch-server.jar
into the /opt directory of the container. The example.conf file is also copied
to /opt and called in the Docker ENTRYPOINT as parameter to the ubirch-server.jar.

The container is meant to be linked to a MQTT container holding the broker and
and ElasticSearch Container providing the logs. That's why the example.conf is
referring to simple hostnames for those.

Assuming you have an Docker container for MQTT name "mqtt" and one for ElasticSearch
name "search" then you can start the ubirch-server like this:

```docker run --link mqtt:mqtt --link search:elastic -t ubirch/ubirch-server```
 
